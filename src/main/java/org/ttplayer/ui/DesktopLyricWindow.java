package org.ttplayer.ui;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.*;
import org.ttplayer.model.Song;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.List;

public class DesktopLyricWindow extends JWindow {
    private final PlayerEngine playerEngine;
    private List<LRCLine> lines;
    private int currentLineIndex = -1;
    private long playbackTimeMs, offsetMs;
    private Font lyricFont;
    private LyricSettings.Style style;
    private LyricWindow source;
    private final Timer updateTimer, saveTimer;
    private boolean singleLine, locked, hovered;
    private TtMenuCatalog.Actions menuActions;

    public DesktopLyricWindow(PlayerEngine engine) {
        playerEngine = engine;
        singleLine = !MenuSettings.flag("desktopDual", false);
        locked = MenuSettings.flag("desktopLocked", false);
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setBackground(new Color(0, true));
        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) { paintLyrics((Graphics2D) graphics); }
        };
        panel.setOpaque(false);
        setContentPane(panel);
        setSize(LyricSettings.clamp(MenuSettings.number("desktop.width", 600), 96, 4096),
                LyricSettings.clamp(MenuSettings.number("desktop.height", singleLine ? 80 : 120), 24, 1080));
        Rectangle area = MiniLyricLayout.usableBounds(this);
        int horizontal = MenuSettings.number("desktop.x", area.x + (area.width - getWidth()) / 2);
        int vertical = MenuSettings.number("desktop.y", area.y + area.height - getHeight() - 60);
        area = MiniLyricLayout.usableBounds(new Rectangle(horizontal, vertical, getWidth(), getHeight()));
        setLocation(Math.max(area.x, Math.min(horizontal, area.x + area.width - getWidth())),
                Math.max(area.y, Math.min(vertical, area.y + area.height - getHeight())));
        refreshSettings();
        MouseAdapter pointer = new MouseAdapter() {
            private Point start, origin;
            private Dimension size;
            private boolean resizing;
            @Override public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) { showPopup(event); return; }
                if (locked || !SwingUtilities.isLeftMouseButton(event)) return;
                start = event.getLocationOnScreen(); origin = getLocation(); size = getSize();
                resizing = event.getX() >= getWidth() - 12 && event.getY() >= getHeight() - 12;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (locked || start == null) return;
                Point pointer = event.getLocationOnScreen();
                if (resizing) setSize(LyricSettings.clamp(size.width + pointer.x - start.x, 96, 4096),
                        LyricSettings.clamp(size.height + pointer.y - start.y, 24, 1080));
                else setLocation(origin.x + pointer.x - start.x, origin.y + pointer.y - start.y);
            }
            @Override public void mouseReleased(MouseEvent event) {
                start = null; origin = null;
                if (event.isPopupTrigger()) showPopup(event);
            }
            @Override public void mouseEntered(MouseEvent event) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent event) { hovered = false; repaint(); }
            @Override public void mouseMoved(MouseEvent event) {
                panel.setCursor(Cursor.getPredefinedCursor(!locked && event.getX() >= getWidth() - 12 &&
                        event.getY() >= getHeight() - 12 ? Cursor.SE_RESIZE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        panel.addMouseListener(pointer);
        panel.addMouseMotionListener(pointer);
        updateTimer = new Timer(25, event -> {
            if (playerEngine != null) setCurrentTime(playerEngine.getPositionMs());
        });
        saveTimer = new Timer(400, event -> {
            MenuSettings.set("desktop.x", getX()); MenuSettings.set("desktop.y", getY());
            MenuSettings.set("desktop.width", getWidth()); MenuSettings.set("desktop.height", getHeight());
        });
        saveTimer.setRepeats(false);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { updateTimer.start(); NativeWindowSupport.setClickThrough(DesktopLyricWindow.this, locked); }
            @Override public void componentHidden(ComponentEvent event) { updateTimer.stop(); }
            @Override public void componentMoved(ComponentEvent event) { saveTimer.restart(); }
            @Override public void componentResized(ComponentEvent event) { saveTimer.restart(); }
        });
    }

    public void setMenuActions(TtMenuCatalog.Actions actions) { menuActions = actions; }
    public void setLyricSource(LyricWindow window) { source = window; refreshSettings(); }
    public boolean isLocked() { return locked; }
    public boolean isSingleLine() { return singleLine; }
    public void toggleLocked() {
        locked = !locked;
        hovered = false;
        MenuSettings.set("desktopLocked", locked);
        NativeWindowSupport.setClickThrough(this, locked);
        if (!locked && isVisible()) toFront();
        repaint();
    }
    public void setSingleLine(boolean value) {
        if (singleLine == value) return;
        singleLine = value;
        setSize(getWidth(), value ? 80 : 120);
        MenuSettings.set("desktopDual", !value);
        repaint();
    }
    public void toggleLines() { setSingleLine(!singleLine); }
    public void setLyrics(List<LRCLine> lyrics) {
        lines = lyrics;
        currentLineIndex = -1;
        adjustFont();
        if (playerEngine != null) setCurrentTime(playerEngine.getPositionMs());
        repaint();
    }
    public void setOffsetMs(long value) { offsetMs = value; setCurrentTime(playbackTimeMs); }
    public long getOffsetMs() { return offsetMs; }
    public int getCurrentLineIndex() { return currentLineIndex; }
    public void setCurrentTime(long positionMs) {
        playbackTimeMs = positionMs;
        currentLineIndex = LyricTimeline.active(lines, positionMs, offsetMs);
        repaint();
    }

    public void refreshSettings() {
        style = source == null ? LyricSettings.load().desktop : source.getEffectiveStyle(true);
        lyricFont = source == null ? FontUtils.getLyricFont(style.fontFamily(), Font.PLAIN, style.fontSize) : source.getEffectiveFont(true);
        setSingleLine(!MenuSettings.flag("desktopDual", false));
        adjustFont();
        repaint();
    }
    private void adjustFont() {
        if (lyricFont == null) return;
        StringBuilder text = new StringBuilder(Messages.get("lyric.none")).append('\n');
        if (lines != null) for (LRCLine line : lines) text.append(line.getText()).append('\n');
        lyricFont = FontUtils.getLyricFontByText(text.toString(), lyricFont.getFamily(), Font.PLAIN, lyricFont.getSize());
    }

    private void paintLyrics(Graphics2D graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setComposite(AlphaComposite.Clear);
        canvas.fillRect(0, 0, getWidth(), getHeight());
        canvas.setComposite(AlphaComposite.SrcOver);
        if (!locked) {
            canvas.setColor(new Color(0, 0, 0, 1));
            canvas.fillRect(0, 0, getWidth(), getHeight());
        }
        if (!style.transparent && (style.backgroundOpacity > 0 || hovered && !locked)) {
            canvas.setColor(LyricTextPainter.alpha(style.background, style.backgroundOpacity > 0 ? style.backgroundOpacity / 100f : 0.3f));
            canvas.fillRoundRect(4, 4, Math.max(1, getWidth() - 8), Math.max(1, getHeight() - 8), 16, 16);
        }
        float rowHeight = getHeight() / (singleLine ? 1f : 2f);
        Font font = lyricFont;
        while (font.getSize() > 8 && canvas.getFontMetrics(font).getHeight() > rowHeight - 4)
            font = font.deriveFont((float) (font.getSize() - 1));
        if (currentLineIndex < 0 || lines == null || lines.isEmpty()) {
            LyricTextPainter.paint(canvas, Messages.get("lyric.none"), new Rectangle2D.Float(12, 0, getWidth() - 24, getHeight()),
                    font, style.normal, style.word, 0, style);
        } else {
            boolean karaoke = MenuSettings.flag("karaoke", true);
            long duration = playerEngine == null ? 0 : playerEngine.getDuration() * 1000L;
            float progress = karaoke ? LyricTimeline.progress(lines, currentLineIndex, playbackTimeMs, duration, offsetMs) : 0;
            LyricTextPainter.paint(canvas, lines.get(currentLineIndex).getText(),
                    new Rectangle2D.Float(12, 0, getWidth() - 24, rowHeight), font,
                    karaoke ? style.normal : style.highlight, style.word, progress, style);
            if (!singleLine && currentLineIndex + 1 < lines.size())
                LyricTextPainter.paint(canvas, lines.get(currentLineIndex + 1).getText(),
                        new Rectangle2D.Float(12, rowHeight, getWidth() - 24, rowHeight), font, style.normal, style.word, 0, style);
        }
        canvas.dispose();
    }

    private void showPopup(MouseEvent event) {
        if (locked) return;
        TtMenuCatalog.Actions actions = menuActions == null ? new TtMenuCatalog.Actions() {
            public void execute(String action) {
                if ("desktop_lines".equals(action)) toggleLines();
                else if ("lock".equals(action)) toggleLocked();
                else if ("desktop".equals(action)) setVisible(false);
                else if ("lyric_search".equals(action) && source != null) source.searchLyricsOnline();
            }
            public boolean checked(String action) { return "lock".equals(action) ? locked : "desktop_dual".equals(action) && !singleLine; }
        } : menuActions;
        TtMenuCatalog.create(TtMenuCatalog.Kind.DESKTOP, actions).show(event.getComponent(), event.getX(), event.getY());
    }

    public void loadLyrics(Song song) {
        if (source != null) { setLyrics(source.getRenderer().getLyrics()); setOffsetMs(source.getOffsetMs()); return; }
        if (song != null && song.filePath != null && MenuSettings.flag("lyric.autoLoad", true)) {
            File local = LyricsRepository.find(song);
            if (local != null) try {
                LRCParser.LRCData data = new LRCParser().parse(local, MenuSettings.flag("lyric.trim", false));
                setLyrics(data.lines); setOffsetMs(data.offsetMs); return;
            } catch (IOException ignored) {}
        }
        setLyrics(null); setOffsetMs(0);
    }

    @Override public void addNotify() { super.addNotify(); NativeWindowSupport.setClickThrough(this, locked); }
    @Override public void removeNotify() { if (updateTimer != null) updateTimer.stop(); super.removeNotify(); }
    @Override public void dispose() { updateTimer.stop(); saveTimer.stop(); super.dispose(); }
}
