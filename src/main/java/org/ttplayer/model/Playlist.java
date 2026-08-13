package org.ttplayer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表：包含多个歌曲
 */
public class Playlist {
    public String name;
    public List<Song> songs;

    public Playlist(String name) {
        this.name = name;
        this.songs = new ArrayList<>();
    }

    public Playlist(String name, List<Song> songs) {
        this.name = name;
        this.songs = songs;
    }

    public void addSong(Song song) {
        songs.add(song);
    }

    public void removeSong(int index) {
        if (index >= 0 && index < songs.size()) {
            songs.remove(index);
        }
    }

    public void clear() {
        songs.clear();
    }

    @Override
    public String toString() {
        return name;
    }
}
