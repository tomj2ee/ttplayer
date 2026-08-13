package org.ttplayer.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LRCLine implements Comparable<LRCLine> {
    private long timeMs;
    private String text;
    private List<LyricChar> chars;

    public LRCLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
    }

    public long getTimeMs() { return timeMs; }
    public String getText() { return text; }

    public void buildChars(long lineDurationMs) {
        if (text == null || text.isEmpty()) { chars = null; return; }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) n++;
        }
        if (n == 0) { chars = null; return; }
        int perChar = (int) (lineDurationMs / n);
        chars = new ArrayList<>(n);
        int offset = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            int dur = Math.max(50, Math.min(800, perChar));
            chars.add(new LyricChar(c, offset, dur));
            offset += perChar;
        }
    }

    public List<LyricChar> getChars() { return chars; }
    public boolean hasChars() { return chars != null && !chars.isEmpty(); }

    public void parseCharsFromEnhanced(String raw) {
        chars = new ArrayList<>();
        StringBuilder clean = new StringBuilder();
        Matcher m = Pattern.compile("([^<]*)<([0-9:.]+)>").matcher(raw);
        int offset = 0;
        while (m.find()) {
            String seg = m.group(1);
            for (int i = 0; i < seg.length(); i++) {
                chars.add(new LyricChar(seg.charAt(i), offset, 0));
            }
            clean.append(seg);
            offset += parseTimestampMs(m.group(2));
        }
        if (!chars.isEmpty()) text = clean.toString();
        else chars = null;
    }

    private static int parseTimestampMs(String ts) {
        ts = ts.trim();
        if (ts.isEmpty()) return 0;
        int min = 0;
        int colon = ts.indexOf(':');
        if (colon >= 0) {
            min = Integer.parseInt(ts.substring(0, colon)) * 60000;
            ts = ts.substring(colon + 1);
        }
        double sec = Double.parseDouble(ts);
        return min + (int) (sec * 1000);
    }

    @Override
    public int compareTo(LRCLine other) {
        return Long.compare(this.timeMs, other.timeMs);
    }
}
