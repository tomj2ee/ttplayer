package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表工具栏，包含7个带菜单的按钮
 */
public class TtToolbar extends JComponent {

    public static class ToolbarButton {
        public int index;
        public String name;
        public JPopupMenu menu;

        public ToolbarButton(int index, String name) {
            this.index = index;
            this.name = name;
        }

        public void setMenu(JPopupMenu menu) {
            this.menu = menu;
        }
    }

    private BufferedImage[] normalImages;
    private BufferedImage[] hoverImages;
    private final List<ToolbarButton> buttons = new ArrayList<>();
    private int buttonWidth;
    private int buttonHeight;
    private int hoverIndex = -1;
    private int pressedIndex = -1;
    private final List<ActionListener> listeners = new ArrayList<>();

    // hover时的放大比例
    private static final double HOVER_SCALE = 1.1;

    public TtToolbar(byte[] bmpData, int buttonCount) {
        this(bmpData, null, buttonCount);
    }

    public TtToolbar(byte[] bmpData, int buttonCount, Color transp) {
        this(bmpData, null, buttonCount, transp);
    }

    public TtToolbar(byte[] bmpData, byte[] hotBmpData, int buttonCount) {
        this(bmpData, hotBmpData, buttonCount, new Color(255, 0, 255));
    }

    public TtToolbar(byte[] bmpData, byte[] hotBmpData, int buttonCount, Color transp) {
        BufferedImage src = SkinWindow.decodeBmp(bmpData, transp);
        if (src != null) {
            int sw = src.getWidth();
            int sh = src.getHeight();

            // 看是否是多行图片（4行对应4种状态）
            int stateCount = sh > 4 * (sw / buttonCount) ? 4 : 1;

            if (stateCount == 4) {
                this.buttonWidth = sw / buttonCount;
                this.buttonHeight = sh / 4;

                normalImages = new BufferedImage[buttonCount];
                hoverImages = new BufferedImage[buttonCount];
                BufferedImage[] pressedImages = new BufferedImage[buttonCount];
                BufferedImage[] disabledImages = new BufferedImage[buttonCount];

                for (int i = 0; i < buttonCount; i++) {
                    normalImages[i] = src.getSubimage(i * buttonWidth, 0, buttonWidth, buttonHeight);
                    hoverImages[i] = src.getSubimage(i * buttonWidth, buttonHeight, buttonWidth, buttonHeight);
                    pressedImages[i] = src.getSubimage(i * buttonWidth, buttonHeight * 2, buttonWidth, buttonHeight);
                    disabledImages[i] = src.getSubimage(i * buttonWidth, buttonHeight * 3, buttonWidth, buttonHeight);
                }
            } else {
                // 只有一行图片
                this.buttonWidth = sw / buttonCount;
                this.buttonHeight = sh;

                normalImages = new BufferedImage[buttonCount];
                for (int i = 0; i < buttonCount; i++) {
                    normalImages[i] = src.getSubimage(i * buttonWidth, 0, buttonWidth, buttonHeight);
                }

                // 加载单独的 hot_image（如果提供）
                if (hotBmpData != null) {
                    BufferedImage hotSrc = SkinWindow.decodeBmp(hotBmpData, transp);
                    if (hotSrc != null) {
                        hoverImages = new BufferedImage[buttonCount];
                        int hotW = hotSrc.getWidth() / buttonCount;
                        int hotH = hotSrc.getHeight();
                        for (int i = 0; i < buttonCount; i++) {
                            hoverImages[i] = hotSrc.getSubimage(i * hotW, 0, hotW, hotH);
                        }
                    }
                }
            }

            String[] defaultNames = {"add", "del", "sort", "find", "mode", "opt", "menu"};
            for (int i = 0; i < buttonCount; i++) {
                buttons.add(new ToolbarButton(i, i < defaultNames.length ? defaultNames[i] : "btn" + i));
            }

            setPreferredSize(new Dimension(sw, stateCount == 4 ? sh / 4 : sh));
        } else {
            normalImages = new BufferedImage[0];
        }

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                updateHover(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int idx = getButtonIndex(e.getX());
                if (idx >= 0) {
                    pressedIndex = idx;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int idx = getButtonIndex(e.getX());
                if (idx >= 0 && pressedIndex == idx) {
                    fireActionPerformed(idx);
                    showMenu(idx, e.getX(), e.getY());
                }
                pressedIndex = -1;
                repaint();
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                updateHover(e);
            }
        });
    }

    private void updateHover(MouseEvent e) {
        int idx = getButtonIndex(e.getX());
        if (idx != hoverIndex) {
            hoverIndex = idx;
            repaint();
        }
    }

    private int getButtonIndex(int x) {
        if (buttonWidth <= 0) return -1;
        int idx = x / buttonWidth;
        if (idx >= 0 && idx < buttons.size()) return idx;
        return -1;
    }

    private void showMenu(int idx, int x, int y) {
        ToolbarButton btn = buttons.get(idx);
        if (btn.menu != null) {
            btn.menu.show(this, x, y);
        }
    }

    public ToolbarButton getButton(int index) {
        if (index >= 0 && index < buttons.size()) {
            return buttons.get(index);
        }
        return null;
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    public void removeActionListener(ActionListener l) {
        listeners.remove(l);
    }

    private void fireActionPerformed(int buttonIndex) {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                buttons.get(buttonIndex).name);
        for (ActionListener l : listeners) {
            l.actionPerformed(e);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (normalImages == null || normalImages.length == 0) return;

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (int i = 0; i < buttons.size(); i++) {
            BufferedImage img = normalImages[i];
            if (img != null) {
                int x = i * buttonWidth;

                if (i == hoverIndex && pressedIndex != i) {
                    // Hover状态：优先使用单独的 hot_image
                    if (hoverImages != null && i < hoverImages.length && hoverImages[i] != null) {
                        g2d.drawImage(hoverImages[i], x, 0, null);
                    } else {
                        // 没有 hot_image，放大一点作为 hover 效果
                        int scaledW = (int) (buttonWidth * HOVER_SCALE);
                        int scaledH = (int) (buttonHeight * HOVER_SCALE);
                        int offsetX = (scaledW - buttonWidth) / 2;
                        int offsetY = (scaledH - buttonHeight) / 2;
                        g2d.drawImage(img, x - offsetX, -offsetY, scaledW, scaledH, null);
                    }
                } else if (i == pressedIndex) {
                    // Pressed状态，也可以做特殊效果（稍微缩小或调整）
                    g2d.drawImage(img, x, 0, null);
                } else {
                    // 普通状态
                    g2d.drawImage(img, x, 0, null);
                }
            }
        }
    }
}
