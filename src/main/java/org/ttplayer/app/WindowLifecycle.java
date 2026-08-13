package org.ttplayer.app;

import org.ttplayer.model.Song;
import org.ttplayer.util.SongUtils;
import org.ttplayer.util.WindowConfig;

import java.awt.*;
import java.util.List;

/**
 * 窗口生命周期控制器
 * 负责：显示恢复/隐藏、迷你模式、皮肤/语言重建、托盘联动显示与 SnapUtils 反注册。
 */
public class WindowLifecycle {

    private final WindowHub hub;

    public WindowLifecycle(WindowHub hub) {
        this.hub = hub;
    }

    // ==================== 显示 / 恢复 ====================

    public void showAndRestore() {
        boolean[] vis = WindowConfig.restoreAll(hub.player, hub.lyric, hub.eq, hub.playlist);
        hub.visStates = vis;

        WindowConfig.restoreMiniWindow(hub.miniWindow);

        hub.inMiniMode = WindowConfig.restoreMiniModeState();
        if (hub.inMiniMode) {
            showWindows(vis);
            toggleMiniMode();
        } else {
            showWindows(vis);
        }

        setupMacDock();
    }

    private void showWindows(boolean[] vis) {
        hub.player.setVisible(vis[0]);
        hub.lyric.setVisible(vis[1]);
        hub.eq.setVisible(vis[2]);
        hub.playlist.setVisible(true);
    }

    private void setupMacDock() {
        try {
            try {
                java.io.InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("ico/ttplayer_32x32_32bpp.png");
                if (is != null) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(is);
                    if (img != null) {
                        org.ttplayer.util.UIUtils.setMacDockIcon(img);
                    }
                }
            } catch (Exception ignored) {}
            org.ttplayer.util.UIUtils.clearMacDockBadge();
        } catch (Exception ignored) {}
    }

    public void showAllFromTray() {
        if (hub.player == null) return;
        hub.player.setVisible(true);
        hub.player.setState(Frame.NORMAL);
        hub.player.toFront();

        if (hub.visStates != null && hub.visStates.length >= 4) {
            hub.lyric.setVisible(hub.visStates[1]);
            hub.eq.setVisible(hub.visStates[2]);
            hub.playlist.setVisible(hub.visStates[3]);
        } else {
            hub.playlist.setVisible(true);
        }
    }

    public void hideAllToTray() {
        if (hub.player == null) return;
        hub.tray.ensureVisible();

        if (hub.lyric != null && hub.eq != null && hub.playlist != null) {
            hub.visStates = new boolean[]{
                hub.player.isVisible(), hub.lyric.isVisible(),
                hub.eq.isVisible(), hub.playlist.isVisible()
            };
        }

        hub.player.setVisible(false);
        if (hub.lyric != null) hub.lyric.setVisible(false);
        if (hub.eq != null) hub.eq.setVisible(false);
        if (hub.playlist != null) hub.playlist.setVisible(false);
        if (hub.miniWindow != null) hub.miniWindow.setVisible(false);
    }

    // ==================== 迷你模式 ====================

    public void toggleMiniMode() {
        hub.inMiniMode = !hub.inMiniMode;
        if (hub.inMiniMode) {
            hub.visStates = new boolean[]{
                hub.player.isVisible(), hub.lyric.isVisible(),
                hub.eq.isVisible(), hub.playlist.isVisible()
            };

            hub.player.setVisible(false);
            hub.lyric.setVisible(false);
            hub.eq.setVisible(false);
            hub.playlist.setVisible(false);

            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Rectangle screen = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
            int mw = hub.miniWindow.getWidth();
            hub.miniWindow.setLocation(screen.x + screen.width - mw - 10, screen.y + 50);

            if (hub.playerEngine != null) {
                hub.miniWindow.updatePlayState(hub.playerEngine.isPlaying());
                int idx = hub.playerEngine.getCurrentSongIndex();
                if (idx >= 0 && hub.playlistManager != null) {
                    List<Song> songs = hub.playlistManager.getCurrentPlaylist().songs;
                    if (idx < songs.size()) {
                        Song s = songs.get(idx);
                        int duration = hub.playerEngine.getDuration();
                        String format = s.filePath != null ? SongUtils.getFileExtension(s.filePath).toUpperCase() : "";
                        hub.miniWindow.updateSongInfo(
                            s.title != null ? s.title : s.getFileName(),
                            s.artist, s.album, format, duration
                        );
                    }
                }
            }

            hub.miniWindow.setVisible(true);
        } else {
            hub.miniWindow.setVisible(false);
            showWindows(hub.visStates);
            if (hub.player != null) {
                hub.player.setVisible(true);
                hub.player.toFront();
            }
        }
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
        hub.visStates = new boolean[]{
            hub.player.isVisible(), hub.lyric.isVisible(),
            hub.eq.isVisible(), hub.playlist.isVisible()
        };
    }

    private void disposeWindows() {
        hub.player.dispose();
        hub.lyric.dispose();
        hub.eq.dispose();
        hub.playlist.dispose();
        if (hub.miniWindow != null) hub.miniWindow.dispose();
        if (hub.desktopLyric != null) hub.desktopLyric.dispose();
    }

    private void restorePlayingSong() {
        Song currentSong = getCurrentSong();
        if (currentSong == null) return;
        if (hub.playerEngine != null && hub.playerEngine.isPlaying()) {
            hub.player.setSongInfo(currentSong.toString());
            hub.player.setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));
        } else if (hub.playerEngine != null && hub.playerEngine.isPaused()) {
            hub.player.setSongInfo(currentSong.toString());
            hub.player.setStatusInfo(org.ttplayer.util.Messages.get("status.paused"));
        }
        hub.lyric.loadLyrics(currentSong);
    }

    public Song getCurrentSong() {
        int idx = hub.playerEngine != null ? hub.playerEngine.getCurrentSongIndex() : -1;
        if (idx >= 0 && hub.playlistManager != null) {
            List<Song> songs = hub.playlistManager.getCurrentPlaylist().songs;
            if (idx < songs.size()) return songs.get(idx);
        }
        return null;
    }
}