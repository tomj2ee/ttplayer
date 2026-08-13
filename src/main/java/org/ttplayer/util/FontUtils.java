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
     * 检查字体是否支持韩文（谚文）显示
     * @param font 要检查的字体
     * @return 如果字体支持韩文显示返回true，否则返回false
     */
    public static boolean isKoreanCapable(Font font) {
        if (font == null) return false;
        try {
            // 现代韩文音节（U+AC00）与谚文字母（U+3130 区）各取典型字符
            return font.canDisplay('한') && font.canDisplay('글');
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查字体是否支持日文（假名）显示
     * @param font 要检查的字体
     * @return 如果字体支持日文显示返回true，否则返回false
     */
    public static boolean isJapaneseCapable(Font font) {
        if (font == null) return false;
        try {
            // 平假名「あ」与片假名「ア」
            return font.canDisplay('あ') && font.canDisplay('ア');
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
     * 获取适合显示中日韩文字的字体
     * 优先尝试指定的字体，如果不支持当前文案则回退到常见 CJK 字体
     * （中文字体链 + 韩文/日文常用字体，保证歌词等 CJK 文案可显示）
     * @param preferredFontName 首选字体名称，可以为null
     * @param style 字体样式（如Font.PLAIN, Font.BOLD等）
     * @param size 字体大小
     * @return 适合显示中日韩文字的字体
     */
    public static Font getChineseFont(String preferredFontName, int style, int size) {
        // 当前 UI 语言决定了需要哪种字形（中文/韩文/日文/其他）
        String lang = uiLanguage();

        // 1. 首先尝试用户指定的字体：需能显示当前 UI 语言
        if (preferredFontName != null && !preferredFontName.isEmpty()) {
            Font font = new Font(preferredFontName, style, size);
            if (capableFor(lang, font)) {
                return font;
            }
        }

        // 2. 按语言选择字体候选链（中文 / 韩文 / 日文 / 通用英文系统）
        String[] fallbackFonts = fontChainFor(lang);
        for (String fontName : fallbackFonts) {
            Font font = new Font(fontName, style, size);
            if (capableFor(lang, font)) {
                return font;
            }
        }

        // 3. 系统枚举兜底（优先找能显示当前语言字形的已装字体）
        Font systemFont = findCapableSystemFont(lang, style, size);
        if (systemFont != null) {
            return systemFont;
        }

        // 4. 最后回退到 Dialog（Java 逻辑字体，依赖系统实际渲染；Swing 会再做系统级字形回退）
        return new Font("Dialog", style, size);
    }

    /** 当前 UI 语言代码（zh/ko/ja/其他），来自 Messages locale */
    private static String uiLanguage() {
        try {
            return Messages.getLocale().getLanguage();
        } catch (Exception e) {
            return "zh";
        }
    }

    /** 该语言下字体所需的最小字形能力 */
    private static boolean capableFor(String lang, Font font) {
        if (font == null) return false;
        if ("ko".equals(lang)) return isKoreanCapable(font);
        if ("ja".equals(lang)) return isJapaneseCapable(font);
        if ("zh".equals(lang)) return isChineseCapable(font);
        // 其他语言（en/de/...）走通用：至少能显示基本拉丁字符
        try {
            return font.canDisplay('A') && font.canDisplay('g') && font.canDisplay('1');
        } catch (Exception e) {
            return false;
        }
    }

    /** 不同语言的字体候选链（顺序即优先度） */
    private static String[] fontChainFor(String lang) {
        if ("ko".equals(lang)) {
            return new String[]{
                    // 韩文优先：能显示谚文，且通常保留汉字字形
                    "Malgun Gothic", "맑은 고딕", "Gulim", "굴림", "Batang", "바탕", "Dotum", "돋움",
                    "Noto Sans KR", "Noto Sans CJK KR", "Noto Sans CJK SC",
                    // 中文兜底（谚文字形部分保留）
                    "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "PingFang SC", "Apple SD Gothic Neo"
            };
        }
        if ("ja".equals(lang)) {
            return new String[]{
                    // 日文优先：含假名与汉字
                    "MS Gothic", "MSGothic", "Yu Gothic", "YuGothic", "Meiryo", "メイリオ",
                    "Hiragino Sans", "Noto Sans CJK JP", "Noto Sans CJK SC",
                    "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "PingFang SC"
            };
        }
        return new String[]{
                // 中文优先
                "SimSun", "宋体", "Microsoft YaHei", "微软雅黑", "SimHei", "NSimSun",
                "PingFang SC", "STHeiti", "Noto Sans CJK SC",
                "Noto Sans CJK KR", "Malgun Gothic", "Apple SD Gothic Neo"
        };
    }

    /** 遍历系统字体，找第一个能显示当前语言字形的已装字体 */
    private static Font findCapableSystemFont(String lang, int style, int size) {
        try {
            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            for (Font f : fonts) {
                if (capableFor(lang, f)) {
                    return f.deriveFont(style, (float) size);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 遍历系统已安装字体，找到第一个同时能显示韩文与中文的字体
     */
    private static Font findKoreanCapableSystemFont(int style, int size) {
        try {
            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            // 第一遍找中+韩都支持的，更通用；找不到再放宽到仅韩文
            for (Font f : fonts) {
                if (f.canDisplay('한') && f.canDisplay('글') && f.canDisplay('中') && f.canDisplay('文')) {
                    return f.deriveFont(style, (float) size);
                }
            }
            for (Font f : fonts) {
                if (f.canDisplay('한') && f.canDisplay('글')) {
                    return f.deriveFont(style, (float) size);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 获取适合显示歌词（跨中日韩）的字体：韩文优先。
     * 歌词文案可能混含中文/韩文/日文，必须保证所有字形可渲染，
     * 首选字体若只支持中文（如微软雅黑）将不会采用，而是回退到支持韩文的字体。
     * @param preferredFontName 皮肤配置的首选字体，可为null
     * @param style 字体样式
     * @param size 字体大小
     * @return 适合显示中日韩歌词的字体
     */
    public static Font getLyricFont(String preferredFontName, int style, int size) {
        // 1. 皮肤指定的字体：仅当能显示韩文时采用
        if (preferredFontName != null && !preferredFontName.isEmpty()) {
            Font font = new Font(preferredFontName, style, size);
            Font derived = font.deriveFont(style, (float) size);
            if (isKoreanCapable(derived)) {
                return derived;
            }
        }
        // 2. 韩文字体优先，同时保留中文能力
        String[] koreanFonts = {
                "Malgun Gothic", "맑은 고딕", "Batang", "돋움", "Dotum", "Gulim", "굴림",
                "Noto Sans KR", "Noto Sans CJK KR", "Noto Sans CJK SC",
                "Apple SD Gothic Neo", "PingFang SC", "STHeiti",
                "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "SimHei", "NSimSun",
                "WenQuanYi Micro Hei", "WenQuanYi Zen Hei"
        };
        for (String fontName : koreanFonts) {
            Font font = new Font(fontName, style, size);
            Font derived = font.deriveFont(style, (float) size);
            if (isKoreanCapable(derived)) {
                return derived;
            }
        }
        // 3. 系统枚举兜底
        Font systemKorean = findKoreanCapableSystemFont(style, size);
        if (systemKorean != null) {
            return systemKorean;
        }
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
