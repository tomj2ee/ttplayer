package org.ttplayer.audio;

import java.util.Arrays;

/** Port of {@code original_scope.c}. */
final class TtOriginalScope {
    byte[] levels;
    int width;
    int height;

    void reset() {
        levels = null;
        width = height = 0;
    }

    void step(int nextWidth, int nextHeight, short[] pcm, boolean blur, int decay) {
        int stride = nextWidth + 2;
        int rows = nextHeight + 2;
        int count = stride * rows;
        if (levels == null || width != nextWidth || height != nextHeight) {
            levels = new byte[count];
            width = nextWidth;
            height = nextHeight;
        }
        if (!blur) {
            Arrays.fill(levels, (byte) 0);
        } else {
            Arrays.fill(levels, 0, stride, (byte) 0);
            Arrays.fill(levels, (rows - 1) * stride, rows * stride, (byte) 0);
            for (int row = 0; row < rows; row++) {
                levels[row * stride] = 0;
                levels[row * stride + stride - 1] = 0;
            }
            for (int row = 1; row < rows - 1; row++) {
                for (int column = 0; column < stride - 1; column++) {
                    int index = row * stride + column;
                    int average = ((levels[index - 1] & 0xff) + (levels[index + 1] & 0xff)
                            + (levels[index - stride] & 0xff) + (levels[index + stride] & 0xff)) / 4;
                    levels[index] = (byte) (average > decay ? average - decay : 0);
                }
            }
        }
        int previous = scopeRow(pcm[0], rows);
        for (int column = 1; column <= nextWidth; column++) {
            int current = scopeRow(pcm[column - 1], rows);
            int first = Math.min(previous, current);
            int last = Math.max(previous, current);
            for (int row = first; row <= last; row++) levels[row * stride + column] = (byte) 255;
            previous = current;
        }
    }

    private static int scopeRow(short sample, int rows) {
        return rows - ((int) sample + 32768) * rows / 65536 - 1;
    }
}
