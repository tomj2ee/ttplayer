package org.ttplayer.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.text.AttributedString;

final class OptionsLayout implements LayoutManager {
    private static final int ROW_GAP = 4;
    private static final String WRAP = "options.wrap";
    private static final String ROW_HEIGHT = "options.rowHeight";
    private static final String TEXT_KEY = "options.wrappedText";

    static void wrapText(JComponent component) { component.putClientProperty(WRAP, Boolean.TRUE); }
    static void rowHeight(JComponent component, int height) { component.putClientProperty(ROW_HEIGHT, height); }

    @Override public void addLayoutComponent(String name, Component component) {}
    @Override public void removeLayoutComponent(Component component) {}

    @Override public Dimension preferredLayoutSize(Container parent) {
        int width = parent.getParent() instanceof JViewport ? parent.getParent().getWidth() : parent.getWidth();
        if (width <= 0) width = 440;
        Insets insets = parent.getInsets();
        int available = Math.max(1, width - insets.left - insets.right);
        int height = insets.top + insets.bottom;
        for (Component row : parent.getComponents()) if (row.isVisible()) height += height(row, available) + ROW_GAP;
        return new Dimension(width, height);
    }

    @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }

    @Override public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int top = insets.top;
        for (Component row : parent.getComponents()) {
            if (!row.isVisible()) continue;
            int height = height(row, width);
            row.setBounds(insets.left, top, width, height);
            top += height + ROW_GAP;
        }
    }

    private static int height(Component component, int width) {
        int height = preferredHeight(component, Math.max(1, width));
        if (component instanceof JComponent) {
            Object minimum = ((JComponent) component).getClientProperty(ROW_HEIGHT);
            if (minimum instanceof Integer) height = Math.max(height, (Integer) minimum);
        }
        return height;
    }

    private static int preferredHeight(Component component, int width) {
        if (component == null || !component.isVisible()) return 0;
        if (component instanceof JComponent && Boolean.TRUE.equals(((JComponent) component).getClientProperty(WRAP)))
            return textHeight((JComponent) component, width);
        if (component instanceof Container) {
            Container container = (Container) component;
            Insets insets = container.getInsets();
            int available = Math.max(1, width - insets.left - insets.right);
            LayoutManager layout = container.getLayout();
            if (layout instanceof Leading) return ((Leading) layout).height(container, width);
            if (layout instanceof BorderLayout) {
                BorderLayout border = (BorderLayout) layout;
                Component west = border.getLayoutComponent(container, BorderLayout.WEST);
                Component east = border.getLayoutComponent(container, BorderLayout.EAST);
                Component center = border.getLayoutComponent(container, BorderLayout.CENTER);
                int westWidth = visible(west) ? Math.min(available, west.getPreferredSize().width) : 0;
                int eastWidth = visible(east) ? Math.min(available, east.getPreferredSize().width) : 0;
                int centerWidth = available - westWidth - eastWidth;
                if (visible(west)) centerWidth -= border.getHgap();
                if (visible(east)) centerWidth -= border.getHgap();
                int middle = Math.max(preferredHeight(center, Math.max(1, centerWidth)),
                        Math.max(preferredHeight(west, westWidth), preferredHeight(east, eastWidth)));
                Component north = border.getLayoutComponent(container, BorderLayout.NORTH);
                Component south = border.getLayoutComponent(container, BorderLayout.SOUTH);
                int height = middle + preferredHeight(north, available) + preferredHeight(south, available);
                if (visible(north)) height += border.getVgap();
                if (visible(south)) height += border.getVgap();
                return height + insets.top + insets.bottom;
            }
            if (layout instanceof GridLayout && container.getComponentCount() > 0) {
                if (layout instanceof Columns) ((Columns) layout).fit(container, available);
                GridLayout grid = (GridLayout) layout;
                int count = container.getComponentCount();
                int rows = grid.getRows(), columns = grid.getColumns();
                if (rows > 0) columns = (count + rows - 1) / rows;
                else rows = (count + columns - 1) / columns;
                int cellWidth = Math.max(1, (available - (columns - 1) * grid.getHgap()) / columns);
                int cellHeight = 0;
                for (Component child : container.getComponents()) cellHeight = Math.max(cellHeight, preferredHeight(child, cellWidth));
                return rows * cellHeight + (rows - 1) * grid.getVgap() + insets.top + insets.bottom;
            }
        }
        return component.getPreferredSize().height;
    }

    private static boolean visible(Component component) { return component != null && component.isVisible(); }

    static final class Columns extends GridLayout {
        private final int maximumColumns, minimumCellWidth;

        Columns(int maximumColumns, int minimumCellWidth, int gap) {
            super(0, maximumColumns, gap, gap);
            this.maximumColumns = maximumColumns; this.minimumCellWidth = minimumCellWidth;
        }

        private void fit(Container parent, int width) {
            int minimum = minimumCellWidth;
            if (minimum <= 0) for (Component child : parent.getComponents()) minimum = Math.max(minimum, child.getPreferredSize().width);
            setColumns(Math.max(1, Math.min(maximumColumns, (Math.max(0, width) + getHgap()) / Math.max(1, minimum + getHgap()))));
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            fit(parent, parent.getWidth() - insets.left - insets.right);
            super.layoutContainer(parent);
        }
    }

    static final class Leading extends BorderLayout {
        private final int leadingWidth, trailingMinimum;

        Leading(int leadingWidth, int trailingMinimum, int gap) {
            super(gap, gap); this.leadingWidth = leadingWidth; this.trailingMinimum = trailingMinimum;
        }

        private boolean stacked(Container parent, int width) {
            Component trailing = getLayoutComponent(parent, CENTER);
            int minimum = trailingMinimum > 0 ? trailingMinimum : visible(trailing) ? trailing.getPreferredSize().width : 0;
            return width < leadingWidth + getHgap() + minimum;
        }

        private int height(Container parent, int width) {
            Insets insets = parent.getInsets();
            int available = Math.max(1, width - insets.left - insets.right);
            Component leading = getLayoutComponent(parent, WEST), trailing = getLayoutComponent(parent, CENTER);
            int height;
            if (stacked(parent, available)) {
                height = preferredHeight(leading, available) + preferredHeight(trailing, available);
                if (visible(leading) && visible(trailing)) height += getVgap();
            } else {
                height = Math.max(preferredHeight(leading, leadingWidth), preferredHeight(trailing, available - leadingWidth - getHgap()));
            }
            return height + insets.top + insets.bottom;
        }

        @Override public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
            int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
            Component leading = getLayoutComponent(parent, WEST), trailing = getLayoutComponent(parent, CENTER);
            if (stacked(parent, width)) {
                int firstHeight = Math.min(height, preferredHeight(leading, Math.max(1, width)));
                if (visible(leading)) leading.setBounds(insets.left, insets.top, width, firstHeight);
                int gap = visible(leading) && visible(trailing) ? getVgap() : 0;
                if (visible(trailing)) trailing.setBounds(insets.left, insets.top + firstHeight + gap, width, Math.max(0, height - firstHeight - gap));
            } else {
                int firstWidth = Math.min(width, leadingWidth);
                if (visible(leading)) leading.setBounds(insets.left, insets.top, firstWidth, height);
                int gap = visible(leading) && visible(trailing) ? getHgap() : 0;
                if (visible(trailing)) trailing.setBounds(insets.left + firstWidth + gap, insets.top, Math.max(0, width - firstWidth - gap), height);
            }
        }
    }

    private static int textHeight(JComponent component, int width) {
        String text;
        Icon icon;
        int gap;
        if (component instanceof JLabel) {
            JLabel label = (JLabel) component; text = label.getText(); icon = label.getIcon(); gap = label.getIconTextGap();
        } else {
            AbstractButton button = (AbstractButton) component; text = button.getText(); icon = button.getIcon(); gap = button.getIconTextGap();
        }
        if (text == null) text = "";
        Insets insets = component.getInsets();
        int iconWidth = icon == null ? 0 : icon.getIconWidth() + (text.isEmpty() ? 0 : gap);
        int available = Math.max(1, width - insets.left - insets.right - iconWidth);
        FontMetrics metrics = component.getFontMetrics(component.getFont());
        int textHeight = metrics.getHeight();
        if (metrics.stringWidth(text) > available || text.indexOf('\n') >= 0) {
            String key = text + "\n" + component.getFont() + "\n" + component.getForeground() + "\n" + available + "\n" + component.isEnabled();
            View view = (View) component.getClientProperty(BasicHTML.propertyKey);
            if (view == null || !key.equals(component.getClientProperty(TEXT_KEY))) {
                String html = wrappedHtml(text, metrics, available);
                if (!component.isEnabled()) html = "<font color='#60747c'>" + html + "</font>";
                view = BasicHTML.createHTMLView(component, "<html>" + html + "</html>");
                component.putClientProperty(BasicHTML.propertyKey, view); component.putClientProperty(TEXT_KEY, key);
            }
            view.setSize(available, 0);
            textHeight = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
        } else {
            component.putClientProperty(BasicHTML.propertyKey, null); component.putClientProperty(TEXT_KEY, null);
        }
        return Math.max(textHeight, icon == null ? 0 : icon.getIconHeight()) + insets.top + insets.bottom;
    }

    private static String wrappedHtml(String text, FontMetrics metrics, int width) {
        StringBuilder html = new StringBuilder();
        for (String paragraph : text.split("\n", -1)) {
            if (html.length() > 0) html.append("<br>");
            if (paragraph.isEmpty()) { html.append("&nbsp;"); continue; }
            AttributedString attributed = new AttributedString(paragraph);
            attributed.addAttribute(TextAttribute.FONT, metrics.getFont());
            LineBreakMeasurer measurer = new LineBreakMeasurer(attributed.getIterator(), metrics.getFontRenderContext());
            while (measurer.getPosition() < paragraph.length()) {
                int start = measurer.getPosition();
                measurer.nextLayout(width);
                if (start > 0) html.append("<br>");
                html.append(paragraph.substring(start, measurer.getPosition()).replaceFirst("\\s+$", "")
                        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
            }
        }
        return html.toString();
    }
}
