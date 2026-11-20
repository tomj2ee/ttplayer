package org.ttplayer.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class TtVisualSettings {
    private static final Logger LOG = LoggerFactory.getLogger(TtVisualSettings.class);
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".ttplayer", "visual.properties");
    private static final PropertyChangeSupport CHANGES = new PropertyChangeSupport(TtVisualSettings.class);
    private static TtVisualSettings current = load();

    public TtVisualizer.Mode mode = TtVisualizer.Mode.BARS;
    public boolean custom;
    private TtVisualStyle style = new TtVisualStyle();

    public static TtVisualSettings get() {
        TtVisualSettings result = new TtVisualSettings();
        result.mode = current.mode;
        result.custom = current.custom;
        result.style = current.style.copy();
        return result;
    }

    public static void update(TtVisualSettings settings) {
        TtVisualSettings next = new TtVisualSettings();
        next.mode = settings.mode == null ? TtVisualizer.Mode.BARS : settings.mode;
        next.custom = settings.custom;
        next.style = settings.style.copy();
        current = next;
        save();
        CHANGES.firePropertyChange("visualSettings", null, null);
    }

    public static void addListener(PropertyChangeListener listener) {
        CHANGES.addPropertyChangeListener(listener);
    }

    public static void removeListener(PropertyChangeListener listener) {
        CHANGES.removePropertyChangeListener(listener);
    }

    public static int frameDelay() {
        return (1000 + current.style.fps - 1) / current.style.fps;
    }

    TtVisualStyle effective(TtVisualStyle skin) {
        TtVisualStyle result = (custom ? style : skin).copy();
        result.fps = style.fps;
        result.showLabel = style.showLabel;
        result.labelColor = style.labelColor;
        result.labelFont = style.labelFont;
        return result;
    }

    void customize(TtVisualStyle skin) {
        if (!custom) {
            style = effective(skin);
            custom = true;
        }
    }

    public int getFps() { return style.fps; }
    public void setFps(int value) { style.fps = Math.max(10, Math.min(40, value)); }
    public int getBlurSpeed() { return style.blurSpeed; }
    public void setBlurSpeed(int value) { style.blurSpeed = Math.max(1, Math.min(10, value)); }
    public boolean isWide() { return style.wide; }
    public void setWide(boolean value) { style.wide = value; }
    public boolean isBlur() { return style.blur; }
    public void setBlur(boolean value) { style.blur = value; }
    public boolean isShowLabel() { return style.showLabel; }
    public void setShowLabel(boolean value) { style.showLabel = value; }
    public int getLabelFont() { return style.labelFont; }
    public void setLabelFont(int value) { style.labelFont = Math.max(0, Math.min(3, value)); }

    public Color getColor(int index) {
        switch (index) {
            case 0: return style.peak;
            case 1: return style.top;
            case 2: return style.middle;
            case 3: return style.bottom;
            case 4: return style.scope;
            case 5: return style.labelColor;
            default: throw new IllegalArgumentException("Unknown visual color: " + index);
        }
    }

    public void setColor(int index, Color color) {
        if (color == null) return;
        switch (index) {
            case 0: style.peak = color; break;
            case 1: style.top = color; break;
            case 2: style.middle = color; break;
            case 3: style.bottom = color; break;
            case 4: style.scope = color; break;
            case 5: style.labelColor = color; break;
            default: throw new IllegalArgumentException("Unknown visual color: " + index);
        }
    }

    private static TtVisualSettings load() {
        TtVisualSettings result = new TtVisualSettings();
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                properties.load(input);
            } catch (IOException | IllegalArgumentException error) {
                properties.clear();
                LOG.warn("Failed to load visual settings", error);
            }
        }
        result.mode = TtVisualizer.Mode.fromId(number(properties, "mode", 30, 0, Integer.MAX_VALUE));
        result.custom = Boolean.parseBoolean(properties.getProperty("custom", "false"));
        result.setFps(number(properties, "fps", 25, 10, 40));
        result.setBlurSpeed(number(properties, "blurSpeed", 3, 1, 10));
        result.setWide(Boolean.parseBoolean(properties.getProperty("wide", "false")));
        result.setBlur(Boolean.parseBoolean(properties.getProperty("blur", "true")));
        result.setShowLabel(Boolean.parseBoolean(properties.getProperty("showLabel", "false")));
        result.setLabelFont(number(properties, "labelFont", 0, 0, 3));
        String[] keys = {"peak", "top", "middle", "bottom", "scope", "labelColor"};
        for (int index = 0; index < keys.length; index++) {
            result.setColor(index, new Color(number(properties, keys[index],
                    result.getColor(index).getRGB() & 0xffffff, 0, 0xffffff)));
        }
        return result;
    }

    private static int number(Properties properties, String key, int fallback, int minimum, int maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Integer.parseInt(properties.getProperty(key))));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("mode", String.valueOf(current.mode.id));
        properties.setProperty("custom", String.valueOf(current.custom));
        properties.setProperty("fps", String.valueOf(current.getFps()));
        properties.setProperty("blurSpeed", String.valueOf(current.getBlurSpeed()));
        properties.setProperty("wide", String.valueOf(current.isWide()));
        properties.setProperty("blur", String.valueOf(current.isBlur()));
        properties.setProperty("showLabel", String.valueOf(current.isShowLabel()));
        properties.setProperty("labelFont", String.valueOf(current.getLabelFont()));
        String[] keys = {"peak", "top", "middle", "bottom", "scope", "labelColor"};
        for (int index = 0; index < keys.length; index++) {
            properties.setProperty(keys[index], String.valueOf(current.getColor(index).getRGB() & 0xffffff));
        }
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "TTPlayer visual settings");
            }
        } catch (IOException error) {
            LOG.warn("Failed to save visual settings", error);
        }
    }
}
