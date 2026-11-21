package org.ttplayer.ui;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.LRCLine;
import org.ttplayer.lyrics.LyricSettings;
import org.ttplayer.lyrics.LyricTimeline;
import org.ttplayer.util.FontUtils;
import org.ttplayer.util.MenuSettings;
import org.ttplayer.util.Messages;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;

public class LyricRenderer extends JPanel {
    private final LyricWindow lyricWindow;
    private final Timer updateTimer;
    private final LyricScroll scroll = new LyricScroll();
    private List<LRCLine> lines;
    private long playbackTimeMs, offsetMs;
    private int currentLineIndex = -1;
    private boolean loading, dragging, compact, recordPressed;
    private Point dragAnchor;
    private float previewLine;
    private LyricSettings.Style style = new LyricSettings.Style(false);
    private boolean configured;
    private Font compactBaseFont;
    private Rectangle2D.Float textBounds = new Rectangle2D.Float();
    private Rectangle2D.Float recordStage;
    private Shape recordShape;
    private Point mouse;

    public Color textColor = new Color(0x8bbac6);
    public Color highlightColor = Color.WHITE;
    public Color bgColor = Color.BLACK;
    public Color wordColor = new Color(0xffdc50);
    public boolean karaokeEnabled = true;
    public Font lyricFont = FontUtils.getLyricFont(null, Font.PLAIN, 14);
    public Font currentFont = lyricFont.deriveFont(16f);
    public boolean transparentBackground;
    public boolean showCover = true;
    public TtMenuCatalog.Kind menuKind = TtMenuCatalog.Kind.LYRIC;

    public LyricRenderer(LyricWindow lyricWindow) {
        this.lyricWindow = lyricWindow;
        setOpaque(false);
        setFocusable(true);
        putClientProperty("innerDrag", Boolean.TRUE);
        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                mouse = event.getPoint();
                if (event.isPopupTrigger()) { showMenu(event); return; }
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                requestFocusInWindow();
                updateGeometry();
                if (recordShape != null && recordShape.contains(mouse)) {
                    recordPressed = true;
                } else if (hasLyrics() && seekEnabled() && textBounds.contains(mouse)) {
                    dragging = true;
                    dragAnchor = mouse;
                    previewLine = Math.max(0, currentLineIndex);
                    if (!compact && style.scrollMode != 2)
                        previewLine += (event.getY() - textBounds.getCenterY()) / rowHeight();
                    previewLine = clampLine(previewLine);
                }
                repaint();
            }
            @Override public void mouseDragged(MouseEvent event) {
                mouse = event.getPoint();
                if (dragging && seekEnabled()) {
                    if (compact) panCompact(dragAnchor.x - event.getX());
                    else previewLine = clampLine(previewLine - (event.getY() - dragAnchor.y) / rowHeight());
                    dragAnchor = mouse;
                    repaint();
                }
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) { showMenu(event); return; }
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                if (recordPressed && recordShape != null && recordShape.contains(event.getPoint()))
                    lyricWindow.getMenuActions().execute("play");
                recordPressed = false;
                if (dragging) {
                    if (seekEnabled()) seekLine(Math.round(previewLine));
                    cancelDrag();
                }
                repaint();
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2 && !hasLyrics() &&
                        (recordShape == null || !recordShape.contains(event.getPoint())))
                    lyricWindow.searchLyricsOnline();
            }
            @Override public void mouseMoved(MouseEvent event) {
                mouse = event.getPoint();
                updateGeometry();
                setCursor(Cursor.getPredefinedCursor((recordShape != null && recordShape.contains(mouse)) ||
                        hasLyrics() && seekEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                repaint();
            }
            @Override public void mouseExited(MouseEvent event) { mouse = null; repaint(); }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                if (compact && hasLyrics() && seekEnabled() && !dragging) {
                    int steps = (int) Math.ceil(Math.abs(event.getPreciseWheelRotation()));
                    if (steps != 0) seekLine(Math.max(0, currentLineIndex) +
                            (event.getPreciseWheelRotation() > 0 ? steps : -steps));
                    event.consume();
                }
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
        updateTimer = new Timer(25, event -> {
            PlayerEngine engine = lyricWindow.playerEngine;
            if (engine != null) setCurrentTime(engine.getPositionMs());
            lyricWindow.advanceCoverAnimation();
            repaint();
        });
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) updateTimer.start();
                else { updateTimer.stop(); cancelDrag(); }
            }
        });
        registerKeyboardAction(event -> cancelDrag(), KeyStroke.getKeyStroke("ESCAPE"), WHEN_FOCUSED);
        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) { cancelDrag(); }
        });
    }

    private boolean hasLyrics() { return lines != null && !lines.isEmpty(); }
    private boolean seekEnabled() { return MenuSettings.flag("lyric.seek", true); }
    private float clampLine(float value) { return Math.max(0, Math.min(lines.size() - 1, value)); }
    public boolean isCompact() { return compact; }
    public void setCompact(boolean value) { compact = value; cancelDrag(); scroll.reset(); repaint(); }
    public void cancelDrag() { dragging = false; recordPressed = false; repaint(); }

    private void seekLine(int index) {
        long position = LyricTimeline.seekPosition(lines, index, offsetMs);
        if (lyricWindow.playerEngine != null) lyricWindow.playerEngine.seekToMs(position);
        setCurrentTime(position);
        scroll.reset();
    }

    private void showMenu(MouseEvent event) {
        cancelDrag();
        TtMenuCatalog.create(menuKind, lyricWindow.getMenuActions()).show(this, event.getX(), event.getY());
    }

    public List<LRCLine> getLyrics() { return lines; }
    public long getOffsetMs() { return offsetMs; }
    public int getCurrentLineIndex() { return currentLineIndex; }
    public void setOffsetMs(long value) { offsetMs = value; scroll.reset(); setCurrentTime(playbackTimeMs); }

    public void setStyle(LyricSettings.Style value, Font font, boolean isConfigured) {
        style = value.copy();
        configured = isConfigured;
        textColor = value.normal;
        highlightColor = value.highlight;
        wordColor = value.word;
        bgColor = value.background;
        lyricFont = font;
        currentFont = font.deriveFont((float) (font.getSize() + (configured ? 0 : 2)));
        cancelDrag();
        scroll.reset();
        adjustFontToLyrics();
    }

    public void copyPresentationFrom(LyricRenderer source) {
        if (lines != source.lines) setLyrics(source.lines);
        textColor = source.textColor;
        highlightColor = source.highlightColor;
        bgColor = source.bgColor;
        wordColor = source.wordColor;
        karaokeEnabled = source.karaokeEnabled;
        style = source.style.copy();
        configured = source.configured;
        offsetMs = source.offsetMs;
        loading = source.loading;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (updateTimer != null && isShowing()) updateTimer.start();
    }
    @Override public void removeNotify() {
        if (updateTimer != null) updateTimer.stop();
        cancelDrag();
        super.removeNotify();
    }

    public void setLyrics(List<LRCLine> newLines) {
        lines = newLines;
        currentLineIndex = -1;
        playbackTimeMs = 0;
        loading = false;
        cancelDrag();
        scroll.reset();
        repaint();
    }
    public void setLoading(boolean value) { loading = value; repaint(); }

    public void adjustFontToLyrics() {
        StringBuilder text = new StringBuilder(Messages.get("lyric.none")).append('\n').append(lyricWindow.getSongTitle()).append('\n');
        if (hasLyrics()) for (LRCLine line : lines) if (line.getText() != null) text.append(line.getText()).append('\n');
        Font font = FontUtils.getLyricFontByText(text.toString(), lyricFont.getFamily(), Font.PLAIN, lyricFont.getSize());
        lyricFont = font;
        currentFont = font.deriveFont((float) (font.getSize() + (configured ? 0 : 2)));
        compactBaseFont = FontUtils.getLyricFontByText(text.toString(), style.custom ? style.fontFamily() : "Tahoma",
                Font.PLAIN, style.custom ? style.fontSize : 14);
        repaint();
    }

    public void setCurrentTime(long positionMs) {
        playbackTimeMs = positionMs;
        currentLineIndex = LyricTimeline.active(lines, positionMs, offsetMs);
        repaint();
    }

    private Font compactFont() {
        Font font = compactBaseFont == null ? lyricFont.deriveFont(14f) : compactBaseFont;
        while (font.getSize() > 10 && getFontMetrics(font).getHeight() > getHeight() - 4)
            font = font.deriveFont((float) (font.getSize() - 1));
        return font;
    }

    private float compactWidth(int index) {
        return Math.max(8, getFontMetrics(compactFont()).stringWidth(lines.get(index).getText()));
    }
    private float compactSpan(int index) { return (compactWidth(index) + compactWidth(index + 1)) / 2 + 32; }

    private void panCompact(float distance) {
        int index = (int) Math.floor(previewLine);
        float fraction = previewLine - index;
        while (distance > 0 && index + 1 < lines.size()) {
            float span = compactSpan(index), remaining = (1 - fraction) * span;
            if (distance < remaining) { fraction += distance / span; break; }
            distance -= remaining;
            index++;
            fraction = 0;
        }
        while (distance < 0 && (index > 0 || fraction > 0)) {
            if (fraction == 0) { index--; fraction = 1; }
            float span = compactSpan(index), remaining = fraction * span;
            if (-distance < remaining) { fraction += distance / span; break; }
            distance += remaining;
            fraction = 0;
        }
        previewLine = clampLine(index + fraction);
    }

    private float rowHeight() {
        return configured ? Math.max(16, getFontMetrics(lyricFont).getHeight() + style.spacing + 4)
                : Math.max(26, getFontMetrics(lyricFont).getHeight());
    }

    private void updateGeometry() {
        textBounds.setRect(0, 0, getWidth(), getHeight());
        recordStage = null;
        recordShape = null;
        if (!compact && showCover && MenuSettings.flag("cover", true)) {
            recordStage = RecordCoverPainter.place(new Rectangle2D.Float(0, 0, getWidth(), getHeight()), hasLyrics(), textBounds);
            if (recordStage != null) recordShape = RecordCoverPainter.recordBounds(recordStage);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        Graphics2D canvas = (Graphics2D) graphics.create();
        if (!isOpaque() || !lyricWindow.paintSkinBehind(canvas, this)) super.paintComponent(graphics);
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        updateGeometry();
        if (!compact && !transparentBackground && !style.transparent) {
            canvas.setColor(LyricTextPainter.alpha(bgColor, style.backgroundOpacity / 100f));
            canvas.fillRect(0, 0, getWidth(), getHeight());
        }
        if (recordStage != null) {
            lyricWindow.advanceCoverAnimation();
            RecordCoverPainter.paint(canvas, recordStage, lyricWindow.getCoverImage(), lyricWindow.getRecordRotation(),
                    lyricWindow.getRecordArm(), lyricWindow.playerEngine != null && lyricWindow.playerEngine.isPlaying() && !lyricWindow.playerEngine.isPaused(),
                    mouse != null && recordShape.contains(mouse));
        }
        if (compact) paintCompact(canvas);
        else paintLines(canvas);
        canvas.dispose();
    }

    private float progress(int index) {
        long duration = lyricWindow.playerEngine == null ? 0 : lyricWindow.playerEngine.getDuration() * 1000L;
        return LyricTimeline.progress(lines, index, playbackTimeMs, duration, offsetMs);
    }

    private void paintLines(Graphics2D canvas) {
        if (!hasLyrics()) {
            String message = Messages.get(loading ? "lyric.loading" : "lyric.none");
            LyricTextPainter.paint(canvas, message, textBounds, lyricFont, configured ? textColor : new Color(0x788296),
                    textColor, 0, style);
            return;
        }
        float center = dragging ? previewLine : style.scrollMode == 0
                ? scroll.update(currentLineIndex, 0, false, playbackTimeMs, System.nanoTime() / 1000000)
                : Math.max(0, currentLineIndex);
        int anchor = Math.max(0, Math.min(lines.size() - 1, Math.round(center)));
        int radius = style.scrollMode == 2 ? 0 : (int) (textBounds.height / rowHeight()) / 2 + 1;
        int active = dragging ? Math.round(previewLine) : currentLineIndex;
        Graphics2D text = (Graphics2D) canvas.create();
        text.clip(textBounds);
        for (int index = Math.max(0, anchor - radius); index <= Math.min(lines.size() - 1, anchor + radius); index++) {
            boolean selected = index == active;
            float alpha = !selected && style.fade ? Math.max(0.15f, 1 - Math.abs(index - center) * 0.22f) : 1;
            Color base = LyricTextPainter.alpha(selected ? highlightColor : textColor, alpha);
            Color sung = wordColor.equals(highlightColor) && !configured ? new Color(0xffdc50) : wordColor;
            Rectangle2D.Float row = new Rectangle2D.Float(textBounds.x,
                    textBounds.y + (textBounds.height - rowHeight()) / 2 +
                            (style.scrollMode == 2 ? 0 : (index - center) * rowHeight()), textBounds.width, rowHeight());
            LyricTextPainter.paint(text, lines.get(index).getText(), row, selected ? currentFont : lyricFont,
                    base, sung, selected && !dragging && karaokeEnabled ? progress(index) : 0, style);
        }
        if (dragging) {
            float centerY = (float) textBounds.getCenterY();
            text.setColor(new Color(22, 119, 255, 180));
            text.fill(new Rectangle2D.Float(textBounds.x, centerY, textBounds.width, 1));
            text.setFont(FontUtils.getDefaultChineseFont(12));
            long milliseconds = lines.get(Math.round(previewLine)).getTimeMs();
            text.drawString(String.format("%d:%02d", milliseconds / 60000, milliseconds / 1000 % 60),
                    textBounds.x + 4, centerY + 4 + text.getFontMetrics().getAscent());
        }
        text.dispose();
    }

    private void paintCompact(Graphics2D canvas) {
        Font font = compactFont();
        LyricSettings.Style compactStyle = style.copy();
        compactStyle.alignment = 1;
        if (!style.custom) { compactStyle.shadow = false; compactStyle.outline = false; }
        Color base = style.custom ? textColor : new Color(0x4f8297);
        Color highlight = style.custom ? highlightColor : new Color(0xb6d8e0);
        Color word = style.custom ? wordColor : new Color(0xdaebef);
        if (!hasLyrics() || currentLineIndex < 0 && !dragging) {
            LyricTextPainter.paint(canvas, hasLyrics() ? lyricWindow.getSongTitle() : Messages.get("lyric.none"),
                    textBounds, font, highlight, word, 0, compactStyle);
            return;
        }
        float center = clampLine(scroll.update(currentLineIndex, previewLine, dragging, playbackTimeMs,
                System.nanoTime() / 1000000));
        int anchor = (int) Math.floor(center);
        float origin = getWidth() / 2f;
        if (anchor + 1 < lines.size()) origin -= (center - anchor) * compactSpan(anchor);
        if (!dragging && anchor == currentLineIndex)
            origin -= (progress(currentLineIndex) - 0.5f) * Math.max(0, compactWidth(currentLineIndex) - getWidth());
        int active = dragging ? Math.round(center) : currentLineIndex;
        float anchorLeft = origin - compactWidth(anchor) / 2;
        for (int direction = -1; direction <= 1; direction += 2) {
            int index = direction < 0 ? anchor - 1 : anchor;
            float left = anchorLeft;
            if (index < anchor && index >= 0) left -= compactWidth(index) + 32;
            while (index >= 0 && index < lines.size()) {
                float width = compactWidth(index);
                if (direction > 0 && left >= getWidth() || direction < 0 && left + width <= 0) break;
                if (left + width > 0 && left < getWidth())
                    LyricTextPainter.paint(canvas, lines.get(index).getText(), new Rectangle2D.Float(left, 0, width, getHeight()),
                            font, index == active && (!style.custom || dragging || !karaokeEnabled) ? highlight : base,
                            word, !dragging && karaokeEnabled && index == currentLineIndex ? progress(index) : 0, compactStyle);
                if (direction > 0) { left += width + 32; index++; }
                else { index--; if (index >= 0) left -= compactWidth(index) + 32; }
            }
        }
    }
}
