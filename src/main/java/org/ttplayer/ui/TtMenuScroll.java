package org.ttplayer.ui;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

final class TtMenuScroll implements LayoutManager, ChangeListener, KeyEventDispatcher {
    private final JPopupMenu popup;
    private final Timer timer;
    private int firstRow;
    private int rowCount;
    private int visibleRows;
    private int direction;
    private boolean scrolling;

    static void install(JPopupMenu popup) {
        if (popup instanceof javax.swing.plaf.basic.BasicComboPopup || popup.getClientProperty("tt.scroll") != null) return;
        TtMenuScroll controller = new TtMenuScroll(popup);
        popup.putClientProperty("tt.scroll", controller);
        popup.setLayout(controller);
    }

    private TtMenuScroll(JPopupMenu popup) {
        this.popup = popup;
        timer = new Timer(90, event -> scroll(direction));
        MouseAdapter mouse = new MouseAdapter() {
            public void mouseMoved(MouseEvent event) {
                direction = !scrolling ? 0 : event.getY() < 22 ? -1 : event.getY() >= popup.getHeight() - 22 ? 1 : 0;
                if (direction == 0) timer.stop();
                else if (!timer.isRunning()) { scroll(direction); timer.start(); }
            }
            public void mousePressed(MouseEvent event) { if (scrolling) mouseMoved(event); }
            public void mouseReleased(MouseEvent event) { timer.stop(); }
            public void mouseExited(MouseEvent event) { timer.stop(); }
            public void mouseWheelMoved(MouseWheelEvent event) {
                if (scrolling) { scroll(event.getWheelRotation() * 3); event.consume(); }
            }
        };
        popup.addMouseListener(mouse);
        popup.addMouseMotionListener(mouse);
        popup.addMouseWheelListener(mouse);
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                firstRow = 0;
                MenuSelectionManager.defaultManager().addChangeListener(TtMenuScroll.this);
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(TtMenuScroll.this);
            }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { stop(); }
            public void popupMenuCanceled(PopupMenuEvent event) { stop(); }
        });
    }

    private void stop() {
        timer.stop();
        MenuSelectionManager.defaultManager().removeChangeListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

    private int availableHeight() {
        GraphicsConfiguration configuration = popup.getInvoker() == null ? null : popup.getInvoker().getGraphicsConfiguration();
        if (configuration == null && !GraphicsEnvironment.isHeadless())
            configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        if (configuration == null) return 720;
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return Math.max(100, configuration.getBounds().height - insets.top - insets.bottom - 12);
    }

    public Dimension preferredLayoutSize(Container container) {
        int width = 198;
        int count = 0;
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            width = Math.max(width, child.getPreferredSize().width);
            count++;
        }
        int maximum = availableHeight();
        int height = count * 24 + 8;
        if (height > maximum) height = Math.max(1, (maximum - 44) / 24) * 24 + 44;
        return new Dimension(Math.min(520, width + 2), height);
    }

    public Dimension minimumLayoutSize(Container container) { return preferredLayoutSize(container); }
    public void addLayoutComponent(String name, Component component) {}
    public void removeLayoutComponent(Component component) {}

    public void layoutContainer(Container container) {
        rowCount = 0;
        for (Component child : container.getComponents()) if (child.isVisible()) rowCount++;
        scrolling = rowCount * 24 + 8 > container.getHeight();
        visibleRows = scrolling ? Math.max(1, (container.getHeight() - 44) / 24) : rowCount;
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, rowCount - visibleRows)));
        int row = 0;
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            int relative = row++ - firstRow;
            if (relative < 0 || relative >= visibleRows) child.setBounds(-10000, -10000, 0, 0);
            else child.setBounds(1, (scrolling ? 22 : 4) + relative * 24, container.getWidth() - 2, 24);
        }
    }

    private void move(int amount) {
        int next = Math.max(0, Math.min(firstRow + amount, rowCount - visibleRows));
        if (next == firstRow) return;
        firstRow = next;
        popup.doLayout();
        popup.repaint();
    }

    private void scroll(int amount) {
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        MenuElement[] path = manager.getSelectedPath();
        for (int index = 0; index < path.length; index++) if (path[index] == popup) {
            if (index + 1 < path.length) manager.setSelectedPath(java.util.Arrays.copyOf(path, index + 1));
            break;
        }
        move(amount);
    }

    public void stateChanged(ChangeEvent event) {
        if (!scrolling) return;
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            Component selected = element.getComponent();
            if (selected.getParent() != popup) continue;
            int index = 0;
            for (Component child : popup.getComponents()) {
                if (!child.isVisible()) continue;
                if (child == selected) {
                    if (index < firstRow) move(index - firstRow);
                    else if (index >= firstRow + visibleRows) move(index - firstRow - visibleRows + 1);
                    return;
                }
                index++;
            }
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED || !popup.isShowing()) return false;
        int key = event.getKeyCode();
        if (key != KeyEvent.VK_HOME && key != KeyEvent.VK_END && key != KeyEvent.VK_PAGE_UP && key != KeyEvent.VK_PAGE_DOWN) return false;
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        int depth = -1;
        for (int index = 0; index < path.length; index++) if (path[index] instanceof JPopupMenu) depth = index;
        if (depth < 0 || path[depth] != popup) return false;
        Component selected = depth + 1 < path.length ? path[depth + 1].getComponent() : null;
        Component[] children = popup.getComponents();
        int current = java.util.Arrays.asList(children).indexOf(selected);
        int direction = key == KeyEvent.VK_END || key == KeyEvent.VK_PAGE_DOWN ? 1 : -1;
        int index = key == KeyEvent.VK_HOME ? 0 : key == KeyEvent.VK_END ? children.length - 1
                : Math.max(0, Math.min(children.length - 1, Math.max(0, current) + direction * Math.max(1, visibleRows - 1)));
        int step = key == KeyEvent.VK_HOME ? 1 : key == KeyEvent.VK_END ? -1 : direction;
        while (index >= 0 && index < children.length) {
            Component child = children[index];
            if (child instanceof JMenuItem && child.isVisible() && child.isEnabled()) {
                MenuElement[] updated = java.util.Arrays.copyOf(path, depth + 2);
                updated[depth + 1] = (MenuElement) child;
                MenuSelectionManager.defaultManager().setSelectedPath(updated);
                event.consume();
                return true;
            }
            index += step;
        }
        event.consume();
        return true;
    }

    static void paint(Graphics graphics, JComponent component) {
        Object property = component.getClientProperty("tt.scroll");
        if (!(property instanceof TtMenuScroll)) return;
        TtMenuScroll scroll = (TtMenuScroll) property;
        if (!scroll.scrolling) return;
        int center = (component.getWidth() + (TtMenuStyle.root(component) ? 24 : 0)) / 2;
        graphics.setColor(scroll.firstRow > 0 ? TtMenuStyle.TEXT : TtMenuStyle.BORDER);
        graphics.fillPolygon(new int[] {center - 4, center + 4, center}, new int[] {14, 14, 9}, 3);
        graphics.setColor(scroll.firstRow + scroll.visibleRows < scroll.rowCount ? TtMenuStyle.TEXT : TtMenuStyle.BORDER);
        int bottom = component.getHeight() - 10;
        graphics.fillPolygon(new int[] {center - 4, center + 4, center}, new int[] {bottom - 5, bottom - 5, bottom}, 3);
    }
}
