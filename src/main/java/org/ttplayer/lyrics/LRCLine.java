package org.ttplayer.lyrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LRCLine implements Comparable<LRCLine> {
    private long timeMs;
    private String text;
    private List<LyricChar> chars;
    private List<Word> words;

    public static final class Word {
        public final long timeMs;
        public final String text;
        public Word(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }

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
    public List<Word> getWords() { return words; }

    public void parseCharsFromEnhanced(String raw) {
        words = new ArrayList<>();
        StringBuilder clean = new StringBuilder();
        Matcher matcher = Pattern.compile("<(\\d+:[0-5]?\\d(?:\\.\\d+)?)>").matcher(raw);
        long timestamp = timeMs;
        int start = 0;
        while (matcher.find()) {
            String fragment = raw.substring(start, matcher.start());
            if (!fragment.isEmpty()) {
                words.add(new Word(timestamp, fragment));
                clean.append(fragment);
            }
            try { timestamp = parseTimestampMs(matcher.group(1)); }
            catch (NumberFormatException error) { timestamp = timeMs; }
            start = matcher.end();
        }
        if (start == 0) { words = null; return; }
        String fragment = raw.substring(start);
        if (!fragment.isEmpty()) { words.add(new Word(timestamp, fragment)); clean.append(fragment); }
        text = clean.toString();
    }

    private static long parseTimestampMs(String timestamp) {
        int colon = timestamp.indexOf(':');
        double milliseconds = Double.parseDouble(timestamp.substring(0, colon)) * 60000 +
                Double.parseDouble(timestamp.substring(colon + 1)) * 1000;
        return (long) Math.max(0, Math.min(Long.MAX_VALUE, milliseconds));
    }

    @Override
    public int compareTo(LRCLine other) {
        return Long.compare(this.timeMs, other.timeMs);
    }
}
