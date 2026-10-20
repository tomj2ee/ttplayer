package org.ttplayer.engine;

import org.ttplayer.model.PlaylistManager;
import org.ttplayer.model.Song;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.*;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import org.ttplayer.util.Messages;

/**
 * 音频播放引擎 — JavaCV(FFmpeg) 解码 + javax.sound 输出
 * 统一处理 MP3 / FLAC / WAV / OGG 等格式，支持拖动 seek。
 */
public class PlayerEngine {

    private PlaylistManager playlistManager;
    private int currentSongIndex = -1;

    /** 图形均衡器（biquad），启用后对输出 PCM 滤波 */
    private final Equalizer equalizer = new Equalizer();

    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean stopped;

    private volatile long positionUs;
    private volatile long durationUs;

    // 频谱分析用单声道环形缓冲（立体声左右平均，对应 C 的 pcm_push）
    // 容量 16384 单声道帧（≈372ms），需容纳音频输出缓冲 + 分析窗，以便做播放位置对齐。
    private final short[] vizRing = new short[16384];
    private int vizHead = 0;
    /** 累计已解码写入环缓冲的单声道帧数（单调递增）。 */
    private volatile long vizPushed = 0;
    /** 延迟补偿基准：flush/seek 后重新对齐，使「已缓冲未播放」帧数计算仍然正确。 */
    private long vizSyncPushed = 0;
    private long vizSyncPlayed = 0;
    private volatile int vizCount = 0;
    /** 频谱重置世代号：每次换曲/停止自增，界面侧据此做一次性清屏。 */
    private volatile int vizResetGen = 0;
    private byte[] vizOut;

    private Thread playThread;
    private SourceDataLine line;
    private final Object stateLock = new Object();

    private volatile File currentFile;
    private volatile Song currentSong;

    private volatile boolean seekRequested;
    private volatile long seekTargetUs;

    /** 当前音量百分比（0-100），与音量条保持一致 */
    private volatile int volumePercent = 70;

    /** 静音状态：静音时增益压到最低 */
    private volatile boolean muted;

    private PlayerEngineListener listener;

    public interface PlayerEngineListener {
        void onPlaybackStarted(Song song);
        void onPlaybackStopped();
        void onPlaybackPaused();
        void onPlaybackResumed();
        void onPlaybackComplete();
        void onPlaybackError(String error);
    }

    public PlayerEngine(PlaylistManager playlistManager) {
        this.playlistManager = playlistManager;
    }

    public void setListener(PlayerEngineListener listener) { this.listener = listener; }

    // ======================== 公共控制 ========================

    public void play(Song song) {
        if (song == null || song.filePath == null) return;
        stopPlayback();
        currentFile = new File(song.filePath);
        currentSong = song;
        startPlayback();
    }

    public void play(int index) {
        if (playlistManager == null || playlistManager.getCurrentPlaylist() == null) return;
        List<Song> songs = playlistManager.getCurrentPlaylist().songs;
        if (index < 0 || index >= songs.size()) return;
        currentSongIndex = index;
        play(songs.get(index));
    }

    public void playPause() {
        if (playThread == null || !playThread.isAlive()) return;
        if (paused) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    public void stop() {
        stopPlayback();
        currentSongIndex = -1;
        currentFile = null;
        currentSong = null;
    }

    public void previous() {
        if (playlistManager == null || playlistManager.getCurrentPlaylist() == null) return;
        List<Song> songs = playlistManager.getCurrentPlaylist().songs;
        if (songs.isEmpty()) return;
        int idx = currentSongIndex - 1;
        if (idx < 0) idx = songs.size() - 1;
        currentSongIndex = idx;
        play(songs.get(idx));
    }

    public void next() {
        if (playlistManager == null || playlistManager.getCurrentPlaylist() == null) return;
        List<Song> songs = playlistManager.getCurrentPlaylist().songs;
        if (songs.isEmpty()) return;
        int idx = currentSongIndex + 1;
        if (idx >= songs.size()) idx = 0;
        currentSongIndex = idx;
        play(songs.get(idx));
    }

    public void seekTo(int seconds) {
        if (seconds < 0 || currentFile == null) return;
        seekToMs(seconds * 1000L);
    }

    /** 毫秒级 seek：目标时间写入后由解码循环在下一个循环位置应用 */
    public void seekToMs(long ms) {
        if (ms < 0 || currentFile == null) return;
        seekTargetUs = ms * 1000L;
        positionUs = seekTargetUs;
        seekRequested = true;
        synchronized (stateLock) { stateLock.notifyAll(); }
    }

    public void setGainFromPercent(int percent) {
        volumePercent = Math.max(0, Math.min(100, percent));
        applyVolume();
    }

    public void mute() {
        muted = !muted;
        applyVolume();
    }

    public void setMuted(boolean m) {
        muted = m;
        applyVolume();
    }

    public boolean isMuted() { return muted; }

    // ======================== 内部 ========================

    private void startPlayback() {
        if (currentFile == null || !currentFile.exists()) {
            SwingUtilities.invokeLater(() -> {
                if (listener != null) listener.onPlaybackError(org.ttplayer.util.Messages.get("player.fileNotFound"));
            });
            return;
        }

        playing = true;
        paused = false;
        stopped = false;

        playThread = new Thread(this::decodeLoop, "playback");
        playThread.setDaemon(true);
        playThread.start();
    }

    // ==================== FFmpeg 解码循环 ====================

    private void decodeLoop() {
        FFmpegFrameGrabber grabber = null;
        SourceDataLine dl = null;

        try {
            grabber = new FFmpegFrameGrabber(currentFile);
            grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
            grabber.setSampleRate(44100);
            grabber.setAudioChannels(2);
            grabber.start();

            long lenUs = grabber.getLengthInTime();
            durationUs = lenUs > 0 ? lenUs : 0;

            AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
            dl = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            // 显式指定音频缓冲为 ~93ms（4096 帧 × 4 字节）。
            // 默认缓冲在 Windows 上常达 0.5s+，会让 dl.write() 长时间阻塞 ——
            // 解码线程卡在 write 上时 vizRing 不更新，频谱观感就是「冻结后突然跳跃」。
            // 缩小缓冲后 write 阻塞粒度变细（~20ms 一次），频谱数据供给平滑，
            // 同时把「频谱超前于声音」的时差压到原版 DirectSound 同量级。
            dl.open(fmt, 16384);
            dl.start();
            this.line = dl;
            applyVolume();
            // 新曲目开始：建立对齐基准（此时线位置为 0，环缓冲为空），
            // 使 lag 从 0 起算，频谱可以立刻显示而不必等待缓冲填满。
            resetVisualAlign(dl);

            final Song startSong = currentSong;
            SwingUtilities.invokeLater(() -> {
                if (listener != null && startSong != null) {
                    listener.onPlaybackStarted(startSong);
                }
            });

            while (playing && !stopped) {
                if (seekRequested) {
                    try {
                        grabber.setTimestamp(seekTargetUs);
                        positionUs = seekTargetUs;
                    } catch (Exception ignored) {
                        // seek 失败时保持当前位置继续播放
                    }
                    // seek 会丢弃已缓冲音频：重设对齐基准，避免 lag 计算出负延迟
                    try { dl.flush(); } catch (Exception ignored) {}
                    resetVisualAlign(dl);
                    seekRequested = false;
                    continue;
                }

                synchronized (stateLock) {
                    while (paused && !stopped) {
                        if (dl.isRunning()) dl.flush();
                        try { stateLock.wait(); } catch (InterruptedException e) { break; }
                        // 恢复播放：缓冲已被 flush，重设基准与环缓冲，避免旧数据残留
                        resetVisualAlign(dl);
                        dl.start();
                        applyVolume();
                    }
                }
                if (stopped) break;

                Frame frame = grabber.grabSamples();
                if (frame == null) break;
                if (frame.samples == null || frame.samples.length == 0) continue;

                Buffer sampleBuf = frame.samples[0];
                if (!(sampleBuf instanceof ShortBuffer)) continue;
                ShortBuffer sb = (ShortBuffer) sampleBuf;
                int count = sb.remaining();
                if (count <= 0) continue;

                byte[] pcm = new byte[count * 2];
                sb.rewind();
                for (int i = 0; i < count; i++) {
                    short s = sb.get();
                    pcm[2 * i] = (byte) (s & 0xFF);
                    pcm[2 * i + 1] = (byte) ((s >> 8) & 0xFF);
                }

                // 均衡器（启用时原地滤波）
                equalizer.process(pcm);

                // 立体声左右平均 → 单声道环缓冲（对应 C pcm_push，取滤波后数据）。
                // 关键：必须在 dl.write() 之前入环 —— dl.write 是阻塞调用，
                // 若放在其后，解码线程阻塞期间环缓冲不更新，频谱会「冻结后跳跃」。
                int mono = count / 2;
                for (int m = 0; m < mono; m++) {
                    int base = m * 4;
                    int l = ((pcm[base + 1] << 8) | (pcm[base] & 0xFF));
                    int r = ((pcm[base + 3] << 8) | (pcm[base + 2] & 0xFF));
                    short s = (short) ((l + r) / 2);
                    vizRing[vizHead] = s;
                    vizHead = (vizHead + 1) % vizRing.length;
                    if (vizCount < vizRing.length) vizCount++;
                }
                vizPushed += mono;

                dl.write(pcm, 0, pcm.length);

                long ts = frame.timestamp;
                if (ts > 0) {
                    positionUs = ts;
                } else {
                    positionUs += count * 1000000L / 44100;
                }
            }

            if (!stopped && playing) {
                positionUs = durationUs;
                SwingUtilities.invokeLater(() -> {
                    if (listener != null) listener.onPlaybackComplete();
                });
            }

        } catch (Exception e) {
            if (!stopped) {
                SwingUtilities.invokeLater(() -> {
                    if (listener != null) listener.onPlaybackError(org.ttplayer.util.Messages.get("player.errorPrefix") + e.getMessage());
                });
            }
        } finally {
            playing = false;
            if (grabber != null) {
                try { grabber.stop(); } catch (Exception ignored) {}
            }
            if (dl != null) {
                try { dl.drain(); } catch (Exception ignored) {}
                try { dl.stop(); dl.close(); } catch (Exception ignored) {}
            }
            this.line = null;
        }
    }

    // ==================== 通用方法 ====================

    private void pausePlayback() {
        paused = true;
        if (line != null && line.isRunning()) line.flush();
        SwingUtilities.invokeLater(() -> {
            if (listener != null) listener.onPlaybackPaused();
        });
    }

    private void resumePlayback() {
        paused = false;
        synchronized (stateLock) { stateLock.notifyAll(); }
        applyVolume();
        SwingUtilities.invokeLater(() -> {
            if (listener != null) listener.onPlaybackResumed();
        });
    }

    private void stopPlayback() {
        stopped = true;
        playing = false;
        paused = false;
        // 推进「频谱重置世代号」：换曲/停止/重播都会走到这里。
        // 界面侧靠它做一次性的清屏 —— 因为 play() 内部是
        // stopPlayback()（playing=false）→ startPlayback()（playing=true）
        // 连续同步执行，20ms 轮询几乎必然错过 playing=false 的瞬时窗口，
        // 只靠 isPlaying() 判断会导致「切换时旧频谱残留」。
        vizResetGen++;
        synchronized (stateLock) { stateLock.notifyAll(); }
        if (playThread != null) {
            playThread.interrupt();
            try { playThread.join(1000); } catch (InterruptedException ignored) {}
            playThread = null;
        }
        if (line != null) {
            try { line.drain(); } catch (Exception ignored) {}
            try { line.stop(); line.close(); } catch (Exception ignored) {}
        }
        line = null;
        positionUs = 0;
        vizHead = 0;
        vizCount = 0;
        vizPushed = 0;
        vizSyncPushed = 0;
        vizSyncPlayed = 0;
        seekRequested = false;
        durationUs = 0;
        SwingUtilities.invokeLater(() -> {
            if (listener != null) listener.onPlaybackStopped();
        });
    }

    private void applyVolume() {
        if (line != null && line.isOpen()) {
            try {
                FloatControl vol = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float min = vol.getMinimum();
                float max = vol.getMaximum();
                if (muted || volumePercent <= 0) {
                    vol.setValue(min);
                } else {
                    // 百分比 → 分贝（对数映射）：100%≈0dB，50%≈-6dB，近似线性响度
                    float db = 20f * (float) Math.log10(volumePercent / 100.0);
                    vol.setValue(Math.max(min, Math.min(max, db)));
                }
            } catch (Exception ignored) {}
        }
    }

    // ======================== 查询 ========================

    /**
     * 重设频谱对齐基准：把当前「已解码帧数」与「已播放帧数」记为新的零点。
     * 在 seek / 恢复播放 / 重新开始等会丢弃音频线缓冲的时刻调用。
     */
    private void resetVisualAlign(SourceDataLine dl) {
        vizSyncPushed = vizPushed;
        if (dl != null && dl.isOpen()) {
            try { vizSyncPlayed = dl.getLongFramePosition(); } catch (Exception ignored) { }
        }
    }

    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return playing; }

    /** 频谱重置世代号：换曲/停止时自增。界面侧比对它即可捕获瞬时清屏时机。 */
    public int getVizResetGen() { return vizResetGen; }
    public int getCurrentSongIndex() { return currentSongIndex; }
    public Song getCurrentSong() { return currentSong; }
    public int getVolumePercent() { return volumePercent; }
    public int getDuration() { return (int) (durationUs / 1000000); }
    public long getPositionMs() { return positionUs / 1000; }
    public int getPosition() { return (int) (positionUs / 1000000); }

    public Equalizer getEqualizer() { return equalizer; }

    /**
     * 返回最近最多 1024 个单声道（左右平均）s16le 样本，供频谱分析使用。
     * 对应 C 的 tt_audio_get_pcm_samples(audio, pcm, VIZ_FFT_N)。
     */
    /**
     * 取最近 {@code FFT_N} 个单声道采样喂给频谱分析。
     *
     * <p>对齐策略：按音频输出线的<b>实际播放帧位置</b>取窗，而非「解码最新位置」。
     * 否则解码线程超前于声音（缓冲深度可达数百毫秒），频谱会明显早于听觉，
     * 且因 dl.write 阻塞呈「冻结→跳跃」节奏。</p>
     *
     * <p>若线尚未启动或无有效播放位置，则退化为取环缓冲最新的一段。</p>
     */
    public byte[] getAudioData() {
        int n = Math.min(1024, vizCount);
        if (n <= 0) return null;
        if (vizOut == null || vizOut.length != n * 2) vizOut = new byte[n * 2];

        // 计算「已解码但尚未播放」的帧数，把读窗从解码前沿倒推回去。
        long lag = 0;
        SourceDataLine ln = this.line;
        if (ln != null && ln.isOpen()) {
            try {
                long played = ln.getLongFramePosition();
                long pushedNet = vizPushed - vizSyncPushed;
                long playedNet = played - vizSyncPlayed;
                lag = pushedNet - playedNet;
                if (lag < 0) lag = 0;
                // 上限必须同时受两处约束，否则会读到「尚未写入」的零区：
                //   1) 环缓冲容量（避免绕圈）；
                //   2) 实际有效帧数 vizCount - n（避免启动/seek 后数据不足时读到空洞，
                //      那样频谱会迟迟不显示 —— 正是「播放时没有马上显示频谱」的根因）。
                long maxLag = (long) vizCount - n;
                long ringMax = (long) vizRing.length - n - 1;
                if (maxLag > ringMax) maxLag = ringMax;
                if (maxLag < 0) maxLag = 0;
                if (lag > maxLag) lag = maxLag;
            } catch (Exception ignored) {
                lag = 0;
            }
        }

        int idx = (int) (vizHead - n - lag);
        idx %= vizRing.length;
        if (idx < 0) idx += vizRing.length;

        int j = 0;
        for (int i = 0; i < n; i++) {
            short s = vizRing[(idx + i) % vizRing.length];
            vizOut[j++] = (byte) (s & 0xFF);
            vizOut[j++] = (byte) ((s >> 8) & 0xFF);
        }
        return vizOut;
    }
}
