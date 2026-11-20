package org.ttplayer.app;

import org.ttplayer.audio.TtVisualizer;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.ui.*;
import org.ttplayer.util.*;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

public final class ApplicationMenus implements TtMenuCatalog.Actions {
    private final WindowHub hub;
    private PlayerFullscreenWindow fullscreen;
    private SystemOptionsDialog options;
    private List<String> skinSpecs = new ArrayList<>();

    ApplicationMenus(WindowHub hub) { this.hub = hub; }

    public void install() {
        hub.playlist.setMenuActions(this);
        hub.player.setMenuActions(this);
        hub.lyric.setMenuActions(this);
        hub.lyric.setOnLyricsChanged(hub.desktopLyric::setLyrics);
        hub.lyric.setOnOffsetChanged(hub.desktopLyric::setOffsetMs);
        hub.lyric.setOnPresentationChanged(hub.desktopLyric::refreshSettings);
        hub.miniWindow.setMenuActions(this);
        hub.desktopLyric.setMenuActions(this);
        TtMenuCatalog.attachContext(hub.miniWindow, TtMenuCatalog.Kind.MAIN, this);
        TtMenuCatalog.attachContext(hub.lyric, TtMenuCatalog.Kind.LYRIC, this);
        if (hub.player.btnSet != null) hub.player.btnSet.addActionListener(event ->
                TtMenuCatalog.create(TtMenuCatalog.Kind.MAIN, this).show(hub.player.btnSet, 0, hub.player.btnSet.getHeight()));
        for (Window window : windows()) window.setAlwaysOnTop(MenuSettings.flag("topmost", false));
        hub.miniWindow.setAlwaysOnTop(true);
        applyOpacity(MenuSettings.number("opacity", 100));
        bindMode(hub.player.btnModeSingle, 0);
        bindMode(hub.player.btnModeCircle, 1);
        bindMode(hub.player.btnModeSlider, 2);
        bindMode(hub.player.btnModeLoop, 3);
        bindMode(hub.player.btnModeRandom, 4);
        syncModeButtons();
        if (options != null && options.isDisplayable()) options.refreshAfterReload();
        bind(hub.playlist.getRootPane(), "ctrl A", "select_all");
        bind(hub.playlist.getRootPane(), "ctrl F", "find");
        bind(hub.playlist.getRootPane(), "F3", "find_next");
        bind(hub.playlist.getRootPane(), "alt ENTER", "properties");
        for (JFrame window : new JFrame[] {hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow}) {
            bind(window.getRootPane(), "ctrl shift R", "resetlayout");
            bind(window.getRootPane(), "F3", "lyric_search");
            bind(window.getRootPane(), "F5", "lyric");
            bind(window.getRootPane(), "F6", "desktop");
            bind(window.getRootPane(), "F9", "minimode");
            bind(window.getRootPane(), "F10", "preferences");
            bind(window.getRootPane(), "ctrl L", "lock");
            bind(window.getRootPane(), "ctrl shift L", "lyric_settings");
            bind(window.getRootPane(), "EQUALS", "lyric_offset_up");
            bind(window.getRootPane(), "shift EQUALS", "lyric_offset_up");
            bind(window.getRootPane(), "ADD", "lyric_offset_up");
            bind(window.getRootPane(), "MINUS", "lyric_offset_down");
            bind(window.getRootPane(), "SUBTRACT", "lyric_offset_down");
        }
    }

    private void bind(JRootPane root, String key, String action) {
        root.registerKeyboardAction(event -> { if (enabled(action)) execute(action); }, KeyStroke.getKeyStroke(key), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private Window[] windows() { return new Window[] {hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow}; }

    @Override
    public void execute(String action) {
        if (!enabled(action)) return;
        if (action.startsWith("mode:")) { hub.playerEngine.setPlayMode(Integer.parseInt(action.substring(5))); syncModeButtons(); return; }
        if (action.startsWith("visual:")) {
            org.ttplayer.audio.TtVisualSettings settings = org.ttplayer.audio.TtVisualSettings.get();
            settings.mode = TtVisualizer.Mode.fromId(Integer.parseInt(action.substring(7)));
            org.ttplayer.audio.TtVisualSettings.update(settings);
            return;
        }
        if (action.startsWith("volume:")) { hub.player.setVolumeFromPercent(Integer.parseInt(action.substring(7))); return; }
        if (action.startsWith("opacity:")) {
            int percentage = Integer.parseInt(action.substring(8));
            applyOpacity(percentage);
            MenuSettings.set("opacity", percentage);
            return;
        }
        if (action.startsWith("skin:")) {
            int index = Integer.parseInt(action.substring(5));
            if (index >= 0 && index < skinSpecs.size()) hub.reloadSkin(skinSpecs.get(index));
            return;
        }
        if (hub.eq.performMenuAction(action)) return;
        switch (action) {
            case "skin": showOptions(3); break;
            case "preferences": showOptions(-1); break;
            case "play": case "play_pause": hub.playerEngine.playPause(); break;
            case "stop": hub.playerEngine.stop(); break;
            case "prev": hub.playerEngine.previous(); break;
            case "next": hub.playerEngine.next(); break;
            case "pause_after": hub.playerEngine.setPauseAfterPlay(!hub.playerEngine.isPauseAfterPlay()); break;
            case "mute":
                hub.playerEngine.mute();
                if (hub.player.btnMute != null) hub.player.btnMute.setSelected(hub.playerEngine.isMuted());
                hub.miniWindow.setMuted(hub.playerEngine.isMuted());
                break;
            case "volume_up": hub.player.setVolumeFromPercent(Math.min(100, hub.playerEngine.getVolumePercent() + 5)); break;
            case "volume_down": hub.player.setVolumeFromPercent(Math.max(0, hub.playerEngine.getVolumePercent() - 5)); break;
            case "playlist": hub.playlist.setVisible(!hub.playlist.isVisible()); break;
            case "show": hub.showAllWindowsFromTray(); break;
            case "ontop":
                boolean topmost = !checked("ontop");
                for (Window window : windows()) window.setAlwaysOnTop(topmost);
                hub.miniWindow.setAlwaysOnTop(true);
                if (hub.lyric.isCompactMode()) hub.lyric.setAlwaysOnTop(true);
                MenuSettings.set("topmost", topmost);
                break;
            case "resetlayout":
                if (hub.inMiniMode) hub.toggleMiniMode();
                WindowConfig.clearWindowPositions(hub.currentSkinPath);
                WindowLayoutUtils.layoutBySkin(hub.player, hub.lyric, hub.eq, hub.playlist);
                hub.player.setVisible(true);
                hub.playlist.setVisible(true);
                break;
            case "lyric_search": hub.lyric.searchLyricsOnline(); break;
            case "lyric_settings": hub.lyric.showSettings(); break;
            case "lyric_export": hub.lyric.chooseExportLyrics(); break;
            case "lyric_offset_up": hub.lyric.adjustOffset(100); break;
            case "lyric_offset_down": hub.lyric.adjustOffset(-100); break;
            case "loadlrc": hub.lyric.loadLocalLyrics(); break;
            case "karaoke": hub.lyric.toggleKaraoke(); break;
            case "cover": MenuSettings.set("cover", !checked("cover")); hub.lyric.repaint(); break;
            case "lock": hub.desktopLyric.toggleLocked(); break;
            case "desktop_lines": hub.desktopLyric.toggleLines(); break;
            case "fullscreen_lyrics": case "fullscreen_visual": case "fullscreen_lyric_video":
                closeVisualFullscreen();
                if (fullscreen == null || !fullscreen.isDisplayable())
                    fullscreen = new PlayerFullscreenWindow(hub.player, hub.lyric, hub.player.getVisualizer(), hub.playerEngine, this);
                fullscreen.setMode(action);
                fullscreen.setVisible(true);
                fullscreen.toFront();
                break;
            case "fullscreen_off":
                if (fullscreen != null) fullscreen.dispose();
                closeVisualFullscreen();
                break;
            case "replaygain_scan": case "replaygain_update":
                ReplayGainDialog.show(hub.player, hub.playlist.getSelectedSongs(), hub.playerEngine, "replaygain_update".equals(action));
                break;
            case "link_ffmpeg": case "link_sdl":
                try { Desktop.getDesktop().browse(new URI("link_ffmpeg".equals(action) ? "https://ffmpeg.org/" : "https://www.libsdl.org/")); }
                catch (Exception error) { JOptionPane.showMessageDialog(hub.player, error.getMessage(), "TTPlayer", JOptionPane.ERROR_MESSAGE); }
                break;
            case "exit":
                WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow, hub.inMiniMode, hub.currentSkinPath);
                PlaylistConfig.save(hub.playlistManager, hub.playerEngine.getCurrentSongIndex());
                disposeTransient();
                hub.playerEngine.shutdown();
                hub.tray.remove();
                System.exit(0);
                break;
            default: hub.playlist.performMenuAction(action); break;
        }
        syncModeButtons();
        WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow, hub.inMiniMode, hub.currentSkinPath);
    }

    private void bindMode(org.ttplayer.controls.TtButton button, int mode) {
        if (button != null) button.addActionListener(event -> execute("mode:" + mode));
    }

    private void syncModeButtons() {
        org.ttplayer.controls.TtButton[] buttons = {hub.player.btnModeSingle, hub.player.btnModeCircle,
                hub.player.btnModeSlider, hub.player.btnModeLoop, hub.player.btnModeRandom};
        for (int index = 0; index < buttons.length; index++) if (buttons[index] != null)
            buttons[index].setSelected(hub.playerEngine.getPlayMode() == index);
    }

    private void applyOpacity(int percentage) {
        float opacity = Math.max(0.5f, Math.min(1f, percentage / 100f));
        for (Window window : windows()) {
            try { window.setOpacity(opacity); }
            catch (UnsupportedOperationException | IllegalArgumentException | IllegalComponentStateException ignored) {}
        }
    }

    @Override
    public boolean checked(String action) {
        if (action.startsWith("mode:")) return hub.playerEngine.getPlayMode() == Integer.parseInt(action.substring(5));
        if (action.startsWith("visual:")) return org.ttplayer.audio.TtVisualSettings.get().mode.id == Integer.parseInt(action.substring(7));
        if (action.startsWith("volume:")) return hub.playerEngine.getVolumePercent() == Integer.parseInt(action.substring(7));
        if (action.startsWith("opacity:")) return Math.round(hub.player.getOpacity() * 100) == Integer.parseInt(action.substring(8));
        if (action.startsWith("skin:")) {
            int index = Integer.parseInt(action.substring(5));
            return index >= 0 && index < skinSpecs.size() && skinName(skinSpecs.get(index)).equalsIgnoreCase(skinName(hub.currentSkinPath));
        }
        if (action.startsWith("fullscreen_")) return fullscreen != null && fullscreen.isDisplayable() && action.equals(fullscreen.getMode());
        if (action.startsWith("language:")) return Messages.getLocale().equals(Messages.fromTag(action.substring(9)));
        if (action.startsWith("list:")) {
            int index = Integer.parseInt(action.substring(5));
            return hub.playlistManager.getPlaylist(index) == hub.playlistManager.getCurrentPlaylist();
        }
        switch (action) {
            case "lyric": return hub.lyric.isVisible();
            case "equalizer": return hub.eq.isVisible();
            case "playlist": return hub.playlist.isVisible();
            case "desktop": return hub.desktopLyric.isVisible();
            case "minimode": return hub.inMiniMode;
            case "pause_after": return hub.playerEngine.isPauseAfterPlay();
            case "karaoke": return hub.lyric.isKaraokeEnabled();
            case "cover": return MenuSettings.flag("cover", true);
            case "lock": return hub.desktopLyric.isLocked();
            case "desktop_dual": return !hub.desktopLyric.isSingleLine();
            case "ontop": return hub.player.isAlwaysOnTop();
            case "mute": return hub.playerEngine.isMuted();
            default: return hub.eq.checked(action);
        }
    }

    @Override
    public boolean enabled(String action) {
        if (action.startsWith("lyric_offset_")) return hub.playerEngine.getCurrentSong() != null;
        if (java.util.Arrays.asList("properties", "open_file_location", "delete", "delete_other", "batch_info", "replaygain_scan", "replaygain_update").contains(action))
            return !hub.playlist.getSelectedSongs().isEmpty();
        boolean hasList = hub.playlistManager.getCurrentPlaylist() != null;
        boolean hasSongs = hasList && !hub.playlistManager.getCurrentPlaylist().songs.isEmpty();
        if (java.util.Arrays.asList("open", "folder", "add_url", "add_search", "export", "delete_list", "rename_list", "playlist_options", "filename_format").contains(action)) return hasList;
        if (action.startsWith("sort_") || java.util.Arrays.asList("clear_list", "delete_duplicates", "select_all", "select_invert", "find", "find_next").contains(action)) return hasSongs;
        if (action.startsWith("opacity:")) return hub.player.getGraphicsConfiguration().getDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
        return true;
    }

    @Override
    public String[] playlists() { return hub.playlistManager.getPlaylistNames(); }

    @Override
    public String[] skins() {
        java.util.Map<String, String> unique = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String spec : SkinLoader.listClasspathSkins()) if (!unique.containsKey(skinName(spec))) unique.put(skinName(spec), spec);
        skinSpecs = new ArrayList<>(unique.values());
        return skinSpecs.stream().map(spec -> skinName(spec).replaceFirst("(?i)\\.skn$", ""))
                .toArray(String[]::new);
    }

    private String skinName(String spec) {
        if (spec == null) return "";
        String normalized = spec.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    public void showOptions(int page) {
        if (options == null || !options.isDisplayable()) options = new SystemOptionsDialog(hub);
        options.open(page);
    }

    public void disposeTransient() { disposeTransient(false); }

    public void disposeTransient(boolean preserveOptions) {
        if (fullscreen != null) fullscreen.dispose();
        if (!preserveOptions && options != null) options.dispose();
    }

    private void closeVisualFullscreen() {
        if (hub.player.getVisualizer() != null) hub.player.getVisualizer().closeFullscreen();
        hub.miniWindow.closeVisualizerFullscreen();
    }
}
