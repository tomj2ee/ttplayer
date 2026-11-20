package org.ttplayer.ui;

import org.ttplayer.app.WindowHub;
import org.ttplayer.audio.TtVisualSettings;
import org.ttplayer.engine.PlaybackSettings;
import org.ttplayer.lyrics.LyricSettings;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class SystemOptionsDialog extends JDialog {
    private static final String[] PAGES = {"system", "playback", "conversion", "skins", "visual", "lyrics", "associations", "about"};
    final WindowHub hub;
    final OptionsMediaPages media;
    private final JList<String> navigation = new JList<>();
    private final JPanel body = new JPanel(new BorderLayout());
    private final JLabel heading = OptionsStyle.label("");
    private final JLabel footerHint = OptionsStyle.note("");
    private final JButton primary, close, defaultSkin;
    private final int[] scrollPositions = new int[PAGES.length];
    private JScrollPane scroll;
    private int selectedPage = -1;
    private boolean changingVisual, refreshing;
    boolean desktopLyrics;
    private final PropertyChangeListener visualListener = event -> {
        if (!changingVisual && selectedPage == 4 && isVisible()) SwingUtilities.invokeLater(this::refreshPage);
    };

    public SystemOptionsDialog(WindowHub hub) {
        super((Window) null, Messages.get("dialog.optionsTitle"), ModalityType.MODELESS);
        this.hub = hub;
        media = new OptionsMediaPages(this);
        setName("systemOptions");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setIconImages(AppIcons.getWindowImages());
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(OptionsStyle.BACKGROUND); root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel sidebar = OptionsStyle.card();
        sidebar.setPreferredSize(new Dimension(148, 100));
        navigation.setName("options.navigation");
        navigation.setFont(FontUtils.getDefaultChineseFont(13));
        navigation.setFixedCellHeight(46);
        navigation.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                int height = Math.max(18, Math.min(46, navigation.getHeight() / PAGES.length));
                if (height != navigation.getFixedCellHeight()) navigation.setFixedCellHeight(height);
            }
        });
        navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigation.setBackground(OptionsStyle.SURFACE);
        navigation.setCellRenderer(new NavigationRenderer());
        navigation.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing && navigation.getSelectedIndex() >= 0)
                selectPage(navigation.getSelectedIndex());
        });
        sidebar.add(navigation, BorderLayout.CENTER);
        JLabel brand = OptionsStyle.note("TTPLAYER"); brand.setBorder(new EmptyBorder(12, 10, 8, 0));
        sidebar.add(brand, BorderLayout.SOUTH); root.add(sidebar, BorderLayout.WEST);
        JPanel card = OptionsStyle.card();
        JPanel header = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics); graphics.setColor(OptionsStyle.ACCENT); graphics.fillRect(0, 0, 3, getHeight());
            }
        };
        header.setBackground(OptionsStyle.HEADER); header.setPreferredSize(new Dimension(100, 44));
        heading.setFont(FontUtils.getDefaultChineseFont(16)); heading.setForeground(OptionsStyle.ACCENT);
        heading.setBorder(new EmptyBorder(0, 12, 0, 8)); header.add(heading);
        card.add(header, BorderLayout.NORTH);
        body.setBackground(OptionsStyle.SURFACE); body.setBorder(new EmptyBorder(8, 0, 0, 0));
        card.add(body, BorderLayout.CENTER); root.add(card, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(10, 0)); footer.setOpaque(false);
        primary = OptionsStyle.button("", this::primaryAction);
        primary.setName("options.primary"); primary.setPreferredSize(new Dimension(106, 30));
        close = OptionsStyle.button(Messages.get("options.close"), () -> setVisible(false), true);
        close.setName("options.close"); close.setPreferredSize(new Dimension(90, 30));
        defaultSkin = OptionsStyle.button(Messages.get("options.skins.default"), () -> media.applySkin(WindowHub.DEFAULT_SKIN));
        defaultSkin.setName("options.skins.default"); defaultSkin.setPreferredSize(new Dimension(148, 30));
        footer.add(defaultSkin, BorderLayout.WEST); footer.add(footerHint, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); buttons.setOpaque(false);
        buttons.add(primary); buttons.add(Box.createHorizontalStrut(16)); buttons.add(close);
        footer.add(buttons, BorderLayout.EAST); root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().registerKeyboardAction(event -> setVisible(false), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(primary);
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowGainedFocus(java.awt.event.WindowEvent event) {
                if (isVisible()) SwingUtilities.invokeLater(SystemOptionsDialog.this::refreshPage);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                if (selectedPage == 6) refreshPage();
            }
        });
        TtVisualSettings.addListener(visualListener);
        refreshAfterReload();
        selectPage(0);
        Rectangle screen = MiniLyricLayout.usableBounds(hub.getPlayer());
        setMinimumSize(new Dimension(480, 360));
        setSize(Math.min(760, screen.width), Math.min(620, screen.height));
        setLocationRelativeTo(hub.isMiniMode() ? hub.getMiniWindow() : hub.getPlayer());
        setLocation(Math.max(screen.x, Math.min(getX(), screen.x + screen.width - getWidth())),
                Math.max(screen.y, Math.min(getY(), screen.y + screen.height - getHeight())));
    }

    public int getSelectedPage() { return selectedPage; }

    public void open(int page) {
        if (page >= 0 && page < PAGES.length) selectPage(page);
        else refreshPage();
        setVisible(true); toFront();
    }

    public void refreshAfterReload() {
        refreshing = true;
        setTitle(Messages.get("dialog.optionsTitle"));
        String[] labels = new String[PAGES.length];
        for (int index = 0; index < labels.length; index++) labels[index] = Messages.get("options.page." + PAGES[index]);
        navigation.setListData(labels);
        if (selectedPage >= 0) navigation.setSelectedIndex(selectedPage);
        close.setText(Messages.get("options.close")); defaultSkin.setText(Messages.get("options.skins.default"));
        refreshing = false;
        if (selectedPage >= 0) refreshPage();
    }

    public void selectPage(int index) {
        if (index < 0 || index >= PAGES.length) return;
        rememberScroll(); selectedPage = index;
        refreshing = true; navigation.setSelectedIndex(index); refreshing = false;
        rebuildPage();
    }

    void refreshPage() {
        if (selectedPage < 0 || refreshing) return;
        rememberScroll(); rebuildPage();
    }

    private void rememberScroll() {
        if (scroll != null && selectedPage >= 0) scrollPositions[selectedPage] = scroll.getVerticalScrollBar().getValue();
    }

    private void rebuildPage() {
        String title = Messages.get("options.page." + PAGES[selectedPage]);
        if (selectedPage == 3 && media.skinCount() >= 0) title += " (" + media.skinCount() + ")";
        heading.setText(title);
        OptionsStyle.Rows rows;
        switch (selectedPage) {
            case 0: rows = systemPage(); break;
            case 1: rows = playbackPage(); break;
            case 2: rows = media.conversionPage(); break;
            case 3: rows = media.skinsPage(); break;
            case 4: rows = OptionsAppearancePages.visual(this); break;
            case 5: rows = OptionsAppearancePages.lyrics(this); break;
            case 6: rows = media.associationsPage(); break;
            default: rows = OptionsAppearancePages.about(); break;
        }
        body.removeAll(); scroll = OptionsStyle.scroll(rows); scroll.setName("options.scroll");
        body.add(scroll);
        if (selectedPage == 3) body.add(media.skinsFooter(), BorderLayout.SOUTH);
        updateFooter();
        JScrollPane currentScroll = scroll;
        int position = scrollPositions[selectedPage];
        SwingUtilities.invokeLater(() -> { if (currentScroll == scroll) currentScroll.getVerticalScrollBar().setValue(position); });
        body.revalidate(); body.repaint();
    }

    void updateFooter() {
        defaultSkin.setVisible(selectedPage == 3);
        footerHint.setVisible(selectedPage != 3);
        String action = selectedPage == 2 ? (media.isConverting() ? "options.conversion.cancel" : "options.conversion.start")
                : selectedPage == 3 ? "options.skins.apply" : selectedPage == 6 && media.associations.isSupported()
                ? (com.sun.jna.Platform.isMac() ? "options.associations.setDefault" : "options.associations.register") : "options.save";
        primary.setText(Messages.get(action));
        primary.setEnabled(selectedPage != 6 || !media.isAssociating());
        footerHint.setText(Messages.get(selectedPage == 6 ? (com.sun.jna.Platform.isMac() ? "options.associations.macHint" : "options.associations.hint") : "options.live"));
    }

    private void primaryAction() {
        switch (selectedPage) {
            case 2: media.toggleConversion(); break;
            case 3: media.applySelectedSkin(); break;
            case 6: if (media.associations.isSupported()) media.registerFiles(); else save(); break;
            default: save(); break;
        }
    }

    public void save() {
        hub.getPlayerEngine().savePlaybackPosition();
        WindowConfig.saveAll(hub.getPlayer(), hub.getLyric(), hub.getEq(), hub.getPlaylist(), hub.getMiniWindow(), hub.isMiniMode(), hub.getCurrentSkinPath());
        PlaylistConfig.save(hub.getPlaylistManager(), hub.getPlayerEngine().getCurrentSongIndex());
        footerHint.setText(Messages.get("options.saved"));
    }

    private OptionsStyle.Rows systemPage() {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        check(rows, "dialog.miniOnStart", MenuSettings.flag("miniOnStart", false), value -> MenuSettings.set("miniOnStart", value), 42);
        check(rows, "dialog.rememberPos", MenuSettings.flag("rememberPosition", true), value -> MenuSettings.set("rememberPosition", value), 42);
        check(rows, "menu.pauseAfterPlay", hub.getPlayerEngine().isPauseAfterPlay(), hub.getPlayerEngine()::setPauseAfterPlay, 42);
        check(rows, "options.system.autoLyrics", LyricSettings.load().autoOnline, this::setAutoLyrics, 42);
        rows.row(Box.createVerticalStrut(20), 60);
        rows.row(OptionsStyle.note(Messages.get("options.system.exitSave")));
        return rows;
    }

    private OptionsStyle.Rows playbackPage() {
        OptionsStyle.Rows rows = new OptionsStyle.Rows();
        PlaybackSettings settings = PlaybackSettings.get();
        JPanel title = new JPanel(new BorderLayout()); title.setOpaque(false);
        JLabel label = OptionsStyle.label(Messages.get("options.page.playback")); label.setForeground(OptionsStyle.ACCENT); title.add(label);
        JButton reset = OptionsStyle.button(Messages.get("options.reset"), () -> {
            PlaybackSettings.update(new PlaybackSettings());
            hub.getPlayerEngine().setPauseAfterPlay(false); hub.menus.execute("mode:3"); refreshPage();
        });
        reset.setName("options.playback.reset"); title.add(reset, BorderLayout.EAST); rows.row(title);
        check(rows, "options.playback.autoPlay", settings.autoPlay, value -> changePlayback(next -> next.autoPlay = value));
        check(rows, "options.playback.resume", settings.resumePosition, value -> changePlayback(next -> next.resumePosition = value));
        check(rows, "options.playback.stopOnError", settings.stopOnError, value -> changePlayback(next -> next.stopOnError = value));
        check(rows, "menu.pauseAfterPlay", hub.getPlayerEngine().isPauseAfterPlay(), hub.getPlayerEngine()::setPauseAfterPlay);
        JButton mode = OptionsStyle.button(modeLabel(), () -> {}); mode.setName("options.playback.mode");
        mode.addActionListener(event -> { hub.menus.execute("mode:" + (hub.getPlayerEngine().getPlayMode() + 1) % 5); mode.setText(modeLabel()); });
        rows.row(mode);
        valueRow(rows, "options.playback.interval", 0, 10000, settings.intervalMs, "ms", value -> changePlayback(next -> next.intervalMs = value));
        valueRow(rows, "options.playback.buffer", 50, 2000, settings.bufferMs, "ms", value -> changePlayback(next -> next.bufferMs = value));
        check(rows, "options.playback.fade", settings.fadeIn, value -> changePlayback(next -> next.fadeIn = value));
        valueRow(rows, "options.playback.fadeTime", 0, 3000, settings.fadeMs, "ms", value -> changePlayback(next -> next.fadeMs = value));
        rows.row(OptionsStyle.note(Messages.get("options.playback.rgTitle")));
        check(rows, "options.playback.replayGain", settings.replayGain, value -> changePlayback(next -> next.replayGain = value));
        check(rows, "options.playback.preventClipping", settings.preventClipping, value -> changePlayback(next -> next.preventClipping = value));
        valueRow(rows, "options.playback.preamp", -12, 12, settings.preampDb, "dB", value -> changePlayback(next -> next.preampDb = value));
        rows.row(OptionsStyle.note(Messages.get("options.playback.untagged")));
        rows.row(OptionsStyle.note(Messages.get("options.playback.live")));
        rows.row(OptionsStyle.note(Messages.get("options.playback.noEdits")));
        return rows;
    }

    private String modeLabel() { return Messages.get("menu.mode") + ": " + Messages.get("menu.playMode." + hub.getPlayerEngine().getPlayMode()); }
    private void changePlayback(Consumer<PlaybackSettings> change) { PlaybackSettings next = PlaybackSettings.get(); change.accept(next); PlaybackSettings.update(next); }

    JCheckBox check(OptionsStyle.Rows rows, String key, boolean value, Consumer<Boolean> changed) { return check(rows, key, value, changed, 30); }
    JCheckBox check(OptionsStyle.Rows rows, String key, boolean value, Consumer<Boolean> changed, int height) {
        JCheckBox checkbox = OptionsStyle.checkbox(Messages.get(key), value, changed); checkbox.setName(key); rows.row(checkbox, height); return checkbox;
    }

    JSlider valueRow(OptionsStyle.Rows rows, String key, int minimum, int maximum, int value, String unit, IntConsumer changed) {
        JPanel row = new JPanel(new OptionsLayout.Leading(174, 120, 6)); row.setOpaque(false);
        JLabel label = OptionsStyle.label(Messages.get(key) + ": " + value + " " + unit);
        JSlider slider = OptionsStyle.slider(minimum, maximum, value); slider.setName(key);
        slider.addChangeListener(event -> {
            label.setText(Messages.get(key) + ": " + slider.getValue() + " " + unit);
            label.setToolTipText(label.getText()); changed.accept(slider.getValue());
        });
        row.add(label, BorderLayout.WEST); row.add(slider); rows.row(row); return slider;
    }

    void setAutoLyrics(boolean value) {
        LyricSettings settings = LyricSettings.load(); settings.autoOnline = value; settings.save(); lyricsChanged();
        if (value && hub.getLyric().getCurrentSong() != null && hub.getLyric().getRenderer().getLyrics().isEmpty())
            hub.getLyric().loadLyrics(hub.getLyric().getCurrentSong());
    }

    void lyricsChanged() { hub.getLyric().refreshSettings(); hub.getLyric().notifyPresentationChanged(); hub.getLyric().repaint(); }

    void changeVisual(boolean customize, Consumer<TtVisualSettings> change) {
        TtVisualSettings settings = customize ? hub.getPlayer().getVisualizer().getEffectiveSettings() : TtVisualSettings.get();
        if (customize) settings.custom = true;
        change.accept(settings);
        changingVisual = true;
        try { TtVisualSettings.update(settings); } finally { changingVisual = false; }
    }

    void changeLyric(boolean desktop, boolean customize, Consumer<LyricSettings.Style> change) {
        LyricSettings settings = LyricSettings.load();
        LyricSettings.Style style = desktop ? settings.desktop : settings.window;
        if (customize && !style.custom) {
            LyricSettings.Style skin = hub.getLyric().getSkinStyle(desktop);
            style.normal = skin.normal; style.highlight = skin.highlight; style.word = skin.word;
            style.background = skin.background; style.fontSize = skin.fontSize; style.custom = true;
        }
        change.accept(style); settings.save(); lyricsChanged();
    }

    void error(Throwable error) { JOptionPane.showMessageDialog(this, error.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE); }

    @Override public void dispose() { media.dispose(); TtVisualSettings.removeListener(visualListener); super.dispose(); }

    private static final class NavigationRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel label = new JLabel((selected ? "›  " : "   ") + value) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D canvas = OptionsStyle.graphics(graphics);
                    if (selected || focused) {
                        canvas.setColor(selected ? OptionsStyle.ACCENT : OptionsStyle.SELECTION);
                        canvas.fillRoundRect(0, 2, getWidth(), getHeight() - 6, 10, 10);
                    }
                    canvas.dispose(); super.paintComponent(graphics);
                }
            };
            label.setOpaque(false); label.setBorder(new EmptyBorder(0, 8, 0, 0));
            label.setToolTipText(String.valueOf(value));
            label.setFont(list.getFont()); label.setForeground(selected ? OptionsStyle.SURFACE : OptionsStyle.TEXT);
            return label;
        }
    }
}
