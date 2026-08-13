package org.ttplayer.controls;

import org.ttplayer.ui.SkinWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class TtButton extends JComponent {

    public enum State { NORMAL, HOVER, PRESSED, DISABLED }

    private final BufferedImage[] frames = new BufferedImage[4];
    private State state = State.NORMAL;
    private boolean enabled = true;
    private boolean selected = false;   // 选中驻留态（用于启用开关），显示按下帧
    private boolean rollover;
    private final List<ActionListener> listeners = new ArrayList<>();

    public TtButton(byte[] bmpData, int left, int top, int right, int bottom) {
        this(bmpData, left, top, right, bottom, new Color(255, 0, 255));
    }

    public TtButton(byte[] bmpData, int left, int top, int right, int bottom, Color transp) {
        // 先尝试解码图片
        BufferedImage src = SkinWindow.decodeBmp(bmpData, transp);
        int btnW, btnH;

        if (src != null) {
            // 原图宽度分成4份作为每帧宽度，图片高度作为每帧高度
            int frameW = src.getWidth() / 4;
            int frameH = src.getHeight();
            btnW = frameW;
            btnH = frameH;

            for (int i = 0; i < 4; i++) {
                frames[i] = src.getSubimage(i * frameW, 0, frameW, frameH);
            }
        } else {
            // 如果没有图片，才使用传入的参数
            btnW = right - left;
            btnH = bottom - top;
        }

        setPreferredSize(new Dimension(btnW, btnH));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                rollover = true;
                if (enabled) { state = State.HOVER; repaint(); }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                rollover = false;
                if (enabled) { state = State.NORMAL; repaint(); }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (enabled) { state = State.PRESSED; repaint(); }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (enabled) {
                    state = rollover ? State.HOVER : State.NORMAL;
                    repaint();
                    if (rollover) fireActionPerformed();
                }
            }
        });
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    public void removeActionListener(ActionListener l) {
        listeners.remove(l);
    }

    private void fireActionPerformed() {
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "click");
        for (ActionListener l : listeners) l.actionPerformed(e);
    }

    public void doClick() {
        fireActionPerformed();
    }

    @Override
    public void setEnabled(boolean e) {
        super.setEnabled(e);
        enabled = e;
        state = e ? State.NORMAL : State.DISABLED;
        repaint();
    }

    /** 设置选中态（启用开关），选中时驻留显示按下帧 */
    public void setSelected(boolean sel) {
        this.selected = sel;
        repaint();
    }

    public boolean isSelected() { return selected; }

    @Override
    protected void paintComponent(Graphics g) {
        BufferedImage frame = frames[selected && enabled ? State.PRESSED.ordinal() : state.ordinal()];
        if (frame != null) {
            g.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
