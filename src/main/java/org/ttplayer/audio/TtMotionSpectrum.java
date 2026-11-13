package org.ttplayer.audio;

import java.awt.Color;
import java.util.Arrays;

/** Port of {@code motion_spectrum.c}. */
final class TtMotionSpectrum {
    static final int BANDS_32 = 0;
    static final int GRAPH = 1;
    static final int MIRROR = 2;
    static final int PRESET_COUNT = 3;
    static final int MAX_BANDS = 32;

    private final float[] levels = new float[MAX_BANDS];
    private final float[] peaks = new float[MAX_BANDS];
    private final float[] velocity = new float[MAX_BANDS];
    private final float[] hold = new float[MAX_BANDS];
    private int width;
    private int height;
    private int preset = -1;
    private int inputCount;

    void reset() {
        Arrays.fill(levels, 0f);
        Arrays.fill(peaks, 0f);
        Arrays.fill(velocity, 0f);
        Arrays.fill(hold, 0f);
        width = height = inputCount = 0;
        preset = -1;
    }

    void render(int[] pixels, int nextWidth, int nextHeight, float[] bars, int nextPreset,
                TtVisualStyle style, float elapsedSeconds) {
        if (width != nextWidth || height != nextHeight || preset != nextPreset || inputCount != bars.length) {
            reset();
            width = nextWidth;
            height = nextHeight;
            preset = nextPreset;
            inputCount = bars.length;
        }
        int bands = Math.min(nextWidth, 32);
        boolean graph = nextPreset == GRAPH;
        int span = nextHeight;
        updateBands(bars, bands, elapsedSeconds / 0.03f);
        Arrays.fill(pixels, 0xff000000);
        for (int column = 0; column < nextWidth; column++) {
            int frequencyColumn = column;
            int frequencyWidth = nextWidth;
            if (nextPreset == MIRROR) {
                int half = (nextWidth + 1) / 2;
                frequencyColumn = column < half ? half - 1 - column : column - nextWidth / 2;
                frequencyWidth = half;
            }
            float ratio = frequencyWidth > 1 ? (float) frequencyColumn / (frequencyWidth - 1) : 0f;
            int band = frequencyColumn * bands / frequencyWidth;
            int bandStart = (band * frequencyWidth + bands - 1) / bands;
            int bandEnd = ((band + 1) * frequencyWidth + bands - 1) / bands;
            if (!graph && bandEnd - bandStart >= 3 && frequencyColumn == bandEnd - 1) continue;
            float amplitude = graph ? sampleLevel(levels, bands, ratio) : levels[band];
            float peak = graph ? 0f : peaks[band];
            drawColumn(pixels, nextWidth, nextHeight, column, span - 1, span, amplitude, peak, graph, style);
            if (graph && column > 0) {
                float previousRatio = nextWidth > 1 ? (float) (column - 1) / (nextWidth - 1) : 0f;
                float previous = sampleLevel(levels, bands, previousRatio);
                int firstRow = span - Math.round(Math.max(previous, amplitude) * span);
                int lastRow = span - Math.round(Math.min(previous, amplitude) * span);
                for (int row = firstRow; row <= lastRow && row < span; row++) {
                    if (row >= 0 && (amplitude > 0f || previous > 0f)) {
                        pixels[row * nextWidth + column] = tint(style, amplitude, 1f);
                    }
                }
            }
        }
    }

    private void updateBands(float[] bars, int bands, float step) {
        for (int band = 0; band < bands; band++) {
            int first = band * bars.length / bands;
            int end = (band + 1) * bars.length / bands;
            float amplitude = 0f;
            if (bars.length >= bands) {
                for (int index = first; index < end; index++) amplitude = Math.max(amplitude, bounded(bars[index]));
            } else {
                amplitude = sampleLevel(bars, bars.length, (float) band / (bands - 1));
            }
            float baseSmoothing = amplitude > levels[band] ? 0.72f : 0.18f;
            float smoothing = step == 1f ? baseSmoothing : 1f - (float) Math.pow(1f - baseSmoothing, step);
            levels[band] += (amplitude - levels[band]) * smoothing;
            if (levels[band] < 0.0001f) levels[band] = 0f;
            if (levels[band] >= peaks[band]) {
                peaks[band] = levels[band];
                hold[band] = 18f;
                velocity[band] = 0f;
            } else {
                float held = Math.min(step, hold[band]);
                float falling = step - held;
                hold[band] -= held;
                float distance = velocity[band] * falling + 0.00075f * falling * (falling + 1f);
                velocity[band] += 0.0015f * falling;
                peaks[band] = Math.max(levels[band], peaks[band] - distance);
            }
        }
    }

    private static void drawColumn(int[] pixels, int width, int height, int column, int baseline, int span,
                                   float amplitude, float peak, boolean graph, TtVisualStyle style) {
        int extent = Math.round(amplitude * span);
        for (int offset = 0; offset < extent; offset++) {
            int row = baseline - offset;
            if (row < 0 || row >= height) continue;
            float ratio = span > 1 ? (float) offset / (span - 1) : 0f;
            pixels[row * width + column] = tint(style, ratio, graph ? 0.7f : 1f);
        }
        if (!graph && peak > 0.002f) {
            int row = baseline - Math.round(peak * (span - 1));
            if (row >= 0 && row < height) pixels[row * width + column] = tint(style, 1f, 1f);
        }
        if (graph && extent > 0) {
            int row = baseline - extent + 1;
            if (row >= 0 && row < height) pixels[row * width + column] = tint(style, amplitude, 1f);
        }
    }

    private static float sampleLevel(float[] values, int count, float ratio) {
        float position = bounded(ratio) * (count - 1);
        int first = (int) position;
        int second = first + 1 < count ? first + 1 : first;
        return bounded(values[first] + (values[second] - values[first]) * (position - first));
    }

    private static int tint(TtVisualStyle style, float ratio, float opacity) {
        float position = bounded(ratio) * 3f;
        int first = (int) position;
        int second = first < 3 ? first + 1 : first;
        float fraction = position - first;
        Color a = colorAt(style, first);
        Color b = colorAt(style, second);
        int red = (int) ((a.getRed() + (b.getRed() - a.getRed()) * fraction) * opacity);
        int green = (int) ((a.getGreen() + (b.getGreen() - a.getGreen()) * fraction) * opacity);
        int blue = (int) ((a.getBlue() + (b.getBlue() - a.getBlue()) * fraction) * opacity);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static Color colorAt(TtVisualStyle style, int index) {
        switch (index) {
            case 0: return style.bottom;
            case 1: return style.middle;
            case 2: return style.top;
            default: return style.peak;
        }
    }

    private static float bounded(float value) {
        return Math.min(1f, Math.max(0f, value));
    }
}
