package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TtHSlider extends javax.swing.JComponent {
    private BufferedImage thumbImg;
    private int value = 50;
    private boolean dragging = false;
    private int thumbFrameW, thumbFrameH;

    public TtHSlider(byte[] thumbData) {
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
            public void mouseReleased(MouseEvent e) { dragging = false; fire(); }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) { updateValue(e.getX()); }
        });
    }

    public interface Listener { void onChanged(int val); }
    private Listener listener;
    public void setListener(Listener l) { this.listener = l; }
    private void fire() { if (listener != null) listener.onChanged(value); }

    private void updateValue(int mx) {
        int w = getWidth() - thumbFrameW;
        if (w > 0) {
            int thumbLeft = mx - thumbFrameW / 2;
            value = Math.max(0, Math.min(100, thumbLeft * 100 / w));
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

        int thumbX = (w - thumbFrameW) * value / 100;
        int thumbY = (h - thumbFrameH) / 2;

        if (thumbImg != null) {
            g.drawImage(thumbImg, thumbX, thumbY, thumbX + thumbFrameW, thumbY + thumbFrameH,
                    0, 0, thumbFrameW, thumbFrameH, null);
        }
    }
}
