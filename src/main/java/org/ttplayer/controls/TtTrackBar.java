package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TtTrackBar extends javax.swing.JComponent {
    private BufferedImage thumbImg;
    private BufferedImage fillImg;   // 进度填充图片
    private BufferedImage bgFillImg; // 背景填充图片
    private Color bgColor = new Color(60, 62, 66);
    private Color fillColor = new Color(0, 180, 0);
    private int value = 0;
    private int max = 100;
    private boolean dragging = false;
    private long lockedUntil = 0; // 锁定到这个时间戳
    private int lockedValue = -1; // 锁定时显示的值
    private int thumbFrameW = 23;
    private int thumbFrameH = 11;

    public TtTrackBar(byte[] thumbData) {
        this(thumbData, null, null);
    }

    public TtTrackBar(byte[] thumbData, byte[] fillData, byte[] bgFillData) {
        if (thumbData != null) {
            thumbImg = SkinWindow.decodeBmp(thumbData, new Color(255, 0, 255));
            if (thumbImg != null) {
                thumbFrameW = thumbImg.getWidth() / 4;
                thumbFrameH = thumbImg.getHeight();
            }
        }
        if (fillData != null) {
            fillImg = SkinWindow.decodeBmp(fillData, new Color(255, 0, 255));
        }
        if (bgFillData != null) {
            bgFillImg = SkinWindow.decodeBmp(bgFillData, new Color(255, 0, 255));
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                updateValue(e.getX());
                dragging = true;
                lockedUntil = System.currentTimeMillis() + 1500; // 锁定1.5秒
                lockedValue = value;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                lockedUntil = System.currentTimeMillis() + 1500; // 锁定1.5秒
                lockedValue = value;
                if (listener != null) listener.onSeek(value);
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateValue(e.getX());
            }
        });
    }

    public interface TrackListener {
        void onSeek(int seconds);
    }

    private TrackListener listener;
    public void setTrackListener(TrackListener l) { this.listener = l; }

    private void updateValue(int mx) {
        int w = getWidth();
        if (w <= 0 || max <= 0) return;
        int trackW = w - thumbFrameW;
        if (trackW > 0) {
            int newVal = (mx - thumbFrameW / 2) * max / trackW;
            if (newVal < 0) newVal = 0;
            if (newVal > max) newVal = max;
            value = newVal;
            repaint();
        }
    }

    public void setRange(int max) { this.max = max; }
    public void setValue(int val) {
        if (!dragging && System.currentTimeMillis() > lockedUntil) {
            this.value = Math.max(0, Math.min(max, val));
            repaint();
        }
    }
    public int getValue() { return value; }
    public boolean isDragging() { return dragging; }

    public void setColors(Color bgColor, Color fillColor) {
        this.bgColor = bgColor;
        this.fillColor = fillColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        // 绘制背景（使用背景图片或颜色）
        if (bgFillImg != null) {
            g.drawImage(bgFillImg, 0, 2, w, h - 4, null);
        } else {
            g.setColor(bgColor);
            g.fillRect(0, 2, w, h - 4);
        }

        // 绘制进度（使用填充图片或颜色）
        if (max > 0) {
            int displayValue = (System.currentTimeMillis() <= lockedUntil && lockedValue >= 0) ? lockedValue : value;
            int progressW = (int) ((float) displayValue / max * w);
            if (fillImg != null) {
                g.drawImage(fillImg, 0, 2, progressW, h - 4, null);
            } else {
                g.setColor(fillColor);
                g.fillRect(0, 2, progressW, h - 4);
            }
        }

        // 绘制滑块缩略图
        if (thumbImg != null && max > 0) {
            int displayValue = (System.currentTimeMillis() <= lockedUntil && lockedValue >= 0) ? lockedValue : value;
            int trackW = w - thumbFrameW;
            int tx = (trackW > 0) ? trackW * displayValue / max : 0;
            g.drawImage(thumbImg, tx, (h - thumbFrameH) / 2, tx + thumbFrameW, (h - thumbFrameH) / 2 + thumbFrameH,
                    0, 0, thumbFrameW, thumbFrameH, null);
        }
    }
}
