package org.ttplayer.lyrics;

import org.ttplayer.util.MenuSettings;
import java.awt.Color;

public final class LyricSettings {
    public Style window = new Style(false);
    public Style desktop = new Style(true);
    public boolean autoLoad = true;
    public boolean autoOnline;
    public boolean trim;
    public boolean seek = true;

    public static final class Style {
        public boolean custom, transparent, outline;
        public boolean fade = true, shadow = true;
        public int fontSize, font, alignment = 1, spacing = 4, scrollMode;
        public int textOpacity = 100, backgroundOpacity;
        public Color normal, highlight, word, background = Color.BLACK;

        public Style(boolean desktop) {
            fontSize = desktop ? 24 : 18;
            backgroundOpacity = desktop ? 0 : 70;
            normal = desktop ? Color.WHITE : new Color(0x8bbac6);
            highlight = desktop ? new Color(0x00ff80) : Color.WHITE;
            word = desktop ? highlight : new Color(0xffdc50);
        }

        public Style copy() {
            Style copy = new Style(false);
            copy.custom = custom;
            copy.fade = fade;
            copy.transparent = transparent;
            copy.shadow = shadow;
            copy.outline = outline;
            copy.fontSize = fontSize;
            copy.font = font;
            copy.alignment = alignment;
            copy.spacing = spacing;
            copy.scrollMode = scrollMode;
            copy.textOpacity = textOpacity;
            copy.backgroundOpacity = backgroundOpacity;
            copy.normal = normal;
            copy.highlight = highlight;
            copy.word = word;
            copy.background = background;
            return copy;
        }

        public boolean isConfigured() {
            return custom || alignment != 1 || scrollMode != 0 || spacing != 4 || !fade || transparent ||
                    !shadow || outline || textOpacity != 100 || backgroundOpacity != 70;
        }

        public String fontFamily() {
            return new String[] {"", "Microsoft YaHei", "SimSun", "Arial"}[clamp(font, 0, 3)];
        }
    }

    public static LyricSettings load() {
        LyricSettings settings = new LyricSettings();
        settings.window = loadStyle("lyric.window.", false);
        settings.desktop = loadStyle("lyric.desktop.", true);
        settings.autoLoad = MenuSettings.flag("lyric.autoLoad", true);
        settings.autoOnline = MenuSettings.flag("lyric.autoOnline", false);
        settings.trim = MenuSettings.flag("lyric.trim", false);
        settings.seek = MenuSettings.flag("lyric.seek", true);
        return settings;
    }

    private static Style loadStyle(String prefix, boolean desktop) {
        Style style = new Style(desktop);
        style.custom = MenuSettings.flag(prefix + "custom", false);
        style.fade = MenuSettings.flag(prefix + "fade", true);
        style.transparent = MenuSettings.flag(prefix + "transparent", false);
        style.shadow = MenuSettings.flag(prefix + "shadow", true);
        style.outline = MenuSettings.flag(prefix + "outline", false);
        style.fontSize = clamp(MenuSettings.number(prefix + "fontSize", style.fontSize), 8, 72);
        style.font = clamp(MenuSettings.number(prefix + "font", 0), 0, 3);
        style.alignment = clamp(MenuSettings.number(prefix + "alignment", 1), 0, 2);
        style.spacing = clamp(MenuSettings.number(prefix + "spacing", 4), 0, 24);
        style.scrollMode = clamp(MenuSettings.number(prefix + "scrollMode", 0), 0, 2);
        style.textOpacity = clamp(MenuSettings.number(prefix + "textOpacity", 100), 0, 100);
        style.backgroundOpacity = clamp(MenuSettings.number(prefix + "backgroundOpacity", style.backgroundOpacity), 0, 100);
        style.normal = new Color(MenuSettings.number(prefix + "normal", style.normal.getRGB()), true);
        style.highlight = new Color(MenuSettings.number(prefix + "highlight", style.highlight.getRGB()), true);
        style.word = new Color(MenuSettings.number(prefix + "word", style.word.getRGB()), true);
        style.background = new Color(MenuSettings.number(prefix + "background", style.background.getRGB()), true);
        return style;
    }

    public void save() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        saveStyle("lyric.window.", window, values);
        saveStyle("lyric.desktop.", desktop, values);
        values.put("lyric.autoLoad", autoLoad);
        values.put("lyric.autoOnline", autoOnline);
        values.put("lyric.trim", trim);
        values.put("lyric.seek", seek);
        MenuSettings.setAll(values);
    }

    private static void saveStyle(String prefix, Style style, java.util.Map<String, Object> values) {
        values.put(prefix + "custom", style.custom);
        values.put(prefix + "fade", style.fade);
        values.put(prefix + "transparent", style.transparent);
        values.put(prefix + "shadow", style.shadow);
        values.put(prefix + "outline", style.outline);
        values.put(prefix + "fontSize", clamp(style.fontSize, 8, 72));
        values.put(prefix + "font", clamp(style.font, 0, 3));
        values.put(prefix + "alignment", clamp(style.alignment, 0, 2));
        values.put(prefix + "spacing", clamp(style.spacing, 0, 24));
        values.put(prefix + "scrollMode", clamp(style.scrollMode, 0, 2));
        values.put(prefix + "textOpacity", clamp(style.textOpacity, 0, 100));
        values.put(prefix + "backgroundOpacity", clamp(style.backgroundOpacity, 0, 100));
        values.put(prefix + "normal", style.normal.getRGB());
        values.put(prefix + "highlight", style.highlight.getRGB());
        values.put(prefix + "word", style.word.getRGB());
        values.put(prefix + "background", style.background.getRGB());
    }

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
