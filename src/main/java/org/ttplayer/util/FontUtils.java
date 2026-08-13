package org.ttplayer.util;

import java.awt.*;

/**
 * 字体工具类，用于处理中文字体的选择和检测
 */
public class FontUtils {

    private FontUtils() {}

    /**
     * 检查字体是否支持中文显示
     * @param font 要检查的字体
     * @return 如果字体支持中文显示返回true，否则返回false
     */
    public static boolean isChineseCapable(Font font) {
        if (font == null) return false;
        try {
            return font.canDisplay('中') && font.canDisplay('文');
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查字体名称是否支持中文显示
     * @param fontName 字体名称
     * @return 如果字体支持中文显示返回true，否则返回false
     */
    public static boolean isChineseCapable(String fontName) {
        if (fontName == null || fontName.isEmpty()) return false;
        try {
            Font font = new Font(fontName, Font.PLAIN, 12);
            return isChineseCapable(font);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取适合显示中文的字体
     * 优先尝试指定的字体，如果不支持中文则回退到通用中文字体
     * @param preferredFontName 首选字体名称，可以为null
     * @param style 字体样式（如Font.PLAIN, Font.BOLD等）
     * @param size 字体大小
     * @return 适合显示中文的字体
     */
    public static Font getChineseFont(String preferredFontName, int style, int size) {
        // 1. 首先尝试用户指定的字体
        if (preferredFontName != null && !preferredFontName.isEmpty()) {
            Font font = new Font(preferredFontName, style, size);
            if (isChineseCapable(font)) {
                return font;
            }
        }

        // 2. 尝试宋体（Windows下常用中文字体）
        Font songFont = new Font("宋体", style, size);
        if (isChineseCapable(songFont)) {
            return songFont;
        }

        // 3. 尝试微软雅黑
        Font yaheiFont = new Font("微软雅黑", style, size);
        if (isChineseCapable(yaheiFont)) {
            return yaheiFont;
        }

        // 4. 尝试其他常见中文字体
        String[] fallbackFonts = {"SimSun", "Microsoft YaHei", "SimHei", "NSimSun", "PingFang SC", "STHeiti"};
        for (String fontName : fallbackFonts) {
            Font font = new Font(fontName, style, size);
            if (isChineseCapable(font)) {
                return font;
            }
        }

        // 5. 最后回退到Dialog（跨平台通用字体）
        return new Font("Dialog", style, size);
    }

    /**
     * 获取适合显示中文的字体，使用默认字体样式
     * @param preferredFontName 首选字体名称，可以为null
     * @param size 字体大小
     * @return 适合显示中文的字体
     */
    public static Font getChineseFont(String preferredFontName, int size) {
        return getChineseFont(preferredFontName, Font.PLAIN, size);
    }

    /**
     * 获取适合显示中文的默认字体
     * @param style 字体样式
     * @param size 字体大小
     * @return 适合显示中文的字体
     */
    public static Font getDefaultChineseFont(int style, int size) {
        return getChineseFont(null, style, size);
    }

    /**
     * 获取适合显示中文的默认字体（普通样式）
     * @param size 字体大小
     * @return 适合显示中文的字体
     */
    public static Font getDefaultChineseFont(int size) {
        return getChineseFont(null, Font.PLAIN, size);
    }
}
