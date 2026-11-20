package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;
import org.ttplayer.util.FontUtils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

public class TtLed extends javax.swing.JComponent {
    private final BufferedImage sheet;
    private String text = "00:00";
    private boolean alignRight = true;
    private int leadingInset, topInset, bottomInset;

    public TtLed(byte[] bmpData) {
        this(bmpData, new Color(255, 0, 255));
    }

    public TtLed(byte[] bmpData, Color transp) {
        sheet = SkinWindow.decodeBmp(bmpData, transp);
        setOpaque(false);
        setFont(FontUtils.getDefaultChineseFont(14));
        int width = sheet == null ? getFontMetrics(getFont()).stringWidth(text) : (int) Math.ceil(sheet.getWidth() / 12f * 5);
        int height = sheet == null ? getFontMetrics(getFont()).getHeight() : sheet.getHeight();
        setDisplayBounds(0, 0, width, height);
    }

    public static String formatTime(long positionMs, long durationMs, boolean remaining) {
        long position = Math.max(0, positionMs), duration = Math.max(0, durationMs);
        long seconds = (remaining ? Math.max(0, duration - position) : position) / 1000;
        return String.format(Locale.ROOT, "%s%02d:%02d", remaining ? "-" : "", seconds / 60, seconds % 60);
    }

    public String getText() { return text; }

    public void setText(String value) {
        value = value == null ? "00:00" : value;
        if (text.equals(value)) return;
        text = value;
        repaint();
    }

    public void setDisplayBounds(int left, int top, int width, int height) {
        width = Math.max(0, width); height = Math.max(0, height);
        leadingInset = sheet == null ? 0 : (int) Math.ceil(sheet.getWidth() / 12f);
        float glyphTop = sheet == null ? -2 : (height - sheet.getHeight()) / 2f;
        float glyphHeight = sheet == null ? getFontMetrics(getFont()).getHeight() : sheet.getHeight();
        topInset = Math.max(0, (int) Math.ceil(-glyphTop));
        bottomInset = Math.max(0, (int) Math.ceil(glyphTop + glyphHeight - height));
        setBounds(left - leadingInset, top - topInset, width + leadingInset, height + topInset + bottomInset);
        setPreferredSize(getSize());
    }

    public Rectangle getDisplayBounds() {
        return new Rectangle(getX() + leadingInset, getY() + topInset, getWidth() - leadingInset,
                getHeight() - topInset - bottomInset);
    }

    @Override public boolean contains(int horizontal, int vertical) {
        return horizontal >= leadingInset && horizontal < getWidth() && vertical >= topInset && vertical < getHeight() - bottomInset;
    }

    @Override protected void paintComponent(Graphics graphics) {
        float width = getWidth() - leadingInset, height = getHeight() - topInset - bottomInset;
        if (width <= 0 || height <= 0 || text.isEmpty()) return;
        Graphics2D canvas = (Graphics2D) graphics.create();
        if (sheet == null) {
            canvas.setFont(getFont());
            canvas.setColor(new Color(210, 240, 250));
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            canvas.clip(new Rectangle2D.Float(0, 0, width, getHeight()));
            FontMetrics metrics = canvas.getFontMetrics();
            canvas.drawString(text, Math.max(0, (width - metrics.stringWidth(text)) / 2), topInset - 2 + metrics.getAscent());
        } else {
            int sign = text.startsWith("-") ? 1 : 0, digits = text.length() - sign;
            if (digits > 0) {
                float digitWidth = sheet.getWidth() / 12f;
                float characterWidth = Math.min(digitWidth, width / digits);
                float left = leadingInset + (alignRight ? Math.max(0, width - characterWidth * digits) : 0);
                float top = topInset + (height - sheet.getHeight()) / 2;
                canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                for (int index = 0; index < text.length(); index++) {
                    char character = text.charAt(index);
                    int digit = character == ':' ? 10 : character == '-' ? 11 : character - '0';
                    if (digit < 0 || digit >= 12) continue;
                    float target = left + (index - sign) * characterWidth;
                    Graphics2D glyph = (Graphics2D) canvas.create();
                    glyph.clip(new Rectangle2D.Float(target, 0, characterWidth, getHeight()));
                    glyph.drawImage(sheet, new AffineTransform(characterWidth / digitWidth, 0, 0, 1,
                            target - digit * characterWidth, top), null);
                    glyph.dispose();
                }
            }
        }
        canvas.dispose();
    }

    public void setAlignRight(boolean value) { alignRight = value; repaint(); }
}
