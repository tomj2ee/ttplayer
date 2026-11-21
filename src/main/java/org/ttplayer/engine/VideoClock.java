package org.ttplayer.engine;

final class VideoClock {
    private long positionUs;
    private long startedNanos;
    private boolean paused;

    synchronized void reset(long positionUs, boolean paused) {
        this.positionUs = positionUs;
        this.paused = paused;
        startedNanos = System.nanoTime();
    }

    synchronized long positionUs() {
        return positionUs + (paused ? 0 : (System.nanoTime() - startedNanos) / 1000);
    }

    synchronized void pause() {
        if (!paused) {
            positionUs = positionUs();
            paused = true;
        }
    }

    synchronized void resume() {
        if (paused) {
            startedNanos = System.nanoTime();
            paused = false;
        }
    }
}
