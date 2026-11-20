package org.ttplayer.app;

import org.ttplayer.model.Song;
import org.ttplayer.util.WindowConfig;
import org.ttplayer.util.MenuSettings;
import org.ttplayer.util.MiniLyricLayout;
import javax.swing.JFrame;

import java.awt.*;

/**
 * 窗口生命周期控制器
 * 负责：显示恢复/隐藏、迷你模式、皮肤/语言重建、托盘联动显示与 SnapUtils 反注册。
 */
public class WindowLifecycle {

    private final WindowHub hub;
    private boolean[] suspendedVisibility, rebuildVisibility;
    private boolean rebuilding, rebuildMini, rebuildLyric, rebuildDesktop;
    private org.ttplayer.ui.LyricWindow.ContentState rebuildLyrics;

    public WindowLifecycle(WindowHub hub) {
        this.hub = hub;
    }

    // ==================== 显示 / 恢复 ====================

    public void showAndRestore() {
        boolean[] vis = WindowConfig.restoreAll(hub.player, hub.lyric, hub.eq, hub.playlist);
        if (!org.ttplayer.util.MenuSettings.flag("rememberPosition", true))
            org.ttplayer.util.WindowLayoutUtils.layoutBySkin(hub.player, hub.lyric, hub.eq, hub.playlist);
        if (rebuilding && rebuildVisibility != null) vis = rebuildVisibility.clone();
        hub.visStates = vis.clone();
        WindowConfig.restoreMiniWindow(hub.miniWindow);
        hub.miniWindow.getRootPane().putClientProperty("mini.positioned", MenuSettings.flag("rememberPosition", true) && WindowConfig.hasMiniWindowPosition());
        boolean mini = rebuilding ? rebuildMini : MenuSettings.flag("miniOnStart", WindowConfig.restoreMiniModeState());
        hub.inMiniMode = false;
        showWindows(vis);
        if (mini) toggleMiniMode();
        if (rebuilding) {
            if (mini) hub.lyric.setVisible(rebuildLyric);
            hub.desktopLyric.setVisible(rebuildDesktop);
        }
        rebuilding = false;
        suspendedVisibility = null;
        setupMacDock();
    }

    private void showWindows(boolean[] vis) {
        hub.player.setVisible(true);
        hub.lyric.setVisible(vis[1]);
        hub.eq.setVisible(vis[2]);
        hub.playlist.setVisible(vis[3]);
    }

    private void setupMacDock() {
        try {
            java.awt.Image icon = org.ttplayer.util.AppIcons.getLargestImage();
            if (icon != null) org.ttplayer.util.UIUtils.setMacDockIcon(icon);
            org.ttplayer.util.UIUtils.clearMacDockBadge();
        } catch (Exception ignored) {}
    }

    public void showAllFromTray() {
        if (hub.player == null) return;
        JFrame[] frames = frames();
        if (suspendedVisibility != null) {
            for (int index = 0; index < frames.length; index++) {
                frames[index].getRootPane().putClientProperty("suspended.visible", null);
                frames[index].setVisible(suspendedVisibility[index]);
            }
            suspendedVisibility = null;
        }
        JFrame primary = hub.inMiniMode ? hub.miniWindow : hub.player;
        primary.setState(Frame.NORMAL);
        primary.setVisible(true);
        hub.lyric.positionCompactLyrics();
        for (JFrame frame : frames) {
            if (frame != primary && frame.isVisible()) {
                frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
                frame.toFront();
            }
        }
        primary.toFront();
    }

    public void hideAllToTray() {
        if (hub.player == null) return;
        hub.tray.ensureVisible();
        if (suspendedVisibility != null) return;
        JFrame[] frames = frames();
        suspendedVisibility = new boolean[frames.length];
        for (int index = 0; index < frames.length; index++) {
            suspendedVisibility[index] = frames[index].isVisible();
            frames[index].getRootPane().putClientProperty("suspended.visible", suspendedVisibility[index]);
            frames[index].setVisible(false);
        }
    }

    private JFrame[] frames() { return new JFrame[] {hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow}; }

    // ==================== 迷你模式 ====================

    public void toggleMiniMode() {
        if (suspendedVisibility != null) showAllFromTray();
        hub.inMiniMode = !hub.inMiniMode;
        if (hub.inMiniMode) {
            hub.visStates = new boolean[]{
                hub.player.isVisible(), hub.lyric.isVisible(),
                hub.eq.isVisible(), hub.playlist.isVisible()
            };
            JFrame[] normal = frames();
            for (int index = 0; index < 4; index++) normal[index].getRootPane().putClientProperty("mini.normalVisible", hub.visStates[index]);
            hub.player.setVisible(false);
            hub.lyric.setVisible(false);
            hub.eq.setVisible(false);
            hub.playlist.setVisible(false);

            if (!Boolean.TRUE.equals(hub.miniWindow.getRootPane().getClientProperty("mini.positioned"))) {
                Rectangle screen = MiniLyricLayout.usableBounds(hub.player);
                hub.miniWindow.setLocation(Math.max(screen.x, screen.x + screen.width - hub.miniWindow.getWidth() - 10), screen.y + 50);
                hub.miniWindow.getRootPane().putClientProperty("mini.positioned", Boolean.TRUE);
            }
            hub.miniWindow.refreshPlaybackState();
            hub.miniWindow.setVisible(true);
            hub.lyric.setCompactMode(true);
            hub.lyric.positionCompactLyrics();
            hub.lyric.setVisible(hub.visStates[1]);
            hub.miniWindow.setLyricVisible(hub.lyric.isVisible());
        } else {
            hub.lyric.setVisible(false);
            hub.lyric.setCompactMode(false);
            hub.miniWindow.setVisible(false);
            for (JFrame frame : frames()) frame.getRootPane().putClientProperty("mini.normalVisible", null);
            showWindows(hub.visStates);
            if (hub.player != null) {
                hub.player.setVisible(true);
                hub.player.toFront();
            }
        }
        WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow, hub.inMiniMode, hub.currentSkinPath);
    }

    // ==================== 皮肤 / 语言重建 ====================

    public void reloadSkin(String skinPath) {
        captureVisStates();
        hub.unregisterSnapAll();
        disposeWindows();
        WindowConfig.clearWindowPositions(skinPath);
        hub.createUI(skinPath);
        restorePlayingSong();
    }

    public void reloadForLanguage() {
        captureVisStates();
        hub.unregisterSnapAll();
        disposeWindows();
        hub.createUI(hub.currentSkinPath);
        restorePlayingSong();
    }

    private void captureVisStates() {
        rebuilding = true;
        rebuildLyrics = hub.lyric.captureContent();
        rebuildMini = hub.inMiniMode;
        rebuildLyric = suspendedVisibility == null ? hub.lyric.isVisible() : suspendedVisibility[1];
        rebuildDesktop = hub.desktopLyric.isVisible();
        rebuildVisibility = hub.inMiniMode && hub.visStates != null ? hub.visStates.clone() :
                suspendedVisibility == null ? new boolean[] {hub.player.isVisible(), hub.lyric.isVisible(), hub.eq.isVisible(), hub.playlist.isVisible()}
                : java.util.Arrays.copyOf(suspendedVisibility, 4);
        WindowConfig.saveAll(hub.player, hub.lyric, hub.eq, hub.playlist, hub.miniWindow, hub.inMiniMode, hub.currentSkinPath);
    }

    private void disposeWindows() {
        hub.menus.disposeTransient(true);
        hub.player.dispose();
        hub.lyric.dispose();
        hub.eq.dispose();
        hub.playlist.dispose();
        if (hub.miniWindow != null) hub.miniWindow.dispose();
        if (hub.desktopLyric != null) hub.desktopLyric.dispose();
    }

    private void restorePlayingSong() {
        Song currentSong = getCurrentSong();
        if (rebuildLyrics != null && rebuildLyrics.matches(currentSong)) hub.lyric.restoreContent(rebuildLyrics);
        else hub.lyric.loadLyrics(currentSong);
        rebuildLyrics = null;
        if (currentSong == null) return;
        if (hub.playerEngine != null && hub.playerEngine.isPaused()) {
            hub.player.setSongInfo(currentSong.toString());
            hub.player.setStatusInfo(org.ttplayer.util.Messages.get("status.paused"));
        } else if (hub.playerEngine != null && hub.playerEngine.isPlaying()) {
            hub.player.setSongInfo(currentSong.toString());
            hub.player.setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));
        }
        hub.miniWindow.refreshPlaybackState();
    }

    public Song getCurrentSong() {
        return hub.playerEngine == null ? null : hub.playerEngine.getCurrentSong();
    }
}
