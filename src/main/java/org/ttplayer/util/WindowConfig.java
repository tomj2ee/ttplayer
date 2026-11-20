package org.ttplayer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class WindowConfig {
    private static final Logger log = LoggerFactory.getLogger(WindowConfig.class);

    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".ttplayer", "windows.properties");

    public static void saveAll(JFrame main, JFrame lyric, JFrame eq, JFrame playlist, String skinPath) {
        saveAll(main, lyric, eq, playlist, null, false, skinPath);
    }

    public static void saveAll(JFrame main, JFrame lyric, JFrame eq, JFrame playlist, JFrame mini, boolean inMiniMode, String skinPath) {
        Properties props = loadAll();
        putWindow(props, "main", main);
        putWindow(props, "lyric", lyric);
        putWindow(props, "eq", eq);
        putWindow(props, "playlist", playlist);
        if (mini != null) {
            putWindow(props, "mini", mini);
        }
        props.setProperty("inMiniMode", String.valueOf(inMiniMode));
        if (skinPath != null && !skinPath.isEmpty()) {
            props.setProperty("skin", skinPath);
        }
        writeAll(props);
    }

    public static boolean[] restoreAll(JFrame main, JFrame lyric, JFrame eq, JFrame playlist) {
        Properties props = loadAll();
        return new boolean[]{
                getWindow(props, "main", main),
                getWindow(props, "lyric", lyric),
                getWindow(props, "eq", eq),
                getWindow(props, "playlist", playlist),
        };
    }

    public static String restoreSkin() {
        Properties props = loadAll();
        return props.getProperty("skin");
    }

    public static void saveLanguage(String lang) {
        Properties props = loadAll();
        if (lang != null && !lang.isEmpty()) {
            props.setProperty("language", lang);
        }
        writeAll(props);
    }

    public static String restoreLanguage() {
        return loadAll().getProperty("language");
    }

    public static boolean restoreMiniModeState() {
        Properties props = loadAll();
        return Boolean.parseBoolean(props.getProperty("inMiniMode", "false"));
    }

    public static boolean restoreMiniWindow(JFrame mini) {
        Properties props = loadAll();
        String x = props.getProperty("mini.x");
        if (x == null) return false;
        return getWindow(props, "mini", mini);
    }

    public static boolean hasMiniWindowPosition() { return loadAll().getProperty("mini.x") != null; }

    /**
     * 清除所有窗口位置配置，只保留皮肤路径
     * 用于切换皮肤后重置窗口布局
     */
    public static void clearWindowPositions(String skinPath) {
        Properties props = loadAll();
        props.keySet().removeIf(key -> key.toString().matches("(main|lyric|eq|playlist|mini)\\.(x|y|width|height)"));
        if (skinPath != null && !skinPath.isEmpty()) {
            props.setProperty("skin", skinPath);
        }
        writeAll(props);
    }

    private static void putWindow(Properties props, String key, JFrame frame) {
        Rectangle bounds = frame instanceof org.ttplayer.ui.LyricWindow ? ((org.ttplayer.ui.LyricWindow) frame).getNormalBounds() : frame.getBounds();
        props.setProperty(key + ".x", String.valueOf(bounds.x));
        props.setProperty(key + ".y", String.valueOf(bounds.y));
        props.setProperty(key + ".width", String.valueOf(bounds.width));
        props.setProperty(key + ".height", String.valueOf(bounds.height));
        Object normal = frame.getRootPane().getClientProperty("mini.normalVisible");
        Object suspended = frame.getRootPane().getClientProperty("suspended.visible");
        props.setProperty(key + ".visible", String.valueOf(normal instanceof Boolean ? normal : suspended instanceof Boolean ? suspended : frame.isVisible()));
    }

    private static boolean getWindow(Properties props, String key, JFrame frame) {
        String x = props.getProperty(key + ".x");
        if (x == null) return true;
        int rx = Integer.parseInt(x);
        int ry = Integer.parseInt(props.getProperty(key + ".y"));

        // 尝试读取保存的窗口大小，如果没有则使用当前大小
        int w, h;
        String widthProp = props.getProperty(key + ".width");
        String heightProp = props.getProperty(key + ".height");
        if (widthProp != null && heightProp != null) {
            w = Integer.parseInt(widthProp);
            h = Integer.parseInt(heightProp);
        } else {
            w = frame.getWidth();
            h = frame.getHeight();
        }

        Rectangle screen = MiniLyricLayout.usableBounds(new Rectangle(rx, ry, w, h));

        // 确保窗口大小合理（不能太小）
        w = Math.max(100, w);
        h = Math.max(50, h);
        if (frame instanceof org.ttplayer.ui.MiniWindow) { w = frame.getWidth(); h = frame.getHeight(); }
        else { w = Math.min(w, screen.width); h = Math.min(h, screen.height); }

        // 确保窗口在屏幕范围内
        rx = Math.max(screen.x, Math.min(rx, screen.x + screen.width - w));
        ry = Math.max(screen.y, Math.min(ry, screen.y + screen.height - h));

        frame.setSize(w, h);
        frame.setLocation(rx, ry);
        return Boolean.parseBoolean(props.getProperty(key + ".visible"));
    }

    private static Properties loadAll() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) {
                log.warn("Failed to load window config", e);
            }
        }
        return props;
    }

    private static void writeAll(Properties props) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "UI-Snap window config");
            }
        } catch (IOException e) {
            log.warn("Failed to save window config", e);
        }
    }
}
