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
    private final short[] vizRing = new short[2048];
    private int vizHead = 0;
    private volatile int vizCount = 0;
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
            dl.open(fmt);
            dl.start();
            this.line = dl;
            applyVolume();

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
                    seekRequested = false;
                    continue;
                }

                synchronized (stateLock) {
                    while (paused && !stopped) {
                        if (dl.isRunning()) dl.flush();
                        try { stateLock.wait(); } catch (InterruptedException e) { break; }
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

                dl.write(pcm, 0, pcm.length);

                // 立体声左右平均 → 单声道环缓冲（对应 C pcm_push，取滤波后数据）
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

    public boolean isPaused() { return paused; }
    public boolean isPlaying() { return playing; }
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
    public byte[] getAudioData() {
        int n = Math.min(1024, vizCount);
        if (n <= 0) return null;
        if (vizOut == null || vizOut.length != n * 2) vizOut = new byte[n * 2];
        int idx = vizHead - n;
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
