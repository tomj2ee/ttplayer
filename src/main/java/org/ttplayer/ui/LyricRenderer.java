package org.ttplayer.ui;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.LRCLine;
import org.ttplayer.util.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * 歌词渲染 
 *
 * 恒居中模型：当前演唱行始终保持在歌词区中央（center_line = current_line），
 * 行距随区域高度动态调整，距中心越远透明度越低；没有手动滚动状态。
 * 拖动歌词区 = 拖拽预览（中心参考线 + 时间徽标随鼠标移动）→ 松手 seek 并立即同步。
 */
public class LyricRenderer extends JPanel {

    private final LyricWindow lyricWindow;

    private List<LRCLine> lines;
    private long playbackTimeMs;
    private int currentLineIndex = -1;
    private boolean loading = false;


    public Color textColor = new Color(160, 190, 220);       // TextColor：普通歌词
    public Color highlightColor = new Color(255, 220, 120);  // HilightColor：当前行
    public Color bgColor = new Color(0, 0, 0);               // BkgndColor：歌词区背景
    public Color wordColor = Color.WHITE;                    // (可选)已唱部分颜色，皮肤可配 HilightWordColor
    public boolean karaokeEnabled = true;
    public Font lyricFont = org.ttplayer.util.FontUtils.getLyricFont(null, Font.PLAIN, 14);
    public Font currentFont = org.ttplayer.util.FontUtils.getLyricFont(null, Font.BOLD, 14);

    // ---- 拖拽预览状态（对应 C lyric_window_events.c） ----
    private boolean dragging;
    private int dragAnchorY;
    private float dragBaseLine;
    private float dragDeltaPx;
    private int dragTargetIndex = -1;
    private long dragTargetTime;

    /** 鼠标滚轮偏移 */
    private float scrollOffset;

    private Timer updateTimer;

    public LyricRenderer(LyricWindow lyricWindow) {
        this.lyricWindow = lyricWindow;

        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // 内部拖拽语义：SnapUtils 检测到后不作为窗口拖动
        putClientProperty("innerDrag", Boolean.TRUE);

        MouseAdapter handler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                    return;
                }
                if (e.getButton() == MouseEvent.BUTTON1 && hasLyrics()) {
                    dragging = true;
                    dragAnchorY = e.getY();
                    dragBaseLine = currentLineIndex;
                    dragDeltaPx = 0f;
                    dragTargetIndex = currentLineIndex;
                    dragTargetTime = currentLineTime();
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    updateDragTarget(e.getY());
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                    return;
                }
                if (dragging) {
                    dragging = false;
                    commitDrag();
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                scrollOffset += e.getWheelRotation() * 5f; // 对应 C
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);

        updateTimer = new Timer(50, e -> {
            PlayerEngine eng = lyricWindow.playerEngine;
            if (eng != null && !dragging) {
                setCurrentTime(eng.getPositionMs());
            }
        });
        updateTimer.start();
    }

    private boolean hasLyrics() { return lines != null && !lines.isEmpty(); }

    private long currentLineTime() {
        if (currentLineIndex >= 0 && currentLineIndex < lines.size()) {
            return lines.get(currentLineIndex).getTimeMs();
        }
        return 0;
    }

    private void updateDragTarget(int y) {
        if (!hasLyrics()) return;
        int lh = lineHeight();
        dragDeltaPx = y - dragAnchorY;
        float frac = dragBaseLine - dragDeltaPx / (float) lh;
        int target = (int) (frac + (frac >= 0 ? 0.5f : -0.5f));
        if (target < 0) target = 0;
        if (target >= lines.size()) target = lines.size() - 1;
        dragTargetIndex = target;
        dragTargetTime = lines.get(target).getTimeMs();
    }

    private void commitDrag() {
        if (!hasLyrics() || dragTargetIndex < 0) return;
        PlayerEngine eng = lyricWindow.playerEngine;
        if (eng != null) {
            // 释放歌词后 seek 到目标行时间点
            eng.seekTo((int) (dragTargetTime / 1000));
        }
        // 本地立即同步：高亮不等待下一轮轮询
        playbackTimeMs = dragTargetTime;
        currentLineIndex = dragTargetIndex;
    }

    private void showMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem searchItem = org.ttplayer.util.UIUtils.createMenuItem(
                Messages.get("lyric.searchOnline"),
                org.ttplayer.util.MenuIcons.search(),
                ev -> lyricWindow.searchLyricsOnline());
        menu.add(searchItem);

        JMenuItem karaokeItem = new JCheckBoxMenuItem("卡拉OK 逐字高亮", karaokeEnabled);
        karaokeItem.addActionListener(ev -> {
            karaokeEnabled = karaokeItem.isSelected();
            repaint();
        });
        menu.add(karaokeItem);
        menu.show(this, e.getX(), e.getY());
    }

    // ==================== 对外接口 ====================

    public void setLyrics(List<LRCLine> newLines) {
        this.lines = newLines;
        this.currentLineIndex = -1;
        this.playbackTimeMs = 0;
        this.loading = false;
        this.dragging = false;
        repaint();
    }

    public void setLoading(boolean l) { this.loading = l; repaint(); }

    /**
     * 依据歌词文本实际语言（中文/韩文/日文/混合）自动选择能覆盖全部字形的字体。
     * 皮肤未指定字体或指定字体能力不足时，也能保证谚文/假名等不出现豆腐块。
     */
    public void adjustFontToLyrics() {
        if (lines == null || lines.isEmpty()) return;
        StringBuilder all = new StringBuilder();
        for (LRCLine line : lines) {
            if (line.getText() != null) all.append(line.getText()).append('\n');
        }
        int size = lyricFont != null ? lyricFont.getSize() : 14;
        String pref = lyricFont != null ? lyricFont.getFamily() : null;
        Font base = org.ttplayer.util.FontUtils.getLyricFontByText(all.toString(), pref, Font.PLAIN, size);
        lyricFont = base.deriveFont(Font.PLAIN, size);
        currentFont = base.deriveFont(Font.BOLD, size + 2);
        repaint();
    }

    /** 外部或 50ms 定时器调用：根据播放时间定位当前行（对应 C lyric_window_update） */
    public void setCurrentTime(long positionMs) {
        playbackTimeMs = positionMs;
        if (!hasLyrics()) return;

        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (positionMs >= lines.get(i).getTimeMs()) idx = i;
        }
        if (idx != currentLineIndex) {
            currentLineIndex = idx;
        }
        repaint();
    }

    // ==================== 渲染（对应 C lyric_window_render.c） ====================

    /** 固定歌词行距（像素），不随窗口高度变化 */
    private static final int LINE_HEIGHT = 26;

    private int lineHeight() {
        return LINE_HEIGHT;
    }

    /** 当前歌词字体的实际行高（getHeight），若字体为 null 则返回默认 20 */
    private int measuredLineHeight() {
        try {
            Font f = lyricFont != null ? lyricFont : new Font("Dialog", Font.PLAIN, 14);
            FontMetrics fm = getFontMetrics(f);
            int h = fm.getHeight();
            return h > 0 ? h : 20;
        } catch (Exception e) {
            return 20;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 歌词区背景：半透明 BkgndColor（alpha≈180 / 255）
        g2.setColor(withAlpha(bgColor, 0.70f));
        g2.fillRect(0, 0, w, h);

        int fs = 14;
        // 固定行距，但与「实际字高」取较大者，避免换大字体后行间重叠
        int lh = Math.max(lineHeight(), measuredLineHeight());
        int centerY = h / 2;

        // 卡拉OK已唱部分颜色：若与高亮色相同则自动派生对比色，保证已唱/未唱可区分
        Color sungColor = effectiveKaraokeColor();

        // 空歌词提示（加载中动画 / 暂无歌词）
        if (!hasLyrics()) {
            g2.setFont(lyricFont);
            g2.setColor(new Color(120, 130, 150));
            String msg;
            if (loading) {
                int dots = (int) ((System.currentTimeMillis() / 350) % 4);
                StringBuilder sb = new StringBuilder(Messages.get("lyric.loading"));
                for (int i = 0; i < dots; i++) sb.append('.');
                msg = sb.toString();
            } else {
                msg = Messages.get("lyric.none");
            }
            drawCenteredText(g2, msg, centerY);
            g2.dispose();
            return;
        }

        // 恒居中：center_line 为当前行；拖动预览时为鼠标推算的浮点行
        float centerLine = dragging
                ? dragBaseLine - dragDeltaPx / (float) lh
                : (float) currentLineIndex;
        int highlightLine = dragging ? dragTargetIndex : currentLineIndex;

        for (int i = 0; i < lines.size(); i++) {
            float offset = i - centerLine;
            int y = centerY + (int) (offset * lh);
            if (y < -fs || y > h + fs) continue;

            // 距中心越远越透明（对应 C：alpha = 1 - |offset|*0.22，下限 0.15）
            float dist = Math.abs(offset);
            float alpha = 1.0f - dist * 0.22f;
            if (alpha < 0.15f) alpha = 0.15f;

            LRCLine line = lines.get(i);
            // 空歌词条目（[00:10] 无文本）只保留计时作用，不绘制
            if (line.getText() == null || line.getText().isEmpty()) continue;
            boolean isHL = (i == highlightLine);

            // 当前行用 currentFont（粗体/稍大），其余行用 lyricFont
            Font baseFont = (isHL && currentFont != null) ? currentFont : lyricFont;
            if (baseFont == null) baseFont = lyricFont;
            g2.setFont(baseFont);
            FontMetrics fm = g2.getFontMetrics();
            // 实际字体度量：行高用于垂直居中/裁剪依据，超长行可横向缩字号防溢出窗口边界
            int th = fm.getHeight();
            int wrappedW = getWidth();
            Font lineFont = baseFont;
            if (fm.stringWidth(line.getText()) > wrappedW) {
                // 行太宽（如长韩文标题）→ 降字号到宽度内
                int shrink = Math.max(9, baseFont.getSize() - 4);
                lineFont = baseFont.deriveFont((float) shrink);
                fm = g2.getFontMetrics(lineFont);
                g2.setFont(lineFont);
            }
            String text = line.getText();
            int tw = fm.stringWidth(text);
            int baseline = y + (lh - th) / 2 + fm.getAscent();
            int tx = (w - tw) / 2;
            // 文本不越过窗口左右边界的裁剪
            g2.setClip(0, 0, w, h);

            // 当前行加柔和阴影，突出层次
            if (isHL) {
                g2.setColor(new Color(0, 0, 0, (int) (60 * alpha)));
                g2.drawString(text, tx + 1, baseline + 1);
                g2.drawString(text, tx + 1, baseline + 2);
            }

            if (isHL && karaokeEnabled) {
                // 逐字高亮：已唱部分白色、未唱部分高亮色（对应 C karaoke）
                float start = lines.get(i).getTimeMs();
                float end = (i + 1 < lines.size()) ? lines.get(i + 1).getTimeMs() : start + 5000f;
                float t = dragging ? dragTargetTime : (float) playbackTimeMs;
                float prog = (end > start) ? (t - start) / (end - start) : 1f;
                if (prog < 0) prog = 0;
                if (prog > 1) prog = 1;

                g2.setColor(withAlpha(highlightColor, alpha));
                g2.drawString(text, tx, baseline);

                if (prog > 0 && tw > 0) {
                    int shot = (int) (tw * prog);
                    // 卡拉OK 已唱部分：仅对已唱宽度高亮，仍受窗口边界裁剪
                    g2.setColor(withAlpha(sungColor, alpha));
                    g2.setClip(Math.max(0, tx), baseline - fm.getAscent(), Math.min(shot, w - Math.max(0, tx)), th);
                    g2.drawString(text, tx, baseline);
                    g2.setClip(0, 0, w, h);
                }
            } else {
                g2.setColor(isHL ? withAlpha(highlightColor, alpha) : withAlpha(textColor, alpha));
                g2.drawString(text, tx, baseline);
            }
        }

        // 拖动预览：中心参考线 + 时间徽标（对应 C render step 5）
        if (dragging && hasLyrics()) {
            g2.setColor(new Color(255, 255, 255, 110));
            g2.drawLine(0, centerY, w, centerY);

            String timeStr = formatTime(dragTargetTime);
            g2.setFont(new Font("Dialog", Font.PLAIN, 12));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(timeStr);
            int th = fm.getHeight();
            int padX = 8, padY = 3;
            int bw = tw + padX * 2, bh = th + padY * 2;
            int bx = (w - bw) / 2;
            int by = centerY - bh / 2;

            g2.setColor(new Color(0, 0, 0, 45));
            g2.fill(new RoundRectangle2D.Float(bx + 1.5f, by + 2, bw, bh, 9, 9));
            g2.setColor(new Color(231, 231, 235));
            g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 9, 9));
            g2.setColor(new Color(255, 255, 255, 240));
            g2.fill(new RoundRectangle2D.Float(bx + 1, by + 1, bw - 2, bh - 2, 8, 8));
            g2.setColor(new Color(29, 29, 31));
            g2.drawString(timeStr, bx + padX, by + padY + fm.getAscent());
        }

        // 底部歌词进度条（拖动预览期间隐藏，对应 C render step 6）
        if (hasLyrics() && !dragging) {
            float lastTime = lines.get(lines.size() - 1).getTimeMs();
            float total = lastTime + 5000f;
            float progress = total > 0 ? ((float) playbackTimeMs) / total : 0;
            if (progress > 1) progress = 1;
            g2.setColor(new Color(100, 150, 200, 180));
            g2.fillRect(0, h - 4, (int) (w * progress), 3);
        }

        g2.dispose();
    }

    private void drawCenteredText(Graphics2D g2, String text, int centerY) {
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int baseline = centerY + fm.getAscent() / 2;
        g2.drawString(text, x, baseline);
    }

    private static Color withAlpha(Color c, float a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                Math.round(Math.max(0, Math.min(1, a)) * 255));
    }

    /**
     * 卡拉OK已唱部分颜色：wordColor 与 highlightColor 相同时（很多皮肤只配了 HilightColor 没配
     * HilightWordColor），自动按亮度派生一个对比色，否则已唱/未唱无法区分。
     */
    private Color effectiveKaraokeColor() {
        if (wordColor != null && !wordColor.equals(highlightColor)) return wordColor;
        Color base = (highlightColor != null) ? highlightColor : Color.WHITE;
        double lum = 0.299 * base.getRed() + 0.587 * base.getGreen() + 0.114 * base.getBlue();
        int delta = 150;
        if (lum > 150) {
            return new Color(Math.max(0, base.getRed() - delta),
                    Math.max(0, base.getGreen() - delta),
                    Math.max(0, base.getBlue() - delta));
        } else {
            return new Color(Math.min(255, base.getRed() + delta),
                    Math.min(255, base.getGreen() + delta),
                    Math.min(255, base.getBlue() + delta));
        }
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        return String.format("%02d:%02d", (s / 60) % 60, s % 60);
    }
}