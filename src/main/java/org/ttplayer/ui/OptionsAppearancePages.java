package org.ttplayer.ui;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.audio.TtVisualSettings;
import org.ttplayer.lyrics.LyricSettings;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class OptionsAppearancePages {
    private OptionsAppearancePages() {}

    static OptionsStyle.Rows visual(SystemOptionsDialog view) {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        TtVisualSettings settings = view.hub.getPlayer().getVisualizer().getEffectiveSettings();
        JPanel title = new JPanel(new BorderLayout()); title.setOpaque(false);
        title.add(OptionsStyle.label(Messages.get("visual.type")));
        JButton reset = OptionsStyle.button(Messages.get("visual.reset"), () -> {
            view.changeVisual(false, next -> {
                TtVisualSettings defaults = new TtVisualSettings();
                next.mode = defaults.mode; next.custom = false;
                next.setFps(defaults.getFps()); next.setWide(defaults.isWide()); next.setBlur(defaults.isBlur());
                next.setBlurSpeed(defaults.getBlurSpeed()); next.setShowLabel(defaults.isShowLabel()); next.setLabelFont(defaults.getLabelFont());
                for (int index = 0; index < 6; index++) next.setColor(index, defaults.getColor(index));
            });
            view.refreshPage();
        });
        reset.setName("options.visual.reset"); title.add(reset, BorderLayout.EAST); rows.row(title);
        JButton mode = OptionsStyle.button(settings.mode.toString(), () -> {}); mode.setName("options.visual.mode");
        JPanel modes = new JPanel(new BorderLayout(6, 0)); modes.setOpaque(false);
        JButton previous = OptionsStyle.button("‹", () -> cycleVisual(view, -1, mode));
        JButton next = OptionsStyle.button("›", () -> cycleVisual(view, 1, mode));
        previous.setPreferredSize(new Dimension(30, 26)); next.setPreferredSize(new Dimension(30, 26));
        previous.setName("options.visual.previous"); next.setName("options.visual.next");
        mode.addActionListener(event -> cycleVisual(view, 1, mode));
        modes.add(previous, BorderLayout.WEST); modes.add(mode); modes.add(next, BorderLayout.EAST); rows.row(modes);
        JLabel fpsLabel = OptionsStyle.label(Messages.get("visual.fps", settings.getFps()));
        JSlider fps = OptionsStyle.slider(10, 40, settings.getFps()); fps.setName("options.visual.fps");
        fps.addChangeListener(event -> {
            fpsLabel.setText(Messages.get("visual.fps", fps.getValue()));
            view.changeVisual(false, changed -> changed.setFps(fps.getValue()));
        });
        JPanel speed = new JPanel(new OptionsLayout.Leading(260, 120, 6)); speed.setOpaque(false);
        speed.add(fpsLabel, BorderLayout.WEST); speed.add(fps); rows.row(speed);
        JCheckBox custom = view.check(rows, "visual.custom", settings.custom, value -> {
            view.changeVisual(value, changed -> changed.custom = value); view.refreshPage();
        });
        rows.row(OptionsStyle.note(Messages.get("visual.colors")));
        JPanel colors = new JPanel(new OptionsLayout.Columns(4, 0, 6)); colors.setOpaque(false);
        String[] keys = {"peak", "top", "middle", "bottom", "scope", "text"};
        for (int index = 0; index < 4; index++) colors.add(visualColor(view, index, keys[index], settings.getColor(index), custom));
        rows.row(colors);
        view.check(rows, "visual.wide", settings.isWide(), value -> {
            view.changeVisual(true, changed -> changed.setWide(value)); custom.setSelected(true);
        });
        rows.row(OptionsStyle.note(Messages.get("visual.mode.64")));
        JCheckBox blur = OptionsStyle.checkbox(Messages.get("visual.blur"), settings.isBlur(), value -> {
            view.changeVisual(true, changed -> changed.setBlur(value)); custom.setSelected(true);
        });
        blur.setName("visual.blur");
        rows.leading(visualColor(view, 4, keys[4], settings.getColor(4), custom), 100, blur);
        view.valueRow(rows, "options.visual.blurSpeed", 1, 10, settings.getBlurSpeed(), "", value -> {
            view.changeVisual(true, changed -> changed.setBlurSpeed(value)); custom.setSelected(true);
        });
        view.check(rows, "visual.showLabel", settings.isShowLabel(), value -> view.changeVisual(false, changed -> changed.setShowLabel(value)));
        JButton font = OptionsStyle.button(fontLabel(settings.getLabelFont()), () -> {}); font.setName("options.visual.font");
        font.addActionListener(event -> {
            int selected = (TtVisualSettings.get().getLabelFont() + 1) % 4;
            view.changeVisual(false, changed -> changed.setLabelFont(selected)); font.setText(fontLabel(selected));
        });
        rows.leading(visualColor(view, 5, keys[5], settings.getColor(5), custom), 100, font);
        return rows;
    }

    private static void cycleVisual(SystemOptionsDialog view, int direction, JButton label) {
        TtVisualizer.Mode[] modes = TtVisualizer.Mode.values();
        TtVisualizer.Mode next = modes[(TtVisualSettings.get().mode.ordinal() + direction + modes.length) % modes.length];
        view.changeVisual(false, settings -> settings.mode = next); label.setText(next.toString());
    }

    private static JButton visualColor(SystemOptionsDialog view, int index, String key, Color color, JCheckBox custom) {
        JButton button = colorButton(view, "visual.color." + key, color, chosen -> {
            view.changeVisual(true, settings -> settings.setColor(index, chosen)); custom.setSelected(true);
        });
        button.setName("options.visual.color." + index); return button;
    }

    private static JButton colorButton(SystemOptionsDialog view, String key, Color initial, Consumer<Color> changed) {
        JButton button = OptionsStyle.button(Messages.get(key), () -> {});
        button.putClientProperty("options.color", initial);
        button.setToolTipText(Messages.get(key) + String.format(" #%06X", initial.getRGB() & 0xffffff));
        button.addActionListener(event -> {
            Color selected = JColorChooser.showDialog(view, Messages.get(key), (Color) button.getClientProperty("options.color"));
            if (selected != null) {
                changed.accept(selected); button.putClientProperty("options.color", selected);
                button.setToolTipText(Messages.get(key) + String.format(" #%06X", selected.getRGB() & 0xffffff)); button.repaint();
            }
        });
        return button;
    }

    static OptionsStyle.Rows lyrics(SystemOptionsDialog view) {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        boolean desktop = view.desktopLyrics;
        LyricSettings settings = LyricSettings.load();
        LyricSettings.Style style = view.hub.getLyric().getEffectiveStyle(desktop);
        JButton window = OptionsStyle.button(Messages.get("options.lyrics.window"), () -> { view.desktopLyrics = false; view.refreshPage(); });
        JButton desk = OptionsStyle.button(Messages.get("options.lyrics.desktop"), () -> { view.desktopLyrics = true; view.refreshPage(); });
        window.setName("options.lyrics.window"); desk.setName("options.lyrics.desktop");
        window.setSelected(!desktop); desk.setSelected(desktop); rows.pair(window, desk);
        if (desktop) view.check(rows, "options.lyrics.dual", MenuSettings.flag("desktopDual", false), value -> {
            MenuSettings.set("desktopDual", value); view.lyricsChanged();
        });
        else {
            JButton mode = OptionsStyle.button(scrollLabel(style.scrollMode), () -> {}); mode.setName("options.lyrics.mode");
            mode.addActionListener(event -> {
                int next = (LyricSettings.load().window.scrollMode + 1) % 3;
                view.changeLyric(false, false, changed -> changed.scrollMode = next); mode.setText(scrollLabel(next));
            });
            rows.row(mode);
        }
        JButton alignment = OptionsStyle.button(alignmentLabel(style.alignment), () -> {}); alignment.setName("options.lyrics.alignment");
        alignment.addActionListener(event -> {
            int next = ((desktop ? LyricSettings.load().desktop : LyricSettings.load().window).alignment + 1) % 3;
            view.changeLyric(desktop, false, changed -> changed.alignment = next); alignment.setText(alignmentLabel(next));
        });
        rows.row(alignment);
        JCheckBox custom = OptionsStyle.checkbox(Messages.get("lyric.custom"), style.custom, value -> {
            view.changeLyric(desktop, value, changed -> changed.custom = value); view.refreshPage();
        });
        custom.setName("lyric.custom");
        JButton font = OptionsStyle.button(fontLabel(style.font), () -> {}); font.setName("options.lyrics.font");
        font.addActionListener(event -> {
            int next = ((desktop ? LyricSettings.load().desktop : LyricSettings.load().window).font + 1) % 4;
            view.changeLyric(desktop, true, changed -> changed.font = next); custom.setSelected(true); font.setText(fontLabel(next));
        });
        rows.row(font);
        view.valueRow(rows, "options.lyrics.fontSize", 8, 72, Math.max(8, Math.min(72, style.fontSize)), "", value -> {
            view.changeLyric(desktop, true, changed -> changed.fontSize = value); custom.setSelected(true);
        });
        if (desktop) view.check(rows, "lyric.transparent", style.transparent, value -> view.changeLyric(true, false, changed -> changed.transparent = value));
        else view.valueRow(rows, "options.lyrics.spacing", 0, 24, style.spacing, "", value -> view.changeLyric(false, false, changed -> changed.spacing = value));
        rows.row(custom);
        if (desktop) {
            view.check(rows, "lyric.shadow", style.shadow, value -> view.changeLyric(true, false, changed -> changed.shadow = value));
            view.check(rows, "lyric.outline", style.outline, value -> view.changeLyric(true, false, changed -> changed.outline = value));
            rows.row(OptionsStyle.note(Messages.get("options.lyrics.followSkin")));
        } else {
            view.check(rows, "lyric.fade", style.fade, value -> view.changeLyric(false, false, changed -> changed.fade = value));
            view.check(rows, "lyric.transparent", style.transparent, value -> view.changeLyric(false, false, changed -> changed.transparent = value));
            JCheckBox shadow = OptionsStyle.checkbox(Messages.get("lyric.shadow"), style.shadow, value -> view.changeLyric(false, false, changed -> changed.shadow = value));
            JCheckBox outline = OptionsStyle.checkbox(Messages.get("lyric.outline"), style.outline, value -> view.changeLyric(false, false, changed -> changed.outline = value));
            shadow.setName("lyric.shadow"); outline.setName("lyric.outline"); rows.pair(shadow, outline);
        }
        JPanel title = new JPanel(new BorderLayout()); title.setOpaque(false); title.add(OptionsStyle.label(Messages.get("options.lyrics.colors")));
        JButton reset = OptionsStyle.button(Messages.get("options.lyrics.reset"), () -> resetLyrics(view)); reset.setName("options.lyrics.reset");
        title.add(reset, BorderLayout.EAST); rows.row(title);
        String[] keys = {"normalColor", "highlightColor", "wordColor", "backgroundColor"};
        Color[] colors = {style.normal, style.highlight, style.word, style.background};
        JButton[] buttons = new JButton[4];
        for (int index = 0; index < buttons.length; index++) {
            final int selected = index;
            buttons[index] = colorButton(view, "lyric." + keys[index], colors[index], color -> {
                view.changeLyric(desktop, true, changed -> {
                    if (selected == 0) changed.normal = color;
                    else if (selected == 1) changed.highlight = color;
                    else if (selected == 2) changed.word = color;
                    else changed.background = color;
                }); custom.setSelected(true);
            });
            buttons[index].setName("options.lyrics.color." + index);
            buttons[index].setText(Messages.get("options.lyrics.color." + index));
        }
        rows.pair(buttons[0], buttons[1]); rows.pair(buttons[2], buttons[3]);
        view.valueRow(rows, "options.lyrics.textOpacity", 0, 100, style.textOpacity, "%", value -> view.changeLyric(desktop, false, changed -> changed.textOpacity = value));
        view.valueRow(rows, "options.lyrics.backgroundOpacity", 0, 100, style.backgroundOpacity, "%", value -> view.changeLyric(desktop, false, changed -> changed.backgroundOpacity = value));
        view.check(rows, "menu.karaoke", MenuSettings.flag("karaoke", true), value -> { MenuSettings.set("karaoke", value); view.lyricsChanged(); });
        rows.row(OptionsStyle.note(Messages.get("options.lyrics.nextLoad")));
        view.check(rows, "lyric.autoLoad", settings.autoLoad, value -> changeLoading(view, changed -> changed.autoLoad = value));
        view.check(rows, "lyric.trim", settings.trim, value -> changeLoading(view, changed -> changed.trim = value));
        view.check(rows, "lyric.seek", settings.seek, value -> changeLoading(view, changed -> changed.seek = value));
        view.check(rows, "lyric.autoOnline", settings.autoOnline, view::setAutoLyrics);
        view.check(rows, "menu.cover", MenuSettings.flag("cover", true), value -> { MenuSettings.set("cover", value); view.lyricsChanged(); });
        rows.row(OptionsStyle.note(Messages.get("options.lyrics.order")));
        return rows;
    }

    private static void changeLoading(SystemOptionsDialog view, Consumer<LyricSettings> changed) {
        LyricSettings settings = LyricSettings.load(); changed.accept(settings); settings.save(); view.lyricsChanged();
    }

    private static void resetLyrics(SystemOptionsDialog view) {
        LyricSettings defaults = new LyricSettings(); defaults.autoOnline = false; defaults.save();
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("karaoke", true); flags.put("cover", true); flags.put("desktopDual", false); MenuSettings.setAll(flags);
        view.lyricsChanged(); view.refreshPage();
    }

    private static String scrollLabel(int mode) { return Messages.get(new String[] {"lyric.smooth", "lyric.line", "lyric.single"}[mode]); }
    private static String alignmentLabel(int alignment) { return Messages.get(new String[] {"lyric.left", "lyric.center", "lyric.right"}[alignment]); }
    private static String fontLabel(int font) { return Messages.get("lyric.font") + ": " + Messages.get("options.font." + font) + " ›"; }

    static OptionsStyle.Rows about() {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        JPanel title = new JPanel(new BorderLayout(16, 0)); title.setOpaque(false);
        title.add(new JLabel(new ImageIcon(AppIcons.getImage(52))), BorderLayout.WEST);
        JPanel name = new JPanel(new GridLayout(2, 1)); name.setOpaque(false);
        JLabel heading = OptionsStyle.label(Messages.get("options.about.title")); heading.setFont(FontUtils.getDefaultChineseFont(24));
        name.add(heading); name.add(OptionsStyle.note("JAVA EDITION")); title.add(name); rows.row(title, 68);
        rows.row(OptionsStyle.label(Messages.get("options.about.description")));
        rows.row(OptionsStyle.note(Messages.get("options.about.features")));
        rows.row(OptionsStyle.note(Messages.get("options.about.visuals")));
        rows.row(OptionsStyle.note("Java 8+ / FFmpeg / Swing"));
        rows.row(OptionsStyle.note(Messages.get("options.about.port")));
        return rows;
    }
}
