package org.ttplayer.util;

import org.ttplayer.model.Song;

import java.io.File;

public class SongUtils {

    public static boolean isAudioFile(File f) {
        if (f == null || !f.isFile()) return false;
        String name = f.getName().toLowerCase(java.util.Locale.ROOT);
        for (String ext : FileAssociationService.EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    public static String getFileExtension(String filePath) {
        if (filePath == null) return "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1);
        }
        return "";
    }

    public static String buildSearchKeyword(Song song) {
        if (song.title != null && !song.title.trim().isEmpty()) {
            if (song.artist != null && !song.artist.trim().isEmpty()) {
                return song.artist.trim() + " " + song.title.trim();
            }
            return song.title.trim();
        }
        if (song.artist != null && !song.artist.trim().isEmpty()) {
            return song.artist.trim();
        }
        if (song.filePath != null) {
            File file = new File(song.filePath);
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            name = name.replaceAll("[_\\-\\.]+", " ").trim();
            return name;
        }
        return "";
    }
}
