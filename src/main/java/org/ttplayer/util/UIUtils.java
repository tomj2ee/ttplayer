package org.ttplayer.util;

import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用UI工具类 - 抽取项目中重复的UI相关代码
 */
public class UIUtils {

    /**
     * 为歌曲生成详细的工具提示文本
     */
    public static String generateSongTooltip(Song song) {
        if (song == null) return null;

        StringBuilder tip = new StringBuilder();
        tip.append("<html>");

        // 歌曲标题
        if (song.title != null && !song.title.isEmpty()) {
            tip.append("<b>").append(song.title).append("</b><br>");
        }

        // 艺术家
        if (song.artist != null && !song.artist.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.artistPrefix")).append(song.artist).append("<br>");
        }

        // 专辑
        if (song.album != null && !song.album.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.albumPrefix")).append(song.album).append("<br>");
        }

        // 年份
        if (song.year != null && !song.year.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.yearPrefix")).append(song.year).append("<br>");
        }

        // 流派
        if (song.genre != null && !song.genre.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.genrePrefix")).append(song.genre).append("<br>");
        }

        // 时长 - 更醒目地显示
        if (song.duration != null && !song.duration.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.durationHtmlPrefix")).append(song.duration).append("</b><br>");
        }

        // 注释
        if (song.comment != null && !song.comment.isEmpty()) {
            tip.append(org.ttplayer.util.Messages.get("song.commentPrefix")).append(song.comment).append("<br>");
        }

        // 文件格式和位置
        if (song.filePath != null && !song.filePath.isEmpty()) {
            File file = new File(song.filePath);
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = fileName.substring(dotIndex + 1).toUpperCase();
                tip.append(org.ttplayer.util.Messages.get("song.formatPrefix")).append(ext).append("<br>");
            }
            // 文件位置
            tip.append(org.ttplayer.util.Messages.get("song.locationPrefix")).append(song.filePath);
        }

        tip.append("</html>");
        return tip.toString();
    }

    /**
     * 创建一个菜单项并设置默认字体
     */
    public static JMenuItem createMenuItem(String text, java.awt.event.ActionListener listener) {
        return createMenuItem(text, null, listener);
    }

    /**
     * 创建一个带图标的菜单项并设置默认字体
     */
    public static JMenuItem createMenuItem(String text, javax.swing.Icon icon, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(text, icon);
        if (listener != null) {
            item.addActionListener(listener);
        }
        item.setFont(FontUtils.getDefaultChineseFont(12));
        return item;
    }

    /**
     * 创建一个复选框菜单项并设置默认字体
     */
    public static JCheckBoxMenuItem createCheckBoxMenuItem(String text, boolean selected) {
        return createCheckBoxMenuItem(text, null, selected);
    }

    /**
     * 创建一个带图标的复选框菜单项并设置默认字体
     */
    public static JCheckBoxMenuItem createCheckBoxMenuItem(String text, javax.swing.Icon icon, boolean selected) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, icon, selected);
        item.setFont(FontUtils.getDefaultChineseFont(12));
        return item;
    }

    /**
     * 设置组件透明
     */
    public static void setTransparent(JComponent comp) {
        comp.setOpaque(false);
        comp.setBackground(new Color(0, 0, 0, 0));
    }

    /**
     * 为菜单及其所有子项设置字体
     */
    public static void setMenuFont(JPopupMenu menu, Font font) {
        menu.setFont(font);
        for (int i = 0; i < menu.getComponentCount(); i++) {
            Component comp = menu.getComponent(i);
            if (comp instanceof JMenuItem) {
                ((JMenuItem) comp).setFont(font);
            } else if (comp instanceof JMenu) {
                ((JMenu) comp).setFont(font);
                setMenuFont((JMenu) comp, font);
            }
        }
    }

    /**
     * 为菜单及其所有子项设置字体（JMenu版本）
     */
    public static void setMenuFont(JMenu menu, Font font) {
        menu.setFont(font);
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item != null) {
                item.setFont(font);
            }
        }
    }

    /**
     * 加载应用程序图标
     */
    public static List<Image> loadApplicationIcons(ClassLoader classLoader, String... iconPaths) {
        List<Image> icons = new ArrayList<>();
        for (String path : iconPaths) {
            tryLoadIcon(classLoader, path, icons);
        }
        return icons;
    }

    /**
     * 尝试加载单个图标
     */
    private static void tryLoadIcon(ClassLoader cl, String path, List<Image> icons) {
        try {
            java.io.InputStream is = cl.getResourceAsStream(path);
            if (is != null) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(is);
                if (img != null) {
                    icons.add(img);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * 设置Mac Dock图标（如果在Mac上运行）
     */
    public static void setMacDockIcon(Image icon) {
        if (icon == null) return;
        try {
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Object application = applicationClass.getMethod("getApplication").invoke(null);
            applicationClass.getMethod("setDockIconImage", Image.class).invoke(application, icon);
        } catch (Exception ignored) {}
    }

    /**
     * 清除Mac Dock徽章（如果在Mac上运行）
     */
    public static void clearMacDockBadge() {
        try {
            Class<?> applicationClass = Class.forName("com.apple.eawt.Application");
            Object application = applicationClass.getMethod("getApplication").invoke(null);
            applicationClass.getMethod("setDockIconBadge", String.class).invoke(application, "");
        } catch (Exception ignored) {}
    }

    /**
     * 设置窗口的图标图像列表
     */
    public static void setWindowIcons(Window window, List<Image> icons) {
        if (window != null && icons != null && !icons.isEmpty()) {
            window.setIconImages(icons);
        }
    }

    /**
     * 从皮肤中加载XML配置文件（先尝试大写，再尝试小写）
     */
    public static byte[] loadSkinXml(TtSkin skin, String baseName) {
        byte[] data = skin.getBmp(baseName + ".xml");
        if (data == null) {
            data = skin.getBmp(baseName.toLowerCase() + ".xml");
        }
        return data;
    }

    /**
     * 从字节数组解析XML文档
     */
    public static Document parseXml(byte[] data) {
        if (data == null) return null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全地从XML元素获取字符串属性
     */
    public static String getAttribute(Element element, String name, String defaultValue) {
        if (element == null || !element.hasAttribute(name)) {
            return defaultValue;
        }
        return element.getAttribute(name);
    }

    /**
     * 安全地从XML元素获取颜色属性
     */
    public static Color getColorAttribute(Element element, String name, Color defaultValue) {
        if (element == null || !element.hasAttribute(name)) {
            return defaultValue;
        }
        Color color = ColorUtils.decode(element.getAttribute(name));
        return color != null ? color : defaultValue;
    }
}
