package org.ttplayer.ui;

import org.ttplayer.lyrics.LyricSettings;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.function.*;

public final class LyricSettingsDialog extends JDialog {
    private final LyricWindow owner;
    private LyricSettings draft = LyricSettings.load();
    private final JTabbedPane tabs = new JTabbedPane();
    private boolean karaoke = MenuSettings.flag("karaoke", true), cover = MenuSettings.flag("cover", true), dual = MenuSettings.flag("desktopDual", false);
    private long offset;

    public LyricSettingsDialog(LyricWindow owner) {
        this(owner, owner);
    }

    public LyricSettingsDialog(Window dialogOwner, LyricWindow owner) {
        super(dialogOwner, Messages.get("lyric.settings"), ModalityType.MODELESS);
        this.owner = owner;
        offset = owner.getOffsetMs();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        add(tabs, BorderLayout.CENTER);
        rebuild();
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton reset = new JButton(Messages.get("menu.reset"));
        reset.addActionListener(event -> {
            draft = new LyricSettings(); draft.autoOnline = false;
            karaoke = true; cover = true; dual = false;
            rebuild();
        });
        buttons.add(reset);
        JButton apply = new JButton(Messages.get("lyric.apply"));
        apply.addActionListener(event -> apply()); buttons.add(apply);
        JButton okay = new JButton(Messages.get("dialog.ok"));
        okay.addActionListener(event -> { apply(); dispose(); }); buttons.add(okay);
        JButton cancel = new JButton(Messages.get("dialog.cancel"));
        cancel.addActionListener(event -> dispose()); buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okay);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(610, 650);
        setMinimumSize(new Dimension(510, 480));
        setLocationRelativeTo(dialogOwner);
    }

    private void rebuild() {
        int selected = Math.max(0, tabs.getSelectedIndex());
        tabs.removeAll();
        tabs.addTab(Messages.get("lyric.title"), stylePanel(draft.window, false));
        tabs.addTab(Messages.get("menu.desktopLyric"), stylePanel(draft.desktop, true));
        JPanel general = new JPanel(new GridLayout(0, 1, 8, 8));
        general.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        general.add(toggle("lyric.autoLoad", draft.autoLoad, value -> draft.autoLoad = value));
        general.add(toggle("lyric.autoOnline", draft.autoOnline, value -> draft.autoOnline = value));
        general.add(toggle("lyric.trim", draft.trim, value -> draft.trim = value));
        general.add(toggle("lyric.seek", draft.seek, value -> draft.seek = value));
        general.add(toggle("menu.karaoke", karaoke, value -> karaoke = value));
        general.add(toggle("menu.cover", cover, value -> cover = value));
        general.add(toggle("desktopLyric.twoLine", dual, value -> dual = value));
        JPanel timing = new JPanel(new FlowLayout(FlowLayout.LEFT));
        timing.add(new JLabel(Messages.get("lyric.offset")));
        JSpinner correction = new JSpinner(new SpinnerNumberModel(offset, -3600000L, 3600000L, 100L));
        correction.addChangeListener(event -> offset = ((Number) correction.getValue()).longValue());
        timing.add(correction); general.add(timing);
        JButton export = new JButton(Messages.get("lyric.export"));
        export.addActionListener(event -> owner.chooseExportLyrics()); general.add(export);
        tabs.addTab(Messages.get("lyric.behavior"), new JScrollPane(general));
        tabs.setSelectedIndex(Math.min(selected, tabs.getTabCount() - 1));
    }

    private JPanel stylePanel(LyricSettings.Style style, boolean desktop) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel preview = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D canvas = (Graphics2D) graphics.create();
                canvas.setColor(new Color(0x142b35)); canvas.fillRect(0, 0, getWidth(), getHeight());
                LyricSettings.Style effective = style.copy();
                if (!style.custom) {
                    LyricSettings.Style skin = owner.getSkinStyle(desktop);
                    effective.normal = skin.normal; effective.highlight = skin.highlight;
                    effective.word = skin.word; effective.background = skin.background; effective.fontSize = skin.fontSize;
                }
                if (!effective.transparent) {
                    canvas.setColor(LyricTextPainter.alpha(effective.background, effective.backgroundOpacity / 100f));
                    canvas.fillRect(0, 0, getWidth(), getHeight());
                }
                Font font = style.custom ? FontUtils.getLyricFont(style.fontFamily(), Font.PLAIN, style.fontSize) : owner.getSkinFont(desktop);
                font = FontUtils.getLyricFontByText(Messages.get("lyric.preview.0") + Messages.get("lyric.preview.1") + Messages.get("lyric.preview.2"),
                        font.getFamily(), Font.PLAIN, font.getSize());
                float height = getHeight() / 3f;
                for (int index = 0; index < 3; index++) {
                    Color base = index == 1 ? effective.highlight : LyricTextPainter.alpha(effective.normal, effective.fade ? 0.78f : 1);
                    LyricTextPainter.paint(canvas, Messages.get("lyric.preview." + index),
                            new Rectangle2D.Float(10, index * height, getWidth() - 20, height), font, base,
                            effective.word, index == 1 ? 0.55f : 0, effective);
                }
                canvas.dispose();
            }
        };
        preview.setPreferredSize(new Dimension(400, 130));
        panel.add(preview, BorderLayout.NORTH);
        JPanel controls = new JPanel(new GridLayout(0, 2, 12, 7));
        JCheckBox custom = toggle("lyric.custom", style.custom, value -> {
            if (value) customize(style, desktop);
            style.custom = value; preview.repaint();
        });
        controls.add(custom);
        controls.add(toggle("lyric.fade", style.fade, value -> { style.fade = value; preview.repaint(); }));
        controls.add(toggle("lyric.transparent", style.transparent, value -> { style.transparent = value; preview.repaint(); }));
        controls.add(toggle("lyric.shadow", style.shadow, value -> { style.shadow = value; preview.repaint(); }));
        controls.add(toggle("lyric.outline", style.outline, value -> { style.outline = value; preview.repaint(); }));
        controls.add(new JLabel());
        addChoice(controls, "lyric.font", new String[] {Messages.get("lyric.fontDefault"), "Microsoft YaHei", "SimSun", "Arial"},
                style.font, value -> { customize(style, desktop); style.font = value; rebuild(); });
        addNumber(controls, "lyric.fontSize", style.fontSize, 8, 72, value -> { customize(style, desktop); style.fontSize = value; custom.setSelected(true); preview.repaint(); });
        addChoice(controls, "lyric.alignment", new String[] {Messages.get("lyric.left"), Messages.get("lyric.center"), Messages.get("lyric.right")},
                style.alignment, value -> { style.alignment = value; preview.repaint(); });
        if (!desktop) addChoice(controls, "lyric.scroll", new String[] {Messages.get("lyric.smooth"), Messages.get("lyric.line"), Messages.get("lyric.single")},
                style.scrollMode, value -> { style.scrollMode = value; preview.repaint(); });
        addNumber(controls, "lyric.spacing", style.spacing, 0, 24, value -> { style.spacing = value; preview.repaint(); });
        addNumber(controls, "lyric.textOpacity", style.textOpacity, 0, 100, value -> { style.textOpacity = value; preview.repaint(); });
        addNumber(controls, "lyric.backgroundOpacity", style.backgroundOpacity, 0, 100, value -> { style.backgroundOpacity = value; preview.repaint(); });
        LyricSettings.Style colors = style.custom ? style : owner.getSkinStyle(desktop);
        addColor(controls, "lyric.normalColor", colors.normal, value -> { customize(style, desktop); style.normal = value; rebuild(); });
        addColor(controls, "lyric.highlightColor", colors.highlight, value -> { customize(style, desktop); style.highlight = value; rebuild(); });
        addColor(controls, "lyric.wordColor", colors.word, value -> { customize(style, desktop); style.word = value; rebuild(); });
        addColor(controls, "lyric.backgroundColor", colors.background, value -> { customize(style, desktop); style.background = value; rebuild(); });
        panel.add(new JScrollPane(controls), BorderLayout.CENTER);
        return panel;
    }

    private void customize(LyricSettings.Style style, boolean desktop) {
        if (style.custom) return;
        LyricSettings.Style skin = owner.getSkinStyle(desktop);
        style.fontSize = skin.fontSize;
        style.normal = skin.normal; style.highlight = skin.highlight; style.word = skin.word; style.background = skin.background;
        style.custom = true;
    }

    private JCheckBox toggle(String key, boolean value, Consumer<Boolean> changed) {
        JCheckBox checkbox = new JCheckBox(Messages.get(key), value);
        checkbox.addActionListener(event -> changed.accept(checkbox.isSelected()));
        return checkbox;
    }
    private void addNumber(JPanel panel, String key, int value, int minimum, int maximum, IntConsumer changed) {
        panel.add(new JLabel(Messages.get(key)));
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 1));
        spinner.addChangeListener(event -> changed.accept(((Number) spinner.getValue()).intValue())); panel.add(spinner);
    }
    private void addChoice(JPanel panel, String key, String[] values, int selected, IntConsumer changed) {
        panel.add(new JLabel(Messages.get(key)));
        JComboBox<String> choice = new JComboBox<>(values);
        choice.setSelectedIndex(selected);
        choice.addActionListener(event -> changed.accept(choice.getSelectedIndex())); panel.add(choice);
    }
    private void addColor(JPanel panel, String key, Color value, Consumer<Color> changed) {
        panel.add(new JLabel(Messages.get(key)));
        JButton button = new JButton(String.format("#%06X", value.getRGB() & 0xffffff));
        button.setForeground(value.darker());
        button.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(this, Messages.get(key), value);
            if (selected != null) changed.accept(selected);
        });
        panel.add(button);
    }

    private void apply() {
        draft.save();
        MenuSettings.set("karaoke", karaoke); MenuSettings.set("cover", cover); MenuSettings.set("desktopDual", dual);
        owner.setOffsetMs(offset);
        owner.refreshSettings();
        owner.notifyPresentationChanged();
    }
}
