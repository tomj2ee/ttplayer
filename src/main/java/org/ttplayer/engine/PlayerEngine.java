package org.ttplayer.engine;

import org.ttplayer.model.PlaylistManager;
import org.ttplayer.model.Playlist;
import org.ttplayer.model.Song;
import org.ttplayer.util.MenuSettings;

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
    private Playlist playingPlaylist;
    private int playMode = Math.max(0, Math.min(4, MenuSettings.number("playMode", 3)));
    private boolean pauseAfterPlay = MenuSettings.flag("pauseAfterPlay", false);
    private volatile long playbackRevision;
    private Timer advanceTimer;
    private int consecutiveErrors;
    private boolean advancingAfterError;

    /** 图形均衡器（biquad），启用后对输出 PCM 滤波 */
    private final Equalizer equalizer = new Equalizer();

    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean stopped;

    private volatile long positionUs;
    private volatile long durationUs;

    // 频谱分析用单声道环形缓冲（立体声左右平均，对应 C 的 pcm_push）
    // 容量 16384 单声道帧（≈372ms），需容纳音频输出缓冲 + 分析窗，以便做播放位置对齐。
    private static final int VIZ_RING_SIZE = 65536;
    private static final int VIZ_WINDOW_FRAMES = 2048;
    private final short[] vizLeft = new short[VIZ_RING_SIZE];
    private final short[] vizRight = new short[VIZ_RING_SIZE];
    private final Object vizLock = new Object();
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
        playFrom(song, 0);
    }

    public void playFrom(Song song, long positionMs) {
        if (song == null || song.filePath == null) return;
        savePlaybackPosition();
        stopPlayback();
        if (!advancingAfterError) consecutiveErrors = 0;
        currentFile = new File(song.filePath);
        currentSong = song;
        if (playlistManager != null) {
            for (Playlist playlist : playlistManager.getAllPlaylists()) {
                if (playlist.songs.contains(song)) {
                    playingPlaylist = playlist;
                    currentSongIndex = playlist.songs.indexOf(song);
                    break;
                }
            }
        }
        if (positionMs > 0) seekToMs(positionMs);
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
        if (!playing || playThread == null || !playThread.isAlive()) {
            if (currentSong != null) play(currentSong);
            else play(0);
            return;
        }
        if (paused) {
            resumePlayback();
        } else {
            pausePlayback();
        }
    }

    public void stop() {
        if (currentSong != null) savePlaybackPosition(0);
        stopPlayback();
        currentSongIndex = -1;
        currentFile = null;
        currentSong = null;
    }

    public void previous() {
        advance(-1, false);
    }

    public void next() {
        advance(1, false);
    }

    public int getPlayMode() { return playMode; }
    public void setPlayMode(int mode) {
        if (mode < 0 || mode > 4) return;
        playMode = mode;
        MenuSettings.set("playMode", mode);
    }
    public boolean isPauseAfterPlay() { return pauseAfterPlay; }
    public void setPauseAfterPlay(boolean value) {
        pauseAfterPlay = value;
        MenuSettings.set("pauseAfterPlay", value);
    }
    public Playlist getPlayingPlaylist() { return playingPlaylist; }
    public void advanceAfterPlayback() { scheduleAdvance(false); }

    public void advanceAfterError() {
        if (PlaybackSettings.get().stopOnError) return;
        Playlist playlist = playingPlaylist != null ? playingPlaylist : playlistManager == null ? null : playlistManager.getCurrentPlaylist();
        if (playlist == null || ++consecutiveErrors >= playlist.songs.size()) return;
        scheduleAdvance(true);
    }

    private void scheduleAdvance(boolean afterError) {
        if (advanceTimer != null) advanceTimer.stop();
        final long revision = playbackRevision;
        Runnable action = () -> {
            if (revision != playbackRevision) return;
            advancingAfterError = afterError;
            try { advance(1, true); } finally { advancingAfterError = false; }
        };
        int delay = PlaybackSettings.get().intervalMs;
        if (delay == 0) action.run();
        else {
            advanceTimer = new Timer(delay, event -> action.run());
            advanceTimer.setRepeats(false);
            advanceTimer.start();
        }
    }

    public void savePlaybackPosition() {
        if (currentSong != null && (playing || paused)) savePlaybackPosition(getPositionMs());
    }

    private void savePlaybackPosition(long positionMs) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("playback.lastPath", currentSong.filePath);
        values.put("playback.lastPositionMs", durationUs > 0 && positionMs + 1000 >= durationUs / 1000 ? 0 : Math.max(0, positionMs));
        MenuSettings.setAll(values);
    }

    public void shutdown() { savePlaybackPosition(); stopPlayback(); }

    private void advance(int direction, boolean automatic) {
        Playlist playlist = playingPlaylist != null ? playingPlaylist
                : playlistManager == null ? null : playlistManager.getCurrentPlaylist();
        if (playlist == null) return;
        int current = playlist.songs.indexOf(currentSong);
        int next = PlaybackPolicy.next(current, playlist.songs.size(), direction, playMode, automatic, pauseAfterPlay);
        if (next >= 0) {
            Song song = playlist.songs.get(next);
            play(song);
            playingPlaylist = playlist;
            currentSongIndex = next;
        } else if (automatic && listener != null) listener.onPlaybackStopped();
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
            final long revision = playbackRevision;
            SwingUtilities.invokeLater(() -> {
                if (playbackRevision == revision && listener != null)
                    listener.onPlaybackError(org.ttplayer.util.Messages.get("player.fileNotFound"));
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
        final long revision = playbackRevision;
        boolean completed = false;

        try {
            grabber = new FFmpegFrameGrabber(currentFile);
            grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
            grabber.setSampleRate(44100);
            grabber.setAudioChannels(2);
            grabber.start();
            if (playbackRevision != revision || stopped) return;
            TrackReplayGain trackGain = TrackReplayGain.read(grabber);
            long fadeFrame = 0;
            int openedBufferMs = PlaybackSettings.get().bufferMs;

            long lenUs = grabber.getLengthInTime();
            durationUs = lenUs > 0 ? lenUs : 0;

            AudioFormat fmt = new AudioFormat(44100, 16, 2, true, false);
            dl = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            dl.open(fmt, Math.max(4, (44100 * openedBufferMs / 1000) * 4));
            dl.start();
            this.line = dl;
            applyVolume();
            // 新曲目开始：建立对齐基准（此时线位置为 0，环缓冲为空），
            // 使 lag 从 0 起算，频谱可以立刻显示而不必等待缓冲填满。
            resetVisualAlign(dl);

            final Song startSong = currentSong;
            SwingUtilities.invokeLater(() -> {
                if (playbackRevision == revision && listener != null && startSong != null) {
                    listener.onPlaybackStarted(startSong);
                }
            });

            while (playing && !stopped && playbackRevision == revision) {
                PlaybackSettings playbackSettings = PlaybackSettings.get();
                if (openedBufferMs != playbackSettings.bufferMs) {
                    dl.flush(); dl.stop(); dl.close();
                    dl = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
                    openedBufferMs = playbackSettings.bufferMs;
                    dl.open(fmt, Math.max(4, (44100 * openedBufferMs / 1000) * 4));
                    dl.start();
                    this.line = dl;
                    applyVolume();
                    synchronized (vizLock) { vizHead = 0; vizCount = 0; }
                    vizResetGen++;
                    resetVisualAlign(dl);
                }
                if (seekRequested) {
                    try {
                        grabber.setTimestamp(seekTargetUs);
                        positionUs = seekTargetUs;
                    } catch (Exception ignored) {
                        // seek 失败时保持当前位置继续播放
                    }
                    // seek 会丢弃已缓冲音频：重设对齐基准，避免 lag 计算出负延迟
                    try { dl.flush(); } catch (Exception ignored) {}
                    synchronized (vizLock) {
                        vizHead = 0;
                        vizCount = 0;
                    }
                    vizResetGen++;
                    resetVisualAlign(dl);
                    seekRequested = false;
                    fadeFrame = 0;
                    continue;
                }

                synchronized (stateLock) {
                    while (paused && !stopped && playbackRevision == revision) {
                        if (dl.isRunning()) dl.flush();
                        try { stateLock.wait(); } catch (InterruptedException e) { break; }
                        // 恢复播放：缓冲已被 flush，重设基准与环缓冲，避免旧数据残留
                        resetVisualAlign(dl);
                        dl.start();
                        applyVolume();
                    }
                }
                if (stopped || playbackRevision != revision) break;

                Frame frame = grabber.grabSamples();
                if (frame == null) break;
                if (frame.samples == null || frame.samples.length == 0) continue;

                Buffer sampleBuf = frame.samples[0];
                if (!(sampleBuf instanceof ShortBuffer)) continue;
                ShortBuffer sb = (ShortBuffer) sampleBuf;
                int count = sb.remaining();
                if (count <= 0) continue;
                consecutiveErrors = 0;

                byte[] pcm = new byte[count * 2];
                sb.rewind();
                for (int i = 0; i < count; i++) {
                    short s = sb.get();
                    pcm[2 * i] = (byte) (s & 0xFF);
                    pcm[2 * i + 1] = (byte) ((s >> 8) & 0xFF);
                }

                // 均衡器（启用时原地滤波）
                equalizer.process(pcm);
                playbackSettings.process(pcm, fadeFrame, 2, 44100, trackGain.decibels, trackGain.peak, trackGain.isTagged());
                fadeFrame += count / 2;

                // 立体声左右平均 → 单声道环缓冲（对应 C pcm_push，取滤波后数据）。
                // 关键：必须在 dl.write() 之前入环 —— dl.write 是阻塞调用，
                // 若放在其后，解码线程阻塞期间环缓冲不更新，频谱会「冻结后跳跃」。
                int frames = count / 2;
                synchronized (vizLock) {
                    for (int frameIndex = 0; frameIndex < frames; frameIndex++) {
                        int base = frameIndex * 4;
                        vizLeft[vizHead] = (short) ((pcm[base + 1] << 8) | (pcm[base] & 0xFF));
                        vizRight[vizHead] = (short) ((pcm[base + 3] << 8) | (pcm[base + 2] & 0xFF));
                        vizHead = (vizHead + 1) % VIZ_RING_SIZE;
                        if (vizCount < VIZ_RING_SIZE) vizCount++;
                    }
                    vizPushed += frames;
                }

                dl.write(pcm, 0, pcm.length);

                long ts = frame.timestamp;
                if (ts > 0) {
                    positionUs = ts;
                } else {
                    positionUs += (count / 2) * 1000000L / 44100;
                }
            }

            if (!stopped && playing && playbackRevision == revision) {
                positionUs = durationUs;
                completed = true;
            }

        } catch (Exception e) {
            if (!stopped) {
                SwingUtilities.invokeLater(() -> {
                    if (playbackRevision == revision && listener != null)
                        listener.onPlaybackError(org.ttplayer.util.Messages.get("player.errorPrefix") + e.getMessage());
                });
            }
        } finally {
            if (playbackRevision == revision) playing = false;
            if (grabber != null) {
                try { grabber.stop(); } catch (Exception ignored) {}
            }
            if (dl != null) {
                try { if (completed && !stopped && playbackRevision == revision) dl.drain(); else dl.flush(); } catch (Exception ignored) {}
                try { dl.stop(); dl.close(); } catch (Exception ignored) {}
            }
            if (this.line == dl) this.line = null;
            if (completed) SwingUtilities.invokeLater(() -> {
                if (!stopped && playbackRevision == revision && listener != null) listener.onPlaybackComplete();
            });
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
        if (advanceTimer != null) advanceTimer.stop();
        playbackRevision++;
        stopped = true;
        playing = false;
        paused = false;
        // 推进「频谱重置世代号」：换曲/停止/重播都会走到这里。
        // 界面侧靠它做一次性的清屏 —— 因为 play() 内部是
        // stopPlayback()（playing=false）→ startPlayback()（playing=true）
        // 连续同步执行，20ms 轮询几乎必然错过 playing=false 的瞬时窗口，
        // 只靠 isPlaying() 判断会导致「切换时旧频谱残留」。
        vizResetGen++;
        SourceDataLine activeLine = line;
        if (activeLine != null) {
            try { activeLine.flush(); activeLine.stop(); activeLine.close(); } catch (Exception ignored) {}
        }
        synchronized (stateLock) { stateLock.notifyAll(); }
        if (playThread != null) {
            playThread.interrupt();
            try { playThread.join(1000); } catch (InterruptedException ignored) {}
            playThread = null;
        }
        if (line != null) {
            try { line.flush(); } catch (Exception ignored) {}
            try { line.stop(); line.close(); } catch (Exception ignored) {}
        }
        line = null;
        positionUs = 0;
        synchronized (vizLock) {
            vizHead = 0;
            vizCount = 0;
            vizPushed = 0;
            vizSyncPushed = 0;
            vizSyncPlayed = 0;
        }
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
        synchronized (vizLock) {
            vizSyncPushed = vizPushed;
            if (dl != null && dl.isOpen()) {
                try { vizSyncPlayed = dl.getLongFramePosition(); } catch (Exception ignored) { }
            }
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
        long playedFrames = -1;
        SourceDataLine ln = this.line;
        if (ln != null && ln.isOpen()) {
            try {
                playedFrames = ln.getLongFramePosition();
            } catch (Exception ignored) {
                playedFrames = -1;
            }
        }
        synchronized (vizLock) {
            int frames = Math.min(VIZ_WINDOW_FRAMES, vizCount);
            if (frames <= 0) return null;
            long lag = 0;
            if (playedFrames >= 0) {
                lag = (vizPushed - vizSyncPushed) - (playedFrames - vizSyncPlayed);
                if (lag < 0) lag = 0;
                long maxLag = Math.max(0L, (long) vizCount - frames);
                long ringMax = (long) VIZ_RING_SIZE - frames - 1;
                if (maxLag > ringMax) maxLag = ringMax;
                if (lag > maxLag) lag = maxLag;
            }
            if (vizOut == null || vizOut.length != frames * 4) vizOut = new byte[frames * 4];
            int index = (int) (vizHead - frames - lag) % VIZ_RING_SIZE;
            if (index < 0) index += VIZ_RING_SIZE;
            int output = 0;
            for (int frame = 0; frame < frames; frame++) {
                int source = (index + frame) % VIZ_RING_SIZE;
                short left = vizLeft[source];
                short right = vizRight[source];
                vizOut[output++] = (byte) left;
                vizOut[output++] = (byte) (left >> 8);
                vizOut[output++] = (byte) right;
                vizOut[output++] = (byte) (right >> 8);
            }
            return vizOut;
        }
    }
}
