package org.ttplayer.app;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.model.PlaylistManager;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.ui.*;
import org.ttplayer.util.SkinLoader;
import org.ttplayer.util.SnapUtils;

import javax.swing.*;

/**
 * 窗口/核心协调门面。
 *
 * 装配与生命周期动作分别委托给：
 *   WindowAssembler  — 窗口创建/关系绑定/图标/配置保存/按钮
 *   WindowLifecycle  — 显示恢复/迷你模式/皮肤语言重建/托盘联动
 *   TrayController / FileDropController / PlayerEventListener — 托盘/拖放/播放事件
 */
public class WindowHub {

    public static final String DEFAULT_SKIN = "skin/default.skn";

    // ---- 核心状态（同包辅助类可直接读写，子包外走 getter） ----
    String currentSkinPath;
    TtSkin skin;
    PlaylistManager playlistManager;
    PlayerEngine playerEngine;

    PlayerWindow player;
    LyricWindow lyric;
    EqualizerWindow eq;
    PlaylistWindow playlist;
    MiniWindow miniWindow;
    DesktopLyricWindow desktopLyric;

    boolean inMiniMode = false;
    boolean[] visStates;

    // ---- 兄弟控制器 ----
    public final TrayController tray = new TrayController(this);
    public final FileDropController fileDrop = new FileDropController(this);
    private final PlayerEventListener playerEvent = new PlayerEventListener(this);
    private final WindowAssembler assembler = new WindowAssembler(this);
    private final WindowLifecycle lifecycle = new WindowLifecycle(this);

    // ==================== 对外只读访问 ====================

    public String getCurrentSkinPath() { return currentSkinPath; }
    public TtSkin getSkin() { return skin; }
    public PlaylistManager getPlaylistManager() { return playlistManager; }
    public PlayerEngine getPlayerEngine() { return playerEngine; }
    public PlayerWindow getPlayer() { return player; }
    public LyricWindow getLyric() { return lyric; }
    public EqualizerWindow getEq() { return eq; }
    public PlaylistWindow getPlaylist() { return playlist; }
    public MiniWindow getMiniWindow() { return miniWindow; }
    public DesktopLyricWindow getDesktopLyric() { return desktopLyric; }
    public boolean isMiniMode() { return inMiniMode; }

    // ==================== 创建 / 重建 ====================

    public void createUI(String skinPath) {
        currentSkinPath = skinPath;
        skin = new TtSkin();
        SkinLoader.loadWithFallback(skin, skinPath);

        int savedSongIndex = -1;
        if (playlistManager == null) {
            playlistManager = new PlaylistManager();
            savedSongIndex = PlaylistConfig.load(playlistManager);
        }
        if (playerEngine == null) {
            playerEngine = new PlayerEngine(playlistManager);
        }

        assembler.createWindows();
        setupDragDrop();
        assembler.setupRelations();
        playerEvent.install();
        assembler.setupIcons();
        assembler.setupConfigSaving();
        lifecycle.showAndRestore();

        if (savedSongIndex >= 0) {
            final int idx = savedSongIndex;
            SwingUtilities.invokeLater(() -> {
                if (playlistManager.getCurrentPlaylist() != null &&
                    idx < playlistManager.getCurrentPlaylist().songs.size()) {
                    playerEngine.play(idx);
                }
            });
        }
    }

    // ==================== 拖放与吸附 ====================

    private void setupDragDrop() {
        SnapUtils.registerMainPlayer(player);
        SnapUtils.registerChildWindow(lyric);
        SnapUtils.registerChildWindow(playlist);
        SnapUtils.registerChildWindow(eq);

        SnapUtils.setupMainPlayerDrag(player);
        SnapUtils.setupChildWindowDrag(lyric);
        SnapUtils.setupChildWindowDrag(playlist);
        SnapUtils.setupChildWindowDrag(eq);

        fileDrop.setupAll(player, lyric, eq, playlist, miniWindow, desktopLyric);
    }

    public void unregisterSnapAll() {
        if (player != null) SnapUtils.unregisterWindow(player);
        if (lyric != null) SnapUtils.unregisterWindow(lyric);
        if (eq != null) SnapUtils.unregisterWindow(eq);
        if (playlist != null) SnapUtils.unregisterWindow(playlist);
        if (miniWindow != null) SnapUtils.unregisterWindow(miniWindow);
        // desktopLyric 是 JWindow，不参与 SnapUtils
    }

    // ==================== 对辅助类暴露的动作（委托） ====================

    public void toggleMiniMode() { lifecycle.toggleMiniMode(); }
    public void reloadSkin(String skinPath) { lifecycle.reloadSkin(skinPath); }
    public void reloadForLanguage() { lifecycle.reloadForLanguage(); }
    public void showAllWindowsFromTray() { lifecycle.showAllFromTray(); }
    public void hideAllWindowsToTray() { lifecycle.hideAllToTray(); }
    public Song getCurrentSong() { return lifecycle.getCurrentSong(); }
}