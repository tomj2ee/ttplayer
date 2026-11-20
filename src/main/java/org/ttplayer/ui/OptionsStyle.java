package org.ttplayer.ui;

import org.ttplayer.util.FontUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicCheckBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

final class OptionsStyle {
    static final Color BACKGROUND = new Color(0xfafaf8), SURFACE = Color.WHITE;
    static final Color TEXT = new Color(0x0e1c23), MUTED = new Color(0x60747c), ACCENT = new Color(0x347e87);
    static final Color EDGE = new Color(0xe0e8e7), BORDER = new Color(0xc7c9c7), SELECTION = new Color(0xd3e6f4);
    static final Color HEADER = new Color(0xf0f6f5), TRACK = new Color(0xeaf0ef), THUMB = new Color(0x9bbec1);
    private OptionsStyle() {}

    static Graphics2D graphics(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return canvas;
    }

    static void paintRoundedBox(Graphics2D canvas, int width, int height, int arc, Color fill, Color border) {
        if (width <= 0 || height <= 0) return;
        double inset = Math.min(1.0, Math.min(width, height) / 2.0);
        RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, width - inset * 2, height - inset * 2, arc, arc);
        canvas.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        canvas.setStroke(new BasicStroke(1f));
        canvas.setColor(fill);
        canvas.fill(shape);
        canvas.setColor(border);
        canvas.draw(shape);
    }

    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(FontUtils.getDefaultChineseFont(13));
        label.setToolTipText(text);
        OptionsLayout.wrapText(label);
        return label;
    }

    static JLabel note(String text) {
        JLabel label = label(text);
        label.setForeground(MUTED);
        label.setFont(FontUtils.getDefaultChineseFont(12));
        return label;
    }

    static JButton button(String text, Runnable action) { return button(text, action, false); }

    static JButton button(String text, Runnable action, boolean primary) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D canvas = graphics(graphics);
                boolean selected = primary || isSelected();
                Color fill = selected ? ACCENT : getModel().isRollover() ? HEADER : SURFACE;
                if (getClientProperty("options.color") instanceof Color) fill = (Color) getClientProperty("options.color");
                if (getModel().isPressed()) fill = fill.darker();
                paintRoundedBox(canvas, getWidth(), getHeight(), 6, fill, selected ? ACCENT : BORDER);
                canvas.setFont(getFont());
                FontMetrics metrics = canvas.getFontMetrics();
                String text = fitText(metrics, getText(), getWidth() - 16);
                Color foreground = selected ? SURFACE : TEXT;
                if (getClientProperty("options.color") instanceof Color)
                    foreground = fill.getRed() * 299 + fill.getGreen() * 587 + fill.getBlue() * 114 < 145000 ? SURFACE : TEXT;
                canvas.setColor(isEnabled() ? foreground : MUTED);
                canvas.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                if (hasFocus()) {
                    canvas.setColor(selected ? SURFACE : ACCENT);
                    canvas.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 4, 4);
                }
                canvas.dispose();
            }
        };
        button.setFont(FontUtils.getDefaultChineseFont(12));
        button.setBorder(new EmptyBorder(4, 10, 4, 10));
        button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false);
        button.setRolloverEnabled(true); button.setOpaque(false);
        button.setPreferredSize(new Dimension(Math.max(90, button.getPreferredSize().width), 30));
        button.addActionListener(event -> action.run());
        return button;
    }

    static String fitText(FontMetrics metrics, String text, int width) {
        if (text == null) return "";
        if (metrics.stringWidth(text) <= width) return text;
        while (!text.isEmpty() && metrics.stringWidth(text + "…") > width)
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        return metrics.stringWidth("…") <= width ? text + "…" : "";
    }

    static JCheckBox checkbox(String text, boolean selected, java.util.function.Consumer<Boolean> changed) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setUI(new BasicCheckBoxUI() {
            @Override protected void paintFocus(Graphics graphics, Rectangle bounds, Dimension size) {
                Graphics2D canvas = graphics(graphics); canvas.setColor(ACCENT);
                canvas.drawRoundRect(1, 1, size.width - 3, size.height - 3, 4, 4); canvas.dispose();
            }
        });
        box.setFont(FontUtils.getDefaultChineseFont(13)); box.setForeground(TEXT);
        box.setOpaque(false); box.setIconTextGap(8); box.setBorder(new EmptyBorder(3, 4, 3, 4));
        box.setIcon(new CheckIcon(false)); box.setSelectedIcon(new CheckIcon(true));
        box.setDisabledIcon(new CheckIcon(false)); box.setDisabledSelectedIcon(new CheckIcon(true));
        box.setToolTipText(text);
        OptionsLayout.wrapText(box);
        box.addActionListener(event -> changed.accept(box.isSelected()));
        return box;
    }

    static JSlider slider(int minimum, int maximum, int value) {
        JSlider slider = new JSlider(minimum, maximum, value);
        slider.setOpaque(false);
        slider.setUI(new BasicSliderUI(slider) {
            @Override protected Dimension getThumbSize() { return new Dimension(12, 18); }
            @Override public void paintFocus(Graphics graphics) {}
            @Override public void paintTrack(Graphics graphics) {
                Graphics2D canvas = graphics(graphics);
                int middle = trackRect.y + trackRect.height / 2;
                canvas.setColor(TRACK); canvas.fillRoundRect(trackRect.x, middle - 3, trackRect.width, 6, 6, 6);
                canvas.setColor(THUMB); canvas.fillRoundRect(trackRect.x, middle - 3, Math.max(0, thumbRect.x - trackRect.x), 6, 6, 6);
                canvas.dispose();
            }
            @Override public void paintThumb(Graphics graphics) {
                Graphics2D canvas = graphics(graphics);
                canvas.setColor(slider.hasFocus() ? ACCENT : THUMB);
                canvas.fillRoundRect(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height, 5, 5);
                canvas.dispose();
            }
        });
        return slider;
    }

    static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
            @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
            private JButton emptyButton() { JButton button = new JButton(); button.setPreferredSize(new Dimension(0, 0)); return button; }
            @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
                graphics.setColor(SURFACE); graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
                Graphics2D canvas = graphics(graphics); canvas.setColor(isThumbRollover() ? ACCENT : THUMB);
                canvas.fillRoundRect(bounds.x + 2, bounds.y, Math.max(4, bounds.width - 4), bounds.height, 6, 6);
                canvas.dispose();
            }
        });
        return scroll;
    }

    static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D canvas = graphics(graphics);
                paintRoundedBox(canvas, getWidth(), getHeight(), 16, SURFACE, EDGE);
                canvas.dispose();
            }
        };
        panel.setOpaque(false); panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    static final class Rows extends JPanel implements Scrollable {
        private final FocusAdapter focusScroller = new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                Component target = event.getComponent();
                Component row = target;
                while (row.getParent() != null && row.getParent() != Rows.this) row = row.getParent();
                if (row.getParent() != Rows.this) return;
                if (row.getHeight() <= getVisibleRect().height) target = row;
                Rectangle bounds = SwingUtilities.convertRectangle(target.getParent(), target.getBounds(), Rows.this);
                bounds.grow(0, 6); scrollRectToVisible(bounds);
            }
        };

        Rows() {
            setLayout(new OptionsLayout()); setBackground(SURFACE);
            setBorder(new EmptyBorder(8, 12, 12, 12));
        }
        void row(Component component) { row(component, 30); }
        void row(Component component, int height) {
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false);
            row.add(component, BorderLayout.CENTER);
            OptionsLayout.rowHeight(row, height); add(row); scrollOnFocus(component);
        }
        private void scrollOnFocus(Component component) {
            if (component.isFocusable()) component.addFocusListener(focusScroller);
            if (component instanceof Container) for (Component child : ((Container) component).getComponents()) scrollOnFocus(child);
        }
        void pair(Component first, Component second) {
            JPanel row = new JPanel(new OptionsLayout.Columns(2, 0, 8)); row.setOpaque(false);
            row.add(first); row.add(second); row(row);
        }
        void leading(Component first, int width, Component second) {
            JPanel row = new JPanel(new OptionsLayout.Leading(width, 0, 12)); row.setOpaque(false);
            row.add(first, BorderLayout.WEST); row.add(second); row(row);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(440, 400); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 30; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(30, visible.height - 30); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class CheckIcon implements Icon {
        private final boolean selected;
        CheckIcon(boolean selected) { this.selected = selected; }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component component, Graphics graphics, int left, int top) {
            Graphics2D canvas = graphics(graphics);
            boolean enabled = component.isEnabled();
            Color edge = enabled && selected ? ACCENT : BORDER;
            canvas.setColor(edge); canvas.fillRect(left, top, 16, 16);
            canvas.setColor(enabled && selected ? ACCENT : enabled ? SURFACE : TRACK);
            canvas.fillRect(left + 1, top + 1, 14, 14);
            if (selected) {
                canvas.setColor(enabled ? SURFACE : MUTED); canvas.setStroke(new BasicStroke(2));
                canvas.drawLine(left + 3, top + 8, left + 6, top + 11);
                canvas.drawLine(left + 6, top + 11, left + 12, top + 4);
            }
            canvas.dispose();
        }
    }
}
