package org.ttplayer.lyrics;

public class SongResult {
    private String mid;
    private String name;
    private String singer;
    private String album;

    public SongResult(String mid, String name, String singer, String album) {
        this.mid = mid;
        this.name = name;
        this.singer = singer;
        this.album = album;
    }

    public String getMid() { return mid; }
    public String getName() { return name; }
    public String getSinger() { return singer; }
    public String getAlbum() { return album; }

    @Override
    public String toString() {
        return name + " - " + singer;
    }
}
