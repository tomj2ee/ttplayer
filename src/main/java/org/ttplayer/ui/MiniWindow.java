package org.ttplayer.ui;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.controls.*;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public class MiniWindow extends SkinWindow {
    private JLabel infoLabel, statusLabel, timeLabel, stereoLabel;
    private TtButton btnPlay, btnPause, btnLyric, btnMute;
    private TtTrackBar progressBar;
    private TtVolumeBar volumeBar;
    private TtLed timeLed;
    private TtVisualizer visualizer;
    private PlayerEngine playerEngine;
    private Runnable onExitMiniMode, onToggleLyric, onMuteChanged, onMinimize;
    private IntConsumer onVolumeChanged;
    private final Timer updateTimer;
    private List<String> information = new ArrayList<>();
    private long carouselSince;
    private int carouselIndex, carouselPhase;
    private String displayOverride;
    private boolean lyricVisible;
    private Song lastSong;
    private SwingWorker<List<String>, Void> metadataWorker;
    private long informationRevision;
    private int channels;
    private int lastResetGeneration, informationDuration;
    private TtMenuCatalog.Actions menuActions;

    public MiniWindow(TtSkin skin) { this(skin, null); }

    public MiniWindow(TtSkin skin, PlayerEngine engine) {
        super(skin, findWindow(skin, "mini_window"), false);
        playerEngine = engine;
        setTitle(Messages.get("menu.miniMode"));
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        if (def == null) createFallback();
        setResizable(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { exitMiniMode(); }
        });
        installDrag(this);
        updateTimer = new Timer(35, event -> updatePlayback());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { updateTimer.start(); }
            @Override public void componentHidden(ComponentEvent event) { updateTimer.stop(); }
        });
        updatePlayback();
    }

    private void createFallback() {
        setUndecorated(true);
        JPanel panel = new JPanel(null);
        panel.setBackground(new Color(0x141b25));
        setContentPane(panel);
        setSize(620, 96);
        TtSkin.Ctl control = new TtSkin.Ctl();
        control.left = 82; control.top = 17; control.right = 334; control.bottom = 48; control.color = "#e5edf3";
        infoLabel = createInfo(control);
        String[] actions = {"prev", "play", "next", "minimode"};
        Icon[] icons = {MenuIcons.previous(), MenuIcons.play(), MenuIcons.next(), MenuIcons.miniMode()};
        for (int index = 0; index < actions.length; index++) {
            String action = actions[index];
            JButton button = new JButton(icons[index]);
            button.setBounds(444 + index % 3 * 52, index == 3 ? 57 : 12, 40, index == 3 ? 30 : 40);
            button.addActionListener(event -> execute(action));
            panel.add(button);
        }
        progressBar = new TtTrackBar(null);
        progressBar.setBounds(84, 64, 420, 14);
        progressBar.setTrackListener(position -> { if (playerEngine != null) playerEngine.seekTo(position); });
        panel.add(progressBar);
    }

    @Override protected void buildControls() {
        if (def == null) return;
        for (TtSkin.Ctl control : def.elements) {
            switch (control.tag) {
                case "info": case "song": case "title": infoLabel = createInfo(control); break;
                case "status": statusLabel = super.createInfo(control); break;
                case "stereo": stereoLabel = super.createInfo(control); break;
                case "led": timeLed = createLed(control); installRemainingTime(timeLed); break;
                case "time": timeLabel = super.createInfo(control); installRemainingTime(timeLabel); break;
                case "volume": createVolumeBar(control); break;
                case "progress": createProgressBar(control); break;
                case "visual":
                    visualizer = new TtVisualizer();
                    visualizer.setBounds(control.left, control.top, control.right - control.left, control.bottom - control.top);
                    getContentPane().add(visualizer);
                    addControl(visualizer, control);
                    loadVisualizerStyle();
                    break;
                case "icon": createTitleImage(control); break;
                default:
                    TtButton button = createButton(control);
                    if (button == null) break;
                    String action = control.tag;
                    button.addActionListener(event -> execute(action));
                    if ("play".equals(action)) btnPlay = button;
                    if ("pause".equals(action)) btnPause = button;
                    if ("lyric".equals(action)) btnLyric = button;
                    if ("mute".equals(action)) btnMute = button;
                    String tooltip = "tooltip." + action;
                    if (isReturn(action)) tooltip = "tooltip.restoreFull";
                    else if ("lyric".equals(action)) tooltip = "tooltip.toggleLyric";
                    button.setToolTipText(Messages.get(tooltip));
                    break;
            }
        }
    }

    private static boolean isReturn(String action) {
        return "close".equals(action) || "exit".equals(action) || "normal".equals(action) ||
                "normalmode".equals(action) || "normal_mode".equals(action) || "default".equals(action) ||
                "defaultmode".equals(action) || "minimode".equals(action) || "mini_mode".equals(action) || "mini".equals(action);
    }

    private void execute(String action) {
        if (isReturn(action)) { exitMiniMode(); return; }
        if ("minimize".equals(action)) { if (onMinimize != null) onMinimize.run(); else setState(Frame.ICONIFIED); return; }
        if ("lyric".equals(action)) { if (onToggleLyric != null) onToggleLyric.run(); return; }
        if ("set".equals(action) || "menu".equals(action)) {
            if (menuActions != null) TtMenuCatalog.create(TtMenuCatalog.Kind.MAIN, menuActions).show(getContentPane(), 0, getHeight());
            return;
        }
        if (playerEngine == null) return;
        switch (action) {
            case "prev": playerEngine.previous(); break;
            case "play": case "pause": playerEngine.playPause(); break;
            case "next": playerEngine.next(); break;
            case "stop": playerEngine.stop(); break;
            case "mute":
                playerEngine.mute(); setMuted(playerEngine.isMuted());
                if (onMuteChanged != null) onMuteChanged.run();
                break;
            default: if (menuActions != null) menuActions.execute(action); break;
        }
        updatePlayback();
    }

    private void createProgressBar(TtSkin.Ctl control) {
        progressBar = new TtTrackBar(skin.getBmp(control.thumbImage), skin.getBmp(control.fillImage), skin.getBmp(control.barImage));
        progressBar.setBounds(control.left, control.top, control.right - control.left, control.bottom - control.top);
        progressBar.putClientProperty("innerDrag", Boolean.TRUE);
        progressBar.setTrackListener(position -> { if (playerEngine != null) playerEngine.seekTo(position); });
        getContentPane().add(progressBar);
        addControl(progressBar, control);
    }

    private void createVolumeBar(TtSkin.Ctl control) {
        volumeBar = new TtVolumeBar(skin.getBmp(control.fillImage), skin.getBmp(control.thumbImage));
        volumeBar.setBounds(control.left, control.top, control.right - control.left, control.bottom - control.top);
        volumeBar.putClientProperty("innerDrag", Boolean.TRUE);
        volumeBar.setVolumeListener(percent -> {
            if (playerEngine != null) playerEngine.setGainFromPercent(percent);
            if (onVolumeChanged != null) onVolumeChanged.accept(percent);
        });
        getContentPane().add(volumeBar);
        addControl(volumeBar, control);
    }

    private void installRemainingTime(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    MenuSettings.set("remainingTime", !MenuSettings.flag("remainingTime", false));
                    updatePlayback();
                }
            }
        });
    }

    private void installDrag(Component component) {
        if (component instanceof TtButton || component instanceof AbstractButton || component instanceof TtTrackBar ||
                component instanceof TtVolumeBar || component instanceof TtVisualizer || component == timeLed || component == timeLabel) return;
        MouseAdapter drag = new MouseAdapter() {
            private Point start, origin;
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                start = event.getLocationOnScreen(); origin = getLocation();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (start == null) return;
                Point pointer = event.getLocationOnScreen();
                int horizontal = origin.x + pointer.x - start.x, vertical = origin.y + pointer.y - start.y;
                Rectangle area = MiniLyricLayout.usableBounds(MiniWindow.this);
                if (Math.abs(horizontal - area.x) <= 10) horizontal = area.x;
                if (Math.abs(horizontal + getWidth() - area.x - area.width) <= 10) horizontal = area.x + area.width - getWidth();
                if (Math.abs(vertical - area.y) <= 10) vertical = area.y;
                if (Math.abs(vertical + getHeight() - area.y - area.height) <= 10) vertical = area.y + area.height - getHeight();
                setLocation(horizontal, vertical);
            }
            @Override public void mouseReleased(MouseEvent event) { start = null; origin = null; }
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) exitMiniMode();
            }
        };
        component.addMouseListener(drag);
        component.addMouseMotionListener(drag);
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) installDrag(child);
    }

    private void loadVisualizerStyle() {
        byte[] data = UIUtils.loadSkinXml(skin, "Visual");
        if (data == null) return;
        try {
            org.w3c.dom.Document document = UIUtils.parseXml(data);
            org.w3c.dom.Element node = (org.w3c.dom.Element) document.getDocumentElement().getElementsByTagName("Visual").item(0);
            if (node != null) visualizer.applyVisualSkin(node);
        } catch (Exception ignored) {}
    }

    private void updatePlayback() {
        if (playerEngine == null) return;
        Song song = playerEngine.getCurrentSong();
        if (song != lastSong || song != null && playerEngine.getDuration() > 0 && playerEngine.getDuration() != informationDuration) {
            lastSong = song;
            if (song != null) setSong(song);
            if (progressBar != null) progressBar.reset(playerEngine.getDuration(), playerEngine.getPosition());
        }
        int generation = playerEngine.getVizResetGen();
        if (generation != lastResetGeneration) {
            lastResetGeneration = generation;
            if (progressBar != null) progressBar.reset(playerEngine.getDuration(), playerEngine.getPosition());
            clearVisualizer();
        }
        boolean paused = playerEngine.isPaused(), playing = playerEngine.isPlaying() && !paused;
        updatePlayState(playing);
        if (progressBar != null && !progressBar.isDragging()) {
            progressBar.setRange(Math.max(1, playerEngine.getDuration()));
            progressBar.setValue(playerEngine.getPosition());
        }
        if (volumeBar != null && !volumeBar.isDragging()) setVolume(playerEngine.getVolumePercent());
        setMuted(playerEngine.isMuted());
        if (btnLyric != null) btnLyric.setSelected(lyricVisible);
        if (statusLabel != null) statusLabel.setText(Messages.get(playing ? "status.playing" : paused ? "status.paused" : "status.stopped"));
        if (stereoLabel != null) stereoLabel.setText(channels > 2 ? channels + " " + Messages.get("song.channels") :
                Messages.get(channels == 1 ? "player.mono" : "player.stereo"));
        boolean remaining = MenuSettings.flag("remainingTime", false);
        String time = TtLed.formatTime(playerEngine.getPositionMs(), playerEngine.getDuration() * 1000L, remaining);
        if (timeLed != null) timeLed.setText(time);
        if (timeLabel != null) timeLabel.setText(time);
        if (infoLabel != null) infoLabel.repaint();
    }

    private void setSong(Song song) {
        String format = song.filePath == null ? "" : SongUtils.getFileExtension(song.filePath).toUpperCase(Locale.ROOT);
        updateSongInfo(song.title == null ? song.getFileName() : song.title, song.artist, song.album, format, playerEngine.getDuration());
        if (metadataWorker != null) metadataWorker.cancel(true);
        if (song.filePath == null) return;
        long revision = informationRevision;
        metadataWorker = new SwingWorker<List<String>, Void>() {
            private int sourceChannels;
            protected List<String> doInBackground() {
                List<String> details = new ArrayList<>();
                try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(song.filePath)) {
                    grabber.start();
                    sourceChannels = grabber.getAudioChannels();
                    int rate = grabber.getSampleRate(), bitrate = grabber.getAudioBitrate();
                    if (rate > 0) details.add(Messages.get("song.audioPrefix") + String.format(Locale.ROOT, "%.3g kHz · %d %s",
                            rate / 1000.0, sourceChannels, Messages.get("song.channels")));
                    if (bitrate > 0) details.add(Messages.get("song.bitratePrefix") + String.format(Locale.ROOT, "%.0f kbps", bitrate / 1000.0));
                } catch (Exception ignored) {}
                long size = new File(song.filePath).length();
                if (size > 0) details.add(Messages.get("song.sizePrefix") + String.format(Locale.ROOT, "%.2f %s",
                        size / (size >= 1073741824L ? 1073741824.0 : 1048576.0), size >= 1073741824L ? "GiB" : "MiB"));
                return details;
            }
            protected void done() {
                if (isCancelled() || lastSong != song || informationRevision != revision) return;
                try { information.addAll(get()); channels = sourceChannels; updateTooltip(); }
                catch (Exception ignored) {}
            }
        };
        metadataWorker.execute();
    }

    public void updateSongInfo(String title, String artist, String album, String format, int durationSeconds) {
        informationDuration = durationSeconds;
        informationRevision++;
        information = new ArrayList<>();
        String name = title == null || title.isEmpty() ? Messages.get("song.unknownTitle") : title;
        information.add(artist == null || artist.isEmpty() ? name : artist + " — " + name);
        if (album != null && !album.isEmpty()) information.add(Messages.get("song.albumPrefix") + album);
        if (durationSeconds > 0) information.add(Messages.get("song.durationPrefix") + String.format(Locale.ROOT, "%d:%02d", durationSeconds / 60, durationSeconds % 60));
        if (format != null && !format.isEmpty()) information.add(Messages.get("song.formatPrefix") + format);
        displayOverride = null;
        resetCarousel();
        updateTooltip();
    }

    private void updateTooltip() {
        if (infoLabel != null) {
            String text = String.join("\n", information);
            infoLabel.setToolTipText(text);
            infoLabel.setFont(FontUtils.getLyricFontByText(text, infoLabel.getFont().getFamily(), Font.PLAIN, infoLabel.getFont().getSize()));
        }
    }
    private void resetCarousel() { carouselIndex = 0; carouselPhase = 0; carouselSince = System.nanoTime() / 1000000; }

    @Override protected JLabel createInfo(TtSkin.Ctl control) {
        JLabel label = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                if (isOpaque()) { graphics.setColor(getBackground()); graphics.fillRect(0, 0, getWidth(), getHeight()); }
                Graphics2D canvas = (Graphics2D) graphics.create();
                canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                canvas.setFont(getFont()); canvas.setColor(getForeground());
                List<String> values = information;
                if (displayOverride != null || values == null || values.isEmpty()) {
                    String value = displayOverride == null ? "TTPlayer" : displayOverride;
                    canvas.drawString(value, 0, (getHeight() - canvas.getFontMetrics().getHeight()) / 2 + canvas.getFontMetrics().getAscent());
                    canvas.dispose(); return;
                }
                carouselIndex %= values.size();
                FontMetrics metrics = canvas.getFontMetrics();
                String current = values.get(carouselIndex), next = values.get((carouselIndex + 1) % values.size());
                int width = metrics.stringWidth(current);
                long now = System.nanoTime() / 1000000, elapsed = now - carouselSince;
                if (carouselPhase == 0 && elapsed >= 2500 && (values.size() > 1 || width > getWidth())) {
                    carouselPhase = width > getWidth() ? 2 : 1; carouselSince = now; elapsed = 0;
                } else if (carouselPhase == 1 && elapsed >= getHeight() * 35L) {
                    carouselIndex = (carouselIndex + 1) % values.size(); carouselPhase = 0; carouselSince = now;
                    current = values.get(carouselIndex); width = metrics.stringWidth(current); elapsed = 0;
                } else if (carouselPhase == 2 && elapsed >= Math.max(0, width - getWidth()) * 35L + 1000) {
                    carouselPhase = values.size() > 1 ? 1 : 0; carouselSince = now; elapsed = 0;
                }
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                int left = getHorizontalAlignment() == SwingConstants.RIGHT ? Math.max(0, getWidth() - width) :
                        getHorizontalAlignment() == SwingConstants.CENTER ? Math.max(0, (getWidth() - width) / 2) : 0;
                if (carouselPhase == 2) left = -(int) Math.min(elapsed / 35, Math.max(0, width - getWidth()));
                if (carouselPhase == 1) {
                    if (width > getWidth()) left = getWidth() - width;
                    canvas.drawString(current, left, baseline - (int) (elapsed / 35));
                    int nextWidth = metrics.stringWidth(next);
                    int nextLeft = getHorizontalAlignment() == SwingConstants.RIGHT ? Math.max(0, getWidth() - nextWidth) :
                            getHorizontalAlignment() == SwingConstants.CENTER ? Math.max(0, (getWidth() - nextWidth) / 2) : 0;
                    canvas.drawString(next, nextLeft, baseline + getHeight() - (int) (elapsed / 35));
                } else canvas.drawString(current, left, baseline);
                canvas.dispose();
            }
        };
        label.setBounds(control.left, control.top, control.right - control.left, control.bottom - control.top);
        Color color = ColorUtils.decode(control.color == null ? "#b6d8e0" : control.color);
        label.setForeground(color == null ? new Color(0xb6d8e0) : color);
        if (control.bkgnd != null && !control.bkgnd.isEmpty()) {
            Color background = ColorUtils.decode(control.bkgnd);
            if (background != null) { label.setBackground(background); label.setOpaque(true); }
        }
        label.setFont(FontUtils.getChineseFont(control.font, Font.PLAIN, control.fontSize > 0 ? control.fontSize : 12));
        String alignment = control.align == null ? "" : control.align.toLowerCase(Locale.ROOT);
        label.setHorizontalAlignment(alignment.contains("right") ? SwingConstants.RIGHT : alignment.contains("center") ? SwingConstants.CENTER : SwingConstants.LEFT);
        getContentPane().add(label); addControl(label, control);
        return label;
    }

    public void updatePlayState(boolean playing) {
        if (playerEngine != null && playerEngine.isPaused()) playing = false;
        if (btnPlay != null && btnPause != null) { btnPlay.setVisible(!playing); btnPause.setVisible(playing); }
        else if (btnPlay != null) btnPlay.setSelected(playing);
    }
    public void setProgress(int percent) { if (progressBar != null) progressBar.setValue(percent); }
    public TtTrackBar getProgressBar() { return progressBar; }
    public void setVolume(int percent) { if (volumeBar != null) volumeBar.setValue(percent); }
    public void setMuted(boolean muted) { if (btnMute != null) btnMute.setSelected(muted); }
    public void setLyricVisible(boolean visible) { lyricVisible = visible; if (btnLyric != null) btnLyric.setSelected(visible); }
    public void setOnVolumeChanged(IntConsumer listener) { onVolumeChanged = listener; }
    public void setMenuActions(TtMenuCatalog.Actions actions) { menuActions = actions; }
    public void refreshPlaybackState() { updatePlayback(); }
    public void setOnMuteChanged(Runnable listener) { onMuteChanged = listener; }
    public void setOnMinimize(Runnable listener) { onMinimize = listener; }
    public void setOnExitMiniMode(Runnable listener) { onExitMiniMode = listener; }
    public void setOnToggleLyric(Runnable listener) { onToggleLyric = listener; }
    public void setDisplayText(String text) { displayOverride = text; if (infoLabel != null) infoLabel.repaint(); }
    public void closeVisualizerFullscreen() { if (visualizer != null) visualizer.closeFullscreen(); }
    public void updateVisualizer(byte[] pcmData) { if (visualizer != null && isVisible()) visualizer.updateData(pcmData); }
    public void clearVisualizer() { if (visualizer != null) visualizer.clearDisplay(); }
    private void exitMiniMode() { if (onExitMiniMode != null) onExitMiniMode.run(); else setVisible(false); }

    @Override public void setVisible(boolean visible) {
        if (visible && updateTimer != null) { updatePlayback(); resetCarousel(); updateTimer.start(); }
        else if (updateTimer != null) updateTimer.stop();
        super.setVisible(visible);
    }
    @Override public void dispose() {
        if (updateTimer != null) updateTimer.stop();
        if (metadataWorker != null) metadataWorker.cancel(true);
        closeVisualizerFullscreen();
        super.dispose();
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) {
        if (skin == null) return null;
        for (TtSkin.WindowDef window : skin.getWindows()) if (name.equalsIgnoreCase(window.name)) return window;
        return null;
    }
}
