package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class SkinScrollBarUI extends BasicScrollBarUI {

    private static final int MINIMUM_THUMB_HEIGHT = 24;
    private static final int MAXIMUM_THUMB_HEIGHT = 64;

    private BufferedImage buttonsImg;
    private BufferedImage[] thumbFrames;
    private BufferedImage barImg;

    private int thumbResizeCenter;
    private boolean thumbResizeTile;


    private final Color fallbackTrack;
    private final Color fallbackThumb;

    public SkinScrollBarUI(Color fallbackTrack, Color fallbackThumb) {
        this.fallbackTrack = fallbackTrack;
        this.fallbackThumb = fallbackThumb;
    }

    public void setSkinImages(byte[] buttonsBmp, byte[] thumbBmp, byte[] barBmp) {
        setSkinImages(buttonsBmp, thumbBmp, barBmp, new Color(255, 0, 255), 0, 0);
    }

    public void setSkinImages(byte[] buttonsBmp, byte[] thumbBmp, byte[] barBmp,
                              Color transp, int resizeCenter, int resizeTile) {
        buttonsImg = buttonsBmp == null ? null : SkinWindow.decodeBmp(buttonsBmp, transp);
        thumbFrames = splitFrames(thumbBmp, transp);
        barImg = barBmp == null ? null : SkinWindow.decodeBmp(barBmp, transp);
        thumbResizeCenter = Math.max(0, resizeCenter);
        thumbResizeTile = resizeTile != 0;
    }



    private static BufferedImage[] splitFrames(byte[] bmpData, Color transp) {
        if (bmpData == null) return null;
        BufferedImage src = SkinWindow.decodeBmp(bmpData, transp);
        if (src == null) return null;
        return splitFramesFromImage(src);
    }

    private static BufferedImage[] splitFramesFromImage(BufferedImage img) {
        if (img == null) return null;
        int sw = img.getWidth();
        int cols;
        if (sw % 3 == 0) cols = 3;
        else if (sw % 2 == 0) cols = 2;
        else cols = 1;
        int fw = sw / cols;
        int fh = img.getHeight();
        BufferedImage[] frames = new BufferedImage[cols];
        for (int i = 0; i < cols; i++) {
            frames[i] = img.getSubimage(i * fw, 0, fw, fh);
        }
        return frames;
    }

    @Override
    protected void configureScrollBarColors() {
        scrollbar.setOpaque(false);
        if (barImg == null) scrollbar.setBackground(fallbackTrack);
        scrollbar.setForeground(fallbackThumb);
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        c.setOpaque(false);
    }

    @Override
    protected Dimension getMinimumThumbSize() {
        Dimension minimum = super.getMinimumThumbSize();
        if (thumbFrames != null && thumbFrames.length > 0 && thumbFrames[0] != null) {
            minimum = new Dimension(thumbFrames[0].getWidth(), thumbFrames[0].getHeight());
        }
        return new Dimension(minimum.width,
                Math.max(MINIMUM_THUMB_HEIGHT, Math.min(MAXIMUM_THUMB_HEIGHT, minimum.height)));
    }

    @Override
    protected Dimension getMaximumThumbSize() {
        Dimension maximum = super.getMaximumThumbSize();
        if (scrollbar.getVisibleAmount() >= (long) scrollbar.getMaximum() - scrollbar.getMinimum()) {
            return maximum;
        }
        return new Dimension(maximum.width, MAXIMUM_THUMB_HEIGHT);
    }


    @Override
    public Dimension getPreferredSize(JComponent c) {
        if (barImg != null) {
            return new Dimension(barImg.getWidth(), c.getHeight());
        }
        return super.getPreferredSize(c);
    }


    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
        g.setColor(fallbackTrack);
        g.fillRect(r.x, r.y, r.width, r.height);
        if (barImg != null) {
            for (int offset = 0; offset < r.height; offset += barImg.getHeight()) {
                int height = Math.min(barImg.getHeight(), r.height - offset);
                g.drawImage(barImg, r.x, r.y + offset, r.x + r.width, r.y + offset + height,
                        0, 0, barImg.getWidth(), height, null);
            }
        }
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !c.isEnabled()) return;
        if (thumbFrames == null || thumbFrames.length == 0) {
            g.setColor(fallbackThumb);
            g.fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        int state = isDragging ? 2 : isThumbRollover() ? 1 : 0;
        BufferedImage frame = thumbFrames[Math.min(state, thumbFrames.length - 1)];
        int cap = Math.min(thumbResizeCenter, (frame.getHeight() - 1) / 2);
        cap = Math.min(cap, (r.height - 1) / 2);
        g.drawImage(frame, r.x, r.y, r.x + r.width, r.y + cap,
                0, 0, frame.getWidth(), cap, null);
        g.drawImage(frame, r.x, r.y + r.height - cap, r.x + r.width, r.y + r.height,
                0, frame.getHeight() - cap, frame.getWidth(), frame.getHeight(), null);
        int centerHeight = frame.getHeight() - cap * 2;
        int targetHeight = r.height - cap * 2;
        if (thumbResizeTile) {
            for (int offset = 0; offset < targetHeight; offset += centerHeight) {
                int height = Math.min(centerHeight, targetHeight - offset);
                g.drawImage(frame, r.x, r.y + cap + offset, r.x + r.width, r.y + cap + offset + height,
                        0, cap, frame.getWidth(), cap + height, null);
            }
        } else {
            g.drawImage(frame, r.x, r.y + cap, r.x + r.width, r.y + r.height - cap,
                    0, cap, frame.getWidth(), frame.getHeight() - cap, null);
        }
    }

    @Override
    protected TrackListener createTrackListener() {
        return new TrackListener() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                scrollbar.repaint();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                scrollbar.repaint();
            }
        };
    }

    @Override
    protected void paintDecreaseHighlight(Graphics g) {}
    @Override
    protected void paintIncreaseHighlight(Graphics g) {}

    @Override
    protected JButton createDecreaseButton(int orientation) {
        if (buttonsImg != null) {
            BufferedImage[] frames = splitButtonHalf(buttonsImg, 0);
            if (frames != null && frames.length > 0) {
                return new SkinBtn(frames);
            }
        }
        return super.createDecreaseButton(orientation);
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        if (buttonsImg != null) {
            BufferedImage[] frames = splitButtonHalf(buttonsImg, 1);
            if (frames != null && frames.length > 0) {
                return new SkinBtn(frames);
            }
        }
        return super.createIncreaseButton(orientation);
    }

    private static BufferedImage[] splitButtonHalf(BufferedImage img, int half) {
        int fullH = img.getHeight();
        int halfH = fullH / 2;
        int y = half * halfH;

        BufferedImage halfImg = img.getSubimage(0, y, img.getWidth(), halfH);
        return splitFramesFromImage(halfImg);
    }

    private static class SkinBtn extends JButton {
        private final BufferedImage[] frames;

        SkinBtn(BufferedImage[] frames) {
            this.frames = frames;
            if (frames != null && frames.length > 0 && frames[0] != null) {
                setPreferredSize(new Dimension(frames[0].getWidth(), frames[0].getHeight()));
            }
            setRequestFocusEnabled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (frames != null && frames.length > 0) {
                int state = getModel().isPressed() && getModel().isArmed() ? 2 : getModel().isRollover() ? 1 : 0;
                int idx = Math.min(state, frames.length - 1);
                if (frames[idx] != null) {
                    g.drawImage(frames[idx], (getWidth() - frames[idx].getWidth()) / 2,
                            (getHeight() - frames[idx].getHeight()) / 2, null);
                }
            } else {
                super.paintComponent(g);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (frames != null && frames.length > 0 && frames[0] != null) {
                return new Dimension(frames[0].getWidth(), frames[0].getHeight());
            }
            return new Dimension(12, 12);
        }
    }


}
