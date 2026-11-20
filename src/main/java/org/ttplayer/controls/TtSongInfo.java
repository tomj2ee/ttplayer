package org.ttplayer.controls;

import org.ttplayer.util.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TtSongInfo extends JLabel {
    private List<String> information = Collections.emptyList();
    private final Timer animationTimer;
    private int carouselIndex;
    private int carouselPhase;
    private long carouselSince;

    public TtSongInfo() {
        animationTimer = new Timer(35, event -> repaint());
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) resetAnimation();
                updateAnimationTimer();
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { updateAnimationTimer(); }
        });
    }

    public void setInformation(List<String> values) {
        List<String> next = values == null || values.isEmpty() ? Collections.singletonList("") : values;
        if (information.equals(next)) return;
        information = Collections.unmodifiableList(new ArrayList<>(next));
        setText(information.get(0));
        String text = String.join("\n", information);
        setToolTipText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>") + "</html>");
        Font font = getFont();
        setFont(FontUtils.getLyricFontByText(text, font.getFamily(), font.getStyle(), font.getSize()));
        resetAnimation();
        updateAnimationTimer();
    }

    public void resetAnimation() {
        carouselIndex = 0;
        carouselPhase = 0;
        carouselSince = System.nanoTime() / 1000000;
        repaint();
    }

    private void updateAnimationTimer() {
        if (animationTimer == null) return;
        boolean animate = isShowing() && !information.isEmpty() && (information.size() > 1 ||
                getFontMetrics(getFont()).stringWidth(information.get(0)) > getWidth());
        if (animate) animationTimer.start();
        else animationTimer.stop();
    }

    @Override public void addNotify() {
        super.addNotify();
        updateAnimationTimer();
    }

    @Override public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.clipRect(0, 0, getWidth(), getHeight());
            if (isOpaque()) {
                canvas.setColor(getBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
            }
            if (information.isEmpty() || getWidth() <= 0 || getHeight() <= 0) return;
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(getFont());
            canvas.setColor(getForeground());
            FontMetrics metrics = canvas.getFontMetrics();
            int width = metrics.stringWidth(information.get(carouselIndex));
            if (information.size() == 1 && isShowing()) {
                if (width > getWidth()) animationTimer.start();
                else animationTimer.stop();
            }
            long now = System.nanoTime() / 1000000;
            long elapsed = Math.max(0, now - carouselSince);
            if (carouselPhase == 0 && elapsed >= 2500 && (information.size() > 1 || width > getWidth())) {
                carouselPhase = width > getWidth() ? 2 : 1;
                carouselSince = now;
                elapsed = 0;
            } else if (carouselPhase == 1 && elapsed >= getHeight() * 35L) {
                carouselIndex = (carouselIndex + 1) % information.size();
                carouselPhase = 0;
                carouselSince = now;
                elapsed = 0;
            } else if (carouselPhase == 2 && elapsed >= Math.max(0, width - getWidth()) * 35L + 1000) {
                carouselPhase = information.size() > 1 ? 1 : 0;
                carouselSince = now;
                elapsed = 0;
            }
            String current = information.get(carouselIndex);
            width = metrics.stringWidth(current);
            float baseline = (getHeight() - metrics.getHeight()) / 2.0f + metrics.getAscent();
            if (carouselPhase == 1) {
                float offset = elapsed / 35.0f;
                float left = width > getWidth() ? getWidth() - width : alignedLeft(width);
                canvas.drawString(current, left, baseline - offset);
                String following = information.get((carouselIndex + 1) % information.size());
                canvas.drawString(following, alignedLeft(metrics.stringWidth(following)), baseline + getHeight() - offset);
            } else if (carouselPhase == 2) {
                canvas.drawString(current, -Math.min(elapsed / 35.0f, Math.max(0, width - getWidth())), baseline);
            } else {
                canvas.drawString(current, alignedLeft(width), baseline);
            }
        } finally {
            canvas.dispose();
        }
    }

    private float alignedLeft(int width) {
        if (getHorizontalAlignment() == SwingConstants.RIGHT) return Math.max(0, getWidth() - width);
        if (getHorizontalAlignment() == SwingConstants.CENTER) return Math.max(0, (getWidth() - width) / 2.0f);
        return 0;
    }
}
