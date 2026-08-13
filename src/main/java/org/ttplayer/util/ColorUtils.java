package org.ttplayer.util;

import java.awt.Color;

/**
 * 颜色工具类，用于安全地解析颜色字符串
 */
public class ColorUtils {

    private ColorUtils() {}

    /**
     * 安全地解析颜色字符串
     * 自动添加#前缀（如果缺少），并处理解析失败的情况
     *
     * @param colorStr 颜色字符串，如"ffffff"或"#ffffff"
     * @param defaultValue 解析失败时返回的默认值
     * @return 解析后的Color对象
     */
    public static Color decode(String colorStr, Color defaultValue) {
        if (colorStr == null || colorStr.isEmpty()) {
            return defaultValue;
        }
        try {
            String processed = colorStr;
            if (!processed.startsWith("#")) {
                processed = "#" + processed;
            }
            return Color.decode(processed);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 安全地解析颜色字符串，失败时返回null
     *
     * @param colorStr 颜色字符串，如"ffffff"或"#ffffff"
     * @return 解析后的Color对象，解析失败返回null
     */
    public static Color decode(String colorStr) {
        return decode(colorStr, null);
    }
}
