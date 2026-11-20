package org.ttplayer.ui;

import org.ttplayer.util.FontUtils;
import org.ttplayer.util.TtMenuGlyphs;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.geom.Path2D;

public final class TtMenuStyle {
    public static final Color BACKGROUND = new Color(0xfafaf8);
    public static final Color TEXT = new Color(0x0e1c23);
    public static final Color BORDER = new Color(0xc7c9c7);
    private static boolean installed;

    private TtMenuStyle() {}

    public static void install() {
        if (installed) return;
        installed = true;
        UIManager.put("PopupMenuUI", PopupUI.class.getName());
        UIManager.put("MenuItemUI", ItemUI.class.getName());
        UIManager.put("CheckBoxMenuItemUI", ItemUI.class.getName());
        UIManager.put("RadioButtonMenuItemUI", ItemUI.class.getName());
        UIManager.put("MenuUI", MenuUI.class.getName());
        UIManager.put("PopupMenuSeparatorUI", SeparatorUI.class.getName());
        UIManager.put("PopupMenu.border", new AbstractBorder() {
            @Override
            public Insets getBorderInsets(Component component) { return new Insets(4, 1, 4, 1); }
            @Override
            public Insets getBorderInsets(Component component, Insets insets) {
                insets.set(4, 1, 4, 1);
                return insets;
            }
            @Override
            public void paintBorder(Component component, Graphics graphics, int left, int top, int width, int height) {
                graphics.setColor(BORDER);
                graphics.drawRect(left, top, width - 1, height - 1);
            }
        });
        UIManager.put("Menu.submenuPopupOffsetX", -2);
        UIManager.put("Menu.submenuPopupOffsetY", -4);
        UIManager.put("MenuItem.font", FontUtils.getDefaultChineseFont(12));
        UIManager.put("Menu.font", FontUtils.getDefaultChineseFont(12));
        UIManager.put("CheckBoxMenuItem.font", FontUtils.getDefaultChineseFont(12));
        UIManager.put("RadioButtonMenuItem.font", FontUtils.getDefaultChineseFont(12));
    }

    public static boolean root(JComponent component) {
        JPopupMenu popup = component instanceof JPopupMenu ? (JPopupMenu) component
                : component.getParent() instanceof JPopupMenu ? (JPopupMenu) component.getParent() : null;
        return popup != null && !(popup instanceof BasicComboPopup) && !(popup.getInvoker() instanceof JMenu);
    }

    private static Dimension itemSize(JMenuItem item) {
        int band = root(item) ? 24 : 0;
        Font font = item.getFont() == null ? FontUtils.getDefaultChineseFont(12) : item.getFont();
        FontMetrics metrics = item.getFontMetrics(font);
        String text = item.getText() == null ? "" : item.getText();
        int width = metrics.stringWidth(text) + band + 50;
        if (item.getAccelerator() != null) width += metrics.stringWidth(accelerator(item)) + 24;
        return new Dimension(Math.min(518, Math.max(198, width)), 24);
    }

    private static String accelerator(JMenuItem item) {
        KeyStroke key = item.getAccelerator();
        if (key == null) return "";
        String modifiers = java.awt.event.InputEvent.getModifiersExText(key.getModifiers());
        return (modifiers.isEmpty() ? "" : modifiers + "+") + java.awt.event.KeyEvent.getKeyText(key.getKeyCode());
    }

    private static void paintItem(Graphics graphics, JMenuItem item) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            int width = item.getWidth(), height = item.getHeight();
            int band = root(item) ? 24 : 0;
            canvas.setColor(BACKGROUND);
            canvas.fillRect(band, 0, width - band, height);
            boolean selected = item.getModel().isArmed() || item instanceof JMenu && item.getModel().isSelected();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected && item.isEnabled()) {
                canvas.setColor(new Color(0xd3e6f4));
                canvas.fillRoundRect(band + 1, 1, width - band - 2, height - 2, 4, 4);
                canvas.setColor(new Color(0xe0e8e7));
                canvas.drawRoundRect(band + 1, 1, width - band - 3, height - 3, 4, 4);
            }
            if (item.isSelected() && !(item instanceof JMenu)) {
                canvas.setColor(new Color(0x217346));
                Path2D check = new Path2D.Double();
                check.moveTo(band + 6, 9);
                check.lineTo(band + 9, 12);
                check.lineTo(band + 15, 6);
                canvas.draw(check);
            } else {
                Icon icon = item.getIcon();
                if (icon == null) icon = TtMenuGlyphs.icon(TtMenuGlyphs.glyph(item.getActionCommand(), item instanceof JMenu));
                icon.paintIcon(item, canvas, band + 3, (height - 16) / 2);
            }
            canvas.setFont(item.getFont());
            canvas.setColor(item.isEnabled() ? TEXT : new Color(0x8f9294));
            FontMetrics metrics = canvas.getFontMetrics();
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            String shortcut = accelerator(item);
            int textWidth = width - band - 46;
            if (!shortcut.isEmpty()) {
                int shortcutWidth = metrics.stringWidth(shortcut);
                canvas.drawString(shortcut, width - 22 - shortcutWidth, baseline);
                textWidth -= shortcutWidth + 16;
            }
            String label = item.getText() == null ? "" : item.getText();
            if (metrics.stringWidth(label) > textWidth) {
                int end = label.length();
                while (end > 0 && metrics.stringWidth(label.substring(0, end) + "…") > textWidth) end--;
                label = label.substring(0, end) + "…";
            }
            canvas.drawString(label, band + 26, baseline);
            if (item instanceof JMenu) {
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(width - 12, height / 2.0 - 4);
                arrow.lineTo(width - 7, height / 2.0);
                arrow.lineTo(width - 12, height / 2.0 + 4);
                arrow.closePath();
                canvas.fill(arrow);
            }
        } finally { canvas.dispose(); }
    }

    public static final class ItemUI extends BasicMenuItemUI {
        public static ComponentUI createUI(JComponent component) { return new ItemUI(); }
        @Override
        public Dimension getPreferredSize(JComponent component) { return itemSize((JMenuItem) component); }
        @Override
        public void paint(Graphics graphics, JComponent component) { paintItem(graphics, (JMenuItem) component); }
        @Override
        public void update(Graphics graphics, JComponent component) { paint(graphics, component); }
    }

    public static final class MenuUI extends BasicMenuUI {
        public static ComponentUI createUI(JComponent component) { return new MenuUI(); }
        @Override
        public Dimension getPreferredSize(JComponent component) { return itemSize((JMenuItem) component); }
        @Override
        public void paint(Graphics graphics, JComponent component) { paintItem(graphics, (JMenuItem) component); }
        @Override
        public void update(Graphics graphics, JComponent component) { paint(graphics, component); }
    }

    public static final class PopupUI extends BasicPopupMenuUI {
        public static ComponentUI createUI(JComponent component) { return new PopupUI(); }
        @Override
        public void installUI(JComponent component) {
            super.installUI(component);
            TtMenuScroll.install((JPopupMenu) component);
        }
        @Override
        public void paint(Graphics graphics, JComponent component) {
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
            TtMenuScroll.paint(graphics, component);
            if (!root(component)) return;
            for (int column = 0; column < 24; column++) {
                graphics.setColor(new Color(148 - column, 190 - column * 26 / 24, 194 - column * 25 / 24));
                graphics.fillRect(column + 1, 1, 1, component.getHeight() - 2);
            }
            int height = component.getHeight();
            String brand = height >= 160 ? "千千静听 尽听精彩" : height >= 80 ? "千千静听" : "";
            int top = height >= 160 ? height - 160 : (height - brand.length() * 16) / 2;
            graphics.setFont(FontUtils.getDefaultChineseFont(12));
            graphics.setColor(new Color(0xf9fcfc));
            FontMetrics metrics = graphics.getFontMetrics();
            for (int index = 0; index < brand.length(); index++) {
                String letter = brand.substring(index, index + 1);
                graphics.drawString(letter, 1 + (24 - metrics.stringWidth(letter)) / 2,
                        top + index * 16 + (16 - metrics.getHeight()) / 2 + metrics.getAscent());
            }
        }
        @Override
        public void update(Graphics graphics, JComponent component) { paint(graphics, component); }
    }

    public static final class SeparatorUI extends BasicSeparatorUI {
        public static ComponentUI createUI(JComponent component) { return new SeparatorUI(); }
        @Override
        public Dimension getPreferredSize(JComponent component) { return new Dimension(198, 24); }
        @Override
        public void paint(Graphics graphics, JComponent component) {
            int band = root(component) ? 24 : 0;
            graphics.setColor(BACKGROUND);
            graphics.fillRect(band, 0, component.getWidth() - band, component.getHeight());
            graphics.setColor(BORDER);
            graphics.fillRect(band + 4, component.getHeight() / 2, component.getWidth() - band - 8, 1);
        }
        @Override
        public void update(Graphics graphics, JComponent component) { paint(graphics, component); }
    }
}
