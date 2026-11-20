package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.audio.TtVisualSettings;
import org.ttplayer.controls.TtButton;
import org.ttplayer.controls.TtLed;
import org.ttplayer.controls.TtTrackBar;
import org.ttplayer.controls.TtVolumeBar;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.skin.TtSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import org.ttplayer.util.Messages;
import org.ttplayer.util.MenuSettings;

public class PlayerWindow extends SkinWindow {
    private static final Logger log = LoggerFactory.getLogger(PlayerWindow.class);

    public TtButton btnPlay, btnPause, btnStop, btnPrev, btnNext, btnMute;
    public TtButton btnOpen, btnLyric, btnEqualizer, btnPlaylist;
    public TtButton btnMinimize, btnExit, btnMiniMode;
    public TtButton btnModeSingle, btnModeLoop, btnModeSlider, btnModeCircle, btnModeRandom;
    public TtButton btnSet, btnDeskLrc;

    private TtTrackBar progressBar;
    private TtVolumeBar volumeBar;
    private TtVisualizer visualizer;
    public TtVisualizer getVisualizer() { return visualizer; }
    private TtLed ledLabel;
    private JLabel infoLabel;
    private JLabel statusLabel;
    private JLabel iconLabel;

    private final PlayerEngine playerEngine;
    private Runnable onToggleMiniMode;
    private Runnable onHideToTray;

    // 子窗口引用
    private LyricWindow lyricWindow;
    private EqualizerWindow equalizerWindow;
    private PlaylistWindow playlistWindow;
    private MiniWindow miniWindow;

    // 子窗口状态
    private boolean[] childWindowStatesBeforeMinimize;

    /** 停止状态下是否已清空过频谱（避免每 20ms 重复清屏） */
    private boolean clearedWhileStopped = false;
    /** 上一次见到的频谱重置世代号：变化即代表换曲/停止，需要清屏。 */
    private int lastVizResetGen = -1;

    public PlayerWindow(TtSkin skin, PlayerEngine playerEngine) {
        super(skin, findWindow(skin), false);
        this.playerEngine = playerEngine;
        setTitle("TTPlayer");
        TtSkin.WindowDef def = findWindow(skin);
        if (def != null && def.width > 0 && def.height > 0) {
            setMinSize(def.width, def.height);
        } else {
            setMinSize(268, 165);
        }

        Timer updateTimer = new Timer(200, e -> doUpdate());
        updateTimer.start();

        Timer visualTimer = new Timer(TtVisualSettings.frameDelay(), event -> {
            ((Timer) event.getSource()).setDelay(TtVisualSettings.frameDelay());
            refreshSpectrum();
        });
        visualTimer.start();

        // 添加主窗口监听器
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                // 主窗口最小化时，保存子窗口状态并隐藏子窗口
                if (lyricWindow != null && equalizerWindow != null && playlistWindow != null) {
                    childWindowStatesBeforeMinimize = new boolean[]{
                        lyricWindow.isVisible(),
                        equalizerWindow.isVisible(),
                        playlistWindow.isVisible()
                    };
                    lyricWindow.setVisible(false);
                    equalizerWindow.setVisible(false);
                    playlistWindow.setVisible(false);
                }
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                // 主窗口还原时，还原之前可见的子窗口
                if (childWindowStatesBeforeMinimize != null) {
                    if (lyricWindow != null) lyricWindow.setVisible(childWindowStatesBeforeMinimize[0]);
                    if (equalizerWindow != null) equalizerWindow.setVisible(childWindowStatesBeforeMinimize[1]);
                    if (playlistWindow != null) playlistWindow.setVisible(childWindowStatesBeforeMinimize[2]);
                }
            }
        });
    }

    // 设置子窗口
    public void setChildWindows(LyricWindow lyric, EqualizerWindow eq, PlaylistWindow playlist) {
        this.lyricWindow = lyric;
        this.equalizerWindow = eq;
        this.playlistWindow = playlist;
    }

    public void setMiniWindow(MiniWindow miniWindow) {
        this.miniWindow = miniWindow;
    }

    // 获取子窗口
    public LyricWindow getLyricWindow() { return lyricWindow; }
    public EqualizerWindow getEqualizerWindow() { return equalizerWindow; }
    public PlaylistWindow getPlaylistWindow() { return playlistWindow; }
    public MiniWindow getMiniWindow() { return miniWindow; }

    @Override
    protected void buildControls() {
        for (TtSkin.Ctl c : def.elements) {
            switch (c.tag) {
                case "play":        btnPlay = createButton(c); tip(btnPlay, org.ttplayer.util.Messages.get("tooltip.play")); break;
                case "pause":       btnPause = createButton(c); tip(btnPause, org.ttplayer.util.Messages.get("tooltip.pause")); break;
                case "stop":        btnStop = createButton(c); tip(btnStop, org.ttplayer.util.Messages.get("tooltip.stop")); break;
                case "prev":        btnPrev = createButton(c); tip(btnPrev, org.ttplayer.util.Messages.get("tooltip.prev")); break;
                case "next":        btnNext = createButton(c); tip(btnNext, org.ttplayer.util.Messages.get("tooltip.next")); break;
                case "mute":        btnMute = createButton(c); tip(btnMute, org.ttplayer.util.Messages.get("tooltip.mute")); break;
                case "set":         btnSet = createButton(c); tip(btnSet, org.ttplayer.util.Messages.get("tooltip.settings")); break;
                case "mode_single": btnModeSingle = createButton(c); tip(btnModeSingle, org.ttplayer.util.Messages.get("mode.single")); break;
                case "mode_loop":   btnModeLoop = createButton(c); tip(btnModeLoop, org.ttplayer.util.Messages.get("mode.listLoop")); break;
                case "mode_slider": btnModeSlider = createButton(c); tip(btnModeSlider, org.ttplayer.util.Messages.get("mode.sequential")); break;
                case "mode_circle": btnModeCircle = createButton(c); tip(btnModeCircle, org.ttplayer.util.Messages.get("mode.singleLoop")); break;
                case "mode_random": btnModeRandom = createButton(c); tip(btnModeRandom, org.ttplayer.util.Messages.get("mode.random")); break;
                case "open":        btnOpen = createButton(c); tip(btnOpen, org.ttplayer.util.Messages.get("tooltip.openFile")); break;
                case "lyric":       btnLyric = createButton(c); tip(btnLyric, org.ttplayer.util.Messages.get("menu.lyricShow")); break;
                case "equalizer":   btnEqualizer = createButton(c); tip(btnEqualizer, org.ttplayer.util.Messages.get("menu.equalizer")); break;
                case "eq":          btnEqualizer = createButton(c); tip(btnEqualizer, org.ttplayer.util.Messages.get("menu.equalizer")); break;
                case "playlist":    btnPlaylist = createButton(c); tip(btnPlaylist, org.ttplayer.util.Messages.get("playlist.title")); break;
                case "desklrc_bar":
                case "desklrc":     btnDeskLrc = createButton(c); tip(btnDeskLrc, org.ttplayer.util.Messages.get("menu.desktopLyric")); break;
                case "minimize":    btnMinimize = createButton(c); tip(btnMinimize, org.ttplayer.util.Messages.get("tooltip.minimize")); break;
                case "exit":        btnExit = createButton(c); tip(btnExit, org.ttplayer.util.Messages.get("tooltip.quit")); break;
                case "close":       btnExit = createButton(c); tip(btnExit, org.ttplayer.util.Messages.get("tooltip.quit")); break;
                case "minimode":
                case "mini":
                case "miniMode":
                case "mini_mode":
                    btnMiniMode = createButton(c);
                    tip(btnMiniMode, org.ttplayer.util.Messages.get("menu.miniMode"));
                    break;
                case "normal":
                case "normalmode":
                case "defaultmode":
                case "default":
                    // 这是回到普通模式的按钮，在迷你模式中可能也有
                    btnMiniMode = createButton(c);
                    tip(btnMiniMode, org.ttplayer.util.Messages.get("mode.default"));
                    break;
                case "skin":
                    // 皮肤选择按钮
                    TtButton btnSkin = createButton(c);
                    if (btnSkin != null) {
                        tip(btnSkin, org.ttplayer.util.Messages.get("tooltip.skin"));
                        btnSkin.addActionListener(e -> {
                            if (playlistWindow != null) {
                                playlistWindow.doSkin();
                            }
                        });
                    }
                    break;
                case "options":
                case "option":
                    // 选项按钮
                    TtButton btnOptions = createButton(c);
                    if (btnOptions != null) {
                        tip(btnOptions, org.ttplayer.util.Messages.get("menu.options"));
                        btnOptions.addActionListener(e -> {
                            if (playlistWindow != null) {
                                playlistWindow.doOptions();
                            }
                        });
                    }
                    break;
                case "about":
                    // 关于按钮
                    TtButton btnAbout = createButton(c);
                    if (btnAbout != null) {
                        tip(btnAbout, org.ttplayer.util.Messages.get("tooltip.about"));
                        btnAbout.addActionListener(e -> {
                            if (playlistWindow != null) {
                                playlistWindow.doAbout();
                            }
                        });
                    }
                    break;
                case "icon":        createIcon(c); break;
                case "info":
                    infoLabel = createInfo(c);
                    infoLabel.setText(org.ttplayer.util.Messages.get("app.name"));
                    break;
                case "status":
                    statusLabel = createInfo(c);
                    statusLabel.setText(org.ttplayer.util.Messages.get("status.stopped"));
                    break;
                case "stereo":
                    JLabel stereoLabel = createInfo(c);
                    stereoLabel.setText(org.ttplayer.util.Messages.get("player.stereo"));
                    break;
                case "led":
                    ledLabel = createLed(c);
                    if (ledLabel != null) {
                        ledLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent e) {
                                if (SwingUtilities.isLeftMouseButton(e)) {
                                    MenuSettings.set("remainingTime", !MenuSettings.flag("remainingTime", false));
                                    updateLedTime();
                                }
                            }
                        });
                    }
                    break;
                case "visual":      createVisualizer(c); break;
                case "progress":    createProgressBar(c); break;
                case "volume":      createVolumeBar(c); break;
            }
        }

        if (btnPlay != null) {
            btnPlay.setVisible(true);
            btnPlay.addActionListener(e -> { if (playerEngine != null) playerEngine.playPause(); });
        }
        if (btnPause != null) {
            btnPause.setVisible(false);
            btnPause.addActionListener(e -> { if (playerEngine != null) playerEngine.playPause(); });
        }
        if (btnStop != null) {
            btnStop.addActionListener(e -> {
                if (playerEngine != null) playerEngine.stop();
                updateLedTime();
                if (progressBar != null) progressBar.setValue(0);
            });
        }
        if (btnPrev != null) btnPrev.addActionListener(e -> { if (playerEngine != null) playerEngine.previous(); });
        if (btnNext != null) btnNext.addActionListener(e -> { if (playerEngine != null) playerEngine.next(); });
        if (btnMute != null) {
            btnMute.setSelected(playerEngine != null && playerEngine.isMuted());
            btnMute.addActionListener(e -> {
                if (playerEngine == null) return;
                playerEngine.mute();
                btnMute.setSelected(playerEngine.isMuted());
                if (miniWindow != null) miniWindow.setMuted(playerEngine.isMuted());
            });
        }
        if (btnExit != null) btnExit.addActionListener(e -> { hideAllWindowsToTray(); });
        if (btnMinimize != null) btnMinimize.addActionListener(e -> { hideAllWindowsToTray(); });
        if (btnMiniMode != null) btnMiniMode.addActionListener(e -> toggleMiniMode());

        // 确保 LED 时间显示在最上层
        if (ledLabel != null) {
            getContentPane().setComponentZOrder(ledLabel, 0);
        }

    }

    public void setMenuActions(TtMenuCatalog.Actions actions) {
        TtMenuCatalog.attachContext(this, TtMenuCatalog.Kind.MAIN, actions);
    }

    private void showPlayerRightClickMenu(java.awt.event.MouseEvent e) {
        if (playlistWindow == null) return;
        JPopupMenu menu = playlistWindow.createRightClickMenu();
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void toggleMiniMode() {
        if (onToggleMiniMode != null) {
            onToggleMiniMode.run();
        } else {
            // 备用逻辑（如果 Main 类没有设置回调）
            if (miniWindow == null) {
                miniWindow = new MiniWindow(skin, playerEngine);
                miniWindow.setOnExitMiniMode(this::exitMiniMode);
                miniWindow.setOnToggleLyric(this::toggleLyric);
                updateMiniModeDisplay();
            }

            // 同步播放状态
            if (playerEngine != null) {
                miniWindow.updatePlayState(playerEngine.isPlaying());
            }

            miniWindow.setLocation(getX() + getWidth() / 2 - miniWindow.getWidth() / 2,
                                   getY() + getHeight() / 2 - miniWindow.getHeight() / 2);
            miniWindow.setVisible(true);
            this.setVisible(false);
        }
    }

    private void toggleLyric() {
        if (btnLyric != null) {
            btnLyric.doClick();
        }
    }

    private void exitMiniMode() {
        this.setVisible(true);
        if (miniWindow != null) {
            miniWindow.setVisible(false);
        }
    }

    public void updateMiniModeDisplay() {
        if (miniWindow != null && infoLabel != null) {
            miniWindow.setDisplayText(infoLabel.getText());
        }
    }

    private void createProgressBar(TtSkin.Ctl ctl) {
        byte[] thumbData = skin.getBmp(ctl.thumbImage);
        progressBar = new TtTrackBar(thumbData);
        progressBar.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        getContentPane().add(progressBar);
        addControl(progressBar, ctl);
        progressBar.setTrackListener(seconds -> {
            if (playerEngine != null) playerEngine.seekTo(seconds);
        });
    }

    private void createVolumeBar(TtSkin.Ctl ctl) {
        byte[] fillData = skin.getBmp(ctl.fillImage);
        byte[] thumbData = skin.getBmp(ctl.thumbImage);
        volumeBar = new TtVolumeBar(fillData, thumbData);
        volumeBar.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        getContentPane().add(volumeBar);
        addControl(volumeBar, ctl);
        if (playerEngine != null) volumeBar.setValue(playerEngine.getVolumePercent());
        volumeBar.setVolumeListener(percent -> {
            if (playerEngine != null) playerEngine.setGainFromPercent(percent);
            if (miniWindow != null) miniWindow.setVolume(percent);
        });
    }

    /** 外部（如迷你窗口滚轮）调整音量时同步音量条与引擎 */
    public void setVolumeFromPercent(int percent) {
        int v = Math.max(0, Math.min(100, percent));
        if (volumeBar != null) volumeBar.setValue(v);
        if (playerEngine != null) playerEngine.setGainFromPercent(v);
        if (miniWindow != null) miniWindow.setVolume(v);
    }

    private void createVisualizer(TtSkin.Ctl ctl) {
        visualizer = new TtVisualizer();
        visualizer.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        loadVisualizerSkinStyle();
        getContentPane().add(visualizer);
        addControl(visualizer, ctl);
    }

    private void createIcon(TtSkin.Ctl ctl) {
        iconLabel = new JLabel();
        iconLabel.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);

        if (ctl.image != null && !ctl.image.isEmpty()) {

            byte[] imageData = skin.getBmp(ctl.image);

            if (imageData != null) {
                try {
                    ImageIcon icon = null;
                    String lowerImage = ctl.image.toLowerCase();

                    // 先尝试用最通用的方法加载
                    java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageData);
                    BufferedImage img = javax.imageio.ImageIO.read(bais);
                    if (img != null) {
                        log.debug(org.ttplayer.util.Messages.get("debug.loadImageSize") + "{}x{}", img.getWidth(), img.getHeight());
                        int w = ctl.right - ctl.left;
                        int h = ctl.bottom - ctl.top;
                        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                        icon = new ImageIcon(scaled);
                    }

                    // 如果没成功，尝试特殊方法
                    if (icon == null) {
                        log.debug(org.ttplayer.util.Messages.get("debug.imageioFail"));
                        if (lowerImage.endsWith(".ico")) {
                            icon = loadIco(imageData);
                        } else {
                            img = SkinWindow.decodeBmp(imageData, getSkinTransparentColor());
                            if (img != null) {
                                int w = ctl.right - ctl.left;
                                int h = ctl.bottom - ctl.top;
                                Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                                icon = new ImageIcon(scaled);
                            }
                        }
                    }

                    if (icon != null) {
                        log.debug(org.ttplayer.util.Messages.get("debug.iconLoaded"));
                        iconLabel.setIcon(icon);
                    } else {
                        log.warn("Failed to load icon, trying fallback");
                        tryLoadFallbackIcon(ctl);
                    }
                } catch (Exception e) {
                    log.error("Error loading icon: {}", e.getMessage(), e);
                    tryLoadFallbackIcon(ctl);
                }
            } else {
                log.warn("Could not find icon file: {}", ctl.image);
                tryLoadFallbackIcon(ctl);
            }
        } else {
            log.warn("No icon image configured");
            tryLoadFallbackIcon(ctl);
        }

        getContentPane().add(iconLabel);
        addControl(iconLabel, ctl);
    }

    private void tryLoadFallbackIcon(TtSkin.Ctl ctl) {
        BufferedImage image = org.ttplayer.util.AppIcons.getImage(16);
        if (image != null) iconLabel.setIcon(new ImageIcon(image));
    }

    private ImageIcon loadIco(byte[] icoData) {
        try {
            javax.imageio.ImageIO.setUseCache(false);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(icoData);

            // 先尝试直接用ImageIO读取
            BufferedImage img = javax.imageio.ImageIO.read(bais);
            if (img != null) {
                int w = iconLabel.getWidth();
                int h = iconLabel.getHeight();
                Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }

            // 重置流，尝试用ICO reader
            bais.reset();
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReadersByFormatName("ico");
            if (readers.hasNext()) {
                javax.imageio.ImageReader reader = readers.next();
                reader.setInput(javax.imageio.ImageIO.createImageInputStream(bais));
                int count = reader.getNumImages(true);
                if (count > 0) {
                    // 读取第一个图标
                    img = reader.read(0);

                    int w = iconLabel.getWidth();
                    int h = iconLabel.getHeight();
                    Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }
            }
        } catch (Exception e) {
            log.error("Error loading ICO file: {}", e.getMessage(), e);
        }
        return null;
    }

    void loadVisualizerSkinStyle() {
        if (visualizer == null) return;
        byte[] data = org.ttplayer.util.UIUtils.loadSkinXml(skin, "Visual");
        if (data == null) return;
        try {
            org.w3c.dom.Document doc = org.ttplayer.util.UIUtils.parseXml(data);
            if (doc == null) return;
            org.w3c.dom.Element visEl = doc.getDocumentElement();
            org.w3c.dom.Element vis = (org.w3c.dom.Element) visEl.getElementsByTagName("Visual").item(0);
            if (vis == null) return;
            visualizer.applyVisualSkin(vis);
        } catch (Exception ignored) {}
    }

    private static void tip(JComponent comp, String text) {
        if (comp != null) comp.setToolTipText(text);
    }

    private void updateLedTime() {
        if (ledLabel != null) ledLabel.setText(TtLed.formatTime(playerEngine == null ? 0 : playerEngine.getPositionMs(),
                playerEngine == null ? 0 : playerEngine.getDuration() * 1000L, MenuSettings.flag("remainingTime", false)));
    }

    private void doUpdate() {
        if (playerEngine == null) return;
        try {
            boolean playing = playerEngine.isPlaying();
            updateLedTime();

            if (btnPlay != null && btnPause != null) {
                btnPlay.setVisible(!playing);
                btnPause.setVisible(playing);
            }

            if (playing) {
                int pos = playerEngine.getPosition();
                int dur = playerEngine.getDuration();
                if (progressBar != null && !progressBar.isDragging()) {
                    if (dur > 0) {
                        progressBar.setRange(dur);
                        progressBar.setValue(pos);
                    }
                }
            }

            // 同步播放状态给迷你窗口
            if (miniWindow != null && miniWindow.isVisible()) {
                miniWindow.updatePlayState(playing);
                // 同步进度条
                if (playing && miniWindow.getProgressBar() != null && !miniWindow.getProgressBar().isDragging()) {
                    int pos = playerEngine.getPosition();
                    int dur = playerEngine.getDuration();
                    if (dur > 0) {
                        miniWindow.getProgressBar().setRange(dur);
                        miniWindow.getProgressBar().setValue(pos);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /** 高速刷新频谱：主窗口与迷你窗口取最新音频数据喂给各自频谱 */
    private void refreshSpectrum() {
        if (playerEngine == null) return;
        boolean mainVisible = isVisible();
        boolean miniVisible = miniWindow != null && miniWindow.isVisible();
        if (!mainVisible && !miniVisible) return;

        // 换曲/停止：引擎世代号变化 → 立即清屏一次。
        // 不能用 isPlaying() 判断：play() 内部 stopPlayback()→startPlayback()
        // 是连续同步执行，20ms 轮询抓不到 playing 的瞬时 false，
        // 旧频谱会残留到第一帧新数据画出来为止（用户反馈的问题 1）。
        int gen = playerEngine.getVizResetGen();
        if (gen != lastVizResetGen) {
            lastVizResetGen = gen;
            if (visualizer != null && mainVisible) visualizer.clearDisplay();
            if (miniVisible) miniWindow.clearVisualizer();
            // 换曲时此刻已在播放 → clearedWhileStopped=false，继续往下取新数据，
            // 使新曲目的频谱在同一帧就接上，不浪费一次刷新。
            clearedWhileStopped = !playerEngine.isPlaying();
        }

        if (!playerEngine.isPlaying()) {
            if (clearedWhileStopped) return;      // 已清过，无需每 20ms 重复刷
            clearedWhileStopped = true;
            if (visualizer != null && mainVisible) visualizer.clearDisplay();
            if (miniVisible) miniWindow.clearVisualizer();
            return;
        }
        clearedWhileStopped = false;

        byte[] data = playerEngine.getAudioData();
        if (visualizer != null && mainVisible) {
            visualizer.updateData(data);
        }
        if (miniVisible) {
            miniWindow.updateVisualizer(data);
        }
    }

    public void setSongInfo(String title){
        if (infoLabel != null) infoLabel.setText(title != null ? title : "");
        updateMiniModeDisplay();
    }

    public void setOnToggleMiniMode(Runnable listener) {
        this.onToggleMiniMode = listener;
    }
    public void setOnHideToTray(Runnable listener) {
        this.onHideToTray = listener;
    }

    private void hideAllWindowsToTray() {
        if (onHideToTray != null) {
            onHideToTray.run();
        } else {
            // 隐藏所有窗口
            this.setVisible(false);
            if (lyricWindow != null) lyricWindow.setVisible(false);
            if (equalizerWindow != null) equalizerWindow.setVisible(false);
            if (playlistWindow != null) playlistWindow.setVisible(false);
            if (miniWindow != null) miniWindow.setVisible(false);
        }
    }

    public void setPlaylistWindow(PlaylistWindow plWindow) {
        this.playlistWindow = plWindow;
    }

    public void setStatusInfo(String status) {
        if (statusLabel != null) statusLabel.setText(status);
    }

    static TtSkin.WindowDef findWindow(TtSkin skin) {
        return LyricWindow.findWindow(skin, "player_window");
    }
}
