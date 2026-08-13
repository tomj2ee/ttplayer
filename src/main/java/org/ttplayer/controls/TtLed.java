package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import java.awt.*;
import java.awt.image.BufferedImage;

public class TtLed extends javax.swing.JComponent {
    private BufferedImage[] frames = new BufferedImage[12];
    private String text = "00:00";

    public TtLed(byte[] bmpData) {
        this(bmpData, new Color(255, 0, 255));
    }

    public TtLed(byte[] bmpData, Color transp) {
        BufferedImage src = SkinWindow.decodeBmp(bmpData, transp);
        if (src != null) {
            int fw = src.getWidth() / 12;
            int fh = src.getHeight();
            for (int i = 0; i < 12; i++) {
                frames[i] = src.getSubimage(i * fw, 0, fw, fh);
            }
            // 设置首选大小为6个字符宽度
            setPreferredSize(new Dimension(fw * 6, fh));
        }
        setOpaque(false);
    }

    public void setText(String t) {
        this.text = (t != null) ? t : "00:00";
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (frames[0] == null) return;

        int w = getWidth();
        int h = getHeight();
        int fw = frames[0].getWidth();
        int fh = frames[0].getHeight();

        String display = text;
        int len = Math.min(display.length(), 6);

        // 计算每个字符的实际绘制宽度，确保6个字符都能放下
        int actualCharWidth = fw;
        if (len > 0) {
            actualCharWidth = Math.min(fw, w / len);
        }

        int totalW = len * actualCharWidth;

        // 计算起始X坐标：如果右对齐且宽度足够，就右对齐；否则从0开始
        int startX = 0;
        if (alignRight) {
            if (totalW <= w) {
                startX = w - totalW;
            }
        }

        int y = (h - fh) / 2;

        for (int i = 0; i < len; i++) {
            char c = display.charAt(i);
            int idx;
            if (c >= '0' && c <= '9') idx = c - '0';
            else if (c == ':') idx = 10;
            else if (c == '-') idx = 11;
            else continue;
            if (idx >= 0 && idx < 12 && frames[idx] != null) {
                int drawX = startX + i * actualCharWidth;
                g.drawImage(frames[idx], drawX, y, actualCharWidth, fh, null);
            }
        }
    }

    private boolean alignRight = true;
    public void setAlignRight(boolean r) { alignRight = r; }
}
