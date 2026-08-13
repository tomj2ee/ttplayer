package org.ttplayer.model;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.File;
import java.util.Map;

/**
 * 表示一首歌
 *
 * 元数据读取：使用JavaCV(FFmpeg)处理所有格式
 */
public class Song {
    public String title;       // 标题
    public String artist;      // 艺术家
    public String album;       // 专辑
    public String year;        // 年份
    public String genre;       // 流派
    public String comment;     // 注释
    public String filePath;    // 文件路径
    public String duration;    // 时长

    public Song() {
    }

    public Song(String filePath) {
        this(filePath, true);
    }

    public Song(String filePath, boolean loadMetadata) {
        this.filePath = filePath;
        if (loadMetadata) {
            loadMetadata();
        } else {
            // 只从文件名解析，不读取元数据
            parseFilePathOnly();
        }
    }

    private void parseFilePathOnly() {
        if (filePath != null) {
            File f = new File(filePath);
            String name = f.getName();
            int dashIdx = name.lastIndexOf(" - ");
            if (dashIdx > 0) {
                artist = name.substring(0, dashIdx);
                title = name.substring(dashIdx + 3);
                int extIdx = title.lastIndexOf('.');
                if (extIdx > 0) title = title.substring(0, extIdx);
            } else {
                title = name;
                int extIdx = title.lastIndexOf('.');
                if (extIdx > 0) title = title.substring(0, extIdx);
            }
        }
    }

    private void loadMetadata() {
        if (filePath != null) {
            File f = new File(filePath);
            // 先尝试从文件名解析
            String name = f.getName();
            int dashIdx = name.lastIndexOf(" - ");
            if (dashIdx > 0) {
                artist = name.substring(0, dashIdx);
                title = name.substring(dashIdx + 3);
                int extIdx = title.lastIndexOf('.');
                if (extIdx > 0) title = title.substring(0, extIdx);
            } else {
                title = name;
                int extIdx = title.lastIndexOf('.');
                if (extIdx > 0) title = title.substring(0, extIdx);
            }

            // 使用JavaCV(FFmpeg)读取元数据
            loadAudioMetadata(f);
        }
    }

    private void loadAudioMetadata(File file) {
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(file);
            grabber.start();

            Map<String, String> md = grabber.getMetadata();
            if (md != null) {
                String metaTitle = md.get("title");
                String metaArtist = md.get("artist");
                String metaAlbum = md.get("album");
                String metaYear = md.get("date");
                String metaGenre = md.get("genre");
                String metaComment = md.get("comment");

                if (metaTitle != null && !metaTitle.isEmpty()) {
                    title = metaTitle;
                }
                if (metaArtist != null && !metaArtist.isEmpty()) {
                    artist = metaArtist;
                }
                if (metaAlbum != null && !metaAlbum.isEmpty()) {
                    album = metaAlbum;
                }
                if (metaYear != null && !metaYear.isEmpty()) {
                    year = metaYear;
                }
                if (metaGenre != null && !metaGenre.isEmpty()) {
                    genre = metaGenre;
                }
                if (metaComment != null && !metaComment.isEmpty()) {
                    comment = metaComment;
                }
            }

            long lengthUs = grabber.getLengthInTime();
            if (lengthUs > 0) {
                int totalSeconds = (int) (lengthUs / 1000000L);
                int minutes = totalSeconds / 60;
                int seconds = totalSeconds % 60;
                duration = String.format("%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            // 元数据读取失败，忽略
        } finally {
            if (grabber != null) {
                try { grabber.stop(); } catch (Exception ignored) {}
            }
        }
    }

    public String getFileName() {
        if (filePath != null) {
            return new File(filePath).getName();
        }
        return "";
    }

    public void refreshMetadata() {
        if (filePath != null) {
            File f = new File(filePath);
            loadAudioMetadata(f);
        }
    }

    @Override
    public String toString() {
        if (title != null && !title.isEmpty()) {
            if (artist != null && !artist.isEmpty()) {
                return artist + " - " + title;
            }
            return title;
        }
        return filePath;
    }
}
