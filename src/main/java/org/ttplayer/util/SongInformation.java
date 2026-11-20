package org.ttplayer.util;

import org.ttplayer.engine.AudioSourceInfo;
import org.ttplayer.model.Song;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SongInformation {
    private SongInformation() {}

    public static List<String> pages(Song song, int durationSeconds, AudioSourceInfo source) {
        if (song == null) return Collections.singletonList(Messages.get("app.name"));
        String title = song.title == null || song.title.isEmpty() ? song.getFileName() : song.title;
        String extension = SongUtils.getFileExtension(song.getFileName()).toUpperCase(Locale.ROOT);
        return pages(title, song.artist, song.album, extension,
                durationSeconds > 0 ? durationSeconds : catalogDuration(song.duration), source);
    }

    public static List<String> pages(String title, String artist, String album, String format, long durationSeconds,
                                     AudioSourceInfo source) {
        if (source == null) source = AudioSourceInfo.EMPTY;
        List<String> values = new ArrayList<>();
        String name = title == null || title.isEmpty() ? Messages.get("app.name") : title;
        values.add(artist == null || artist.isEmpty() ? name : artist + " — " + name);
        if (album != null && !album.isEmpty()) values.add(Messages.get("song.albumPrefix") + album);
        if (durationSeconds > 0) values.add(Messages.get("song.durationPrefix") +
                String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60));
        String encoding = format == null ? "" : format;
        if (!source.codec.isEmpty()) encoding += (encoding.isEmpty() ? "" : " · ") + source.codec;
        if (!encoding.isEmpty()) values.add(Messages.get("song.formatPrefix") + encoding);
        if (source.sampleRate > 0) {
            String rate = BigDecimal.valueOf(source.sampleRate).movePointLeft(3).round(new MathContext(3))
                    .stripTrailingZeros().toPlainString();
            String audio = Messages.get("song.audioPrefix") + rate + " kHz";
            if (source.bitsPerSample > 0) audio += " · " + source.bitsPerSample + " bit";
            if (source.channels > 0) audio += " · " + source.channels + " " + Messages.get("song.channels");
            values.add(audio);
        }
        if (source.bitRate > 0) values.add(Messages.get("song.bitratePrefix") +
                String.format(Locale.ROOT, "%.0f kbps", source.bitRate / 1000.0));
        if (source.fileSize > 0) {
            boolean large = source.fileSize >= 1073741824L;
            values.add(Messages.get("song.sizePrefix") + String.format(Locale.ROOT, "%.2f %s",
                    source.fileSize / (large ? 1073741824.0 : 1048576.0), large ? "GiB" : "MiB"));
        }
        return values;
    }

    private static long catalogDuration(String duration) {
        if (duration == null || duration.isEmpty()) return 0;
        String[] parts = duration.split(":", -1);
        if (parts.length > 3) return 0;
        long seconds = 0;
        try {
            for (int index = 0; index < parts.length; index++) {
                if (!parts[index].matches("[0-9]+")) return 0;
                long value = Long.parseLong(parts[index]);
                if (index > 0 && value >= 60) return 0;
                seconds = Math.addExact(Math.multiplyExact(seconds, 60), value);
            }
            return seconds;
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            return 0;
        }
    }
}
