package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class TtScrollbar extends JComponent {
    private BufferedImage barImg;
    private BufferedImage thumbImg;
    private BufferedImage buttonImg;
    private int thumbResizeCenter = 8;

    private int minThumbHeight = 20;
    private int value = 0;
    private int min = 0;
    private int max = 100;
    private int extent = 10;

    private boolean dragging = false;
    private int dragStartY;
    private int dragStartValue;

    public TtScrollbar(byte[] barData, byte[] thumbData, byte[] buttonData, int thumbResizeCenter) {
        this.barImg = (barData != null) ? SkinWindow.decodeBmp(barData, new Color(255, 0, 255)) : null;
        this.thumbImg = (thumbData != null) ? SkinWindow.decodeBmp(thumbData, new Color(255, 0, 255)) : null;
        this.buttonImg = (buttonData != null) ? SkinWindow.decodeBmp(buttonData, new Color(255, 0, 255)) : null;
        this.thumbResizeCenter = thumbResizeCenter;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (thumbImg != null) {
                    Rectangle thumbRect = getThumbRect();
                    if (thumbRect.contains(e.getPoint())) {
                        dragging = true;
                        dragStartY = e.getY();
                        dragStartValue = value;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    int dy = e.getY() - dragStartY;
                    int trackHeight = getTrackHeight();
                    int maxValue = max - min - extent;
                    if (maxValue <= 0) return;

                    int newValue = dragStartValue + (dy * maxValue / trackHeight);
                    setValue(Math.max(min, Math.min(max - extent, newValue)));
                }
            }
        });
    }

    private Rectangle getThumbRect() {
        int w = getWidth();
        int h = getHeight();
        int trackHeight = getTrackHeight();
        int maxValue = max - min - extent;

        if (maxValue <= 0) {
            return new Rectangle(0, 0, w, h);
        }

        int thumbHeight = Math.max(minThumbHeight, (int) (trackHeight * (double) extent / (max - min)));
        int thumbY = (value - min) * trackHeight / maxValue;
        return new Rectangle(0, thumbY, w, thumbHeight);
    }

    private int getTrackHeight() {
        int h = getHeight();
        if (buttonImg != null) {
            h -= buttonImg.getHeight() * 2;
        }
        return h;
    }

    public void setValues(int min, int max, int extent, int value) {
        this.min = min;
        this.max = max;
        this.extent = extent;
        this.value = Math.max(min, Math.min(max - extent, value));
        repaint();
    }

    public void setValue(int value) {
        int oldValue = this.value;
        this.value = Math.max(min, Math.min(max - extent, value));
        if (oldValue != this.value) {
            repaint();
            firePropertyChange("value", oldValue, this.value);
        }
    }

    public int getValue() { return value; }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        if (barImg != null) {
            if (barImg.getHeight() > 0) {
                for (int y = 0; y < h; y += barImg.getHeight()) {
                    int drawH = Math.min(barImg.getHeight(), h - y);
                    g.drawImage(barImg, 0, y, w, y + drawH,
                            0, 0, barImg.getWidth(), drawH, null);
                }
            }
        } else {
            g.setColor(Color.GRAY);
            g.fillRect(0, 0, w, h);
        }

        if (buttonImg != null) {
            int btnH = buttonImg.getHeight();
            g.drawImage(buttonImg, 0, 0, w, btnH,
                    0, 0, buttonImg.getWidth(), btnH / 2, null);
            g.drawImage(buttonImg, 0, h - btnH, w, h,
                    0, btnH / 2, buttonImg.getWidth(), btnH, null);
        }

        if (thumbImg != null) {
            Rectangle thumbRect = getThumbRect();
            drawNinePatchTiled((Graphics2D) g, thumbImg, thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height,
                    0, thumbResizeCenter, 0, thumbImg.getHeight() - thumbResizeCenter);
        }
    }

    private static void drawNinePatchTiled(Graphics2D g, BufferedImage src,
                                            int dx1, int dy1, int dx2, int dy2,
                                            int nl, int nt, int nr, int nb) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int dw = dx2 - dx1;
        int dh = dy2 - dy1;

        if (nl == 0 && nt == 0 && nr == 0 && nb == 0) {
            drawTiled(g, src, dx1, dy1, dx2, dy2, 0, 0, sw, sh);
            return;
        }

        g.drawImage(src, dx1, dy1, dx1 + nl, dy1 + nt, 0, 0, nl, nt, null);
        g.drawImage(src, dx2 - nr, dy1, dx2, dy1 + nt, sw - nr, 0, sw, nt, null);
        g.drawImage(src, dx1, dy2 - nb, dx1 + nl, dy2, 0, sh - nb, nl, sh, null);
        g.drawImage(src, dx2 - nr, dy2 - nb, dx2, dy2, sw - nr, sh - nb, sw, sh, null);

        int mc = sw - nl - nr;
        int mr = sh - nt - nb;

        if (mc > 0) {
            drawTiled(g, src, dx1 + nl, dy1, dx2 - nr, dy1 + nt, nl, 0, nl + mc, nt);
            drawTiled(g, src, dx1 + nl, dy2 - nb, dx2 - nr, dy2, nl, sh - nb, nl + mc, sh);
        }

        if (mr > 0) {
            drawTiled(g, src, dx1, dy1 + nt, dx1 + nl, dy2 - nb, 0, nt, nl, nt + mr);
            drawTiled(g, src, dx2 - nr, dy1 + nt, dx2, dy2 - nb, sw - nr, nt, sw, nt + mr);
        }

        if (mc > 0 && mr > 0) {
            drawTiled(g, src, dx1 + nl, dy1 + nt, dx2 - nr, dy2 - nb, nl, nt, nl + mc, nt + mr);
        }
    }

    private static void drawTiled(Graphics2D g, BufferedImage src,
                                   int dx1, int dy1, int dx2, int dy2,
                                   int sx1, int sy1, int sx2, int sy2) {
        int tileW = sx2 - sx1;
        int tileH = sy2 - sy1;
        int dstW = dx2 - dx1;
        int dstH = dy2 - dy1;

        if (tileW <= 0 || tileH <= 0 || dstW <= 0 || dstH <= 0) return;

        for (int y = 0; y < dstH; y += tileH) {
            int drawH = Math.min(tileH, dstH - y);
            for (int x = 0; x < dstW; x += tileW) {
                int drawW = Math.min(tileW, dstW - x);
                g.drawImage(src, dx1 + x, dy1 + y, dx1 + x + drawW, dy1 + y + drawH,
                        sx1, sy1, sx1 + drawW, sy1 + drawH, null);
            }
        }
    }
}
