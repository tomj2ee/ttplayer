package org.ttplayer.ui;

import org.ttplayer.controls.TtButton;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.lyrics.LRCParser;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.FontUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class LyricWindow extends SkinWindow {

    PlayerEngine playerEngine;
    private LyricRenderer renderer;
    private TtSkin.Ctl lyricCtl;

    private Song currentSong;


    public LyricWindow(TtSkin skin, PlayerEngine engine) {
        super(skin, findWindow(skin, "lyric_window"), false);
        this.playerEngine = engine;
        setTitle(org.ttplayer.util.Messages.get("lyric.title"));

        TtSkin.WindowDef def = findWindow(skin, "lyric_window");
        int w = (def != null && def.width > 0) ? def.width : 268;
        setMinSize(w, 150);
        setSize(w, 280);
    }


    @Override
    protected void repositionControls() {
        super.repositionControls();
        // 皮肤未定义 <lyric> 控件时的兜底布局
        if (renderer != null && lyricCtl == null) {
            int margin = 6;
            int titleBottom = 28;
            renderer.setBounds(margin, titleBottom, getWidth() - margin * 2, getHeight() - titleBottom - 10);
        }
    }

    @Override
    protected void buildControls() {
        for (TtSkin.Ctl c : def.elements) {
            switch (c.tag) {
                case "close":
                    TtButton btnClose = createButton(c);
                    if (btnClose != null) {
                        btnClose.addActionListener(e -> setVisible(false));
                    }
                    break;
                case "title":
                    createTitleImage(c);
                    break;
                case "lyric":
                    lyricCtl = c;
                    renderer = new LyricRenderer(this);
                    renderer.setBounds(c.left, c.top, c.right - c.left, c.bottom - c.top);
                    getContentPane().add(renderer);
                    // 用 fill 定位：窗口缩放时歌词区随尺寸拉伸
                    // 四周留边 = 配置矩形的边距（left/top/(bgW-right)/(bgH-bottom)）
                    TtSkin.Ctl fillCtl = new TtSkin.Ctl();
                    fillCtl.left = c.left;
                    fillCtl.top = c.top;
                    fillCtl.right = c.right;
                    fillCtl.bottom = c.bottom;
                    fillCtl.align = "fill";
                    addControl(renderer, fillCtl);
                    break;
            }
        }
        loadLyricStyle();
    }


    private void loadLyricStyle() {
        byte[] data = org.ttplayer.util.UIUtils.loadSkinXml(skin, "Lyric");
        if (data == null) return;
        try {
            Document doc = org.ttplayer.util.UIUtils.parseXml(data);
            if (doc == null) return;
            Element lyricEl = doc.getDocumentElement();
            Element lyricNode = (Element) lyricEl.getElementsByTagName("Lyric").item(0);
            if (lyricNode == null) return;

            String fontSpec = org.ttplayer.util.UIUtils.getAttribute(lyricNode, "Font", "");
            if (renderer != null) {
                renderer.textColor = org.ttplayer.util.UIUtils.getColorAttribute(lyricNode, "TextColor", renderer.textColor);
                renderer.highlightColor = org.ttplayer.util.UIUtils.getColorAttribute(lyricNode, "HilightColor", renderer.highlightColor);
                renderer.bgColor = org.ttplayer.util.UIUtils.getColorAttribute(lyricNode, "BkgndColor", renderer.bgColor);
                renderer.wordColor = org.ttplayer.util.UIUtils.getColorAttribute(lyricNode, "HilightWordColor", renderer.wordColor);
                if (!fontSpec.isEmpty()) {
                    applyFontSpec(fontSpec);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void applyFontSpec(String fontSpec) {
        try {
            String[] parts = fontSpec.split(",");
            int lfHeight = Integer.parseInt(parts[0].trim());
            int size = Math.max(12, Math.min(28, Math.abs(lfHeight)));
            int style = Font.PLAIN;
            if (parts.length > 4 && Integer.parseInt(parts[4].trim()) >= 700) style = Font.BOLD;
            String fontName = parts.length > 13 ? parts[13].trim() : null;

            renderer.lyricFont = FontUtils.getChineseFont(fontName, style, size);
            renderer.currentFont = FontUtils.getChineseFont(fontName, Font.BOLD, size + 2);
        } catch (Exception ignored) {
        }
    }

    public void loadLyrics(Song song) {
        this.currentSong = song;
        if (renderer == null) return;

        if (song == null || song.filePath == null) {
            renderer.setLyrics(null);
            return;
        }

        File audioFile = new File(song.filePath);
        File lrcFile = LRCParser.findLRCFile(audioFile);
        if (lrcFile != null) {
            try {
                LRCParser parser = new LRCParser();
                LRCParser.LRCData data = parser.parse(lrcFile);
                renderer.setLyrics(data.lines);
                return;
            } catch (IOException ignored) {
            }
        }
        renderer.setLyrics(null);
    }

    public void searchLyricsOnline() {
        LyricSearchDialog dialog = new LyricSearchDialog(this, currentSong);
        dialog.setOnLyricDownloaded(() -> {
            if (currentSong != null) {
                loadLyrics(currentSong);
            }
        });
        dialog.setVisible(true);
    }

    static TtSkin.WindowDef findWindow(TtSkin skin, String name) {
        return MiniWindow.findWindow(skin, name);
    }

}
