package org.ttplayer.audio;

import java.util.Arrays;

/** Port of {@code classic_spectrum.c}. */
final class TtClassicSpectrum {
    static final class Column {
        int height;
        int age;
        int peak;
        int previousPeak;
    }

    final Column[] columns = new Column[256];
    final int[] bins = new int[256];
    int width;
    int height;
    int count;

    TtClassicSpectrum() {
        for (int index = 0; index < columns.length; index++) columns[index] = new Column();
    }

    void reset() {
        for (Column column : columns) {
            column.height = 0;
            column.age = 0;
            column.peak = 0;
            column.previousPeak = 0;
        }
        Arrays.fill(bins, 0);
        width = height = count = 0;
    }

    void step(int nextWidth, int nextHeight, short[] spectrumBins) {
        if (width != nextWidth || height != nextHeight) {
            reset();
            width = nextWidth;
            height = nextHeight;
            count = Math.min(nextWidth, 256);
            int tableCount = Math.min(nextWidth + 6, 256);
            int previous = 0;
            for (int index = 0; index < count; index++) {
                int candidate = (int) Math.pow(2.0, (double) index * 7.5 / tableCount);
                bins[index] = candidate > previous ? candidate : previous + 1;
                previous = bins[index];
            }
        }
        double scale = nextHeight * 1.3333 / Math.log(256.0);
        for (int index = 0; index < count; index++) {
            Column column = columns[index];
            int bin = bins[index];
            int magnitude = bin < spectrumBins.length && spectrumBins[bin] > 0 ? spectrumBins[bin] / 32 : 0;
            int level = magnitude > 0 ? (int) (Math.log(magnitude) * scale) : 0;
            int left = index == 0 ? level : columns[index - 1].height;
            int right = index == count - 1 ? level : columns[index + 1].height;
            int target = (left + level * 2 + right) / 4;
            if (target > nextHeight - 1) target = nextHeight - 1;
            if (column.height < target) column.height = target;
            else if (column.height > 2) column.height -= 2;
            column.previousPeak = column.peak;
            if (column.peak <= column.height) {
                column.peak = column.height;
                column.age = 0;
            } else {
                if (column.peak > 0) {
                    column.peak -= column.age >> 3;
                    column.age++;
                }
                if (column.age >= nextHeight - 1) column.peak = 0;
            }
        }
    }
}
