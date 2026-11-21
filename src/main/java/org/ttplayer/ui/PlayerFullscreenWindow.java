package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.Song;
import org.ttplayer.util.FontUtils;
import org.ttplayer.util.Messages;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public final class PlayerFullscreenWindow extends JDialog {
    private final TtVisualizer visualizer = new TtVisualizer();
    private final LyricRenderer lyrics;
    private final Timer timer;
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
    private final JLabel title = new JLabel();
    private final JLabel artist = new JLabel();
    private String mode = "fullscreen_lyrics";
    private long lastActivity = System.currentTimeMillis();

    public PlayerFullscreenWindow(Window owner, LyricWindow source, TtVisualizer original, PlayerEngine engine,
                                  TtMenuCatalog.Actions actions) {
        super(owner);
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (original != null) visualizer.copySkinFrom(original);
        visualizer.setContextMenu(() -> TtMenuCatalog.create(TtMenuCatalog.Kind.FULLSCREEN, actions));
        lyrics = new LyricRenderer(source);
        lyrics.transparentBackground = true;
        lyrics.menuKind = TtMenuCatalog.Kind.FULLSCREEN;
        title.setForeground(new Color(0xe5f5f4));
        title.setFont(FontUtils.getDefaultChineseFont(23));
        artist.setForeground(new Color(0x97a8be));
        artist.setFont(FontUtils.getDefaultChineseFont(15));
        for (String[] control : new String[][] {{"tooltip.prev", "prev"}, {"tray.playPause", "play"},
                {"tooltip.next", "next"}, {"menu.fullscreenLyrics", "fullscreen_lyrics"},
                {"visual.fullscreen", "fullscreen_visual"}, {"menu.fullscreenVideo", "fullscreen_lyric_video"},
                {"visual.exitFullscreen", "fullscreen_off"}}) {
            JButton button = new JButton(Messages.get(control[0]));
            button.addActionListener(event -> actions.execute(control[1]));
            controls.add(button);
        }
        controls.setBackground(new Color(0x0d1727));
        JLayeredPane layers = new JLayeredPane() {
            protected void paintComponent(Graphics graphics) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                canvas.setPaint(new GradientPaint(0, 0, new Color(0x09101d), 0, getHeight(), new Color(0x111a2c)));
                canvas.fillRect(0, 0, getWidth(), getHeight());
                canvas.dispose();
            }
            public void doLayout() {
                int width = getWidth(), height = getHeight();
                visualizer.setBounds(0, 0, width, height);
                title.setBounds(30, 23, Math.max(1, width - 60), 32);
                artist.setBounds(30, 64, Math.max(1, width - 60), 26);
                boolean video = "fullscreen_lyric_video".equals(mode);
                lyrics.setBounds(width / 10, video ? height * 48 / 100 : 120, width * 8 / 10,
                        video ? height * 35 / 100 : Math.max(80, height - 240));
                controls.setBounds(0, height - controls.getPreferredSize().height, width, controls.getPreferredSize().height);
            }
        };
        layers.add(visualizer, JLayeredPane.DEFAULT_LAYER);
        layers.add(lyrics, JLayeredPane.PALETTE_LAYER);
        layers.add(title, JLayeredPane.MODAL_LAYER);
        layers.add(artist, JLayeredPane.MODAL_LAYER);
        layers.add(controls, JLayeredPane.MODAL_LAYER);
        setContentPane(layers);
        MouseAdapter activity = new MouseAdapter() {
            public void mouseMoved(MouseEvent event) { lastActivity = System.currentTimeMillis(); controls.setVisible(true); }
            public void mouseDragged(MouseEvent event) { mouseMoved(event); }
        };
        layers.addMouseMotionListener(activity);
        lyrics.addMouseMotionListener(activity);
        visualizer.addMouseMotionListener(activity);
        controls.addMouseMotionListener(activity);
        for (Component control : controls.getComponents()) control.addMouseMotionListener(activity);
        TtMenuCatalog.attachContext(layers, TtMenuCatalog.Kind.FULLSCREEN, actions);
        bind("ESCAPE", () -> actions.execute("fullscreen_off"));
        bind("F11", () -> actions.execute("fullscreen_off"));
        bind("1", () -> actions.execute("fullscreen_lyrics"));
        bind("2", () -> actions.execute("fullscreen_visual"));
        bind("3", () -> actions.execute("fullscreen_lyric_video"));
        bind("SPACE", engine::playPause);
        bind("V", visualizer::cycleMode);
        bind("F3", () -> actions.execute("lyric_search"));
        timer = new Timer(25, event -> {
            lyrics.copyPresentationFrom(source.getRenderer());
            int fontSize = Math.max(22, Math.min(48, getWidth() / 20));
            lyrics.lyricFont = source.getRenderer().lyricFont.deriveFont((float) fontSize);
            lyrics.currentFont = source.getRenderer().currentFont.deriveFont((float) fontSize);
            lyrics.setCurrentTime(engine.getPositionMs());
            if (visualizer.isVisible()) {
                if (engine.isPlaying()) visualizer.updateData(engine.getAudioData());
                else visualizer.clearDisplay();
            }
            Song song = engine.getCurrentSong();
            title.setText(song == null ? "TTPlayer" : song.title == null ? song.getFileName() : song.title);
            artist.setText(song == null || song.artist == null ? Messages.get("fullscreen.hint") : song.artist);
            if (System.currentTimeMillis() - lastActivity > 3000) controls.setVisible(false);
        });
        timer.start();
        setBounds(owner.getGraphicsConfiguration().getBounds());
        setMode(mode);
    }

    @Override public void setVisible(boolean visible) {
        super.setVisible(visible);
        MacFullscreenPresentation.update(this, visible);
        if (visible) { toFront(); requestFocus(); }
    }

    private void bind(String key, Runnable action) {
        getRootPane().registerKeyboardAction(event -> action.run(), KeyStroke.getKeyStroke(key), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    public String getMode() { return mode; }
    public void setMode(String value) {
        mode = value;
        visualizer.setVisible(!"fullscreen_lyrics".equals(value));
        lyrics.setVisible(!"fullscreen_visual".equals(value));
        lyrics.showCover = "fullscreen_lyrics".equals(value);
        lastActivity = System.currentTimeMillis();
        controls.setVisible(true);
        getContentPane().doLayout();
        repaint();
    }
    @Override
    public void dispose() {
        MacFullscreenPresentation.update(this, false);
        if (timer != null) timer.stop();
        super.dispose();
    }
}
