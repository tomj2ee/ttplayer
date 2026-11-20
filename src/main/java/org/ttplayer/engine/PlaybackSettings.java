package org.ttplayer.engine;

import org.ttplayer.util.MenuSettings;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlaybackSettings {
    private static volatile PlaybackSettings current = load();
    public boolean autoPlay = true, resumePosition, stopOnError = true;
    public boolean fadeIn, replayGain, preventClipping = true;
    public int intervalMs, bufferMs = 125, fadeMs = 300, preampDb;

    public static PlaybackSettings get() { return current.copy(); }

    public PlaybackSettings copy() {
        PlaybackSettings copy = new PlaybackSettings();
        copy.autoPlay = autoPlay; copy.resumePosition = resumePosition; copy.stopOnError = stopOnError;
        copy.fadeIn = fadeIn; copy.replayGain = replayGain; copy.preventClipping = preventClipping;
        copy.intervalMs = intervalMs; copy.bufferMs = bufferMs; copy.fadeMs = fadeMs; copy.preampDb = preampDb;
        return copy;
    }

    public void normalize() {
        intervalMs = clamp(intervalMs, 0, 10000);
        bufferMs = clamp(bufferMs, 50, 2000);
        fadeMs = clamp(fadeMs, 0, 3000);
        preampDb = clamp(preampDb, -12, 12);
    }

    public static void update(PlaybackSettings settings) {
        PlaybackSettings next = settings.copy();
        next.normalize();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("playback.autoPlay", next.autoPlay);
        values.put("playback.resumePosition", next.resumePosition);
        values.put("playback.stopOnError", next.stopOnError);
        values.put("playback.fadeIn", next.fadeIn);
        values.put("playback.replayGain", next.replayGain);
        values.put("playback.preventClipping", next.preventClipping);
        values.put("playback.intervalMs", next.intervalMs);
        values.put("playback.bufferMs", next.bufferMs);
        values.put("playback.fadeMs", next.fadeMs);
        values.put("playback.preampDb", next.preampDb);
        MenuSettings.setAll(values);
        current = next;
    }

    private static PlaybackSettings load() {
        PlaybackSettings settings = new PlaybackSettings();
        settings.autoPlay = MenuSettings.flag("playback.autoPlay", true);
        settings.resumePosition = MenuSettings.flag("playback.resumePosition", false);
        settings.stopOnError = MenuSettings.flag("playback.stopOnError", true);
        settings.fadeIn = MenuSettings.flag("playback.fadeIn", false);
        settings.replayGain = MenuSettings.flag("playback.replayGain", false);
        settings.preventClipping = MenuSettings.flag("playback.preventClipping", true);
        settings.intervalMs = MenuSettings.number("playback.intervalMs", 0);
        settings.bufferMs = MenuSettings.number("playback.bufferMs", 125);
        settings.fadeMs = MenuSettings.number("playback.fadeMs", 300);
        settings.preampDb = MenuSettings.number("playback.preampDb", 0);
        settings.normalize();
        return settings;
    }

    public double gain(double trackDb, double peak, boolean tagged) {
        if (!replayGain || !tagged || !Double.isFinite(trackDb)) return 1;
        double gain = Math.pow(10, Math.max(-60, Math.min(60, trackDb + preampDb)) / 20);
        return preventClipping && Double.isFinite(peak) && peak > 0 ? Math.min(gain, 1 / peak) : gain;
    }

    public double fadeGain(long frame, int sampleRate) {
        if (!fadeIn || fadeMs <= 0 || sampleRate <= 0) return 1;
        return Math.max(0, Math.min(1, frame / (sampleRate * fadeMs / 1000.0)));
    }

    public void process(byte[] pcm, long firstFrame, int channels, int sampleRate, double trackDb, double peak, boolean tagged) {
        double gain = gain(trackDb, peak, tagged);
        if (gain == 1 && (!fadeIn || fadeGain(firstFrame, sampleRate) == 1)) return;
        for (int sampleIndex = 0; sampleIndex + 1 < pcm.length; sampleIndex += 2) {
            int sample = (short) ((pcm[sampleIndex] & 255) | pcm[sampleIndex + 1] << 8);
            double level = gain * fadeGain(firstFrame + sampleIndex / (2 * Math.max(1, channels)), sampleRate);
            int value = (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * level)));
            pcm[sampleIndex] = (byte) value;
            pcm[sampleIndex + 1] = (byte) (value >> 8);
        }
    }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
}
