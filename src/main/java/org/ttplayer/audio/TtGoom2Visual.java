package org.ttplayer.audio;

import java.util.Arrays;

/** Port of {@code goom2_visual.c}. */
final class TtGoom2Visual {
    private static final int GOOM_WIDTH = 320;
    private static final int GOOM_HEIGHT = 200;
    private static final float TAU = 6.283185307179586f;

    private int[] front;
    private int[] back;
    private int[] mapLeft;
    private int[] mapTop;
    private float[] mapHorizontal;
    private float[] mapVertical;
    private int width;
    private int height;
    private int filter;
    private int frame;
    private int lastChange;
    private int lastBeat;
    private int random;
    private float energy;
    private float average;
    private float pulse;
    private float phase;
    private float hue;
    private float centerX;
    private float centerY;
    private int scope;
    private int nextScope;
    private int scopeAge;

    void render(int[] output, int outputWidth, int outputHeight, float[] bars, float[] waveform) {
        resize(Math.min(outputWidth, GOOM_WIDTH), Math.min(outputHeight, GOOM_HEIGHT));
        analyze(bars, waveform);
        transform();
        feedback();
        particles();
        scopes(waveform);
        int[] previous = front;
        front = back;
        back = previous;
        for (int top = 0; top < outputHeight; top++) {
            int sourceRow = top * height / outputHeight;
            for (int left = 0; left < outputWidth; left++) {
                int sourceColumn = left * width / outputWidth;
                output[top * outputWidth + left] = front[sourceRow * width + sourceColumn];
            }
        }
        frame++;
    }

    private void resize(int nextWidth, int nextHeight) {
        if (nextWidth == width && nextHeight == height) return;
        int count = nextWidth * nextHeight;
        front = new int[count];
        back = new int[count];
        mapLeft = new int[count];
        mapTop = new int[count];
        mapHorizontal = new float[count];
        mapVertical = new float[count];
        width = nextWidth;
        height = nextHeight;
        initialize();
    }

    private void initialize() {
        Arrays.fill(front, 0);
        Arrays.fill(back, 0);
        random = 0xe98fda55;
        filter = 0;
        frame = lastChange = lastBeat = 0;
        energy = average = pulse = phase = 0f;
        hue = 0.7f;
        centerX = centerY = 0.5f;
        scope = 0;
        nextScope = 1;
        scopeAge = 0;
    }

    private void transform() {
        float centerPixelX = centerX * (width - 1);
        float centerPixelY = centerY * (height - 1);
        float scaleX = 320f / width;
        float scaleY = 200f / height;
        float speed = 126f - energy * 8f - pulse * 2f;
        for (int top = 0; top < height; top++) {
            for (int left = 0; left < width; left++) {
                float sourceX;
                float sourceY;
                if (filter == 5) {
                    sourceX = left + ((float) Math.sin(top * 0.13f + phase * 3f) * (1.2f + energy * 3f)
                            + (float) Math.sin(phase) * 0.6f) / scaleX;
                    sourceY = top + (132f - Math.min(speed, 130f)) / (16f * scaleY);
                } else {
                    float velocityX = (left - centerPixelX) * scaleX * 512f;
                    float velocityY = (top - centerPixelY) * scaleY * 512f;
                    float distance = (velocityX * velocityX + velocityY * velocityY) / (512f * 512f);
                    float factor = speed * 16f;
                    switch (filter) {
                        case 6:
                            velocityX += tableSine(-velocityY + distance) / 2f;
                            velocityY += tableSine(velocityX + distance) / 2f;
                            break;
                        case 7:
                            velocityX += tableSine(-velocityY / 2f + distance);
                            velocityY += tableSine(velocityX / 2f + distance);
                            factor = 128f * 16f;
                            break;
                        case 8:
                            factor *= 1f + tableSine(velocityY) / 65536f;
                            break;
                        case 9:
                            factor -= velocityY / 256f;
                            break;
                        default:
                            break;
                    }
                    factor = Math.max(-3024f, factor);
                    sourceX = centerPixelX + velocityX * factor / (1048576f * scaleX);
                    sourceY = centerPixelY + velocityY * factor / (1048576f * scaleY);
                }
                int index = top * width + left;
                mapLeft[index] = (int) Math.floor(sourceX);
                mapTop[index] = (int) Math.floor(sourceY);
                mapHorizontal[index] = sourceX - (float) Math.floor(sourceX);
                mapVertical[index] = sourceY - (float) Math.floor(sourceY);
            }
        }
    }

    private void feedback() {
        float decay = 0.92f + energy * 0.035f;
        for (int index = 0; index < front.length; index++) {
            int left = mapLeft[index];
            int top = mapTop[index];
            int first = sourcePixel(left, top);
            int second = sourcePixel(left + 1, top);
            int third = sourcePixel(left, top + 1);
            int fourth = sourcePixel(left + 1, top + 1);
            int result = 0xff000000;
            for (int shift = 0; shift < 24; shift += 8) {
                float upper = ((first >>> shift) & 255) * (1f - mapHorizontal[index])
                        + ((second >>> shift) & 255) * mapHorizontal[index];
                float lower = ((third >>> shift) & 255) * (1f - mapHorizontal[index])
                        + ((fourth >>> shift) & 255) * mapHorizontal[index];
                result |= (int) ((upper * (1f - mapVertical[index]) + lower * mapVertical[index]) * decay) << shift;
            }
            back[index] = result;
        }
    }

    private void particles() {
        float pixelWidth = width - 1f;
        float pixelHeight = height - 1f;
        for (int index = 0; index < 28; index++) {
            float offset = index * 0.16f;
            float angle = phase * (1.2f + (index % 5) * 0.1f) + offset;
            float left = pixelWidth * (0.5f + (0.22f + energy * 0.21f) * (float) Math.cos(angle));
            float top = pixelHeight * (0.5f + (0.18f + energy * 0.23f) * (float) Math.sin(angle * 1.17f + offset));
            int tint = color(hue + offset, 120f + 130f * energy);
            point(left + 1f, top, tint);
            point(left, top + 1f, tint);
            point(left + 1f, top + 1f, 0xffffffff);
            point(left + 2f, top + 1f, tint);
            point(left + 1f, top + 2f, tint);
        }
    }

    private void scopes(float[] samples) {
        float blend = limit(scopeAge / 64f, 0f, 1f);
        float pixelWidth = width - 1f;
        float pixelHeight = height - 1f;
        float amplitude = 0.15f + energy * 0.17f;
        for (int channel = 0; channel < 2; channel++) {
            float previousX = 0f;
            float previousY = 0f;
            float direction = channel == 0 ? 1f : -1f;
            int tint = color(hue + channel * 2f, 150f + energy * 100f);
            for (int index = 0; index < 512; index++) {
                float ratio = index / 511f;
                float sample = wave(samples, ratio);
                float firstX;
                float firstY;
                if (scope == 1) {
                    firstX = pixelWidth * (0.5f + direction * 0.15f + sample * amplitude);
                    firstY = pixelHeight * ratio;
                } else {
                    firstX = pixelWidth * ratio;
                    firstY = pixelHeight * (0.5f + direction * 0.12f + sample * amplitude);
                }
                float secondX;
                float secondY;
                if (nextScope == 1) {
                    secondX = pixelWidth * (0.5f + direction * 0.15f + sample * amplitude);
                    secondY = pixelHeight * ratio;
                } else {
                    secondX = pixelWidth * ratio;
                    secondY = pixelHeight * (0.5f + direction * 0.12f + sample * amplitude);
                }
                float left = firstX + (secondX - firstX) * blend;
                float top = firstY + (secondY - firstY) * blend;
                if (index > 0) segment(previousX, previousY, left, top, tint);
                previousX = left;
                previousY = top;
            }
        }
    }

    private void analyze(float[] bars, float[] samples) {
        float nextEnergy = 0f;
        float waveformPower = 0f;
        for (float bar : bars) nextEnergy += limit(bar, 0f, 1f) / bars.length;
        for (float sample : samples) {
            float value = limit(sample, -1f, 1f);
            waveformPower += value * value / samples.length;
        }
        nextEnergy = limit(nextEnergy * 0.65f + (float) Math.sqrt(waveformPower) * 0.65f, 0f, 1f);
        boolean beat = nextEnergy > average * 1.25f + 0.04f && frame - lastBeat > 8;
        average = average * 0.97f + nextEnergy * 0.03f;
        energy = nextEnergy;
        pulse *= 0.88f;
        if (beat) {
            pulse = 1f;
            lastBeat = frame;
        }
        phase = (phase + 0.025f + energy * 0.04f) % (TAU * 100f);
        hue = (hue + 0.006f + pulse * 0.04f) % TAU;
        int age = frame - lastChange;
        if (age > 360 || (age > 90 && beat)) {
            int[] filters = {0, 5, 6, 7, 8, 9};
            int selection = (int) (randomUnit() * (filters.length - 1));
            for (int candidate : filters) {
                if (candidate == filter) continue;
                if (selection-- == 0) {
                    filter = candidate;
                    break;
                }
            }
            centerX = 0.38f + randomUnit() * 0.24f;
            centerY = 0.38f + randomUnit() * 0.24f;
            lastChange = frame;
        }
        if (++scopeAge > 220) {
            scope = nextScope;
            nextScope = 1 - scope;
            scopeAge = 0;
        }
    }

    private void segment(float left, float top, float right, float bottom, int tint) {
        float deltaX = right - left;
        float deltaY = bottom - top;
        int steps = (int) Math.min(1024f, Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaY))));
        if (steps <= 0) {
            point(left, top, tint);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            float ratio = (float) step / steps;
            point(left + ratio * deltaX, top + ratio * deltaY, tint);
        }
    }

    private void point(float left, float top, int tint) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || left < -1f || top < -1f || left >= width || top >= height) return;
        plot((int) left, (int) top, tint);
    }

    private void plot(int left, int top, int tint) {
        if (left < 0 || top < 0 || left >= width || top >= height) return;
        int index = top * width + left;
        int pixel = back[index];
        int result = 0xff000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int value = ((pixel >>> shift) & 255) + ((tint >>> shift) & 255);
            result |= Math.min(value, 255) << shift;
        }
        back[index] = result;
    }

    private int sourcePixel(int left, int top) {
        return left < 0 || top < 0 || left >= width || top >= height ? 0 : front[top * width + left];
    }

    private float randomUnit() {
        random ^= random << 13;
        random ^= random >>> 17;
        random ^= random << 5;
        return (random >>> 8) / 16777216f;
    }

    private static float tableSine(float position) {
        return 1024f * (float) Math.sin(Math.IEEEremainder(position, 65536f) * TAU / 65536f);
    }

    private static float wave(float[] samples, float ratio) {
        if (samples.length == 0) return 0f;
        float position = limit(ratio, 0f, 1f) * (samples.length - 1);
        int index = (int) position;
        int next = index + 1 < samples.length ? index + 1 : index;
        return limit(samples[index] + (samples[next] - samples[index]) * (position - index), -1f, 1f);
    }

    private static int color(float hue, float brightness) {
        float red = 0.5f + 0.5f * (float) Math.sin(hue);
        float green = 0.5f + 0.5f * (float) Math.sin(hue + TAU / 3f);
        float blue = 0.5f + 0.5f * (float) Math.sin(hue + TAU * 2f / 3f);
        return 0xff000000 | (int) limit(red * brightness, 0f, 255f) << 16
                | (int) limit(green * brightness, 0f, 255f) << 8
                | (int) limit(blue * brightness, 0f, 255f);
    }

    private static float limit(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
