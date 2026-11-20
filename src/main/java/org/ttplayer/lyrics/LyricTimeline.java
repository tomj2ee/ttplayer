package org.ttplayer.lyrics;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class LyricTimeline {
    private LyricTimeline() {}

    public static int active(List<LRCLine> lines, long position, long offset) {
        if (lines == null) return -1;
        long time = saturatedAdd(position, offset);
        int first = 0, end = lines.size();
        while (first < end) {
            int middle = first + (end - first) / 2;
            if (time < lines.get(middle).getTimeMs()) end = middle;
            else first = middle + 1;
        }
        return first - 1;
    }

    public static float progress(List<LRCLine> lines, int index, long position, long duration, long offset) {
        if (lines == null || index < 0 || index >= lines.size()) return 0;
        LRCLine line = lines.get(index);
        long end = index + 1 < lines.size() ? lines.get(index + 1).getTimeMs()
                : Math.max(duration, saturatedAdd(line.getTimeMs(), 5000));
        long now = saturatedAdd(position, offset);
        List<LRCLine.Word> words = line.getWords();
        if (words == null || words.size() <= 1 || line.getText().isEmpty())
            return ratio(now, line.getTimeMs(), end);
        double completed = 0;
        for (int wordIndex = 0; wordIndex < words.size(); wordIndex++) {
            LRCLine.Word word = words.get(wordIndex);
            long next = wordIndex + 1 < words.size() ? words.get(wordIndex + 1).timeMs : end;
            completed += word.text.getBytes(StandardCharsets.UTF_8).length * ratio(now, word.timeMs, next);
        }
        return (float) Math.min(1, completed / Math.max(1, line.getText().getBytes(StandardCharsets.UTF_8).length));
    }

    private static float ratio(long now, long start, long end) {
        if (now <= start) return 0;
        if (end <= start) return 1;
        return (float) Math.min(1, ((double) now - start) / ((double) end - start));
    }

    public static long seekPosition(List<LRCLine> lines, int index, long offset) {
        if (lines == null || lines.isEmpty()) return 0;
        return Math.max(0, saturatedAdd(lines.get(LyricSettings.clamp(index, 0, lines.size() - 1)).getTimeMs(), -offset));
    }

    public static long saturatedAdd(long value, long amount) {
        if (amount > 0 && value > Long.MAX_VALUE - amount) return Long.MAX_VALUE;
        if (amount < 0 && value < Long.MIN_VALUE - amount) return Long.MIN_VALUE;
        return value + amount;
    }
}
