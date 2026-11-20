package org.ttplayer.ui;

final class LyricScroll {
    private float origin, center;
    private int target;
    private long position, started;
    private boolean initialized, dragging;

    void reset() { initialized = false; }

    float update(int active, float preview, boolean previewing, long playback, long now) {
        float destination = previewing ? preview : Math.max(0, active);
        double difference = (double) playback - position;
        if (!initialized || difference < -100 || difference > 300 || previewing || dragging ||
                Math.abs(destination - target) > 1 || now < started) {
            origin = center = destination;
            target = Math.max(0, active);
            started = now;
        } else {
            float progress = Math.min(1, (now - started) / 180f);
            float remaining = 1 - progress;
            center = origin + (target - origin) * (1 - remaining * remaining * remaining);
            if (target != active && active >= 0) {
                origin = center;
                target = active;
                started = now;
            }
        }
        position = playback;
        dragging = previewing;
        initialized = true;
        return center;
    }
}
