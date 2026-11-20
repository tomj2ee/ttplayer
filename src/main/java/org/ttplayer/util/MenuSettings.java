package org.ttplayer.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class MenuSettings {
    private static final Path PATH = Paths.get(System.getProperty("user.home"), ".ttplayer", "menus.properties");
    private static final Properties VALUES = new Properties();
    static {
        if (Files.isRegularFile(PATH)) {
            try (InputStream input = Files.newInputStream(PATH)) { VALUES.load(input); }
            catch (IOException error) { System.err.println(error.getMessage()); }
        }
    }
    private MenuSettings() {}
    public static synchronized String get(String key, String fallback) { return VALUES.getProperty(key, fallback); }
    public static boolean flag(String key, boolean fallback) { return Boolean.parseBoolean(get(key, String.valueOf(fallback))); }
    public static int number(String key, int fallback) {
        try { return Integer.parseInt(get(key, String.valueOf(fallback))); }
        catch (NumberFormatException error) { return fallback; }
    }
    public static synchronized void set(String key, Object value) {
        setAll(java.util.Collections.singletonMap(key, value));
    }
    public static synchronized void setAll(java.util.Map<String, ?> values) {
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) VALUES.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        try {
            Files.createDirectories(PATH.getParent());
            Path temporary = Files.createTempFile(PATH.getParent(), "menus-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) { VALUES.store(output, "TTPlayer menus"); }
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException error) { System.err.println(error.getMessage()); }
    }
}
