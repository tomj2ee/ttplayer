package org.ttplayer.util;

import org.ttplayer.model.Song;

import java.io.File;

public class SongUtils {
    private static final String[] VIDEO_EXTENSIONS = {
            "mp4", "mkv", "avi", "mov", "webm", "m4v", "wmv", "flv", "mpg", "mpeg", "m2ts", "mts", "ts", "3gp", "ogv", "vob"
    };

    public static boolean isMediaFile(File file) {
        if (isAudioFile(file)) return true;
        if (file == null || !file.isFile()) return false;
        String extension = getFileExtension(file.getName()).toLowerCase(java.util.Locale.ROOT);
        for (String candidate : VIDEO_EXTENSIONS) if (candidate.equals(extension)) return true;
        return false;
    }

    public static javax.swing.filechooser.FileNameExtensionFilter mediaFileFilter() {
        java.util.Set<String> extensions = new java.util.LinkedHashSet<>();
        for (String extension : FileAssociationService.EXTENSIONS) extensions.add(extension.substring(1));
        java.util.Collections.addAll(extensions, VIDEO_EXTENSIONS);
        return new javax.swing.filechooser.FileNameExtensionFilter(Messages.get("video.mediaFiles"),
                extensions.toArray(new String[0]));
    }

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
