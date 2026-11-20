package org.ttplayer.ui;

import org.ttplayer.util.Messages;
import org.ttplayer.util.TtMenuGlyphs;
import javax.swing.*;
import javax.swing.event.*;

public final class TtMenuCatalog {
    public enum Kind { ADD, DELETE, LIST, SORT, FIND, OPTIONS, MODE, MAIN, LYRIC, EQUALIZER, PRESETS,
        SKINS, PLAYLISTS, LANGUAGE, DESKTOP, FULLSCREEN, PLAYLIST_CONTEXT, REPLAYGAIN, TRAY,
        LINKS, PLAYBACK, VOLUME, VOLUME_LEVEL, VISUAL, SKIN_TRAY, OPACITY, VIEW, TRAY_FULLSCREEN }

    public interface Actions {
        void execute(String action);
        default boolean checked(String action) { return false; }
        default boolean enabled(String action) { return true; }
        default String[] playlists() { return new String[0]; }
        default String[] skins() { return new String[0]; }
    }

    private TtMenuCatalog() {}

    public static void attachContext(java.awt.Component component, Kind kind, Actions actions) {
        if (component instanceof org.ttplayer.audio.TtVisualizer || component instanceof LyricRenderer) return;
        component.addMouseListener(new java.awt.event.MouseAdapter() {
            private void show(java.awt.event.MouseEvent event) {
                if (event.isPopupTrigger()) create(kind, actions).show(event.getComponent(), event.getX(), event.getY());
            }
            public void mousePressed(java.awt.event.MouseEvent event) { show(event); }
            public void mouseReleased(java.awt.event.MouseEvent event) { show(event); }
        });
        if (component instanceof java.awt.Container)
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) attachContext(child, kind, actions);
    }

    public static JPopupMenu create(Kind kind, Actions actions) {
        TtMenuStyle.install();
        JPopupMenu popup = new JPopupMenu();
        populate(popup, kind, actions);
        popup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) { populate(popup, kind, actions); }
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {}
            public void popupMenuCanceled(PopupMenuEvent event) {}
        });
        return popup;
    }

    private static void populate(JPopupMenu popup, Kind kind, Actions actions) {
        popup.removeAll();
        switch (kind) {
            case ADD:
                item(popup, "menu.addFile", "open", actions);
                item(popup, "menu.addFolder", "folder", actions);
                popup.addSeparator();
                item(popup, "menu.addUrl", "add_url", actions);
                item(popup, "menu.addSearchResult", "add_search", actions);
                break;
            case DELETE:
                item(popup, "menu.deleteSelected", "delete", actions);
                item(popup, "menu.deleteNotSelected", "delete_other", actions);
                popup.addSeparator();
                item(popup, "menu.deleteDuplicates", "delete_duplicates", actions);
                item(popup, "menu.clearList", "clear_list", actions);
                break;
            case LIST:
                submenu(popup, "menu.switchList", Kind.PLAYLISTS, actions, 6);
                popup.addSeparator();
                item(popup, "menu.newList", "new_list", actions);
                item(popup, "menu.addList", "import", actions);
                popup.addSeparator();
                item(popup, "menu.saveList", "export", actions);
                item(popup, "menu.deleteList", "delete_list", actions);
                item(popup, "menu.saveAllLists", "save_all", actions);
                popup.addSeparator();
                item(popup, "menu.rename", "rename_list", actions);
                item(popup, "menu.sortByName", "sort_lists", actions);
                break;
            case SORT:
                item(popup, "menu.sortByTitle", "sort_title", actions);
                item(popup, "menu.sortByArtist", "sort_artist", actions);
                item(popup, "menu.sortByFilename", "sort_filename", actions);
                item(popup, "menu.sortByPath", "sort_path", actions);
                popup.addSeparator();
                item(popup, "menu.reverse", "sort_reverse", actions);
                item(popup, "menu.randomSort", "sort_random", actions);
                break;
            case FIND:
                item(popup, "menu.findDot", "find", actions);
                item(popup, "menu.findNext", "find_next", actions);
                popup.addSeparator();
                item(popup, "menu.locateCurrent", "locate", actions);
                break;
            case OPTIONS:
                item(popup, "menu.playlistOptions", "playlist_options", actions);
                popup.addSeparator();
                item(popup, "menu.batchFileInfo", "batch_info", actions);
                item(popup, "menu.filenameFormat", "filename_format", actions);
                item(popup, "menu.selectAll", "select_all", actions);
                item(popup, "menu.invertSelection", "select_invert", actions);
                break;
            case MODE:
                for (int index = 0; index < 5; index++) toggle(popup, "menu.playMode." + index, "mode:" + index, actions);
                popup.addSeparator();
                toggle(popup, "menu.pauseAfterPlay", "pause_after", actions);
                break;
            case PLAYLIST_CONTEXT:
                item(popup, "menu.properties", "properties", actions);
                item(popup, "menu.openFileLocation", "open_file_location", actions);
                popup.addSeparator();
            case MAIN:
                main(popup, actions);
                break;
            case LYRIC:
                toggle(popup, "menu.cover", "cover", actions);
                item(popup, "lyric.searchOnline", "lyric_search", actions);
                item(popup, "menu.loadLyrics", "loadlrc", actions);
                toggle(popup, "menu.karaoke", "karaoke", actions);
                toggle(popup, "menu.desktopLyric", "desktop", actions);
                toggle(popup, "menu.lockDesktop", "lock", actions);
                break;
            case EQUALIZER:
                toggle(popup, "menu.eqEnabled", "enabled", actions);
                toggle(popup, "menu.dolby", "dolby", actions);
                item(popup, "menu.reset", "reset", actions);
                submenu(popup, "menu.presets", Kind.PRESETS, actions, 10);
                break;
            case PRESETS:
                for (int index = 0; index < 13; index++) toggle(popup, "menu.preset." + index, "preset:" + index, actions);
                break;
            case SKINS:
                String[] skins = actions.skins();
                for (int index = 0; index < skins.length; index++) named(popup, skins[index], "skin:" + index, actions, true);
                item(popup, "menu.openSkin", "open_skin", actions);
                break;
            case PLAYLISTS:
                String[] playlists = actions.playlists();
                for (int index = 0; index < playlists.length; index++) named(popup, playlists[index], "list:" + index, actions, true);
                break;
            case LANGUAGE:
                for (String[] language : Messages.SUPPORTED_LANGUAGES)
                    named(popup, language[1], "language:" + language[0], actions, true);
                break;
            case DESKTOP:
                item(popup, actions.checked("desktop_dual") ? "desktopLyric.oneLine" : "desktopLyric.twoLine", "desktop_lines", actions);
                toggle(popup, "menu.lockDesktop", "lock", actions);
                item(popup, "lyric.searchOnline", "lyric_search", actions);
                item(popup, "menu.close", "desktop", actions);
                break;
            case FULLSCREEN:
                item(popup, "menu.fullscreenLyrics", "fullscreen_lyrics", actions);
                item(popup, "visual.fullscreen", "fullscreen_visual", actions);
                item(popup, "menu.fullscreenVideo", "fullscreen_lyric_video", actions);
                popup.addSeparator();
                toggle(popup, "menu.cover", "cover", actions);
                item(popup, "visual.exitFullscreen", "fullscreen_off", actions);
                break;
            case REPLAYGAIN:
                item(popup, "menu.replaygainScan", "replaygain_scan", actions);
                item(popup, "menu.replaygainUpdate", "replaygain_update", actions);
                break;
            case TRAY:
                item(popup, "menu.ttOptions", "preferences", actions);
                submenu(popup, "menu.links", Kind.LINKS, actions, 1);
                popup.addSeparator();
                submenu(popup, "menu.playback", Kind.PLAYBACK, actions, 3);
                submenu(popup, "menu.volume", Kind.VOLUME, actions, 4);
                submenu(popup, "menu.playbackMode", Kind.MODE, actions, 5);
                submenu(popup, "menu.playlists", Kind.PLAYLISTS, actions, 6);
                popup.addSeparator();
                submenu(popup, "menu.visual", Kind.VISUAL, actions, 8);
                submenu(popup, "menu.lyricShow", Kind.LYRIC, actions, 9);
                submenu(popup, "menu.equalizer", Kind.EQUALIZER, actions, 10);
                popup.addSeparator();
                submenu(popup, "menu.skins", Kind.SKIN_TRAY, actions, 12);
                submenu(popup, "menu.opacity", Kind.OPACITY, actions, 13);
                submenu(popup, "menu.view", Kind.VIEW, actions, 18);
                popup.addSeparator();
                toggle(popup, "menu.desktopLyric", "desktop", actions);
                popup.addSeparator();
                item(popup, "menu.restore", "show", actions);
                submenu(popup, "menu.fullscreen", Kind.TRAY_FULLSCREEN, actions, 19);
                item(popup, "menu.exit", "exit", actions);
                break;
            case LINKS:
                item(popup, "menu.about", "about", actions);
                popup.addSeparator();
                item(popup, "menu.ffmpegWebsite", "link_ffmpeg", actions);
                item(popup, "menu.sdlWebsite", "link_sdl", actions);
                break;
            case PLAYBACK:
                item(popup, "tray.playPause", "play", actions);
                item(popup, "tooltip.stop", "stop", actions);
                popup.addSeparator();
                item(popup, "tooltip.prev", "prev", actions);
                item(popup, "tooltip.next", "next", actions);
                popup.addSeparator();
                item(popup, "menu.addFile", "open", actions);
                item(popup, "menu.addFolder", "folder", actions);
                break;
            case VOLUME:
                item(popup, "menu.volumeUp", "volume_up", actions);
                item(popup, "menu.volumeDown", "volume_down", actions);
                toggle(popup, "tooltip.mute", "mute", actions);
                popup.addSeparator();
                submenu(popup, "menu.volumeLevel", Kind.VOLUME_LEVEL, actions, 4);
                break;
            case VOLUME_LEVEL:
                for (int percentage = 0; percentage <= 100; percentage += 25)
                    named(popup, percentage + "%", "volume:" + percentage, actions, true);
                break;
            case VISUAL:
                for (int identifier : new int[] {30, 64, 65, 41, 44, 46, 59}) {
                    org.ttplayer.audio.TtVisualizer.Mode mode = org.ttplayer.audio.TtVisualizer.Mode.fromId(identifier);
                    named(popup, mode.toString(), "visual:" + mode.id, actions, true);
                }
                break;
            case SKIN_TRAY:
                item(popup, "menu.chooseSkin", "skin", actions);
                popup.addSeparator();
                submenu(popup, "menu.installedSkins", Kind.SKINS, actions, 12);
                break;
            case OPACITY:
                for (int percentage = 100; percentage >= 50; percentage -= 10)
                    named(popup, percentage + "%", "opacity:" + percentage, actions, true);
                break;
            case VIEW:
                item(popup, "tray.show", "show", actions);
                toggle(popup, "playlist.title", "playlist", actions);
                toggle(popup, "lyric.title", "lyric", actions);
                toggle(popup, "menu.equalizer", "equalizer", actions);
                popup.addSeparator();
                toggle(popup, "menu.miniMode", "minimode", actions);
                toggle(popup, "menu.onTop", "ontop", actions);
                item(popup, "menu.resetLayout", "resetlayout", actions);
                break;
            case TRAY_FULLSCREEN:
                toggle(popup, "menu.fullscreenLyrics", "fullscreen_lyrics", actions);
                toggle(popup, "visual.fullscreen", "fullscreen_visual", actions);
                toggle(popup, "menu.fullscreenVideo", "fullscreen_lyric_video", actions);
                popup.addSeparator();
                item(popup, "visual.exitFullscreen", "fullscreen_off", actions);
                break;
        }
    }

    private static void main(JPopupMenu popup, Actions actions) {
        submenu(popup, "menu.add", Kind.ADD, actions, 23);
        submenu(popup, "menu.delete", Kind.DELETE, actions, 24);
        submenu(popup, "menu.sort", Kind.SORT, actions, 26);
        submenu(popup, "menu.find", Kind.FIND, actions, 14);
        submenu(popup, "menu.mode", Kind.MODE, actions, 5);
        submenu(popup, "menu.options", Kind.OPTIONS, actions, 2);
        submenu(popup, "menu.replaygain", Kind.REPLAYGAIN, actions, 10);
        popup.addSeparator();
        item(popup, "menu.skin", "skin", actions);
        item(popup, "menu.options_", "preferences", actions);
        popup.addSeparator();
        toggle(popup, "menu.lyricShow", "lyric", actions);
        toggle(popup, "menu.equalizer", "equalizer", actions);
        popup.addSeparator();
        toggle(popup, "menu.desktopLyric", "desktop", actions);
        toggle(popup, "menu.miniMode", "minimode", actions);
        popup.addSeparator();
        submenu(popup, "menu.language", Kind.LANGUAGE, actions, 33);
        item(popup, "menu.about", "about", actions);
        item(popup, "menu.resetLayout", "resetlayout", actions);
        submenu(popup, "menu.fullscreen", Kind.FULLSCREEN, actions, 19);
        item(popup, "menu.exit", "exit", actions);
    }

    private static void item(JPopupMenu popup, String key, String action, Actions actions) {
        named(popup, Messages.get(key), action, actions, false);
    }

    private static void toggle(JPopupMenu popup, String key, String action, Actions actions) {
        named(popup, Messages.get(key), action, actions, true);
    }

    private static void named(JPopupMenu popup, String text, String action, Actions actions, boolean checked) {
        JMenuItem entry = checked ? new JCheckBoxMenuItem(text, actions.checked(action)) : new JMenuItem(text);
        entry.setActionCommand(action);
        entry.setIcon(TtMenuGlyphs.icon(action));
        entry.setEnabled(actions.enabled(action));
        if ("properties".equals(action)) entry.setAccelerator(KeyStroke.getKeyStroke("alt ENTER"));
        if ("resetlayout".equals(action)) entry.setAccelerator(KeyStroke.getKeyStroke("ctrl shift R"));
        if ("select_all".equals(action)) entry.setAccelerator(KeyStroke.getKeyStroke("ctrl A"));
        if ("find".equals(action)) entry.setAccelerator(KeyStroke.getKeyStroke("ctrl F"));
        if ("find_next".equals(action)) entry.setAccelerator(KeyStroke.getKeyStroke("F3"));
        entry.addActionListener(event -> actions.execute(action));
        popup.add(entry);
    }

    private static void submenu(JPopupMenu popup, String key, Kind kind, Actions actions, int glyph) {
        JMenu menu = new JMenu(Messages.get(key));
        menu.setActionCommand(kind.name().toLowerCase(java.util.Locale.ROOT));
        menu.setIcon(TtMenuGlyphs.icon(glyph));
        populate(menu.getPopupMenu(), kind, actions);
        menu.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent event) { populate(menu.getPopupMenu(), kind, actions); }
            public void menuDeselected(MenuEvent event) {}
            public void menuCanceled(MenuEvent event) {}
        });
        popup.add(menu);
    }
}
