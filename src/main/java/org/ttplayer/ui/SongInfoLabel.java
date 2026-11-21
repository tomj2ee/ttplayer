package org.ttplayer.ui;

import javax.swing.JLabel;
import javax.swing.Timer;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.util.Collections;
import java.util.List;

final class SongInfoLabel extends JLabel {
    private enum Phase { HOLD, SCROLL, SLIDE }

    private static final int HOLD_MS = 2500;
    private static final int MS_PER_PIXEL = 35;
    private static final int END_HOLD_MS = 800;
    private final Timer animation = new Timer(MS_PER_PIXEL, event -> repaint());
    private List<String> pages = Collections.emptyList();
    private int index;
    private Phase phase = Phase.HOLD;
    private long phaseStart;

    SongInfoLabel() {
        putClientProperty("html.disable", true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                resetAnimation();
                updateTimer();
            }
        });
    }

    void setPages(List<String> values, String idleText) {
        pages = values;
        setText(values.isEmpty() ? idleText : values.get(0));
        setToolTipText(values.isEmpty() ? null : String.join(" \u00b7 ", values));
        resetAnimation();
        updateTimer();
        repaint();
    }

    private void updateTimer() {
        if (isShowing() && !pages.isEmpty()) animation.start();
        else animation.stop();
    }

    private void resetAnimation() {
        index = 0;
        phase = Phase.HOLD;
        phaseStart = System.nanoTime();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (pages.isEmpty()) {
            super.paintComponent(graphics);
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            if (isOpaque()) {
                canvas.setColor(getBackground());
                canvas.fillRect(0, 0, getWidth(), getHeight());
            }
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.setFont(getFont());
            canvas.setColor(getForeground());
            FontMetrics metrics = canvas.getFontMetrics();
            int nextIndex = (index + 1) % pages.size();
            String current = pages.get(index);
            int width = metrics.stringWidth(current), overflow = Math.max(0, width - getWidth());
            long now = System.nanoTime(), elapsed = (now - phaseStart) / 1_000_000;
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            int slideTime = Math.max(1, getHeight()) * MS_PER_PIXEL;
            if (phase == Phase.HOLD && elapsed >= HOLD_MS && (overflow > 0 || pages.size() > 1)) {
                phase = overflow > 0 ? Phase.SCROLL : Phase.SLIDE;
                phaseStart = now;
                elapsed = 0;
            } else if (phase == Phase.SCROLL && elapsed >= overflow * (long) MS_PER_PIXEL + END_HOLD_MS) {
                phase = pages.size() > 1 ? Phase.SLIDE : Phase.HOLD;
                phaseStart = now;
                elapsed = 0;
            } else if (phase == Phase.SLIDE && elapsed >= slideTime) {
                index = nextIndex;
                phase = Phase.HOLD;
                phaseStart = now;
                current = pages.get(index);
                width = metrics.stringWidth(current);
            }

            if (phase == Phase.SCROLL) {
                canvas.drawString(current, -(int) Math.min(overflow, elapsed / MS_PER_PIXEL), baseline);
            } else if (phase == Phase.SLIDE) {
                int offset = (int) (elapsed / MS_PER_PIXEL);
                String next = pages.get(nextIndex);
                int left = overflow > 0 ? -overflow : alignedLeft(width);
                canvas.drawString(current, left, baseline - offset);
                canvas.drawString(next, alignedLeft(metrics.stringWidth(next)), baseline + getHeight() - offset);
            } else {
                canvas.drawString(current, alignedLeft(width), baseline);
            }
        } finally {
            canvas.dispose();
        }
    }

    private int alignedLeft(int textWidth) {
        int space = Math.max(0, getWidth() - textWidth);
        return getHorizontalAlignment() == RIGHT ? space : getHorizontalAlignment() == CENTER ? space / 2 : 0;
    }
}
