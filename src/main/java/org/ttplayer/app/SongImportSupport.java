package org.ttplayer.app;

import org.ttplayer.model.Song;

import javax.swing.*;
import java.io.File;

/**
 * 歌曲导入（  供主窗口"打开文件"按钮等多处复用）。
 * 打开文件选择器 → 追加到当前播放列表 → 无可播放歌曲时从第一首开始播放。
 */
public class SongImportSupport {

    private SongImportSupport() {}

    /** 弹出多选文件对话框并导入到当前播放列表 */
    public static void openAndAddToPlaylist(WindowHub hub) {
        if (hub == null || hub.getPlayer() == null) return;
        openAndAddToPlaylist(hub, hub.getPlayer());
    }

    /** 以 parent 为父窗口弹出对话框导入 */
    public static void openAndAddToPlaylist(WindowHub hub, java.awt.Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) return;

        for (File file : chooser.getSelectedFiles()) {
            Song song = new Song(file.getAbsolutePath());
            hub.getPlaylistManager().getCurrentPlaylist().addSong(song);
        }
        hub.getPlaylist().refreshRightListPublic();

        if (!hub.getPlaylistManager().getCurrentPlaylist().songs.isEmpty()) {
            hub.getPlayerEngine().play(0);
        }
    }
}