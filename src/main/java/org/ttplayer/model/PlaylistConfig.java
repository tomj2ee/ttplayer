package org.ttplayer.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.ttplayer.util.Messages;

/**
 * 保存/加载播放列表及歌曲配置到 XML 文件
 */
public class PlaylistConfig {
    private static final Logger log = LoggerFactory.getLogger(PlaylistConfig.class);

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.ttplayer";
    private static final String FILE_NAME = "playlists.xml";

    public static void save(PlaylistManager manager, int currentSongIndex) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElement("ttplayer_playlists");
            root.setAttribute("currentSongIndex", String.valueOf(currentSongIndex));
            doc.appendChild(root);

            for (Playlist pl : manager.getAllPlaylists()) {
                Element plElem = doc.createElement("playlist");
                plElem.setAttribute("name", pl.name);
                plElem.setAttribute("current", pl == manager.getCurrentPlaylist() ? "true" : "false");
                root.appendChild(plElem);

                for (Song song : pl.songs) {
                    Element songElem = doc.createElement("song");
                    songElem.setAttribute("path", song.filePath != null ? song.filePath : "");
                    songElem.setAttribute("title", song.title != null ? song.title : "");
                    songElem.setAttribute("artist", song.artist != null ? song.artist : "");
                    songElem.setAttribute("album", song.album != null ? song.album : "");
                    songElem.setAttribute("year", song.year != null ? song.year : "");
                    songElem.setAttribute("genre", song.genre != null ? song.genre : "");
                    songElem.setAttribute("comment", song.comment != null ? song.comment : "");
                    songElem.setAttribute("duration", song.duration != null ? song.duration : "");
                    plElem.appendChild(songElem);
                }
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(new File(dir, FILE_NAME)), StandardCharsets.UTF_8)) {
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
            }

        } catch (Exception e) {
            log.error("{} {}", org.ttplayer.util.Messages.get("config.saveFailPrefix"), e.getMessage(), e);
        }
    }

    public static void save(PlaylistManager manager) {
        save(manager, -1);
    }

    public static int load(PlaylistManager manager) {
        File file = new File(CONFIG_DIR, FILE_NAME);
        if (!file.exists()) return -1;

        int savedSongIndex = -1;

        try {
            byte[] data = Files.readAllBytes(file.toPath());

            int offset = 0;
            if (data.length >= 3 && data[0] == (byte) 0xEF && data[1] == (byte) 0xBB && data[2] == (byte) 0xBF) {
                offset = 3;
            }

            String xmlStr = new String(data, offset, data.length - offset, StandardCharsets.UTF_8);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlStr)));

            manager.getAllPlaylists().clear();

            // 读取当前播放歌曲索引
            String indexStr = doc.getDocumentElement().getAttribute("currentSongIndex");
            if (indexStr != null && !indexStr.isEmpty()) {
                try {
                    savedSongIndex = Integer.parseInt(indexStr);
                } catch (NumberFormatException ignored) {}
            }

            NodeList plList = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < plList.getLength(); i++) {
                if (plList.item(i) instanceof Element) {
                    Element plElem = (Element) plList.item(i);
                    String name = plElem.getAttribute("name");
                    boolean isCurrent = "true".equals(plElem.getAttribute("current"));

                    Playlist pl = new Playlist(name);
                    manager.addPlaylist(pl);

                    NodeList songList = plElem.getChildNodes();
                    for (int j = 0; j < songList.getLength(); j++) {
                        if (songList.item(j) instanceof Element) {
                            Element songElem = (Element) songList.item(j);
                            Song song = new Song(songElem.getAttribute("path"), false);
                            song.title = songElem.getAttribute("title");
                            song.artist = songElem.getAttribute("artist");
                            song.album = songElem.getAttribute("album");
                            song.year = songElem.getAttribute("year");
                            song.genre = songElem.getAttribute("genre");
                            song.comment = songElem.getAttribute("comment");
                            song.duration = songElem.getAttribute("duration");
                            pl.addSong(song);
                        }
                    }

                    if (isCurrent) {
                        manager.setCurrentPlaylist(manager.getPlaylistCount() - 1);
                    }
                }
            }

        } catch (Exception e) {
            log.error("{} {}", org.ttplayer.util.Messages.get("config.loadFailPrefix"), e.getMessage(), e);
            if (manager.getAllPlaylists().isEmpty()) {
                manager.addPlaylist(new Playlist(org.ttplayer.util.Messages.get("playlist.defaultName")));
            }
        }

        return savedSongIndex;
    }
}
