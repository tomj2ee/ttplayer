package org.ttplayer.ui;

import org.ttplayer.controls.TtButton;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.*;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.*;
import org.w3c.dom.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.function.*;

public class LyricWindow extends SkinWindow {
    PlayerEngine playerEngine;
    private LyricRenderer renderer;
    private TtSkin.Ctl lyricCtl;
    private Song currentSong;
    private File lyricFile;
    private LRCParser.LRCData lyricData;
    private long offsetMs, lyricRevision;
    private TtMenuCatalog.Actions menuActions;
    private Consumer<List<LRCLine>> lyricsChanged;
    private LongConsumer offsetChanged;
    private Runnable presentationChanged;
    private boolean disposed;
    private BufferedImage coverImage;
    private SwingWorker<BufferedImage, Void> coverWorker;
    private SwingWorker<Boolean, Void> autoWorker;
    private LyricSearchDialog searchDialog;
    private LyricSettingsDialog settingsDialog;
    private LyricSettings settings = LyricSettings.load();
    private LyricSettings.Style skinWindowStyle = new LyricSettings.Style(false);
    private LyricSettings.Style skinDesktopStyle = new LyricSettings.Style(true);
    private Font skinFont = FontUtils.getLyricFont(null, Font.PLAIN, 14);
    private boolean compact, positioning, compactResizing;
    private Rectangle normalBounds;
    private Container normalContent, normalRendererParent;
    private Window miniAnchor;
    private int[] compactSide = {0};
    private long coverTick, spinMillis;
    private float recordArm;
    private boolean coverPlaying;
    private String coverKey;
    private JButton fallbackSearch;

    public LyricWindow(TtSkin skin, PlayerEngine engine) {
        super(skin, findWindow(skin, "lyric_window"), false);
        playerEngine = engine;
        if (def == null) {
            setUndecorated(true);
            setType(Window.Type.UTILITY);
            setSize(520, 370);
            JPanel panel = new JPanel(null) {
                protected void paintComponent(Graphics graphics) {
                    graphics.setColor(new Color(0xf5f7fb)); graphics.fillRect(0, 0, getWidth(), getHeight());
                    graphics.setFont(FontUtils.getDefaultChineseFont(18)); graphics.setColor(new Color(0x299b78));
                    graphics.drawString(Messages.get("lyric.title"), 20, 32);
                }
            };
            fallbackSearch = new JButton(Messages.get("lyric.searchOnline"));
            fallbackSearch.setBounds(360, 10, 140, 30);
            fallbackSearch.addActionListener(event -> searchLyricsOnline()); panel.add(fallbackSearch);
            setContentPane(panel);
            setBackground(new Color(0, true));
        }
        if (renderer == null) { renderer = new LyricRenderer(this); getContentPane().add(renderer); }
        setTitle(Messages.get("lyric.title"));
        setMinSize(100, 100);
        enableEdgeResizing();
        setResizable(true);
        loadSkinStyle();
        refreshSettings();
        repositionControls();
        getGlassPane().addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (compact && SwingUtilities.isLeftMouseButton(event)) compactResizing = true;
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (compact && compactResizing) {
                    compactResizing = false;
                    MenuSettings.set("miniLyricWidth", LyricSettings.clamp(getWidth(), 96, 4096));
                    MenuSettings.set("miniLyricHeight", LyricSettings.clamp(getHeight(), 24, 1080));
                    positionCompactLyrics();
                }
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(WindowEvent event) { renderer.cancelDrag(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { setVisible(false); }
        });
    }

    @Override protected void buildControls() {
        if (def == null) return;
        for (TtSkin.Ctl control : def.elements) {
            if ("close".equals(control.tag) || "exit".equals(control.tag)) {
                TtButton button = createButton(control);
                if (button != null) button.addActionListener(event -> setVisible(false));
            } else if ("title".equals(control.tag)) createTitleImage(control);
            else if ("lyric".equals(control.tag)) {
                lyricCtl = control;
                renderer = new LyricRenderer(this);
                renderer.setBounds(control.left, control.top, control.right - control.left, control.bottom - control.top);
                getContentPane().add(renderer);
                TtSkin.Ctl fill = new TtSkin.Ctl();
                fill.left = control.left;
                fill.top = control.top;
                fill.right = control.right;
                fill.bottom = control.bottom;
                fill.align = "fill";
                addControl(renderer, fill);
            }
        }
    }

    @Override protected void repositionControls() {
        if (compact) {
            if (renderer != null) renderer.setBounds(6, 2, Math.max(1, getWidth() - 12), Math.max(1, getHeight() - 4));
            return;
        }
        super.repositionControls();
        if (fallbackSearch != null) fallbackSearch.setLocation(Math.max(16, getWidth() - 160), 10);
        if (renderer != null && lyricCtl == null)
            renderer.setBounds(def == null ? 16 : 6, def == null ? 56 : 28,
                    Math.max(1, getWidth() - (def == null ? 32 : 12)), Math.max(1, getHeight() - (def == null ? 90 : 38)));
    }

    private void loadSkinStyle() {
        byte[] data = UIUtils.loadSkinXml(skin, "Lyric");
        if (data == null) return;
        try {
            Document document = UIUtils.parseXml(data);
            NodeList nodes = document.getElementsByTagName("*");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element node = (Element) nodes.item(index);
                if (!"lyric".equalsIgnoreCase(node.getTagName())) continue;
                for (LyricSettings.Style style : new LyricSettings.Style[] {skinWindowStyle, skinDesktopStyle}) {
                    style.normal = UIUtils.getColorAttribute(node, "TextColor", style.normal);
                    style.highlight = UIUtils.getColorAttribute(node, "HilightColor", style.highlight);
                    style.word = UIUtils.getColorAttribute(node, "HilightWordColor", style.word);
                    style.background = UIUtils.getColorAttribute(node, "BkgndColor", style.background);
                }
                String[] specification = UIUtils.getAttribute(node, "Font", "-14").split(",");
                int size = LyricSettings.clamp((int) Math.min(72, Math.abs(Long.parseLong(specification[0].trim()))), 8, 72);
                String family = specification.length > 13 ? specification[13].trim() : null;
                skinFont = FontUtils.getLyricFont(family, Font.PLAIN, size);
                skinWindowStyle.fontSize = size;
                skinDesktopStyle.fontSize = size;
                break;
            }
        } catch (Exception ignored) {}
    }

    public LyricSettings.Style getEffectiveStyle(boolean desktop) {
        LyricSettings.Style style = (desktop ? settings.desktop : settings.window).copy();
        if (!style.custom) {
            LyricSettings.Style skinStyle = desktop ? skinDesktopStyle : skinWindowStyle;
            style.fontSize = skinStyle.fontSize;
            style.normal = skinStyle.normal;
            style.highlight = skinStyle.highlight;
            style.word = skinStyle.word;
            style.background = skinStyle.background;
        }
        return style;
    }

    public Font getEffectiveFont(boolean desktop) {
        LyricSettings.Style style = getEffectiveStyle(desktop);
        String family = style.custom ? style.fontFamily() : skinFont.getFamily();
        int size = !desktop && !settings.window.isConfigured() ? LyricSettings.clamp(style.fontSize, 12, 28) : style.fontSize;
        return FontUtils.getLyricFont(family, Font.PLAIN, size);
    }

    public LyricSettings.Style getSkinStyle(boolean desktop) { return (desktop ? skinDesktopStyle : skinWindowStyle).copy(); }
    public Font getSkinFont(boolean desktop) { return skinFont.deriveFont((float) getSkinStyle(desktop).fontSize); }
    public void setOnPresentationChanged(Runnable listener) { presentationChanged = listener; }
    public void notifyPresentationChanged() { if (presentationChanged != null) presentationChanged.run(); }

    public void refreshSettings() {
        settings = LyricSettings.load();
        renderer.setStyle(getEffectiveStyle(false), getEffectiveFont(false), settings.window.isConfigured());
        renderer.karaokeEnabled = MenuSettings.flag("karaoke", true);
        if (!settings.autoLoad || !settings.autoOnline) cancelAutomaticLyrics();
    }

    public void setMenuActions(TtMenuCatalog.Actions actions) { menuActions = actions; }
    public void setOnLyricsChanged(Consumer<List<LRCLine>> listener) { lyricsChanged = listener; }
    public void setOnOffsetChanged(LongConsumer listener) { offsetChanged = listener; }
    public LyricRenderer getRenderer() { return renderer; }
    public BufferedImage getCoverImage() { return coverImage; }
    public boolean isKaraokeEnabled() { return renderer.karaokeEnabled; }
    public String getSongTitle() { return currentSong == null ? "TTPlayer" : currentSong.title == null ? currentSong.getFileName() : currentSong.title; }
    public Song getCurrentSong() { return currentSong; }
    public long getOffsetMs() { return offsetMs; }
    public void toggleKaraoke() {
        renderer.karaokeEnabled = !renderer.karaokeEnabled;
        MenuSettings.set("karaoke", renderer.karaokeEnabled);
        renderer.repaint();
    }

    public TtMenuCatalog.Actions getMenuActions() {
        if (menuActions != null) return menuActions;
        return new TtMenuCatalog.Actions() {
            public void execute(String action) {
                if ("lyric_search".equals(action)) searchLyricsOnline();
                else if ("loadlrc".equals(action)) loadLocalLyrics();
                else if ("karaoke".equals(action)) toggleKaraoke();
                else if ("cover".equals(action)) { MenuSettings.set("cover", !checked("cover")); repaint(); }
                else if ("play".equals(action) && playerEngine != null) playerEngine.playPause();
            }
            public boolean checked(String action) {
                return "karaoke".equals(action) ? isKaraokeEnabled() : "cover".equals(action) && MenuSettings.flag("cover", true);
            }
            public boolean enabled(String action) { return !"desktop".equals(action) && !"lock".equals(action); }
        };
    }

    public void loadLyrics(Song song) {
        cancelAutomaticLyrics();
        currentSong = song;
        lyricFile = null;
        lyricRevision++;
        loadCover(song);
        LRCParser.LRCData data = null;
        File local = null;
        if (song != null && song.filePath != null && settings.autoLoad) {
            local = LyricsRepository.find(song);
            if (local != null) {
                try { data = new LRCParser().parse(local, settings.trim); }
                catch (IOException ignored) {}
            }
        }
        applyData(data, local);
        if (song == null || !settings.autoLoad || !settings.autoOnline || data != null && !data.lines.isEmpty()) return;
        long revision = lyricRevision;
        autoWorker = LyricAutoDownloader.searchAndDownload(song, downloaded -> {
            if (currentSong == song && lyricRevision == revision && !disposed) {
                File cached = LyricsRepository.cachePath(downloaded).toFile();
                try { applyData(new LRCParser().parse(cached, settings.trim), cached); }
                catch (IOException ignored) {}
            }
        });
    }

    public static final class ContentState {
        private Song song;
        private LRCParser.LRCData data;
        private File file;
        private long offset, spin;
        private BufferedImage cover;
        private float arm;
        public boolean matches(Song current) {
            return song == current || song != null && current != null && java.util.Objects.equals(song.filePath, current.filePath);
        }
    }

    public ContentState captureContent() {
        ContentState state = new ContentState();
        state.song = currentSong; state.data = lyricData; state.file = lyricFile; state.offset = offsetMs;
        state.cover = coverImage; state.spin = spinMillis; state.arm = recordArm;
        return state;
    }

    public void restoreContent(ContentState state) {
        if (state.data == null) { loadLyrics(state.song); return; }
        cancelAutomaticLyrics();
        currentSong = state.song;
        loadCover(currentSong);
        if (state.cover != null) {
            if (coverWorker != null) coverWorker.cancel(true);
            coverImage = state.cover;
        }
        spinMillis = state.spin; recordArm = state.arm;
        applyData(state.data, state.file);
        offsetMs = state.offset; renderer.setOffsetMs(offsetMs);
        if (offsetChanged != null) offsetChanged.accept(offsetMs);
    }

    private void applyData(LRCParser.LRCData data, File source) {
        lyricRevision++;
        lyricFile = source;
        lyricData = data;
        offsetMs = data == null ? 0 : data.offsetMs;
        String saved = MenuSettings.get(offsetKey(), "");
        if (!saved.isEmpty()) {
            try { offsetMs = Long.parseLong(saved); } catch (NumberFormatException ignored) {}
        }
        offsetMs = Math.max(-3600000, Math.min(3600000, offsetMs));
        List<LRCLine> lines = data == null ? null : data.lines;
        renderer.setLyrics(lines);
        renderer.setOffsetMs(offsetMs);
        renderer.adjustFontToLyrics();
        if (playerEngine != null) renderer.setCurrentTime(playerEngine.getPositionMs());
        if (lyricsChanged != null) lyricsChanged.accept(lines);
        if (offsetChanged != null) offsetChanged.accept(offsetMs);
    }

    private String offsetKey() {
        String path = currentSong == null || currentSong.filePath == null ? "" : new File(currentSong.filePath).getAbsolutePath();
        return "lyric.offset." + Base64.getUrlEncoder().withoutPadding().encodeToString(path.getBytes(StandardCharsets.UTF_8));
    }

    public void adjustOffset(long amount) { setOffsetMs(LyricTimeline.saturatedAdd(offsetMs, amount)); }
    public void setOffsetMs(long value) {
        offsetMs = Math.max(-3600000, Math.min(3600000, value));
        if (currentSong != null && currentSong.filePath != null) MenuSettings.set(offsetKey(), offsetMs);
        renderer.setOffsetMs(offsetMs);
        if (offsetChanged != null) offsetChanged.accept(offsetMs);
        renderer.setToolTipText(Messages.get("lyric.offsetValue", offsetMs));
    }

    public void loadLocalLyrics() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("LRC", "lrc", "txt"));
        if (currentSong != null && currentSong.filePath != null)
            chooser.setCurrentDirectory(new File(currentSong.filePath).getAbsoluteFile().getParentFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { loadLocalLyrics(chooser.getSelectedFile()); }
        catch (IOException error) { showError(error); }
    }

    public void loadLocalLyrics(File file) throws IOException {
        LRCParser.LRCData data = new LRCParser().parse(file, settings.trim);
        cancelAutomaticLyrics();
        applyData(data, file);
    }

    public void exportLyrics(File file) throws IOException {
        StringBuilder output = new StringBuilder();
        if (lyricData != null) {
            appendMetadata(output, "ti", lyricData.title);
            appendMetadata(output, "ar", lyricData.artist);
            appendMetadata(output, "al", lyricData.album);
        }
        output.append("[offset:").append(offsetMs).append("]\n");
        List<LRCLine> lines = renderer.getLyrics();
        if (lines != null) for (LRCLine line : lines) {
            output.append(timestamp(line.getTimeMs(), '[', ']'));
            if (line.getWords() == null) output.append(line.getText());
            else for (LRCLine.Word word : line.getWords()) output.append(timestamp(word.timeMs, '<', '>')).append(word.text);
            output.append('\n');
        }
        Files.write(file.toPath(), output.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendMetadata(StringBuilder output, String name, String value) {
        if (value != null && value.indexOf('\n') < 0 && value.indexOf('\r') < 0 && value.indexOf(']') < 0)
            output.append('[').append(name).append(':').append(value).append("]\n");
    }

    private static String timestamp(long milliseconds, char open, char close) {
        return String.format(java.util.Locale.ROOT, "%c%02d:%02d.%03d%c", open, milliseconds / 60000,
                milliseconds / 1000 % 60, milliseconds % 1000, close);
    }

    public void chooseExportLyrics() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("LRC", "lrc"));
        chooser.setSelectedFile(lyricFile == null ? new File(getSongTitle() + ".lrc") : lyricFile);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lrc")) destination = new File(destination + ".lrc");
        if (destination.exists() && JOptionPane.showConfirmDialog(this, Messages.get("lyric.overwrite"),
                Messages.get("lyric.export"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try { exportLyrics(destination); } catch (IOException error) { showError(error); }
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), Messages.get("lyric.title"), JOptionPane.ERROR_MESSAGE);
    }

    private void cancelAutomaticLyrics() { if (autoWorker != null) { autoWorker.cancel(true); autoWorker = null; } }
    private void loadCover(Song song) {
        String key = song == null || song.filePath == null ? "" : new File(song.filePath).getAbsolutePath();
        if (key.equals(coverKey)) return;
        coverKey = key;
        if (coverWorker != null) coverWorker.cancel(true);
        coverImage = null;
        spinMillis = 0;
        coverTick = System.nanoTime() / 1000000;
        if (song == null) return;
        coverWorker = new SwingWorker<BufferedImage, Void>() {
            protected BufferedImage doInBackground() { return AlbumArt.load(song); }
            protected void done() {
                if (isCancelled() || !key.equals(coverKey)) return;
                try { coverImage = get(); } catch (Exception error) { coverImage = null; }
                renderer.repaint();
            }
        };
        coverWorker.execute();
    }

    public void advanceCoverAnimation() {
        long now = System.nanoTime() / 1000000;
        long elapsed = coverTick == 0 ? 0 : Math.max(0, now - coverTick);
        boolean playing = playerEngine != null && playerEngine.isPlaying() && !playerEngine.isPaused();
        if (coverPlaying && playing) spinMillis += elapsed;
        recordArm += ((playing ? 1 : 0) - recordArm) * (1 - Math.exp(-elapsed / 100.0));
        coverPlaying = playing;
        coverTick = now;
    }
    public float getRecordRotation() { return (spinMillis % 8000) / 8000f; }
    public float getRecordArm() { return recordArm; }

    public boolean isCompactMode() { return compact; }
    public Rectangle getNormalBounds() { return compact && normalBounds != null ? new Rectangle(normalBounds) : getBounds(); }
    public void setMiniAnchor(Window anchor) { miniAnchor = anchor; }
    public void setCompactMode(boolean value) {
        if (compact == value) return;
        boolean visible = isVisible();
        setVisible(false);
        renderer.cancelDrag();
        if (value) {
            normalBounds = getBounds();
            normalContent = getContentPane();
            normalRendererParent = renderer.getParent();
            normalRendererParent.remove(renderer);
            JPanel panel = new JPanel(null) {
                protected void paintComponent(Graphics graphics) {
                    graphics.setColor(new Color(0x142b35)); graphics.fillRect(0, 0, getWidth(), getHeight());
                    graphics.setColor(new Color(0x97a7b1)); graphics.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    graphics.setColor(new Color(0x2f4450)); graphics.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                }
                public void doLayout() { repositionControls(); }
            };
            panel.add(renderer);
            setContentPane(panel);
            compact = true;
            compactSide[0] = 0;
            setMinSize(96, 24);
            setAlwaysOnTop(true);
            getRootPane().putClientProperty("independentLayout", Boolean.TRUE);
        } else {
            getContentPane().remove(renderer);
            setContentPane(normalContent);
            normalRendererParent.add(renderer);
            compact = false;
            compactResizing = false;
            setMinSize(100, 100);
            setAlwaysOnTop(MenuSettings.flag("topmost", false));
            getRootPane().putClientProperty("independentLayout", null);
            setBounds(normalBounds);
        }
        renderer.setCompact(value);
        if (value) positionCompactLyrics();
        repositionControls();
        validate();
        if (visible) setVisible(true);
    }

    public void positionCompactLyrics() {
        if (!compact || positioning || compactResizing || miniAnchor == null) return;
        positioning = true;
        try {
            int width = LyricSettings.clamp(MenuSettings.number("miniLyricWidth", 270), 96, 4096);
            int height = MenuSettings.number("miniLyricHeight", 0);
            height = height <= 0 ? LyricSettings.clamp(miniAnchor.getHeight(), 24, 48) : LyricSettings.clamp(height, 24, 1080);
            Rectangle bounds = MiniLyricLayout.adjacent(miniAnchor.getBounds(), MiniLyricLayout.usableBounds(miniAnchor),
                    new Dimension(width, height), compactSide);
            if (!getBounds().equals(bounds)) setBounds(bounds);
        } finally { positioning = false; }
    }

    public void showSettings() {
        showSettings(this);
    }

    public void showSettings(Window dialogOwner) {
        if (settingsDialog == null || !settingsDialog.isDisplayable() || settingsDialog.getOwner() != dialogOwner) {
            if (settingsDialog != null) settingsDialog.dispose();
            settingsDialog = new LyricSettingsDialog(dialogOwner, this);
        }
        settingsDialog.setVisible(true);
        settingsDialog.toFront();
    }

    public void searchLyricsOnline() {
        if (searchDialog != null && searchDialog.isDisplayable()) { searchDialog.toFront(); return; }
        cancelAutomaticLyrics();
        Song requested = currentSong;
        long revision = lyricRevision;
        searchDialog = new LyricSearchDialog(this, requested);
        searchDialog.setOnLyricsReceived(lyrics -> {
            if (currentSong == requested && lyricRevision == revision && !disposed)
                applyData(new LRCParser().parse(lyrics, settings.trim),
                        currentSong == null || currentSong.filePath == null ? null : LyricsRepository.cachePath(currentSong).toFile());
        });
        searchDialog.setVisible(true);
    }

    @Override public void dispose() {
        disposed = true;
        cancelAutomaticLyrics();
        if (coverWorker != null) coverWorker.cancel(true);
        if (searchDialog != null) searchDialog.dispose();
        if (settingsDialog != null) settingsDialog.dispose();
        super.dispose();
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) { return MiniWindow.findWindow(skin, name); }
}
