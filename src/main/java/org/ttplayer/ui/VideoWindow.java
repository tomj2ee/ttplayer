package org.ttplayer.ui;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.Song;
import org.ttplayer.util.AppIcons;
import org.ttplayer.util.Messages;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public final class VideoWindow extends JFrame {
    private static final Color BACKGROUND = new Color(20, 24, 30);
    private static final Color FOREGROUND = new Color(230, 235, 241);
    private static final Color ACCENT = new Color(54, 169, 255);
    private final PlayerEngine engine;
    private final Runnable openFile;
    private final JPanel controls = new JPanel(new BorderLayout(8, 6));
    private final JSlider progress = new JSlider(0, 10000);
    private final JSlider volume = new JSlider(0, 100);
    private final JLabel time = new JLabel();
    private final VideoButton play = new VideoButton("play");
    private final VideoButton mute = new VideoButton("volume");
    private final VideoButton fullscreen = new VideoButton("fullscreen");
    private final Timer refreshTimer;
    private final JPanel surface;
    private boolean refreshing;
    private boolean fullscreenMode;
    private boolean positioned;
    private Rectangle normalBounds;
    private int normalState;
    private long lastInteraction = System.currentTimeMillis();
    private String errorMessage;
    private Cursor hiddenCursor;

    public VideoWindow(PlayerEngine engine, Runnable openFile) {
        this.engine = engine;
        this.openFile = openFile;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(AppIcons.getWindowImages());
        setMinimumSize(new Dimension(560, 340));
        surface = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                engine.getVideoFrames().paint((Graphics2D) graphics, getWidth(), getHeight());
                if (errorMessage != null || !engine.getVideoFrames().hasFrame()) {
                    Graphics2D canvas = (Graphics2D) graphics.create();
                    canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    canvas.setColor(FOREGROUND);
                    canvas.setFont(getFont().deriveFont(15f));
                    String message = errorMessage == null ? Messages.get("video.loading") : Messages.get("video.error");
                    canvas.drawString(message, Math.max(16, (getWidth() - canvas.getFontMetrics().stringWidth(message)) / 2), getHeight() / 2);
                    canvas.dispose();
                }
            }
        };
        surface.setBackground(Color.BLACK);
        surface.setFocusable(true);
        setLayout(new BorderLayout());
        add(surface, BorderLayout.CENTER);
        buildControls();
        add(controls, BorderLayout.SOUTH);
        installShortcuts();
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { showControls(); }
            @Override public void mouseDragged(MouseEvent event) { showControls(); }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) toggleFullscreen();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                changeVolume(-event.getWheelRotation() * 5);
                showControls();
            }
        };
        surface.addMouseListener(mouse);
        surface.addMouseMotionListener(mouse);
        surface.addMouseWheelListener(mouse);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                engine.stop();
                hidePlayback();
            }
        });
        refreshTimer = new Timer(33, event -> refreshControls());
        refreshTimer.setCoalesce(true);
        refreshLabels();
    }

    private void buildControls() {
        controls.setBackground(BACKGROUND);
        controls.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 14));
        styleSlider(progress);
        styleSlider(volume);
        progress.addChangeListener(event -> {
            if (refreshing) return;
            showControls();
            if (!progress.getValueIsAdjusting() && engine.getDuration() > 0) {
                engine.seekToMs((long) progress.getValue() * engine.getDuration() * 1000 / progress.getMaximum());
            }
            refreshTime();
        });
        volume.setPreferredSize(new Dimension(90, 24));
        volume.addChangeListener(event -> {
            if (refreshing) return;
            engine.setGainFromPercent(volume.getValue());
            if (volume.getValue() > 0) engine.setMuted(false);
            showControls();
        });
        controls.add(progress, BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        transport.setOpaque(false);
        transport.add(button("previous", "video.previous", engine::previous));
        play.addActionListener(event -> engine.playPause());
        play.setToolTipText(Messages.get("video.playPause"));
        transport.add(play);
        transport.add(button("next", "video.next", engine::next));
        transport.add(button("open", "video.open", openFile));
        row.add(transport, BorderLayout.WEST);
        time.setForeground(FOREGROUND);
        time.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        row.add(time, BorderLayout.CENTER);
        JPanel sound = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        sound.setOpaque(false);
        mute.addActionListener(event -> engine.mute());
        sound.add(mute);
        sound.add(volume);
        fullscreen.addActionListener(event -> toggleFullscreen());
        sound.add(fullscreen);
        row.add(sound, BorderLayout.EAST);
        controls.add(row, BorderLayout.CENTER);
    }

    private VideoButton button(String icon, String label, Runnable action) {
        VideoButton button = new VideoButton(icon);
        button.setToolTipText(Messages.get(label));
        button.putClientProperty("messageKey", label);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void styleSlider(JSlider slider) {
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setPreferredSize(new Dimension(120, 18));
        slider.setUI(new BasicSliderUI(slider) {
            @Override protected Dimension getThumbSize() { return new Dimension(12, 12); }
            @Override public void paintFocus(Graphics graphics) {}
            @Override public void paintTrack(Graphics graphics) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int center = trackRect.y + trackRect.height / 2;
                canvas.setColor(new Color(58, 66, 78));
                canvas.fillRoundRect(trackRect.x, center - 2, trackRect.width, 4, 4, 4);
                canvas.setColor(ACCENT);
                canvas.fillRoundRect(trackRect.x, center - 2, Math.max(0, thumbRect.x + thumbRect.width / 2 - trackRect.x), 4, 4, 4);
                canvas.dispose();
            }
            @Override public void paintThumb(Graphics graphics) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setColor(slider.isEnabled() ? FOREGROUND : Color.GRAY);
                canvas.fillOval(thumbRect.x + 1, thumbRect.y + 1, 10, 10);
                canvas.dispose();
            }
            @Override protected TrackListener createTrackListener(JSlider slider) {
                return new TrackListener() {
                    @Override public void mousePressed(MouseEvent event) {
                        if (slider.isEnabled() && SwingUtilities.isLeftMouseButton(event) && !thumbRect.contains(event.getPoint())) {
                            slider.setValue(valueForXPosition(event.getX()));
                        }
                        super.mousePressed(event);
                    }
                };
            }
        });
    }

    private void installShortcuts() {
        bind("SPACE", "playPause", engine::playPause);
        bind("LEFT", "backward", () -> seekRelative(-5000));
        bind("RIGHT", "forward", () -> seekRelative(5000));
        bind("UP", "volumeUp", () -> changeVolume(5));
        bind("DOWN", "volumeDown", () -> changeVolume(-5));
        bind("M", "mute", engine::mute);
        bind("F", "fullscreen", this::toggleFullscreen);
        bind("ENTER", "fullscreenEnter", this::toggleFullscreen);
        bind("ESCAPE", "exitFullscreen", () -> { if (fullscreenMode) toggleFullscreen(); });
        bind("control O", "open", openFile);
    }

    private void bind(String stroke, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                action.run();
                showControls();
            }
        });
    }

    private void seekRelative(long offset) {
        long duration = engine.getDuration() * 1000L;
        engine.seekToMs(Math.max(0, Math.min(duration > 0 ? duration : Long.MAX_VALUE, engine.getPositionMs() + offset)));
    }

    private void changeVolume(int offset) {
        engine.setGainFromPercent(engine.getVolumePercent() + offset);
        if (engine.getVolumePercent() > 0) engine.setMuted(false);
    }

    public void showPlayback(Song song) {
        errorMessage = null;
        surface.setToolTipText(null);
        refreshLabels();
        setTitle(song.toString() + " — TTPlayer " + Messages.get("video.title"));
        if (!positioned) {
            Rectangle screen = getGraphicsConfiguration().getBounds();
            double scale = Math.min(Math.min(960, screen.width - 80.0) / engine.getVideoFrames().getDisplayWidth(),
                    Math.min(600, screen.height - 160.0) / engine.getVideoFrames().getDisplayHeight());
            setSize(Math.max(560, (int) (engine.getVideoFrames().getDisplayWidth() * scale)),
                    Math.max(340, (int) (engine.getVideoFrames().getDisplayHeight() * scale) + 110));
            setLocationRelativeTo(null);
            positioned = true;
        }
        setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
        showControls();
        setVisible(true);
        toFront();
        surface.requestFocusInWindow();
    }

    public void showPlaybackError(String error) {
        Song song = engine.getCurrentSong();
        if (song != null && !isVisible()) showPlayback(song);
        errorMessage = error;
        surface.setToolTipText(error);
        surface.repaint();
    }

    public void hidePlayback() {
        setVisible(false);
        if (fullscreenMode) toggleFullscreen();
        errorMessage = null;
    }

    public void refreshLabels() {
        play.setToolTipText(Messages.get("video.playPause"));
        mute.setToolTipText(Messages.get("video.mute"));
        fullscreen.setToolTipText(Messages.get("video.fullscreen"));
        progress.setToolTipText(Messages.get("video.seek"));
        volume.setToolTipText(Messages.get("video.volume"));
        refreshButtonLabels(controls);
    }

    private void refreshButtonLabels(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton) {
                Object key = ((JButton) component).getClientProperty("messageKey");
                if (key != null) ((JButton) component).setToolTipText(Messages.get(key.toString()));
            }
            if (component instanceof Container) refreshButtonLabels((Container) component);
        }
    }

    private void refreshControls() {
        refreshing = true;
        long duration = engine.getDuration() * 1000L;
        progress.setEnabled(duration > 0 && engine.getCurrentSong() != null);
        if (!progress.getValueIsAdjusting()) {
            progress.setValue(duration > 0 ? (int) (engine.getPositionMs() * progress.getMaximum() / duration) : 0);
        }
        if (!volume.getValueIsAdjusting()) volume.setValue(engine.getVolumePercent());
        refreshing = false;
        refreshTime();
        play.repaint();
        mute.repaint();
        surface.repaint();
        if (fullscreenMode && engine.isPlaying() && !engine.isPaused()
                && System.currentTimeMillis() - lastInteraction > 2500 && !progress.getValueIsAdjusting()
                && !volume.getValueIsAdjusting()) {
            Point pointer = MouseInfo.getPointerInfo().getLocation();
            SwingUtilities.convertPointFromScreen(pointer, controls);
            if (!controls.isVisible() || !controls.contains(pointer)) {
                controls.setVisible(false);
                if (hiddenCursor == null) hiddenCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(), "video-hidden");
                surface.setCursor(hiddenCursor);
            }
        }
    }

    private void refreshTime() {
        long position = progress.getValueIsAdjusting()
                ? (long) progress.getValue() * engine.getDuration() / progress.getMaximum() : engine.getPositionMs() / 1000;
        time.setText(formatTime(position) + " / " + formatTime(engine.getDuration()));
    }

    private static String formatTime(long seconds) {
        return seconds >= 3600 ? String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void showControls() {
        lastInteraction = System.currentTimeMillis();
        controls.setVisible(true);
        surface.setCursor(Cursor.getDefaultCursor());
    }

    public void toggleFullscreen() {
        boolean visible = isVisible();
        if (!fullscreenMode) {
            normalBounds = getBounds();
            normalState = getExtendedState();
        }
        Rectangle screen = getGraphicsConfiguration().getBounds();
        dispose();
        fullscreenMode = !fullscreenMode;
        setUndecorated(fullscreenMode);
        setExtendedState(Frame.NORMAL);
        setBounds(fullscreenMode ? screen : normalBounds);
        if (!fullscreenMode) setExtendedState(normalState);
        showControls();
        if (visible) {
            setVisible(true);
            toFront();
            surface.requestFocusInWindow();
        }
    }

    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        MacFullscreenPresentation.update(this, fullscreenMode && visible);
        if (refreshTimer != null) {
            if (visible) refreshTimer.start(); else refreshTimer.stop();
        }
    }

    @Override public void dispose() {
        MacFullscreenPresentation.update(this, false);
        if (refreshTimer != null) refreshTimer.stop();
        super.dispose();
    }

    private final class VideoButton extends JButton {
        private final String icon;

        VideoButton(String icon) {
            this.icon = icon;
            setPreferredSize(new Dimension(32, 32));
            setFocusable(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D canvas = (Graphics2D) graphics.create();
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isRollover() || getModel().isPressed()) {
                canvas.setColor(new Color(45, 55, 68));
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            canvas.translate((getWidth() - 24) / 2, (getHeight() - 24) / 2);
            canvas.setColor("play".equals(icon) ? ACCENT : FOREGROUND);
            canvas.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (icon) {
                case "play":
                    if (engine.isPlaying() && !engine.isPaused()) {
                        canvas.fillRoundRect(7, 5, 4, 14, 1, 1);
                        canvas.fillRoundRect(14, 5, 4, 14, 1, 1);
                    } else canvas.fillPolygon(new int[] {8, 8, 19}, new int[] {4, 20, 12}, 3);
                    break;
                case "previous":
                case "next":
                    if ("previous".equals(icon)) { canvas.translate(24, 0); canvas.scale(-1, 1); }
                    canvas.fillPolygon(new int[] {6, 6, 16}, new int[] {6, 18, 12}, 3);
                    canvas.drawLine(18, 6, 18, 18);
                    break;
                case "open":
                    canvas.drawPolyline(new int[] {3, 3, 10, 13, 21, 21}, new int[] {18, 5, 5, 8, 8, 18}, 6);
                    canvas.drawPolygon(new int[] {3, 6, 22, 19}, new int[] {19, 11, 11, 19}, 4);
                    break;
                case "volume":
                    canvas.drawPolygon(new int[] {4, 8, 13, 13, 8, 4}, new int[] {9, 9, 5, 19, 15, 15}, 6);
                    if (engine.isMuted() || engine.getVolumePercent() == 0) {
                        canvas.drawLine(17, 9, 22, 15); canvas.drawLine(22, 9, 17, 15);
                    } else {
                        canvas.drawArc(10, 7, 10, 10, -55, 110);
                        canvas.drawArc(9, 3, 17, 18, -55, 110);
                    }
                    break;
                case "fullscreen":
                    canvas.drawPolyline(new int[] {4, 4, 9}, new int[] {9, 4, 4}, 3);
                    canvas.drawPolyline(new int[] {15, 20, 20}, new int[] {4, 4, 9}, 3);
                    canvas.drawPolyline(new int[] {20, 20, 15}, new int[] {15, 20, 20}, 3);
                    canvas.drawPolyline(new int[] {9, 4, 4}, new int[] {20, 20, 15}, 3);
                    break;
                default: break;
            }
            canvas.dispose();
        }
    }
}
