package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.audio.TtVisualSettings;
import org.ttplayer.util.Messages;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;

public final class VisualSettingsDialog extends JDialog {
    private final TtVisualizer source;
    private final JComboBox<TtVisualizer.Mode> modes = new JComboBox<>(TtVisualizer.Mode.values());
    private final JSlider fps = new JSlider(10, 40, 25);
    private final JLabel fpsLabel = new JLabel();
    private final JCheckBox custom = new JCheckBox(Messages.get("visual.custom"));
    private final JCheckBox wide = new JCheckBox(Messages.get("visual.wide"));
    private final JCheckBox blur = new JCheckBox(Messages.get("visual.blur"));
    private final JSlider blurSpeed = new JSlider(1, 10, 3);
    private final JLabel blurLabel = new JLabel();
    private final JCheckBox showLabel = new JCheckBox(Messages.get("visual.showLabel"));
    private final JComboBox<String> fonts = new JComboBox<>(new String[] {
            Messages.get("visual.defaultFont"), "Microsoft YaHei", "SimSun", "Arial"});
    private final JButton[] colors = new JButton[6];
    private final PropertyChangeListener settingsListener = event -> refresh();
    private boolean updating;

    public VisualSettingsDialog(TtVisualizer source) {
        super(SwingUtilities.getWindowAncestor(source), Messages.get("visual.settings"), ModalityType.MODELESS);
        this.source = source;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        int row = 0;
        addRow(form, row++, new JLabel(Messages.get("visual.type")), modes);
        addRow(form, row++, fpsLabel, fps);
        addRow(form, row++, custom, null);
        JPanel palette = new JPanel(new GridLayout(2, 3, 8, 8));
        palette.setBorder(BorderFactory.createTitledBorder(Messages.get("visual.colors")));
        String[] names = {"peak", "top", "middle", "bottom", "scope", "text"};
        for (int index = 0; index < colors.length; index++) {
            final int selected = index;
            JButton button = new JButton(Messages.get("visual.color." + names[index]));
            button.setOpaque(true);
            button.addActionListener(event -> {
                Color chosen = JColorChooser.showDialog(this, button.getText(), button.getBackground());
                if (chosen != null) change(true, settings -> settings.setColor(selected, chosen));
            });
            colors[index] = button;
            palette.add(button);
        }
        addRow(form, row++, palette, null);
        addRow(form, row++, wide, null);
        addRow(form, row++, blur, null);
        addRow(form, row++, blurLabel, blurSpeed);
        addRow(form, row++, showLabel, null);
        addRow(form, row++, new JLabel(Messages.get("visual.font")), fonts);
        JLabel hint = new JLabel(Messages.get("visual.livePreview"));
        addRow(form, row, hint, null);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton(Messages.get("visual.reset"));
        reset.addActionListener(event -> TtVisualSettings.update(new TtVisualSettings()));
        JButton close = new JButton(Messages.get("visual.close"));
        close.addActionListener(event -> dispose());
        buttons.add(reset);
        buttons.add(close);
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(close);
        bindControls();
        refresh();
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(source);
    }

    private void bindControls() {
        modes.addActionListener(event -> change(false,
                settings -> settings.mode = (TtVisualizer.Mode) modes.getSelectedItem()));
        custom.addActionListener(event -> change(false, settings -> settings.custom = custom.isSelected()));
        wide.addActionListener(event -> change(true, settings -> settings.setWide(wide.isSelected())));
        blur.addActionListener(event -> change(true, settings -> settings.setBlur(blur.isSelected())));
        showLabel.addActionListener(event -> change(false, settings -> settings.setShowLabel(showLabel.isSelected())));
        fonts.addActionListener(event -> change(false, settings -> settings.setLabelFont(fonts.getSelectedIndex())));
        fps.setMajorTickSpacing(10);
        fps.setPaintTicks(true);
        fps.setPaintLabels(true);
        fps.addChangeListener(event -> {
            fpsLabel.setText(Messages.get("visual.fps", fps.getValue()));
            if (!fps.getValueIsAdjusting()) change(false, settings -> settings.setFps(fps.getValue()));
        });
        blurSpeed.setMajorTickSpacing(1);
        blurSpeed.setPaintTicks(true);
        blurSpeed.addChangeListener(event -> {
            blurLabel.setText(Messages.get("visual.blurSpeed", blurSpeed.getValue()));
            if (!blurSpeed.getValueIsAdjusting()) change(true, settings -> settings.setBlurSpeed(blurSpeed.getValue()));
        });
    }

    private void change(boolean customize, Consumer<TtVisualSettings> edit) {
        if (updating) return;
        TtVisualSettings settings = TtVisualSettings.get();
        if (customize && !settings.custom) {
            settings = source.getEffectiveSettings();
            settings.custom = true;
        }
        edit.accept(settings);
        TtVisualSettings.update(settings);
    }

    private void refresh() {
        updating = true;
        try {
            TtVisualSettings settings = source.getEffectiveSettings();
            modes.setSelectedItem(settings.mode);
            fps.setValue(settings.getFps());
            fpsLabel.setText(Messages.get("visual.fps", settings.getFps()));
            custom.setSelected(settings.custom);
            wide.setSelected(settings.isWide());
            blur.setSelected(settings.isBlur());
            blurSpeed.setValue(settings.getBlurSpeed());
            blurLabel.setText(Messages.get("visual.blurSpeed", settings.getBlurSpeed()));
            showLabel.setSelected(settings.isShowLabel());
            fonts.setSelectedIndex(settings.getLabelFont());
            for (int index = 0; index < colors.length; index++) {
                Color color = settings.getColor(index);
                colors[index].setBackground(color);
                int brightness = color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114;
                colors[index].setForeground(brightness > 140000 ? Color.BLACK : Color.WHITE);
                colors[index].setToolTipText(String.format("#%06X", color.getRGB() & 0xffffff));
            }
        } finally {
            updating = false;
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        TtVisualSettings.addListener(settingsListener);
        refresh();
    }

    @Override
    public void removeNotify() {
        TtVisualSettings.removeListener(settingsListener);
        super.removeNotify();
    }

    private static void addRow(JPanel form, int row, JComponent label, JComponent control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.insets = new Insets(6, 4, 6, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = control == null ? 1 : 0;
        constraints.gridwidth = control == null ? 2 : 1;
        form.add(label, constraints);
        if (control != null) {
            constraints.gridx = 1;
            constraints.weightx = 1;
            form.add(control, constraints);
        }
    }
}
