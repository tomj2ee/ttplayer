package org.ttplayer.lyrics;

public class LyricChar {
    public final char ch;
    public final int startMs;
    public final int durationMs;

    public LyricChar(char ch, int startMs, int durationMs) {
        this.ch = ch;
        this.startMs = startMs;
        this.durationMs = durationMs;
    }

    public int endMs() { return startMs + durationMs; }
}
