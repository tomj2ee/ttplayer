package org.ttplayer.ui;

import org.ttplayer.lyrics.LyricSettings;
import java.awt.*;
import java.awt.geom.Rectangle2D;

final class LyricTextPainter {
    private LyricTextPainter() {}

    static Color alpha(Color color, float opacity) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * Math.max(0, Math.min(1, opacity))));
    }

    static void paint(Graphics2D graphics, String text, Rectangle2D bounds, Font font, Color base,
                      Color highlight, float progress, LyricSettings.Style style) {
        if (text == null || text.isEmpty() || bounds.getWidth() <= 0 || bounds.getHeight() <= 0) return;
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.clip(bounds);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics = canvas.getFontMetrics(font);
        if (metrics.stringWidth(text) > bounds.getWidth()) {
            int size = Math.max(8, (int) (font.getSize2D() * bounds.getWidth() / metrics.stringWidth(text)));
            font = font.deriveFont((float) size);
            metrics = canvas.getFontMetrics(font);
        }
        canvas.setFont(font);
        int width = metrics.stringWidth(text);
        float left = (float) (bounds.getX() + Math.max(0, bounds.getWidth() - width) *
                (style.alignment == 0 ? 0 : style.alignment == 2 ? 1 : 0.5));
        float baseline = (float) (bounds.getY() + (bounds.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
        base = alpha(base, style.textOpacity / 100f);
        highlight = alpha(highlight, style.textOpacity / 100f);
        int opacity = Math.max(base.getAlpha(), highlight.getAlpha());
        if (style.shadow) {
            canvas.setColor(new Color(0, 0, 0, opacity * 90 / 255));
            canvas.drawString(text, left + 1, baseline + 1);
        }
        if (style.outline) {
            canvas.setColor(new Color(0, 0, 0, opacity));
            canvas.drawString(text, left - 1, baseline);
            canvas.drawString(text, left + 1, baseline);
            canvas.drawString(text, left, baseline - 1);
            canvas.drawString(text, left, baseline + 1);
        }
        canvas.setColor(base);
        canvas.drawString(text, left, baseline);
        float reveal = Math.min((float) bounds.getWidth(), width) * Math.max(0, Math.min(1, progress));
        if (reveal > 0) {
            float feather = progress >= 1 ? 0 : Math.min(4, reveal);
            canvas.clip(new Rectangle2D.Float(left, (float) bounds.getY(), reveal, (float) bounds.getHeight()));
            if (feather > 0) canvas.setPaint(new GradientPaint(left + reveal - feather, 0, highlight,
                    left + reveal, 0, alpha(highlight, 0)));
            else canvas.setColor(highlight);
            canvas.drawString(text, left, baseline);
        }
        canvas.dispose();
    }
}
