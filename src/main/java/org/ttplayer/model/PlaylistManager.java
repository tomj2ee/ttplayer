package org.ttplayer.model;

import java.util.ArrayList;
import java.util.List;
import org.ttplayer.util.Messages;

/**
 * 播放列表管理器：管理多个播放列表
 */
public class PlaylistManager {
    private List<Playlist> playlists;
    private Playlist currentPlaylist;

    public PlaylistManager() {
        this.playlists = new ArrayList<>();
        // 默认创建一个空的默认列表
        addPlaylist(new Playlist(org.ttplayer.util.Messages.get("playlist.defaultName")));
    }

    public void addPlaylist(Playlist playlist) {
        playlists.add(playlist);
        if (currentPlaylist == null) {
            currentPlaylist = playlist;
        }
    }

    public void removePlaylist(int index) {
        if (index >= 0 && index < playlists.size()) {
            Playlist removed = playlists.remove(index);
            if (removed == currentPlaylist) {
                if (playlists.isEmpty()) {
                    currentPlaylist = null;
                } else if (index < playlists.size()) {
                    currentPlaylist = playlists.get(index);
                } else {
                    currentPlaylist = playlists.get(playlists.size() - 1);
                }
            }
        }
    }

    public void renamePlaylist(int index, String newName) {
        if (index >= 0 && index < playlists.size()) {
            playlists.get(index).name = newName;
        }
    }

    public Playlist getPlaylist(int index) {
        if (index >= 0 && index < playlists.size()) {
            return playlists.get(index);
        }
        return null;
    }

    public int getPlaylistCount() {
        return playlists.size();
    }

    public Playlist getCurrentPlaylist() {
        return currentPlaylist;
    }

    public void setCurrentPlaylist(int index) {
        if (index >= 0 && index < playlists.size()) {
            currentPlaylist = playlists.get(index);
        }
    }

    public List<Playlist> getAllPlaylists() {
        return playlists;
    }

    public String[] getPlaylistNames() {
        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            names[i] = playlists.get(i).name;
        }
        return names;
    }
}
