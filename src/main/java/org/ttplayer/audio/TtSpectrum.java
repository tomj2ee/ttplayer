package org.ttplayer.audio;

import java.util.Arrays;

/** Java port of the C player's {@code spectrum_analyzer.c}. */
public final class TtSpectrum {
    public static final int FFT_SIZE = 2048;
    public static final int CLASSIC_SIZE = 512;
    public static final int CLASSIC_BINS = 256;
    public static final int BAR_COUNT = 96;
    public static final int WAVEFORM_SIZE = 256;
    public static final int SAMPLE_RATE = 44100;

    private final short[][] pcm = new short[2][FFT_SIZE];
    private final short[][] voice = new short[2][CLASSIC_SIZE];
    private final short[] classicBins = new short[CLASSIC_BINS];
    private final float[] bars = new float[BAR_COUNT];
    private final float[] smoothed = new float[BAR_COUNT];
    private final float[] waveform = new float[WAVEFORM_SIZE];
    private final float[] transformReal = new float[FFT_SIZE];
    private final float[] transformImaginary = new float[FFT_SIZE];
    private final float[] power = new float[FFT_SIZE / 2];
    private final float[] classicReal = new float[CLASSIC_SIZE];
    private final float[] classicImaginary = new float[CLASSIC_SIZE];

    public void analyze(byte[] data, int bytes, float elapsedSeconds) {
        float elapsed = clamp(elapsedSeconds, 0.001f, 0.1f);
        int frames = data == null ? 0 : Math.min(bytes, data.length) / 4;
        int available = Math.min(frames, FFT_SIZE);
        int sourceOffset = Math.max(0, frames - available) * 4;
        int padding = FFT_SIZE - available;
        Arrays.fill(pcm[0], (short) 0);
        Arrays.fill(pcm[1], (short) 0);
        for (int frame = 0; frame < available; frame++) {
            int offset = sourceOffset + frame * 4;
            pcm[0][padding + frame] = readShort(data, offset);
            pcm[1][padding + frame] = readShort(data, offset + 2);
        }
        updateWaveform(available, padding);
        updateVoice(available);
        updateBars(elapsed);
        updateClassicBins();
    }

    public void analyze(byte[] data, int bytes) {
        analyze(data, bytes, 0.03f);
    }

    public void reset() {
        Arrays.fill(pcm[0], (short) 0);
        Arrays.fill(pcm[1], (short) 0);
        Arrays.fill(voice[0], (short) 0);
        Arrays.fill(voice[1], (short) 0);
        Arrays.fill(classicBins, (short) 0);
        Arrays.fill(bars, 0f);
        Arrays.fill(smoothed, 0f);
        Arrays.fill(waveform, 0f);
        Arrays.fill(power, 0f);
    }

    float[] bars() { return bars; }
    short[] classicBins() { return classicBins; }
    float[] waveform() { return waveform; }
    short[][] voice() { return voice; }

    private void updateWaveform(int available, int padding) {
        if (available == 0) {
            Arrays.fill(waveform, 0f);
            return;
        }
        for (int index = 0; index < WAVEFORM_SIZE; index++) {
            int frame = padding + index * available / WAVEFORM_SIZE;
            waveform[index] = ((int) pcm[0][frame] + (int) pcm[1][frame]) / 65536f;
        }
    }

    private void updateVoice(int available) {
        Arrays.fill(voice[0], (short) 0);
        Arrays.fill(voice[1], (short) 0);
        int count = Math.min(available, CLASSIC_SIZE);
        int source = FFT_SIZE - count;
        int target = CLASSIC_SIZE - count;
        if (count > 0) {
            System.arraycopy(pcm[0], source, voice[0], target, count);
            System.arraycopy(pcm[1], source, voice[1], target, count);
        }
    }

    private void updateBars(float elapsedSeconds) {
        final float pi = 3.14159265358979323846f;
        final float normalization = 4f / (FFT_SIZE - 1);
        Arrays.fill(power, 0f);
        for (int channel = 0; channel < 2; channel++) {
            Arrays.fill(transformReal, 0f);
            Arrays.fill(transformImaginary, 0f);
            for (int index = 0; index < FFT_SIZE; index++) {
                float phase = (float) index / (FFT_SIZE - 1);
                float window = 0.5f - 0.5f * (float) Math.cos(2f * pi * phase);
                transformReal[index] = pcm[channel][index] / 32768f * window;
            }
            transform(transformReal, transformImaginary, FFT_SIZE);
            for (int bin = 1; bin < FFT_SIZE / 2; bin++) {
                float magnitude = (float) Math.hypot(transformReal[bin], transformImaginary[bin]) * normalization;
                power[bin] += magnitude * magnitude * 0.5f;
            }
        }

        float highest = Math.min(16000f, SAMPLE_RATE * 0.5f);
        float lowest = Math.min(40f, highest * 0.5f);
        float binScale = (float) FFT_SIZE / SAMPLE_RATE;
        for (int bar = 0; bar < BAR_COUNT; bar++) {
            float first = lowest * (float) Math.pow(highest / lowest, (float) bar / BAR_COUNT) * binScale;
            float last = lowest * (float) Math.pow(highest / lowest, (float) (bar + 1) / BAR_COUNT) * binScale;
            float bandPower = 0f;
            if (last - first < 1f) {
                float center = Math.min(FFT_SIZE / 2f - 1f, (float) Math.sqrt(first * last));
                int lower = (int) center;
                int upper = lower + 1 < FFT_SIZE / 2 ? lower + 1 : lower;
                bandPower = power[lower] + (power[upper] - power[lower]) * (center - lower);
            } else {
                int begin = (int) Math.max(1, Math.floor(first + 0.5f));
                int end = (int) Math.ceil(last + 0.5f);
                for (int bin = begin; bin < end && bin < FFT_SIZE / 2; bin++) {
                    float weight = Math.max(0f, Math.min(last, bin + 0.5f) - Math.max(first, bin - 0.5f));
                    bandPower += power[bin] * weight;
                }
                bandPower /= 1.5f;
            }
            float decibels = 10f * (float) Math.log10(Math.max(bandPower, 1.0e-12f));
            float level = clamp((decibels + 72f) / 66f, 0f, 1f);
            float seconds = level > smoothed[bar] ? 0.018f : 0.09f;
            float smoothing = 1f - (float) Math.exp(-elapsedSeconds / seconds);
            smoothed[bar] += (level - smoothed[bar]) * smoothing;
            if (smoothed[bar] < 0.0001f) smoothed[bar] = 0f;
            bars[bar] = smoothed[bar];
        }
    }

    private void updateClassicBins() {
        Arrays.fill(classicReal, 0f);
        Arrays.fill(classicImaginary, 0f);
        for (int index = 0; index < CLASSIC_SIZE; index++) {
            classicReal[index] = voice[0][index];
        }
        transform(classicReal, classicImaginary, CLASSIC_SIZE);
        for (int bin = 0; bin < CLASSIC_BINS; bin++) {
            float real = classicReal[bin];
            float imaginary = classicImaginary[bin];
            int magnitude = (int) Math.sqrt((double) (real * real + imaginary * imaginary));
            classicBins[bin] = (short) (magnitude >>> 8);
        }
    }

    private static short readShort(byte[] data, int offset) {
        return (short) ((data[offset + 1] << 8) | (data[offset] & 0xff));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void transform(float[] real, float[] imaginary, int size) {
        int reversed = 0;
        for (int index = 1; index < size; index++) {
            int bit = size >> 1;
            for (; (reversed & bit) != 0; bit >>= 1) reversed ^= bit;
            reversed ^= bit;
            if (index < reversed) {
                float temporary = real[index];
                real[index] = real[reversed];
                real[reversed] = temporary;
                temporary = imaginary[index];
                imaginary[index] = imaginary[reversed];
                imaginary[reversed] = temporary;
            }
        }
        for (int length = 2; length <= size; length <<= 1) {
            float angle = -2f * 3.14159265358979323846f / length;
            float stepReal = (float) Math.cos(angle);
            float stepImaginary = (float) Math.sin(angle);
            for (int offset = 0; offset < size; offset += length) {
                float weightReal = 1f;
                float weightImaginary = 0f;
                for (int index = 0; index < length / 2; index++) {
                    int evenIndex = offset + index;
                    int oddIndex = evenIndex + length / 2;
                    float oddReal = real[oddIndex] * weightReal - imaginary[oddIndex] * weightImaginary;
                    float oddImaginary = real[oddIndex] * weightImaginary + imaginary[oddIndex] * weightReal;
                    float evenReal = real[evenIndex];
                    float evenImaginary = imaginary[evenIndex];
                    real[evenIndex] = evenReal + oddReal;
                    imaginary[evenIndex] = evenImaginary + oddImaginary;
                    real[oddIndex] = evenReal - oddReal;
                    imaginary[oddIndex] = evenImaginary - oddImaginary;
                    float nextReal = weightReal * stepReal - weightImaginary * stepImaginary;
                    weightImaginary = weightReal * stepImaginary + weightImaginary * stepReal;
                    weightReal = nextReal;
                }
            }
        }
    }
}
