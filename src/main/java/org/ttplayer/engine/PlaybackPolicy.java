package org.ttplayer.engine;

import java.util.concurrent.ThreadLocalRandom;

public final class PlaybackPolicy {
    private PlaybackPolicy() {}
    public static int next(int current, int count, int direction, int mode, boolean automatic, boolean pauseAfter) {
        if (count <= 0 || current < -1 || current >= count || mode < 0 || mode > 4
                || (direction != -1 && direction != 1) || automatic && (mode == 0 || pauseAfter)) return -1;
        int next = current + direction;
        if (automatic && mode == 1) next = current;
        else if (mode == 4 && count > 1) next = Math.max(0, current) + ThreadLocalRandom.current().nextInt(count - 1) + 1;
        if (automatic && mode == 2 && next >= count) return -1;
        return Math.floorMod(next, count);
    }
}
