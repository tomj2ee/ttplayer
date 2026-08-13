package org.ttplayer.controls;

import org.ttplayer.model.Song;
import org.ttplayer.util.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 虚拟列表组件 - 只渲染可见区域的项目，大幅提升大数据量性能
 */
public class VirtualList extends JComponent implements Scrollable {

    // 数据
    private List<String> items = new ArrayList<>();
    private List<Song> songs = new ArrayList<>();

    // 选中状态
    private Set<Integer> selectedIndices = new HashSet<>();
    private int leadIndex = -1;
    private int anchorIndex = -1;

    // 颜色配置（PlayList.xml：Color_* 全部支持）
    private Color colorText = Color.decode("#0080ff");     // 歌曲名
    private Color colorHilight = Color.decode("#00ff00");  // 选中行前景
    private Color colorSelect = Color.decode("#3269c8");   // 选中行背景
    private Color colorBkgnd = Color.decode("#000000");    // 奇数行背景
    private Color colorBkgnd2 = Color.decode("#202020");   // 偶数行背景
    private Color colorNumber = Color.decode("#008000");   // 序号
    private Color colorDuration = Color.decode("#c08020"); // 时长

    // "12. 歌曲名 (04:32)" → 序号/歌名/时长 分段渲染
    private static final java.util.regex.Pattern ROW_PATTERN = java.util.regex.Pattern.compile(
        "^(\\d+)\\.\\s+(.*?)(?:\\s+\\(([^)]*)\\))?$");

    // 行高
    private int rowHeight = 20;

    // 字体
    private Font listFont;

    // 鼠标事件相关
    private boolean isDragging = false;

    // 点击监听器
    public interface MouseClickListener {
        void onMouseClicked(MouseEvent e, int row);
        void onMouseDoubleClicked(MouseEvent e, int row);
        void onPopupTrigger(MouseEvent e, int row);
    }
    private MouseClickListener mouseClickListener;

    // 工具提示生成器
    public interface ToolTipProvider {
        String getToolTip(int row);
    }
    private ToolTipProvider toolTipProvider;

    public VirtualList() {
        listFont = FontUtils.getDefaultChineseFont(12);
        setOpaque(false);

        // 计算初始行高
        FontMetrics fm = getFontMetrics(listFont);
        rowHeight = fm.getHeight() + 4;

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePress(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseRelease(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int row = locationToIndex(e.getPoint());
                if (row >= 0) {
                    if (e.getClickCount() == 2 && mouseClickListener != null) {
                        mouseClickListener.onMouseDoubleClicked(e, row);
                    } else if (mouseClickListener != null) {
                        mouseClickListener.onMouseClicked(e, row);
                    }
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDrag(e);
            }
        });
    }

    public void setColors(Color text, Color hilight, Color select) {
        this.colorText = text;
        this.colorHilight = hilight;
        this.colorSelect = select;
    }

    /** PlayList.xml 七色完整设置 */
    public void setColors(Color text, Color hilight, Color select, Color bkgnd, Color bkgnd2,
                          Color number, Color duration) {
        this.colorText = text;
        this.colorHilight = hilight;
        this.colorSelect = select;
        this.colorBkgnd = bkgnd;
        this.colorBkgnd2 = bkgnd2;
        this.colorNumber = number;
        this.colorDuration = duration;
    }

    public void setListFont(Font font) {
        this.listFont = font;
        // 计算行高
        FontMetrics fm = getFontMetrics(font);
        if (fm != null) {
            rowHeight = fm.getHeight() + 4;
        }
        revalidate();
        repaint();
    }

    public void setMouseClickListener(MouseClickListener listener) {
        this.mouseClickListener = listener;
    }

    public void setToolTipProvider(ToolTipProvider provider) {
        this.toolTipProvider = provider;
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (toolTipProvider != null) {
            int row = locationToIndex(e.getPoint());
            if (row >= 0 && row < items.size()) {
                return toolTipProvider.getToolTip(row);
            }
        }
        return super.getToolTipText(e);
    }

    public void setData(List<String> items, List<Song> songs) {
        this.items = items != null ? items : new ArrayList<>();
        this.songs = songs != null ? songs : new ArrayList<>();
        this.selectedIndices.clear();
        this.leadIndex = -1;
        this.anchorIndex = -1;
        revalidate();
        repaint();
    }

    public int getSelectedIndex() {
        if (selectedIndices.isEmpty()) return -1;
        return selectedIndices.iterator().next();
    }

    public int[] getSelectedIndices() {
        int[] arr = new int[selectedIndices.size()];
        int i = 0;
        for (int idx : selectedIndices) arr[i++] = idx;
        return arr;
    }

    public void setSelectedIndex(int index) {
        selectedIndices.clear();
        if (index >= 0 && index < items.size()) {
            selectedIndices.add(index);
            leadIndex = index;
            anchorIndex = index;
        }
        ensureIndexIsVisible(index);
        repaint();
    }

    public void ensureIndexIsVisible(int index) {
        if (index < 0 || index >= items.size()) return;

        Rectangle rect = new Rectangle(0, index * rowHeight, getWidth(), rowHeight);
        scrollRectToVisible(rect);
    }

    private int locationToIndex(Point p) {
        int row = p.y / rowHeight;
        if (row >= 0 && row < items.size()) {
            return row;
        }
        return -1;
    }

    private void handleMousePress(MouseEvent e) {
        int row = locationToIndex(e.getPoint());
        if (e.isPopupTrigger() && mouseClickListener != null) {
            mouseClickListener.onPopupTrigger(e, row);
            return;
        }

        if (row >= 0 && row < items.size()) {
            if (e.isControlDown()) {
                if (selectedIndices.contains(row)) {
                    selectedIndices.remove(row);
                } else {
                    selectedIndices.add(row);
                }
                leadIndex = row;
            } else if (e.isShiftDown() && anchorIndex >= 0) {
                selectedIndices.clear();
                int start = Math.min(anchorIndex, row);
                int end = Math.max(anchorIndex, row);
                for (int i = start; i <= end; i++) {
                    selectedIndices.add(i);
                }
                leadIndex = row;
            } else {
                selectedIndices.clear();
                selectedIndices.add(row);
                anchorIndex = row;
                leadIndex = row;
            }
            isDragging = true;
            repaint();
        }
    }

    private void handleMouseRelease(MouseEvent e) {
        isDragging = false;
        if (e.isPopupTrigger() && mouseClickListener != null) {
            int row = locationToIndex(e.getPoint());
            mouseClickListener.onPopupTrigger(e, row);
        }
    }

    private void handleMouseDrag(MouseEvent e) {
        if (isDragging) {
            int row = locationToIndex(e.getPoint());
            if (row >= 0 && row < items.size()) {
                selectedIndices.clear();
                int start = Math.min(anchorIndex, row);
                int end = Math.max(anchorIndex, row);
                for (int i = start; i <= end; i++) {
                    selectedIndices.add(i);
                }
                leadIndex = row;
                repaint();
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, items.size() * rowHeight);
    }

    // ============ Scrollable：宽度跟随 viewport，高度内容滚动 ============

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(200, 200);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;   // 宽度始终等于可视区宽度（分割条/窗口变化时列表随之变化）
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;  // 高度按内容滚动
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(1, rowHeight);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(rowHeight, visibleRect.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setFont(listFont);
        FontMetrics fm = g2d.getFontMetrics();

        // 获得可见区域
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = getVisibleRect();
        }

        int width = getWidth();

        // 计算需要绘制的行范围
        int firstRow = Math.max(0, clip.y / rowHeight);
        int lastRow = Math.min(items.size(), (clip.y + clip.height) / rowHeight + 2);

        for (int i = firstRow; i < lastRow; i++) {
            int y = i * rowHeight;
            boolean isSelected = selectedIndices.contains(i);
            String text = items.get(i);

            // 行背景：选中用 Color_Select，否则按 Color_Bkgnd/Color_Bkgnd2 隔行
            if (isSelected) {
                g2d.setColor(colorSelect);
                g2d.fillRect(0, y, width, rowHeight);
            } else {
                g2d.setColor((i % 2 == 0) ? colorBkgnd : colorBkgnd2);
                g2d.fillRect(0, y, width, rowHeight);
            }

            int textY = y + (rowHeight - fm.getHeight()) / 2 + fm.getAscent();

            if (isSelected) {
                // 选中行：全部文字用 Hilight
                g2d.setColor(colorHilight);
                g2d.drawString(text, 5, textY);
                continue;
            }

            // 非选中：分段颜色 序号→Number / 歌名→Text / 时长→Duration（右对齐）
            java.util.regex.Matcher m = ROW_PATTERN.matcher(text);
            int x = 5;
            if (m.matches()) {
                String number = m.group(1);
                String title = m.group(2);
                String dur = m.group(3);

                g2d.setColor(colorNumber);
                String num = number + ". ";
                g2d.drawString(num, x, textY);
                x += fm.stringWidth(num);

                if (dur != null && !dur.isEmpty()) {
                    // 时长右对齐：预留位置，歌名过长时省略号截断，不遮挡时长
                    String dstr = "(" + dur + ")";
                    int durW = fm.stringWidth(dstr);
                    int durX = width - 5 - durW;
                    int avail = durX - x - 6;
                    g2d.setColor(colorText);
                    drawEllipsis(g2d, title, x, Math.max(10, avail), textY);
                    g2d.setColor(colorDuration);
                    g2d.drawString(dstr, durX, textY);
                } else {
                    g2d.setColor(colorText);
                    g2d.drawString(title, x, textY);
                }
            } else {
                g2d.setColor(colorText);
                g2d.drawString(text, 5, textY);
            }
        }
    }

    /** 若文本超出 maxW 宽度，截断并追加省略号 */
    private void drawEllipsis(Graphics2D g2d, String text, int x, int maxW, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        if (fm.stringWidth(text) <= maxW) {
            g2d.drawString(text, x, y);
            return;
        }
        String ell = "…";
        int ellW = fm.stringWidth(ell);
        StringBuilder sb = new StringBuilder();
        int used = ellW;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int cw = fm.charWidth(ch);
            if (used + cw > maxW) break;
            sb.append(ch);
            used += cw;
        }
        sb.append(ell);
        g2d.drawString(sb.toString(), x, y);
    }

    // 以下是为了模拟JList接口的方法，方便替换
    public void setCellRenderer(Object renderer) {
        // 不做什么，保持接口兼容
    }

    public void setForeground(Color fg) {
        this.colorText = fg;
        repaint();
    }

    public void setSelectionBackground(Color bg) {
        this.colorSelect = bg;
        repaint();
    }

    public void setSelectionForeground(Color fg) {
        this.colorHilight = fg;
        repaint();
    }

    public void setFont(Font font) {
        super.setFont(font);
        if (font != null) {
            setListFont(font);
        }
    }
}
