package org.ttplayer.lyrics;

import org.ttplayer.model.Song;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class LyricsRepository {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private LyricsRepository() {}

    public static Path cachePath(Song song) {
        String path = new File(song.filePath).getAbsoluteFile().toPath().normalize().toString();
        long hash = 0xcbf29ce484222325L;
        for (byte value : path.getBytes(StandardCharsets.UTF_8)) { hash ^= value & 0xff; hash *= 0x100000001b3L; }
        return Paths.get(System.getProperty("user.home"), ".ttplayer", "lyrics", Long.toUnsignedString(hash) + ".lrc");
    }

    public static File find(Song song) {
        if (song == null || song.filePath == null) return null;
        File local = LRCParser.findLRCFile(new File(song.filePath));
        if (local != null) return local;
        File cache = cachePath(song).toFile();
        return cache.isFile() ? cache : null;
    }

    public static void store(Song song, String lyrics, File destination, boolean associate) throws IOException {
        byte[] bytes = lyrics.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("LRC exceeds 4 MiB");
        if (song != null && song.filePath != null) write(cachePath(song), bytes, true);
        if (destination != null) write(destination.toPath(), bytes, true);
        if (associate && song != null && song.filePath != null) {
            String path = new File(song.filePath).getAbsolutePath();
            int extension = path.lastIndexOf('.');
            int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            Path local = Paths.get((extension > separator ? path.substring(0, extension) : path) + ".lrc");
            if (!Files.exists(local)) {
                try { write(local, bytes, false); }
                catch (IOException error) {
                    if (!Files.isRegularFile(cachePath(song))) throw error;
                }
            }
        }
    }

    private static void write(Path destination, byte[] bytes, boolean replace) throws IOException {
        destination = destination.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".ttplayer-lrc-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (replace) {
                try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException error) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
            } else Files.move(temporary, destination);
        } finally { Files.deleteIfExists(temporary); }
    }
}
