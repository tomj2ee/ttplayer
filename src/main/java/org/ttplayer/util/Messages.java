package org.ttplayer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * 国际化消息工具 — 从 messages[_locale].properties 读取文案，支持运行时切换语言。
 */
public class Messages {

    private static Locale locale = Locale.getDefault();
    private static final ResourceBundle.Control CONTROL = new UTF8Control();
    private static ResourceBundle bundle = ResourceBundle.getBundle("messages", locale, CONTROL);

    /** 支持的语言：{语言标签, 显示名}。新增语言 = 加一行 + 提供对应的 messages_xx.properties */
    public static final String[][] SUPPORTED_LANGUAGES = {
        {"zh_CN", "中文"},
        {"en", "English"},
        {"ja", "日本語"},
        {"ko", "한국어"},
        {"de", "Deutsch"},
    };

    private Messages() {}

    public static Locale getLocale() {
        return locale;
    }

    public static void setLocale(Locale l) {
        locale = l;
        bundle = ResourceBundle.getBundle("messages", locale, CONTROL);
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }

    /** 解析 "zh_CN" / "en" 之类的语言标签为 Locale */
    public static Locale fromTag(String tag) {
        if (tag == null || tag.isEmpty()) return Locale.getDefault();
        String[] parts = tag.split("_");
        if (parts.length >= 2) return new Locale(parts[0], parts[1]);
        return new Locale(parts[0]);
    }

    /**
     * 自定义 Control：properties 按 UTF-8 读取；且不因请求的 locale 无对应 bundle
     * 就回退到系统默认 locale（否则英文界面会错误地加载中文包）。
     */
    private static class UTF8Control extends ResourceBundle.Control {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            try (InputStream stream = loader.getResourceAsStream(resourceName)) {
                if (stream == null) return null;
                final Properties props = new Properties();
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    props.load(reader);
                }
                return new UTF8PropertiesBundle(props);
            }
        }
    }

    /**
     * 基于 Properties 的 UTF-8 安全 ResourceBundle
     */
    private static class UTF8PropertiesBundle extends ResourceBundle {
        private final Properties props;

        UTF8PropertiesBundle(Properties props) {
            this.props = props;
        }

        @Override
        protected Object handleGetObject(String key) {
            return props.getProperty(key);
        }

        @Override
        public java.util.Enumeration<String> getKeys() {
            return java.util.Collections.enumeration(props.stringPropertyNames());
        }
    }
}
