package org.ttplayer.lyrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ttplayer.model.Song;
import org.ttplayer.util.SongUtils;

import javax.swing.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * 歌词自动搜索下载（从 PlayerEventListener 拆分）。
 * 播放启动时若歌曲目录没有 .lrc，则在后台搜索并保存到歌曲同目录。
 */
public class LyricAutoDownloader {
    private static final Logger log = LoggerFactory.getLogger(LyricAutoDownloader.class);

    private LyricAutoDownloader() {}

    /**
     * 若歌曲缺歌词则后台搜索下载；下载成功回调 onDownloaded。
     */
    public static SwingWorker<Boolean, Void> searchAndDownload(Song song, Consumer<Song> onDownloaded) {
        if (song == null || song.filePath == null) return null;

        File lrcFile = LyricsRepository.find(song);
        if (lrcFile != null && lrcFile.exists()) {
            try { if (!new LRCParser().parse(lrcFile).lines.isEmpty()) return null; }
            catch (java.io.IOException ignored) {}
        }

        String keyword = SongUtils.buildSearchKeyword(song);
        if (keyword.trim().isEmpty()) return null;

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    List<SongResult> results = LyricSearchService.searchSong(keyword, 10);
                    if (results.isEmpty() || isCancelled()) return false;

                    SongResult selected = results.get(0);
                    String lrcText = LyricSearchService.getLyricByMid(selected.getMid());
                    if (lrcText.trim().isEmpty() || isCancelled()) return false;

                    if (new LRCParser().parse(lrcText).lines.isEmpty() || isCancelled()) return false;
                    LyricsRepository.store(song, lrcText, null, true);
                    return true;
                } catch (Exception e) {
                    log.error("Failed to search/download lyrics for {}", song.filePath, e);
                    return false;
                }
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    if (get() && onDownloaded != null) {
                        onDownloaded.accept(song);
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
        return worker;
    }
}
