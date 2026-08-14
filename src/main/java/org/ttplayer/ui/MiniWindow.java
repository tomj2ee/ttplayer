package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.controls.TtButton;
import org.ttplayer.controls.TtTrackBar;
import org.ttplayer.controls.TtVolumeBar;
import org.ttplayer.util.ColorUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import org.ttplayer.util.Messages;

public class MiniWindow extends SkinWindow {

    private JLabel infoLabel;
    private TtButton btnPrev, btnPlay, btnPause, btnNext, btnStop;
    private TtButton btnLyric, btnMinimize, btnExit;
    private TtButton btnMute;
    private TtTrackBar progressBar;
    private TtVolumeBar volumeBar;
    private TtVisualizer visualizer;
    private Runnable onExitMiniMode;
    private Runnable onToggleLyric;
    private Runnable onMuteChanged;
    private java.util.function.IntConsumer onVolumeChanged;
    private PlayerEngine playerEngine;

    // 歌曲信息轮播
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private String currentFormat = "";
    private String currentDuration = "";
    private int infoDisplayIndex = 0;
    private Timer pauseTimer;           // 每轮之间的停顿定时器
    private boolean rotationPaused = false;  // 是否正在等待下一轮

    // 动画相关
    private Timer animationTimer;
    private Timer marqueeTimer;
    private int scrollOffset = 0;      // 垂直滚动画偏移
    private int marqueeOffset = 0;     // 水平跑马灯偏移
    private String currentText = "";
    private String nextText = "";
    private boolean isAnimating = false;
    private boolean isMarquee = false; // 是否正在跑马灯

    Point dragStartScreen ;
    Point dragStartWindow ;

    public MiniWindow(TtSkin skin) {
        this(skin, null);
    }

    public MiniWindow(TtSkin skin, PlayerEngine engine) {
        super(skin, findWindow(skin, "mini_window"), false);
        this.playerEngine = engine;
        setTitle(org.ttplayer.util.Messages.get("menu.miniMode"));
        setAlwaysOnTop(true); // 让迷你窗口总是置顶

        // 添加定时器更新进度条
        Timer updateTimer = new Timer(200, e -> updateProgress());
        updateTimer.start();
        // 拖动支持
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStartScreen = e.getLocationOnScreen();
                dragStartWindow = MiniWindow.this.getLocation();
            }

            @Override
            public void mouseDragged(MouseEvent e) {

                if (dragStartScreen != null && dragStartWindow != null) {
                    Point current = e.getLocationOnScreen();
                    int dx = current.x - dragStartScreen.x;
                    int dy = current.y - dragStartScreen.y;
                    MiniWindow.this.setLocation(dragStartWindow.x + dx,
                            dragStartWindow.y + dy);
                }
            }
        };
        addMouseListener(dragAdapter);
        addMouseMotionListener(dragAdapter);


    }

    public void setOnVolumeChanged(java.util.function.IntConsumer c) { this.onVolumeChanged = c; }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            // 每次显示时，确保窗口尺寸正确
            TtSkin.WindowDef def = findWindow(skin, "mini_window");
            if (def != null) {
                byte[] bgData = skin.getBmp(def.image);
                if (bgData != null && bgData.length > 18) {
                    int w = ((bgData[18] & 0xFF) | ((bgData[19] & 0xFF) << 8) |
                             ((bgData[20] & 0xFF) << 16) | ((bgData[21] & 0xFF) << 24));
                    int h = ((bgData[22] & 0xFF) | ((bgData[23] & 0xFF) << 8) |
                             ((bgData[24] & 0xFF) << 16) | ((bgData[25] & 0xFF) << 24));
                    if (w > 0 && h > 0) {
                        setSize(w, h);
                    }
                }
            }
            // 显示时同步音量和静音状态（可能已在主窗口调整过）
            if (volumeBar != null && playerEngine != null) volumeBar.setValue(playerEngine.getVolumePercent());
            if (btnMute != null && playerEngine != null) btnMute.setSelected(playerEngine.isMuted());
            // 显示时恢复轮播
            stopAllAnimations();
            if (infoLabel != null && (!currentTitle.isEmpty() || !currentArtist.isEmpty())) {
                // 有歌曲信息，直接显示标题
                infoDisplayIndex = 0;
                String text = getDisplayText(0);
                infoLabel.setText(text);
                infoDisplayIndex = 1;
                startPauseBeforeNext();
            }
        } else {
            stopAllAnimations();
        }
        super.setVisible(visible);
    }

    @Override
    protected void buildControls() {
        if (def == null) return;

        for (TtSkin.Ctl c : def.elements) {
            switch (c.tag) {
                case "close":
                case "exit":
                case "normal":
                case "normalmode":
                case "default":
                case "defaultmode":
                case "minimode":
                case "minimize":
                    TtButton btnClose = createButton(c);
                    if (btnClose != null) {
                        if ("minimize".equals(c.tag)) {
                            tip(btnClose, org.ttplayer.util.Messages.get("tooltip.minimize"));
                            btnClose.addActionListener(e -> setState(Frame.ICONIFIED));
                        } else {
                            tip(btnClose, org.ttplayer.util.Messages.get("tooltip.restoreFull"));
                            btnClose.addActionListener(e -> exitMiniMode());
                        }
                    }
                    break;
                case "info":
                case "song":
                case "title":
                    infoLabel = createInfo(c);
                    break;
                case "prev":
                    btnPrev = createButton(c); tip(btnPrev, org.ttplayer.util.Messages.get("tooltip.prev"));
                    if (btnPrev != null) btnPrev.addActionListener(e -> { if (playerEngine != null) playerEngine.previous(); });
                    break;
                case "play":
                    btnPlay = createButton(c); tip(btnPlay, org.ttplayer.util.Messages.get("tooltip.play"));
                    if (btnPlay != null) btnPlay.addActionListener(e -> { if (playerEngine != null) playerEngine.playPause(); });
                    break;
                case "pause":
                    btnPause = createButton(c); tip(btnPause, org.ttplayer.util.Messages.get("tooltip.pause"));
                    if (btnPause != null) btnPause.addActionListener(e -> { if (playerEngine != null) playerEngine.playPause(); });
                    break;
                case "next":
                    btnNext = createButton(c); tip(btnNext, org.ttplayer.util.Messages.get("tooltip.next"));
                    if (btnNext != null) btnNext.addActionListener(e -> { if (playerEngine != null) playerEngine.next(); });
                    break;
                case "stop":
                    btnStop = createButton(c); tip(btnStop, org.ttplayer.util.Messages.get("tooltip.stop"));
                    if (btnStop != null) btnStop.addActionListener(e -> { if (playerEngine != null) playerEngine.stop(); });
                    break;
                case "lyric":
                    btnLyric = createButton(c); tip(btnLyric, org.ttplayer.util.Messages.get("tooltip.toggleLyric"));
                    if (btnLyric != null) btnLyric.addActionListener(e -> { if (onToggleLyric != null) onToggleLyric.run(); });
                    break;
                case "mute":
                    btnMute = createButton(c);
                    if (btnMute != null) {
                        tip(btnMute, org.ttplayer.util.Messages.get("tooltip.mute"));
                        btnMute.setSelected(playerEngine != null && playerEngine.isMuted());
                        btnMute.addActionListener(e -> {
                            if (playerEngine == null) return;
                            playerEngine.mute();
                            btnMute.setSelected(playerEngine.isMuted());
                            if (onMuteChanged != null) onMuteChanged.run();
                        });
                    }
                    break;
                case "volume":
                    createVolumeBar(c);
                    break;
                case "icon":
                    try {
                        createTitleImage(c);
                    } catch (Exception e) {
                        // 如果图标加载失败，忽略错误继续
                    }
                    break;
                case "progress":
                    createProgressBar(c);
                    break;
                case "visual":
                    createMiniVisualizer(c);
                    break;
            }
        }
    }

    public void updatePlayState(boolean playing) {
        if (btnPlay != null) btnPlay.setVisible(!playing);
        if (btnPause != null) btnPause.setVisible(playing);
    }

    public void setProgress(int percent) {
        if (progressBar != null) {
            progressBar.setValue(percent);
        }
    }

    public TtTrackBar getProgressBar() {
        return progressBar;
    }

    private void createProgressBar(TtSkin.Ctl ctl) {
        byte[] thumbData = skin.getBmp(ctl.thumbImage);
        byte[] fillData = (ctl.fillImage != null && !ctl.fillImage.isEmpty()) ? skin.getBmp(ctl.fillImage) : null;
        byte[] fillData2 = null;

        // 某些皮肤有 fill_image2 用于背景
        progressBar = new TtTrackBar(thumbData, fillData, fillData2);
        progressBar.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        getContentPane().add(progressBar);
        addControl(progressBar, ctl);
        progressBar.setTrackListener(seconds -> {
            if (playerEngine != null) playerEngine.seekTo(seconds);
        });
    }

    private void createVolumeBar(TtSkin.Ctl ctl) {
        byte[] fillData = (ctl.fillImage != null && !ctl.fillImage.isEmpty()) ? skin.getBmp(ctl.fillImage) : null;
        byte[] thumbData = (ctl.thumbImage != null && !ctl.thumbImage.isEmpty()) ? skin.getBmp(ctl.thumbImage) : null;
        volumeBar = new TtVolumeBar(fillData, thumbData);
        volumeBar.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        getContentPane().add(volumeBar);
        addControl(volumeBar, ctl);
        if (playerEngine != null) volumeBar.setValue(playerEngine.getVolumePercent());
        volumeBar.setVolumeListener(percent -> {
            if (playerEngine != null) playerEngine.setGainFromPercent(percent);
            if (onVolumeChanged != null) onVolumeChanged.accept(percent);
        });
    }

    /** 外部（主窗口/引擎）音量变化时同步迷你窗口音量条 */
    public void setVolume(int percent) {
        if (volumeBar != null) volumeBar.setValue(Math.max(0, Math.min(100, percent)));
    }

    public void setMuted(boolean m) {
        if (btnMute != null) btnMute.setSelected(m);
    }

    public void setOnMuteChanged(Runnable r) { this.onMuteChanged = r; }

    private void updateProgress() {
        if (playerEngine == null || progressBar == null) return;
        try {
            boolean playing = playerEngine.isPlaying();
            if (playing && !progressBar.isDragging()) {
                int pos = playerEngine.getPosition();
                int dur = playerEngine.getDuration();
                if (dur > 0) {
                    progressBar.setRange(dur);
                    progressBar.setValue(pos);
                }
            }
        } catch (Exception ignored) {}
    }

    private void createMiniVisualizer(TtSkin.Ctl ctl) {
        visualizer = new TtVisualizer();
        visualizer.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        loadMiniVisualizerSkinStyle();
        visualizer.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    visualizer.cycleMode();
                }
            }
        });
        getContentPane().add(visualizer);
        addControl(visualizer, ctl);
    }

    private void loadMiniVisualizerSkinStyle() {
        if (visualizer == null) return;
        byte[] data = org.ttplayer.util.UIUtils.loadSkinXml(skin, "Visual");
        if (data == null) return;
        try {
            org.w3c.dom.Document doc = org.ttplayer.util.UIUtils.parseXml(data);
            if (doc == null) return;
            org.w3c.dom.Element visEl = (org.w3c.dom.Element) doc.getDocumentElement();
            org.w3c.dom.Element vis = (org.w3c.dom.Element) visEl.getElementsByTagName("Visual").item(0);
            if (vis == null) return;
            visualizer.skinColorTop = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumTopColor", visualizer.skinColorTop);
            visualizer.skinColorMid = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumMidColor", visualizer.skinColorMid);
            visualizer.skinColorBtm = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumBtmColor", visualizer.skinColorBtm);
            visualizer.skinColorPeak = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumPeakColor", visualizer.skinColorPeak);
            visualizer.skinColorBlur = org.ttplayer.util.UIUtils.getColorAttribute(vis, "BlurScopeColor", visualizer.skinColorBlur);
        } catch (Exception ignored) {}
    }

    /** 由 PlayerWindow 高频喂数据（仅在窗口可见且已创建频谱组件时更新） */
    public void updateVisualizer(byte[] pcmData) {
        if (visualizer != null && isVisible()) {
            visualizer.updateData(pcmData);
        }
    }

    public void setOnExitMiniMode(Runnable r) { this.onExitMiniMode = r; }
    public void setOnToggleLyric(Runnable r) { this.onToggleLyric = r; }

    private void exitMiniMode() {
        setVisible(false);
        if (onExitMiniMode != null) onExitMiniMode.run();
    }

    @Override
    protected JLabel createInfo(TtSkin.Ctl ctl) {
        // 创建支持动画的自定义 JLabel
        JLabel lb = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                if (!isAnimating && !isMarquee) {
                    super.paintComponent(g);
                    return;
                }

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                FontMetrics fm = g2.getFontMetrics();
                int textHeight = fm.getHeight();

                if (isAnimating) {
                    // 垂直滚动动画
                    int textWidth1 = fm.stringWidth(currentText);
                    int textWidth2 = fm.stringWidth(nextText);
                    int x1 = (getWidth() - textWidth1) / 2;
                    int x2 = (getWidth() - textWidth2) / 2;

                    int y1 = (getHeight() + textHeight) / 2 - scrollOffset;
                    int y2 = (getHeight() + textHeight) / 2 + getHeight() - scrollOffset;

                    g2.setColor(getForeground());
                    if (y1 > 0 && y1 < getHeight() + textHeight) {
                        g2.drawString(currentText, x1, y1);
                    }
                    if (y2 > 0 && y2 < getHeight() + textHeight) {
                        g2.drawString(nextText, x2, y2);
                    }
                } else if (isMarquee) {
                    // 水平跑马灯
                    int fullWidth = fm.stringWidth(getText());
                    if (fullWidth <= getWidth()) {
                        // 文字不超出，直接居中绘制
                        super.paintComponent(g);
                    } else {
                        int y = (getHeight() + textHeight) / 2;
                        g2.setColor(getForeground());
                        // 从右到左滚动
                        int x = getWidth() - marqueeOffset;
                        g2.drawString(getText(), x, y);
                        // 循环补尾
                        if (x + fullWidth < getWidth()) {
                            g2.drawString(getText(), x + fullWidth, y);
                        }
                    }
                }

                g2.dispose();
            }
        };

        lb.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        if (ctl.color != null && !ctl.color.isEmpty()) {
            Color color = ColorUtils.decode(ctl.color);
            if (color != null) lb.setForeground(color);
        }
        if (ctl.bkgnd != null && !ctl.bkgnd.isEmpty()) {
            Color color = ColorUtils.decode(ctl.bkgnd);
            if (color != null) {
                lb.setOpaque(true);
                lb.setBackground(color);
            }
        }
        int fontSize = ctl.fontSize > 0 ? ctl.fontSize : 12;
        if (ctl.font != null && !ctl.font.isEmpty()) {
            lb.setFont(org.ttplayer.util.FontUtils.getChineseFont(ctl.font, Font.PLAIN, fontSize));
        } else {
            lb.setFont(org.ttplayer.util.FontUtils.getDefaultChineseFont(fontSize));
        }
        lb.setHorizontalAlignment(parseAlign(ctl.align));
        getContentPane().add(lb);
        addControl(lb, ctl);
        return lb;
    }

    public void setDisplayText(String text) {
        if (infoLabel != null) {
            infoLabel.setText(text != null ? text : "");
        }
    }

    /**
     * 更新歌曲信息用于轮播显示
     */
    public void updateSongInfo(String title, String artist, String album, String format, int durationSeconds) {
        this.currentTitle = title != null ? title : "";
        this.currentArtist = artist != null ? artist : "";
        this.currentAlbum = album != null ? album : "";
        this.currentFormat = format != null ? format : "";
        if (durationSeconds > 0) {
            int minutes = durationSeconds / 60;
            int seconds = durationSeconds % 60;
            this.currentDuration = String.format("%d:%02d", minutes, seconds);
        } else {
            this.currentDuration = "";
        }
        // 重置显示索引，从标题开始
        infoDisplayIndex = 0;
        if (isVisible()) {
            rotateInfoDisplay();
        }
        // 不可见时只存储信息，等 setVisible(true) 时显示
    }

    /**
     * 轮播显示歌曲信息：标题 → 艺术家 → 专辑 → 格式 → 长度
     */
    private void rotateInfoDisplay() {
        if (infoLabel == null) return;

        String displayText = getDisplayText(infoDisplayIndex);

        // 第一次显示：直接设置文本，不用动画
        String current = infoLabel.getText();
        if (current == null || current.isEmpty()) {
            infoLabel.setText(displayText);
            infoDisplayIndex++;
            startPauseBeforeNext();
            return;
        }

        // 后续切换：启动垂直滚动动画
        startTextAnimation(displayText);
        infoDisplayIndex++;
    }

    private String getDisplayText(int index) {
        switch (index % 5) {
            case 0: return currentTitle.isEmpty() ? org.ttplayer.util.Messages.get("song.unknownTitle") : currentTitle;
            case 1: return currentArtist.isEmpty() ? org.ttplayer.util.Messages.get("song.unknownArtist") : "♪ " + currentArtist;
            case 2: return currentAlbum.isEmpty() ? org.ttplayer.util.Messages.get("song.unknownAlbum") : "♫ " + currentAlbum;
            case 3: return currentFormat.isEmpty() ? org.ttplayer.util.Messages.get("song.unknownFormat") : org.ttplayer.util.Messages.get("song.formatPrefix") + currentFormat;
            case 4: return currentDuration.isEmpty() ? org.ttplayer.util.Messages.get("song.unknownLength") : org.ttplayer.util.Messages.get("song.durationPrefix") + currentDuration;
            default: return "";
        }
    }

    /**
     * 启动文本切换动画（慢速向上滚动）
     */
    private void startTextAnimation(String newText) {
        if (infoLabel == null) return;

        // 停止之前的动画
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        stopMarquee();
        if (pauseTimer != null && pauseTimer.isRunning()) {
            pauseTimer.stop();
        }

        String labelText = infoLabel.getText();
        currentText = (labelText != null) ? labelText : "";
        nextText = (newText != null) ? newText : "";
        scrollOffset = 0;
        isAnimating = true;

        // 动画定时器：50ms 一帧，每次移动 1 像素（更慢）
        animationTimer = new Timer(50, e -> {
            scrollOffset += 1;

            // 动画完成
            if (scrollOffset >= infoLabel.getHeight()) {
                animationTimer.stop();
                isAnimating = false;
                infoLabel.setText(nextText);

                // 动画完成后，检查是否需要跑马灯
                FontMetrics fm = infoLabel.getFontMetrics(infoLabel.getFont());
                if (fm != null) {
                    int textWidth = fm.stringWidth(nextText);
                    if (textWidth > infoLabel.getWidth()) {
                        // 文本太长，启动跑马灯，跑完再停顿
                        startMarquee(nextText);
                    } else {
                        // 文本不超长，停顿后进入下一轮
                        startPauseBeforeNext();
                    }
                }
            }

            infoLabel.repaint();
        });
        animationTimer.start();
    }

    /**
     * 跑马灯完成后停顿，再进入下一轮
     */
    private void startPauseBeforeNext() {
        rotationPaused = true;
        pauseTimer = new Timer(1500, e -> {
            pauseTimer.stop();
            rotationPaused = false;
            rotateInfoDisplay();
        });
        pauseTimer.setRepeats(false);
        pauseTimer.start();
    }

    /**
     * 启动跑马灯（从右到左滚动，滚完一轮后停顿再切页）
     */
    private void startMarquee(String displayText) {
        stopMarquee();
        marqueeOffset = 0;
        isMarquee = true;

        marqueeTimer = new Timer(40, e -> {
            marqueeOffset += 1;

            FontMetrics fm = infoLabel.getFontMetrics(infoLabel.getFont());
            int textWidth = fm != null ? fm.stringWidth(displayText) : infoLabel.getWidth();
            int totalScroll = textWidth + infoLabel.getWidth();

            // 滚动完一轮后停止，进入停顿
            if (marqueeOffset >= totalScroll) {
                marqueeTimer.stop();
                isMarquee = false;
                marqueeOffset = 0;
                infoLabel.setText(displayText);
                startPauseBeforeNext();
            }

            infoLabel.repaint();
        });
        marqueeTimer.start();
    }

    private void stopMarquee() {
        if (marqueeTimer != null && marqueeTimer.isRunning()) {
            marqueeTimer.stop();
        }
        isMarquee = false;
        marqueeOffset = 0;
    }

    private void stopAllAnimations() {
        if (animationTimer != null && animationTimer.isRunning()) animationTimer.stop();
        stopMarquee();
        if (pauseTimer != null && pauseTimer.isRunning()) pauseTimer.stop();
        isAnimating = false;
        rotationPaused = false;
    }

    private static void tip(JComponent comp, String text) {
        if (comp != null) comp.setToolTipText(text);
    }

    private int parseAlign(String a) {
        if (a == null) return SwingConstants.LEFT;
        if (a.contains("right")) return SwingConstants.RIGHT;
        if (a.contains("center")) return SwingConstants.CENTER;
        return SwingConstants.LEFT;
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) {
        for (TtSkin.WindowDef wd : skin.getWindows()) {
            if (wd.name.equals(name)) return wd;
        }
        return null;
    }
}
