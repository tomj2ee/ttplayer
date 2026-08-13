package org.ttplayer.app;

import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.model.Song;
import org.ttplayer.util.SongUtils;

import javax.swing.*;

/**
 *
 * 播放/暂停/停止/完成/错误 → 更新界面、歌词、托盘与播放状态持久化；
 * 并负责自动搜索下载歌词。
 */
public class PlayerEventListener {

    private final WindowHub hub;
    private Timer playbackSaveTimer;

    public PlayerEventListener(WindowHub hub) {
        this.hub = hub;
        playbackSaveTimer = new Timer(2000, e -> {
            if (hub.getPlaylistManager() != null) {
                int idx = hub.getPlayerEngine() != null ? hub.getPlayerEngine().getCurrentSongIndex() : -1;
                PlaylistConfig.save(hub.getPlaylistManager(), idx);
            }
        });
        playbackSaveTimer.setRepeats(false);
    }

    public void install() {
        hub.getPlayerEngine().setListener(new PlayerEngine.PlayerEngineListener() {
            @Override
            public void onPlaybackStarted(Song song) {
                String info = (song.artist != null ? song.artist + " - " : "") +
                              (song.title != null ? song.title : "");
                hub.getPlayer().setSongInfo(info);
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));

                hub.tray.updateToolTip(info);

                hub.getLyric().loadLyrics(song);
                if (hub.getDesktopLyric() != null) hub.getDesktopLyric().loadLyrics(song);

                org.ttplayer.lyrics.LyricAutoDownloader.searchAndDownload(
                    song, s -> hub.getLyric().loadLyrics(s));

                if (hub.isMiniMode() && hub.getMiniWindow() != null) {
                    int duration = hub.getPlayerEngine().getDuration();
                    String format = song.filePath != null ? SongUtils.getFileExtension(song.filePath).toUpperCase() : "";
                    hub.getMiniWindow().updateSongInfo(
                        song.title != null ? song.title : song.getFileName(),
                        song.artist, song.album, format, duration
                    );
                }

                playbackSaveTimer.restart();
            }

            @Override
            public void onPlaybackStopped() {
                hub.getPlayer().setSongInfo("");
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.stopped"));
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("status.stopped"));

                if (hub.isMiniMode() && hub.getMiniWindow() != null) {
                    hub.getMiniWindow().setDisplayText(org.ttplayer.util.Messages.get("status.stopped"));
                }
            }

            @Override
            public void onPlaybackPaused() {
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.paused"));
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("status.paused"));
            }

            @Override
            public void onPlaybackResumed() {
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));
            }

            @Override
            public void onPlaybackComplete() {
                int nextIdx = hub.getPlayerEngine().getCurrentSongIndex() + 1;
                if (hub.getPlaylistManager() != null &&
                    hub.getPlaylistManager().getCurrentPlaylist() != null &&
                    nextIdx < hub.getPlaylistManager().getCurrentPlaylist().songs.size()) {
                    hub.getPlayerEngine().play(nextIdx);
                }
            }

            @Override
            public void onPlaybackError(String error) {
                System.err.println(org.ttplayer.util.Messages.get("player.errorPrefix") + error);
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("player.errorPrefix") + error);
            }
        });
    }
}