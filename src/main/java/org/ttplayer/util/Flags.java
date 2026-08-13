package org.ttplayer.util;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 语言国旗图标：程序化绘制小国旗（Swing 菜单用 ImageIcon，托盘菜单用 emoji）。
 */
public class Flags {

    private static final int W = 20;
    private static final int H = 14;

    private Flags() {}

    /** 根据语言标签（如 zh_CN / en / ja）返回绘制的国旗图标 */
    public static ImageIcon iconFor(String langTag) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (langOf(langTag)) {
            case "zh": drawChina(g); break;
            case "en": drawUk(g); break;
            case "ja": drawJapan(g); break;
            case "ko": drawKorea(g); break;
            case "de": drawGermany(g); break;
            default:  drawDefault(g); break;
        }
        g.dispose();
        return new ImageIcon(img);
    }

    /** 根据语言标签返回国旗 emoji（用于 AWT 托盘菜单） */
    public static String emojiFor(String langTag) {
        switch (langOf(langTag)) {
            case "zh": return "🇨🇳"; // 🇨🇳
            case "en": return "🇬🇧"; // 🇬🇧
            case "ja": return "🇯🇵"; // 🇯🇵
            case "ko": return "🇰🇷"; // 🇰🇷
            case "de": return "🇩🇪"; // 🇩🇪
            default: return "";
        }
    }

    private static String langOf(String tag) {
        return tag == null ? "" : tag.split("_")[0];
    }

    private static void drawGermany(Graphics2D g) {
        g.setColor(Color.BLACK);           g.fillRect(0, 0, W, H / 3);
        g.setColor(Color.RED);             g.fillRect(0, H / 3, W, H / 3);
        g.setColor(new Color(0xFFCC00));   g.fillRect(0, 2 * H / 3, W, H - 2 * H / 3);
    }

    private static void drawJapan(Graphics2D g) {
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        g.setColor(Color.RED);   g.fillOval(W / 2 - 4, H / 2 - 4, 8, 8);
    }

    private static void drawChina(Graphics2D g) {
        g.setColor(Color.RED); g.fillRect(0, 0, W, H);
        g.setColor(Color.YELLOW);
        drawStar(g, 4.5, 4.5, 3.0);
        drawStar(g, 8.0, 2.5, 1.3);
        drawStar(g, 9.5, 4.0, 1.3);
        drawStar(g, 9.5, 6.0, 1.3);
        drawStar(g, 8.0, 7.5, 1.3);
    }

    private static void drawKorea(Graphics2D g) {
        g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
        int cx = W / 2, cy = H / 2, r = 5;
        g.setColor(Color.RED);  g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 0, 180);
        g.setColor(Color.BLUE); g.fillArc(cx - r, cy - r, 2 * r, 2 * r, 180, 180);
        g.setColor(Color.BLACK);
        g.fillRect(1, 1, 2, 2);
        g.fillRect(W - 3, 1, 2, 2);
        g.fillRect(1, H - 3, 2, 2);
        g.fillRect(W - 3, H - 3, 2, 2);
    }

    private static void drawUk(Graphics2D g) {
        g.setColor(new Color(0x012169)); g.fillRect(0, 0, W, H);
        // 圣乔治十字（白 + 红）
        g.setColor(Color.WHITE); g.fillRect(0, H / 2 - 1, W, 3);
        g.fillRect(W / 2 - 1, 0, 3, H);
        g.setColor(Color.RED);   g.fillRect(0, H / 2, W, 1);
        g.fillRect(W / 2, 0, 1, H);
        // 圣安德鲁斜十字（白 + 红）
        g.setColor(Color.WHITE); g.drawLine(0, 0, W, H); g.drawLine(0, H, W, 0);
        g.setColor(Color.RED);   g.drawLine(1, 1, W - 1, H - 1); g.drawLine(1, H - 1, W - 1, 1);
    }

    private static void drawDefault(Graphics2D g) {
        g.setColor(Color.LIGHT_GRAY); g.fillRect(0, 0, W, H);
        g.setColor(Color.DARK_GRAY);  g.drawRect(0, 0, W - 1, H - 1);
    }

    private static void drawStar(Graphics2D g, double cx, double cy, double r) {
        int[] xs = new int[10];
        int[] ys = new int[10];
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double rad = (i % 2 == 0) ? r : r * 0.4;
            xs[i] = (int) Math.round(cx + rad * Math.cos(ang));
            ys[i] = (int) Math.round(cy + rad * Math.sin(ang));
        }
        g.fillPolygon(xs, ys, 10);
    }
}
