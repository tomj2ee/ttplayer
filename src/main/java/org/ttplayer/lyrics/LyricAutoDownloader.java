package org.ttplayer.lyrics;

import org.ttplayer.model.Song;
import org.ttplayer.util.SongUtils;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

/**
 * 歌词自动搜索下载（从 PlayerEventListener 拆分）。
 * 播放启动时若歌曲目录没有 .lrc，则在后台搜索并保存到歌曲同目录。
 */
public class LyricAutoDownloader {

    private LyricAutoDownloader() {}

    /**
     * 若歌曲缺歌词则后台搜索下载；下载成功回调 onDownloaded。
     */
    public static void searchAndDownload(Song song, Consumer<Song> onDownloaded) {
        if (song == null || song.filePath == null) return;

        final File audioFile = new File(song.filePath);
        File lrcFile = LRCParser.findLRCFile(audioFile);
        if (lrcFile != null && lrcFile.exists()) return;

        String keyword = SongUtils.buildSearchKeyword(song);
        if (keyword.trim().isEmpty()) return;

        SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    List<SongResult> results = LyricSearchService.searchSong(keyword, 10);
                    if (results.isEmpty()) return false;

                    SongResult selected = results.get(0);
                    String lrcText = LyricSearchService.getLyricByMid(selected.getMid());
                    if (lrcText.trim().isEmpty()) return false;

                    String path = audioFile.getAbsolutePath();
                    int dot = path.lastIndexOf('.');
                    String lrcPath = (dot > 0 ? path.substring(0, dot) : path) + ".lrc";

                    try (PrintWriter w = new PrintWriter(lrcPath, "UTF-8")) {
                        w.print(lrcText);
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (get() && onDownloaded != null) {
                        onDownloaded.accept(song);
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }
}