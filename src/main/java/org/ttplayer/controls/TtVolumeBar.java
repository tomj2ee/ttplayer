package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TtVolumeBar extends javax.swing.JComponent {
    private BufferedImage fillImg;
    private BufferedImage thumbImg;
    private int value = 70;
    private boolean dragging = false;
    private int thumbFrameW = 10;
    private int thumbFrameH = 18;

    public TtVolumeBar(byte[] fillData, byte[] thumbData) {
        if (fillData != null) {
            fillImg = SkinWindow.decodeBmp(fillData, new Color(255, 0, 255));
        }
        if (thumbData != null) {
            thumbImg = SkinWindow.decodeBmp(thumbData, new Color(255, 0, 255));
            if (thumbImg != null) {
                thumbFrameW = thumbImg.getWidth() / 4;
                thumbFrameH = thumbImg.getHeight();
            }
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                updateValue(e.getX());
                dragging = true;
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
                if (listener != null) listener.onVolumeChanged(value);
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateValue(e.getX());
            }
        });
    }

    public interface VolumeListener { void onVolumeChanged(int percent); }
    private VolumeListener listener;
    public void setVolumeListener(VolumeListener l) { this.listener = l; }

    private void updateValue(int mx) {
        int w = getWidth();
        int trackWidth = w - thumbFrameW;
        if (trackWidth > 0) {
            int thumbLeft = mx - thumbFrameW / 2;
            value = Math.max(0, Math.min(100, thumbLeft * 100 / trackWidth));
            repaint();
        }
    }

    public void setValue(int v) { value = Math.max(0, Math.min(100, v)); repaint(); }
    public int getValue() { return value; }
    public boolean isDragging() { return dragging; }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int trackWidth = w - thumbFrameW;
        int thumbX = (trackWidth > 0) ? trackWidth * value / 100 : 0;
        int thumbY = (h - thumbFrameH) / 2;
        int fillEnd = thumbX + thumbFrameW / 2;

        if (fillImg != null && fillEnd > 0) {
            int fillH = fillImg.getHeight();
            int fillY = (h - fillH) / 2;
            int srcW = (fillImg.getWidth() - 1) * fillEnd / w + 1;
            if (srcW > fillImg.getWidth()) srcW = fillImg.getWidth();
            g.drawImage(fillImg,
                    0, fillY, fillEnd, fillY + fillH,
                    0, 0, srcW, fillH,
                    null);
        }

        if (thumbImg != null) {
            g.drawImage(thumbImg, thumbX, thumbY, thumbX + thumbFrameW, thumbY + thumbFrameH,
                    0, 0, thumbFrameW, thumbFrameH, null);
        }
    }
}
