package org.ttplayer.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.model.Song;

import javax.swing.*;

/**
 *
 * 播放/暂停/停止/完成/错误 → 更新界面、歌词、托盘与播放状态持久化；
 * 并负责自动搜索下载歌词。
 */
public class PlayerEventListener {
    private static final Logger log = LoggerFactory.getLogger(PlayerEventListener.class);

    private final WindowHub hub;
    private Timer playbackSaveTimer;

    public PlayerEventListener(WindowHub hub) {
        this.hub = hub;
        playbackSaveTimer = new Timer(2000, e -> {
            if (hub.getPlaylistManager() != null) {
                int idx = hub.getPlayerEngine() != null ? hub.getPlayerEngine().getCurrentSongIndex() : -1;
                PlaylistConfig.save(hub.getPlaylistManager(), idx);
                hub.getPlayerEngine().savePlaybackPosition();
            }
        });
        playbackSaveTimer.setRepeats(true);
    }

    public void install() {
        hub.getPlayerEngine().setListener(new PlayerEngine.PlayerEngineListener() {
            @Override
            public void onPlaybackStarted(Song song) {
                if (song != hub.getPlayerEngine().getCurrentSong()) return;
                String info = (song.artist != null ? song.artist + " - " : "") +
                              (song.title != null ? song.title : "");
                hub.getPlayer().setSongInfo(info);
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));

                // 播放列表自动选中当前播放的歌曲
                if (hub.getPlaylist() != null) hub.getPlaylist().syncPlayingHighlight();

                hub.tray.updateToolTip(info);

                if (hub.getPlayerEngine().isVideoPlayback()) hub.getVideoWindow().showPlayback(song);
                else hub.getVideoWindow().hidePlayback();
                hub.getLyric().loadLyrics(hub.getPlayerEngine().isVideoPlayback() ? null : song);
                if (hub.getMiniWindow() != null) {
                    hub.getMiniWindow().setDisplayText(null);
                    hub.getMiniWindow().refreshPlaybackState();
                }

                playbackSaveTimer.restart();
            }

            @Override
            public void onPlaybackStopped() {
                if (!hub.getPlayerEngine().isVideoPlayback() || hub.getPlayerEngine().getCurrentSong() == null) {
                    hub.getVideoWindow().hidePlayback();
                }
                playbackSaveTimer.stop();
                hub.getPlayer().setSongInfo("");
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.stopped"));
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("status.stopped"));

                if (hub.isMiniMode() && hub.getMiniWindow() != null) {
                    hub.getMiniWindow().refreshPlaybackState();
                }
                hub.getLyric().advanceCoverAnimation();
            }

            @Override
            public void onPlaybackPaused() {
                hub.getPlayerEngine().savePlaybackPosition();
                playbackSaveTimer.stop();
                hub.getLyric().advanceCoverAnimation();
                hub.getMiniWindow().refreshPlaybackState();
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.paused"));
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("status.paused"));
            }

            @Override
            public void onPlaybackResumed() {
                playbackSaveTimer.restart();
                hub.getLyric().advanceCoverAnimation();
                hub.getMiniWindow().refreshPlaybackState();
                hub.getPlayer().setStatusInfo(org.ttplayer.util.Messages.get("status.playing"));
            }

            @Override
            public void onPlaybackComplete() {
                playbackSaveTimer.stop();
                hub.getPlayerEngine().advanceAfterPlayback();
            }

            @Override
            public void onPlaybackError(String error) {
                if (hub.getPlayerEngine().isVideoPlayback()) hub.getVideoWindow().showPlaybackError(error);
                log.error("{} {}", org.ttplayer.util.Messages.get("player.errorPrefix"), error);
                hub.tray.updateToolTip(org.ttplayer.util.Messages.get("player.errorPrefix") + error);
                playbackSaveTimer.stop();
                hub.getPlayerEngine().advanceAfterError();
            }
        });
    }
}
