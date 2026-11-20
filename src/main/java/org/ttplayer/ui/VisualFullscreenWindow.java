package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.util.Messages;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class VisualFullscreenWindow extends JDialog {
    private final TtVisualizer visualizer = new TtVisualizer();
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
    private final Timer hideControls = new Timer(3000, event -> controls.setVisible(false));

    public VisualFullscreenWindow(TtVisualizer source) {
        super(SwingUtilities.getWindowAncestor(source));
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        visualizer.copySkinFrom(source);
        visualizer.backgroundColor = Color.BLACK;
        JButton previous = new JButton("‹");
        previous.addActionListener(event -> visualizer.cycleMode(-1));
        JButton next = new JButton("›");
        next.addActionListener(event -> visualizer.cycleMode());
        JButton settings = new JButton(Messages.get("visual.settings"));
        settings.addActionListener(event -> visualizer.showSettings());
        JButton close = new JButton(Messages.get("visual.exitFullscreen"));
        close.addActionListener(event -> dispose());
        controls.add(previous);
        controls.add(new JLabel(Messages.get("visual.fullscreenHint")));
        controls.add(next);
        controls.add(settings);
        controls.add(close);
        JLayeredPane layers = new JLayeredPane() {
            @Override
            public void doLayout() {
                visualizer.setBounds(0, 0, getWidth(), getHeight());
                int height = controls.getPreferredSize().height;
                controls.setBounds(0, getHeight() - height, getWidth(), height);
            }
        };
        layers.add(visualizer, JLayeredPane.DEFAULT_LAYER);
        layers.add(controls, JLayeredPane.PALETTE_LAYER);
        setContentPane(layers);
        MouseAdapter activity = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) { showControls(); }
            @Override
            public void mouseDragged(MouseEvent event) { showControls(); }
        };
        visualizer.addMouseMotionListener(activity);
        for (Component control : controls.getComponents()) control.addMouseMotionListener(activity);
        controls.addMouseMotionListener(activity);
        bind("ESCAPE", this::dispose);
        bind("F11", this::dispose);
        bind("RIGHT", visualizer::cycleMode);
        bind("LEFT", () -> visualizer.cycleMode(-1));
        setBounds(source.getGraphicsConfiguration().getBounds());
        hideControls.setRepeats(false);
        showControls();
    }

    private void bind(String key, Runnable action) {
        getRootPane().registerKeyboardAction(event -> action.run(), KeyStroke.getKeyStroke(key),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void showControls() {
        controls.setVisible(true);
        hideControls.restart();
    }

    public void updateData(byte[] data) { visualizer.updateData(data); }
    public void clearDisplay() { visualizer.clearDisplay(); }

    @Override
    public void dispose() {
        hideControls.stop();
        super.dispose();
    }
}
