package org.ttplayer.ui;


import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.LRCLine;
import org.ttplayer.lyrics.LRCParser;
import org.ttplayer.lyrics.LyricChar;
import org.ttplayer.model.Song;
import org.ttplayer.util.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 桌面歌词窗口 - 透明无边框，在桌面显示单行歌词
 */
public class DesktopLyricWindow extends JWindow {

    private PlayerEngine playerEngine;
    private List<LRCLine> lines;
    private int currentLineIndex = -1;
    private long adjustedPositionMs = 0;

    // 歌词样式
    private Color textColor = new Color(255, 255, 255, 200);
    private Color highlightColor = new Color(0, 255, 128);
    private float bgAlpha = 0.3f;
    private Font lyricFont;

    /**
     * 依据歌词文本实际语言自动选择能覆盖全部字形的字体，
     * 韩文/日文歌词不出现豆腐块。
     */
    private void adjustFontToLyrics() {
        if (lines == null || lines.isEmpty()) return;
        StringBuilder all = new StringBuilder();
        for (org.ttplayer.lyrics.LRCLine line : lines) {
            if (line.getText() != null) all.append(line.getText()).append('\n');
        }
        int size = lyricFont != null ? lyricFont.getSize() : 24;
        lyricFont = org.ttplayer.util.FontUtils.getLyricFontByText(all.toString(), null, Font.PLAIN, size);
    }

    // 拖动相关
    private Point dragStartScreen;
    private Point dragStartWindow;

    // 更新定时器
    private Timer updateTimer;

    // 单行/双行模式
    private boolean singleLine = true;

    public DesktopLyricWindow(PlayerEngine engine) {
        this.playerEngine = engine;
        lyricFont = FontUtils.getLyricFont(null, Font.PLAIN, 24);
        initUI();
        startUpdateTimer();
    }

    private void initUI() {
        setSize(600, 80);
        setAlwaysOnTop(true);
        setFocusableWindowState(false);


        setBackground(new Color(0, 0, 0, 0));

        JPanel panel = createPanel();
        setContentPane(panel);

        // 拖动支持
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartScreen = e.getLocationOnScreen();
                dragStartWindow = DesktopLyricWindow.this.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartScreen != null && dragStartWindow != null) {
                    Point current = e.getLocationOnScreen();
                    int dx = current.x - dragStartScreen.x;
                    int dy = current.y - dragStartScreen.y;
                    DesktopLyricWindow.this.setLocation(dragStartWindow.x + dx, dragStartWindow.y + dy);
                }
            }
        };
        panel.addMouseListener(dragAdapter);
        panel.addMouseMotionListener(dragAdapter);

        // 右键菜单
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
        });

        // 定位到屏幕底部中间
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, screen.height - getHeight() - 60);
    }


    private JPanel createPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // 半透明背景
                g2.setColor(new Color(0, 0, 0, (int) (bgAlpha * 255)));
                g2.fillRoundRect(4, 4, w - 8, h - 8, 16, 16);

                // 绘制歌词
                drawLyrics(g2, w, h);

                g2.dispose();
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private void showPopup(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem closeItem = org.ttplayer.util.UIUtils.createMenuItem(
            org.ttplayer.util.Messages.get("desktopLyric.close"),
            org.ttplayer.util.MenuIcons.exit(),
            ev -> setVisible(false));
        menu.add(closeItem);

        JMenuItem singleItem = org.ttplayer.util.UIUtils.createMenuItem(
            singleLine ? org.ttplayer.util.Messages.get("desktopLyric.twoLine") : org.ttplayer.util.Messages.get("desktopLyric.oneLine"),
            org.ttplayer.util.MenuIcons.lyric(),
            ev -> {
                singleLine = !singleLine;
                int newH = singleLine ? 80 : 120;
                setSize(getWidth(), newH);
            });
        menu.add(singleItem);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void drawLyrics(Graphics2D g2, int w, int h) {
        if (lines == null || lines.isEmpty() || currentLineIndex < 0) {
            // 没有歌词时显示提示
            g2.setColor(textColor);
            g2.setFont(lyricFont);
            FontMetrics fm = g2.getFontMetrics();
            String msg = org.ttplayer.util.Messages.get("lyric.none");
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2 + fm.getAscent() / 2);
            return;
        }

        // 当前行
        LRCLine currentLine = lines.get(currentLineIndex);

        if (singleLine) {
            drawSingleLine(g2, currentLine, w, h);
        } else {
            // 双行模式：显示当前行和下一行
            LRCLine nextLine = (currentLineIndex + 1 < lines.size()) ? lines.get(currentLineIndex + 1) : null;
            drawDualLines(g2, currentLine, nextLine, w, h);
        }
    }

    private void drawSingleLine(Graphics2D g2, LRCLine currentLine, int w, int h) {
        if (currentLine == null) return;

        String text = currentLine.getText();
        g2.setFont(lyricFont);
        FontMetrics fm = g2.getFontMetrics();

        if (currentLine.hasChars()) {
            // 逐字变色
            long lineStart = currentLine.getTimeMs();
            long pos = adjustedPositionMs - lineStart;
            List<LyricChar> chars = currentLine.getChars();

            int totalW = fm.stringWidth(text);
            int x = (w - totalW) / 2;
            int y = h / 2 + fm.getAscent() / 2;

            for (LyricChar lc : chars) {
                String s = String.valueOf(lc.ch);
                int cw = fm.stringWidth(s);
                if (lc.startMs < pos) {
                    g2.setColor(highlightColor);
                } else if (lc.startMs < pos + 200) {
                    float t = Math.max(0f, Math.min(1f, (float) (pos - lc.startMs) / 200f));
                    g2.setColor(blend(highlightColor, textColor, t));
                } else {
                    g2.setColor(textColor);
                }
                g2.drawString(s, x, y);
                x += cw;
            }
        } else {
            // 无逐字时间信息，整体绘制
            int x = (w - fm.stringWidth(text)) / 2;
            int y = h / 2 + fm.getAscent() / 2;
            g2.setColor(highlightColor);
            g2.drawString(text, x, y);
        }
    }

    private void drawDualLines(Graphics2D g2, LRCLine currentLine, LRCLine nextLine, int w, int h) {
        g2.setFont(lyricFont);
        FontMetrics fm = g2.getFontMetrics();

        // 当前行 - 高亮，在上面
        if (currentLine != null) {
            String text = currentLine.getText();
            int x = (w - fm.stringWidth(text)) / 2;
            int y = h / 3 + fm.getAscent() / 2;
            g2.setColor(highlightColor);
            g2.drawString(text, x, y);
        }

        // 下一行 - 暗色，在下面
        if (nextLine != null) {
            String text = nextLine.getText();
            int x = (w - fm.stringWidth(text)) / 2;
            int y = h * 2 / 3 + fm.getAscent() / 2;
            g2.setColor(new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 120));
            g2.drawString(text, x, y);
        }
    }

    /**
     * 加载新的歌词
     */
    public void loadLyrics(Song song) {
        if (song == null || song.filePath == null) {
            this.lines = null;
            this.currentLineIndex = -1;
            repaint();
            return;
        }

        File audioFile = new File(song.filePath);
        File lrcFile = LRCParser.findLRCFile(audioFile);
        if (lrcFile != null) {
            try {
                LRCParser parser = new LRCParser();
                LRCParser.LRCData data = parser.parse(lrcFile);
                this.lines = data.lines;
                this.currentLineIndex = -1;
                adjustFontToLyrics(); // 按歌词内容语言自动选字体
                repaint();
                return;
            } catch (IOException ignored) {
            }
        }
        this.lines = null;
        this.currentLineIndex = -1;
        repaint();
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(50, e -> {
            if (playerEngine != null && playerEngine.isPlaying()) {
                setCurrentTime(playerEngine.getPositionMs());
            }
        });
        updateTimer.start();
    }

    private void setCurrentTime(long positionMs) {
        if (lines == null || lines.isEmpty() || !isVisible()) return;

        adjustedPositionMs = positionMs;
        int newIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            LRCLine line = lines.get(i);
            long nextTime = (i + 1 < lines.size()) ? lines.get(i + 1).getTimeMs() : Long.MAX_VALUE;
            if (line.getTimeMs() <= adjustedPositionMs && adjustedPositionMs < nextTime) {
                newIndex = i;
                break;
            }
        }
        if (newIndex == -1 && !lines.isEmpty() && adjustedPositionMs >= lines.get(lines.size() - 1).getTimeMs()) {
            newIndex = lines.size() - 1;
        }

        if (newIndex != currentLineIndex) {
            currentLineIndex = newIndex;
            repaint();
        } else if (newIndex >= 0 && lines.get(newIndex).hasChars()) {
            repaint(); // 逐字更新需要持续重绘
        }
    }

    private Color blend(Color a, Color b, float t) {
        return getColor(a, b, t);
    }


    static Color getColor(Color a, Color b, float t) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }
}
