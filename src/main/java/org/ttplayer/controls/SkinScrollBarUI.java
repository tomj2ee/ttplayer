package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class SkinScrollBarUI extends BasicScrollBarUI {

    private BufferedImage buttonsImg;
    private BufferedImage[] thumbFrames;
    private BufferedImage barImg;

    private int thumbState = 0;


    private final Color fallbackTrack;
    private final Color fallbackThumb;

    public SkinScrollBarUI(Color fallbackTrack, Color fallbackThumb) {
        this.fallbackTrack = fallbackTrack;
        this.fallbackThumb = fallbackThumb;
    }

    public void setSkinImages(byte[] buttonsBmp, byte[] thumbBmp, byte[] barBmp) {
        Color transp = new Color(255, 0, 255);
        if (buttonsBmp != null) {
            buttonsImg = SkinWindow.decodeBmp(buttonsBmp, transp);
        }
        thumbFrames = splitFrames(thumbBmp, transp);
        if (barBmp != null) {
            BufferedImage src = SkinWindow.decodeBmp(barBmp, transp);
            if (src != null) {
                barImg = src;
            }
        }
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
        if (thumbFrames != null && thumbFrames.length > 0 && thumbFrames[0] != null) {
            return new Dimension(thumbFrames[0].getWidth(), thumbFrames[0].getHeight());
        }
        return super.getMinimumThumbSize();
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
        if (barImg != null) {
            g.drawImage(barImg, r.x, r.y, r.width, r.height, null);
        }
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty()) return;
        if (thumbFrames != null && thumbState >= 0 && thumbState < thumbFrames.length) {
            BufferedImage frame = thumbFrames[thumbState];
            if (frame != null) {
                g.drawImage(frame, r.x, r.y, r.width, r.height, null);
            }
        }
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        scrollbar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { thumbState = 1; scrollbar.repaint(); }
            @Override
            public void mouseExited(MouseEvent e)  { thumbState = 0; scrollbar.repaint(); }
            @Override
            public void mousePressed(MouseEvent e) {
                Rectangle tb = getThumbBounds();
                if (tb != null && tb.contains(e.getPoint())) {
                    thumbState = 2;
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) { thumbState = 1; scrollbar.repaint(); }
        });
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
        private int state = 0;

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
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { state = 1; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { state = 0; repaint(); }
                @Override public void mousePressed(MouseEvent e) { state = 2; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { state = 1; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (frames != null && frames.length > state && frames[state] != null) {
                int idx = Math.min(state, frames.length - 1);
                if (frames[idx] != null) {
                    g.drawImage(frames[idx], 0, 0, frames[idx].getWidth(), frames[idx].getHeight(), null);
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
