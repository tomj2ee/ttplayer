package org.ttplayer.app;

import org.ttplayer.ui.*;
import org.ttplayer.util.WindowConfig;
import org.ttplayer.util.WindowLayoutUtils;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 窗口装配器
 * 负责：创建各子窗口、建立窗口间关系、绑定主窗口按钮/监听、设置图标与配置保存。
 */
public class WindowAssembler {

    private final WindowHub hub;

    public WindowAssembler(WindowHub hub) {
        this.hub = hub;
    }

    public void createWindows() {
        hub.player = new PlayerWindow(hub.skin, hub.playerEngine);
        hub.lyric = new LyricWindow(hub.skin, hub.playerEngine);
        hub.eq = new EqualizerWindow(hub.skin, hub.playerEngine);
        hub.playlist = new PlaylistWindow(hub.skin);
        hub.miniWindow = new MiniWindow(hub.skin, hub.playerEngine);

        if (hub.desktopLyric == null) {
            hub.desktopLyric = new DesktopLyricWindow(hub.playerEngine);
        }
        hub.player.setTitle("TTPlayer");
        hub.player.setChildWindows(hub.lyric, hub.eq, hub.playlist);
        hub.player.setMiniWindow(hub.miniWindow);

        WindowLayoutUtils.layoutBySkin(hub.player, hub.lyric, hub.eq, hub.playlist);

        hub.tray.init();
    }

    public void setupRelations() {
        hub.playlist.setPlaylistManager(hub.playlistManager);
        hub.playlist.setPlayerEngine(hub.playerEngine);
        hub.playlist.setLyricWindow(hub.lyric);
        hub.playlist.setEqualizerWindow(hub.eq);
        hub.playlist.setMiniWindow(hub.miniWindow);
        hub.playlist.setDesktopLyricWindow(hub.desktopLyric);
        hub.player.setPlaylistWindow(hub.playlist);

        setupMainWindowListener();

        hub.playlist.setSkinChangeListener(skinSpec -> {
            if (skinSpec != null) hub.reloadSkin(skinSpec);
        });
        hub.playlist.setLanguageChangeListener(locale -> hub.reloadForLanguage());
        hub.playlist.setMiniModeListener(hub::toggleMiniMode);
        hub.player.setOnToggleMiniMode(hub::toggleMiniMode);

        hub.miniWindow.setOnExitMiniMode(() -> {
            if (hub.inMiniMode) hub.toggleMiniMode();
        });
        hub.miniWindow.setOnToggleLyric(() -> {
            if (hub.lyric != null) hub.lyric.setVisible(!hub.lyric.isVisible());
        });

        hub.player.setOnHideToTray(hub::hideAllWindowsToTray);
        bindPlayerButtons();
    }

    private void setupMainWindowListener() {
        if (hub.player == null) return;
        hub.player.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        hub.player.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                hub.hideAllWindowsToTray();
            }
        });
    }

    public void setupIcons() {
        WindowLayoutUtils.setAllWindowIcons(
                hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow, hub.skin);
        if (hub.player != null) {
            hub.player.setTitle(org.ttplayer.util.Messages.get("app.titleBar"));
        }
    }

    public void setupConfigSaving() {
        javax.swing.Timer configSaveTimer = new javax.swing.Timer(500, e ->
            WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist,
                                 hub.miniWindow, hub.inMiniMode, hub.currentSkinPath));
        configSaveTimer.setRepeats(false);

        ComponentAdapter configSaver = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { configSaveTimer.restart(); }
            @Override public void componentMoved(ComponentEvent e) { configSaveTimer.restart(); }
        };

        hub.player.addComponentListener(configSaver);
        hub.lyric.addComponentListener(configSaver);
        hub.eq.addComponentListener(configSaver);
        hub.playlist.addComponentListener(configSaver);
    }

    private void bindPlayerButtons() {
        if (hub.player.btnLyric != null) {
            hub.player.btnLyric.addActionListener(e -> {
                hub.lyric.setVisible(!hub.lyric.isVisible());
                saveConfig();
            });
        }
        if (hub.player.btnEqualizer != null) {
            hub.player.btnEqualizer.addActionListener(e -> {
                hub.eq.setVisible(!hub.eq.isVisible());
                saveConfig();
            });
        }
        if (hub.player.btnPlaylist != null) {
            hub.player.btnPlaylist.addActionListener(e -> {
                hub.playlist.setVisible(!hub.playlist.isVisible());
                saveConfig();
            });
        }
        if (hub.player.btnDeskLrc != null) {
            hub.player.btnDeskLrc.addActionListener(e -> {
                if (hub.desktopLyric != null) {
                    hub.desktopLyric.setVisible(!hub.desktopLyric.isVisible());
                }
            });
        }
        if (hub.player.btnOpen != null) {
            hub.player.btnOpen.addActionListener(e -> SongImportSupport.openAndAddToPlaylist(hub));
        }
    }

    private void saveConfig() {
        WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist,
                             hub.miniWindow, hub.inMiniMode, hub.currentSkinPath);
    }
}