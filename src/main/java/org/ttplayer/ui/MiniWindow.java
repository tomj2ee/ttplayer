package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.controls.*;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.engine.AudioSourceInfo;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.function.IntConsumer;

public class MiniWindow extends SkinWindow {
    private TtSongInfo infoLabel;
    private JLabel statusLabel, timeLabel, stereoLabel;
    private TtButton btnPlay, btnPause, btnLyric, btnMute;
    private TtTrackBar progressBar;
    private TtVolumeBar volumeBar;
    private TtLed timeLed;
    private TtVisualizer visualizer;
    private PlayerEngine playerEngine;
    private Runnable onExitMiniMode, onToggleLyric, onMuteChanged, onMinimize;
    private IntConsumer onVolumeChanged;
    private final Timer updateTimer;
    private String displayOverride;
    private boolean lyricVisible;
    private Song lastSong;
    private int lastResetGeneration;
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
        if (song != lastSong) {
            lastSong = song;
            displayOverride = null;
            if (infoLabel != null) infoLabel.resetAnimation();
            if (progressBar != null) progressBar.reset(playerEngine.getDuration(), playerEngine.getPosition());
        }
        AudioSourceInfo source = song == null ? AudioSourceInfo.EMPTY : playerEngine.getSourceInfo();
        if (infoLabel != null) infoLabel.setInformation(displayOverride == null ?
                SongInformation.pages(song, playerEngine.getDuration(), source) : Collections.singletonList(displayOverride));
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
        if (stereoLabel != null) stereoLabel.setText(source.channels > 2 ? source.channels + " " + Messages.get("song.channels") :
                Messages.get(source.channels == 1 ? "player.mono" : "player.stereo"));
        boolean remaining = MenuSettings.flag("remainingTime", false);
        String time = TtLed.formatTime(playerEngine.getPositionMs(), playerEngine.getDuration() * 1000L, remaining);
        if (timeLed != null) timeLed.setText(time);
        if (timeLabel != null) timeLabel.setText(time);
    }

    public void updateSongInfo(String title, String artist, String album, String format, int durationSeconds) {
        displayOverride = null;
        if (infoLabel != null) infoLabel.setInformation(SongInformation.pages(title, artist, album, format,
                durationSeconds, AudioSourceInfo.EMPTY));
    }

    @Override protected TtSongInfo createInfo(TtSkin.Ctl control) {
        TtSongInfo label = super.createInfo(control, new TtSongInfo());
        if (control.color == null || control.color.isEmpty()) label.setForeground(new Color(0xb6d8e0));
        label.setInformation(Collections.singletonList(Messages.get("app.name")));
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
    public void setDisplayText(String text) {
        displayOverride = text;
        if (text == null) updatePlayback();
        else if (infoLabel != null) infoLabel.setInformation(Collections.singletonList(text));
    }
    public void closeVisualizerFullscreen() { if (visualizer != null) visualizer.closeFullscreen(); }
    public void updateVisualizer(byte[] pcmData) { if (visualizer != null && isVisible()) visualizer.updateData(pcmData); }
    public void clearVisualizer() { if (visualizer != null) visualizer.clearDisplay(); }
    private void exitMiniMode() { if (onExitMiniMode != null) onExitMiniMode.run(); else setVisible(false); }

    @Override public void setVisible(boolean visible) {
        if (visible && updateTimer != null) { updatePlayback(); if (infoLabel != null) infoLabel.resetAnimation(); updateTimer.start(); }
        else if (updateTimer != null) updateTimer.stop();
        super.setVisible(visible);
    }
    @Override public void dispose() {
        if (updateTimer != null) updateTimer.stop();
        closeVisualizerFullscreen();
        super.dispose();
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) {
        if (skin == null) return null;
        for (TtSkin.WindowDef window : skin.getWindows()) if (name.equalsIgnoreCase(window.name)) return window;
        return null;
    }
}
