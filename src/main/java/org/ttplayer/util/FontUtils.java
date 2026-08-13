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
        if (font == null || isLogicalFont(font)) return false;
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
        if (font == null || isLogicalFont(font)) return false;
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
        if (font == null || isLogicalFont(font)) return false;
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
     * 根据歌词内容自动选择能覆盖全部所需字形的字体。
     * 扫描文本判断包含哪些文字种类（汉字/谚文/假名/拉丁），
     * 再选一个候选链中能同时显示所有这些字形的字体——彻底避免豆腐块。
     * @param text 待渲染的歌词文本（可整首拼接传入）
     * @param preferredFontName 皮肤配置的首选字体，可为null
     * @param style 字体样式
     * @param size 字体大小
     * @return 能显示歌词中所有文字种类的字体
     */
    public static Font getLyricFontByText(String text, String preferredFontName, int style, int size) {
        TextAbility need = analyzeText(text);

        // 1. 首选字体：需满足文本需要的全部能力
        if (preferredFontName != null && !preferredFontName.isEmpty()) {
            Font font = new Font(preferredFontName, style, size);
            if (hasAbility(font, need)) {
                return font.deriveFont(style, (float) size);
            }
        }
        // 2. 按内容语言优先的候选链 + 全能型兜底：
        //    纯中文 → 中文字体优先；纯韩文 → 韩文字体优先；纯日文 → 日文字体优先；
        //    混合 → 中日韩全能字体（Noto/Malgun 等）。始终要求覆盖文本全部字形。
        String[] candidates = languageAwareChain(need);
        Font fallback = null;
        for (String fontName : candidates) {
            Font font = new Font(fontName, style, size);
            if (hasAbility(font, need)) {
                return font.deriveFont(style, (float) size);
            }
            // 记录第一个真物理字体作最后的兜底（至少不是 Dialog）
            if (fallback == null && !isLogicalFont(font)) {
                fallback = font.deriveFont(style, (float) size);
            }
        }
        // 3. 系统枚举兜底：找能满足能力的已装字体
        Font systemFont = findSystemFontForAbility(need, style, size);
        if (systemFont != null) {
            return systemFont;
        }
        // 4. 候选链里至少有一个物理字体时用其兜底（多语种覆盖尽力而为）
        if (fallback != null) {
            return fallback;
        }
        // 5. 最后回退到 Dialog
        return new Font("Dialog", style, size);
    }

    /** 依据检测出的文本语言返回合适候选链（全能字体置后作为兜底） */
    private static String[] languageAwareChain(TextAbility need) {
        boolean hasHangul = need.hangul;
        boolean hasKana = need.kana;
        boolean hasHan = need.han;

        // 混合文本（含谚文/假名 + 汉字等）→ 全能字体优先
        int scriptCount = (hasHangul ? 1 : 0) + (hasKana ? 1 : 0) + (hasHan ? 1 : 0);
        if (scriptCount >= 2) {
            return new String[]{
                    "Noto Sans CJK KR", "Noto Sans CJK JP", "Noto Sans CJK SC", "Noto Sans KR",
                    "Malgun Gothic", "맑은 고딕", "Gulim", "굴림", "Batang", "바탕", "Dotum", "돋움",
                    "Yu Gothic", "YuGothic", "MS Gothic", "MSGothic", "Meiryo", "メイリオ",
                    "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "SimHei", "NSimSun",
                    "Apple SD Gothic Neo", "PingFang SC", "STHeiti", "Hiragino Sans",
                    "WenQuanYi Micro Hei", "WenQuanYi Zen Hei"
            };
        }
        if (hasHangul) {
            // 纯韩文 → 韩文字体优先（覆盖谚文 + 基本拉丁）
            return new String[]{
                    "Malgun Gothic", "맑은 고딕", "Gulim", "굴림", "Batang", "바탕", "Dotum", "돋움",
                    "Noto Sans KR", "Noto Sans CJK KR", "Apple SD Gothic Neo",
                    "Noto Sans CJK SC", "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "PingFang SC"
            };
        }
        if (hasKana) {
            // 纯日文 → 日文字体优先
            return new String[]{
                    "MS Gothic", "MSGothic", "Yu Gothic", "YuGothic", "Meiryo", "メイリオ",
                    "Hiragino Sans", "Noto Sans CJK JP", "Noto Sans CJK SC",
                    "Noto Sans CJK KR", "Malgun Gothic",
                    "Microsoft YaHei", "微软雅黑", "SimSun", "宋体", "PingFang SC"
            };
        }
        // 纯中文或拉丁 → 中文字体优先
        return new String[]{
                "SimSun", "宋体", "Microsoft YaHei", "微软雅黑", "SimHei", "NSimSun",
                "PingFang SC", "STHeiti", "Noto Sans CJK SC",
                "Noto Sans CJK KR", "Malgun Gothic", "Apple SD Gothic Neo",
                "Noto Sans KR"
        };
    }

    /** 文本需要哪些字形能力 */
    private static TextAbility analyzeText(String text) {
        TextAbility need = new TextAbility();
        if (text == null || text.isEmpty()) {
            need.latin = true;
            return need;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (isHangul(cp)) need.hangul = true;
            else if (isKana(cp)) need.kana = true;
            else if (isHan(cp)) need.han = true;
            else if (cp < 128 && Character.isLetterOrDigit(cp)) need.latin = true;
        }
        return need;
    }

    private static boolean isHangul(int cp) {
        return (cp >= 0xAC00 && cp <= 0xD7A3)   // 现代谚文音节
                || (cp >= 0x1100 && cp <= 0x11FF) // 谚文 Jamo
                || (cp >= 0x3130 && cp <= 0x318F) // 谚文兼容字母
                || (cp >= 0xA960 && cp <= 0xA97F) // 扩展 A
                || (cp >= 0xD7B0 && cp <= 0xD7FF); // 扩展 B
    }

    private static boolean isKana(int cp) {
        return (cp >= 0x3040 && cp <= 0x30FF)   // 平假名/片假名
                || (cp >= 0x31F0 && cp <= 0x31FF); // 片假名扩展
    }

    private static boolean isHan(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)   // CJK 统一表意
                || (cp >= 0x3400 && cp <= 0x4DBF) // 扩展 A
                || (cp >= 0xF900 && cp <= 0xFAFF); // 兼容表意
    }

    /** 字体是否具备给定能力集合 */
    private static boolean hasAbility(Font font, TextAbility need) {
        if (font == null) return false;
        // 跳过逻辑字体：AWT 中 Dialog/SansSerif 等逻辑字体的 canDisplay 对缺字形也常虚报 true，
        // 必须确认是真实物理字体（特定字体族）才接受，否则早退回 Dialog 导致豆腐块。
        if (isLogicalFont(font)) return false;
        try {
            if (need.han && !isChineseCapable(font)) return false;
            if (need.hangul && !isKoreanCapable(font)) return false;
            if (need.kana && !isJapaneseCapable(font)) return false;
            if (need.latin && !font.canDisplay('A')) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLogicalFont(Font font) {
        if (font == null) return true;
        String family = font.getFamily();
        if (family == null) return true;
        String f = family.toLowerCase();
        return f.contains("dialog") || f.contains("sansserif") || f.contains("serif")
                || f.contains("monospaced") || f.contains("dialoginput");
    }

    /** 遍历系统字体找能满足全部能力的字体 */
    private static Font findSystemFontForAbility(TextAbility need, int style, int size) {
        try {
            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            for (Font f : fonts) {
                if (hasAbility(f, need)) {
                    return f.deriveFont(style, (float) size);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static class TextAbility {
        boolean han;
        boolean hangul;
        boolean kana;
        boolean latin;
    }

    /** 获取适合显示歌词（跨中日韩）的字体：韩文优先。
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
