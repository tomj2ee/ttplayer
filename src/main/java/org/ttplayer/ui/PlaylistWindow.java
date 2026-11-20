package org.ttplayer.ui;

import org.ttplayer.controls.SkinScrollBarUI;
import org.ttplayer.controls.TtButton;
import org.ttplayer.controls.TtToolbar;
import org.ttplayer.engine.PlayerEngine;
import org.ttplayer.model.Playlist;
import org.ttplayer.model.PlaylistConfig;
import org.ttplayer.model.PlaylistManager;
import org.ttplayer.model.Song;
import org.ttplayer.skin.TtSkin;
import org.ttplayer.util.FontUtils;
import org.ttplayer.util.Messages;
import org.ttplayer.util.MenuSettings;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 播放列表窗口 — playlist_window
 */
public class PlaylistWindow extends SkinWindow implements TtMenuCatalog.Actions {

    public TtButton btnClose;
    public TtToolbar toolbar;
    public JPopupMenu addMenu, delMenu, sortMenu, findMenu, modeMenu, optMenu, mainMenu;
    public JPopupMenu playlistMenu;

    private JList<String> listLeft;
    private org.ttplayer.controls.VirtualList listRight;
    private JScrollPane scrollLeft;
    private JScrollPane scrollRight;
    private TtSkin.Ctl playlistCtl;
    private  TtToolbar toolbarCtl;
    private TtSkin.Ctl scrollbarCtl;
    private  int toolbarLeft = 0;

    // ---- 左右列表分割条 ----
    private JPanel divider;
    private double splitRatio = 0.35;   // 左列表宽度占比
    private int startScreenX = -1;
    private int startLeftWidth = 0;
    private static final int SPLIT_W = 5;
    private static final int MIN_LEFT = 70;
    private static final int MIN_RIGHT = 70;

    private Color colorText = Color.decode("#0080ff");
    private Color colorHilight = Color.decode("#00ff00");
    private Color colorSelect = Color.decode("#3269c8");
    private Color colorBkgnd = Color.decode("#000000");
    private Color colorNumber = Color.decode("#008000");
    private Color colorDuration = Color.decode("#c08020");
    private Color colorBkgnd2 = Color.decode("#202020");

    public PlaylistManager playlistManager;

    public PlaylistWindow(TtSkin skin) {
        super(skin, findWindow(skin), false);
        setTitle(org.ttplayer.util.Messages.get("playlist.title"));
        TtSkin.WindowDef def = findWindow(skin);
        if (def != null && def.width > 0 && def.height > 0) {
            setMinSize(def.width, def.height);
        } else {
            setMinSize(268, 165);
        }
    }

    public void setPlaylistManager(PlaylistManager playlistManager) {
        this.playlistManager = playlistManager;
        refreshLeftList();
        refreshRightList();
    }

    public void savePlaylistConfig() {
        PlaylistConfig.save(playlistManager);
    }

    public void refreshRightListPublic() {
        refreshRightList();
    }

    private PlayerEngine playerEngine;
    private LyricWindow lyricWindow;
    private EqualizerWindow equalizerWindow;
    private MiniWindow miniWindow;

    public interface SkinChangeListener {
        void onSkinChanged(String skinSpec);
    }
    private SkinChangeListener skinChangeListener;

    public interface MiniModeListener {
        void onToggleMiniMode();
    }
    private MiniModeListener miniModeListener;

    public interface LanguageChangeListener {
        void onLanguageChanged(Locale locale);
    }
    private LanguageChangeListener languageChangeListener;

    public void setPlayerEngine(PlayerEngine engine) {
        this.playerEngine = engine;
        syncPlayingHighlight();
    }

    /** 自动选中当前正在播放的歌曲（若在当前播放列表里则高亮并滚动到可见） */
    public void syncPlayingHighlight() {
        if (playerEngine == null || playlistManager == null || listRight == null) return;
        Playlist current = playlistManager.getCurrentPlaylist();
        if (current == null) return;
        Song playing = playerEngine.getCurrentSong();
        if (playing == null) return;
        // 优先对象引用匹配；播放对象可能来自其它路径（非列表内引用），按文件路径兜底
        int idx = current.songs.indexOf(playing);
        if (idx < 0 && playing.filePath != null) {
            for (int i = 0; i < current.songs.size(); i++) {
                Song s = current.songs.get(i);
                if (s.filePath != null && s.filePath.equals(playing.filePath)) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx >= 0) {
            listRight.setSelectedIndex(idx);
        }
    }

    public void setLyricWindow(LyricWindow lw) {
        this.lyricWindow = lw;
    }

    public void setEqualizerWindow(EqualizerWindow eq) {
        this.equalizerWindow = eq;
    }

    public void setMiniWindow(MiniWindow mw) {
        this.miniWindow = mw;
    }

    private DesktopLyricWindow desktopLyricWindow;
    public void setDesktopLyricWindow(DesktopLyricWindow dw) {
        this.desktopLyricWindow = dw;
    }

    public void setSkinChangeListener(SkinChangeListener l) {
        this.skinChangeListener = l;
    }

    public void setMiniModeListener(MiniModeListener l) {
        this.miniModeListener = l;
    }

    public void setLanguageChangeListener(LanguageChangeListener l) {
        this.languageChangeListener = l;
    }

    private void changeLanguage(Locale locale) {
        Messages.setLocale(locale);
        org.ttplayer.util.WindowConfig.saveLanguage(locale.toString());
        if (languageChangeListener != null) {
            languageChangeListener.onLanguageChanged(locale);
        }
    }

    private void doPlaySelected() {
        int index = listRight.getSelectedIndex();
        if (index >= 0 && playerEngine != null) {
            playerEngine.play(index);
        }
    }

    @Override
    protected void buildControls() {
        if (playlistManager == null) {
            playlistManager = new PlaylistManager();
        }

        // 先找到toolbar并记录高度
        for (TtSkin.Ctl c : def.elements) {
            if ("toolbar".equals(c.tag)) {
                toolbarLeft= c.left;
            }
        }

        for (TtSkin.Ctl c : def.elements) {
            switch (c.tag) {
                case "close":
                    btnClose = createButton(c);
                    if (btnClose != null) {
                        btnClose.addActionListener(e -> setVisible(false));
                    }
                    break;
                case "title":
                    createTitleImage(c);
                    break;
                case "toolbar":
                    toolbarCtl=createToolbar(c);
                    break;
                case "playlist":
                    playlistCtl = c;
                    createPlaylistArea(c);
                    break;
                case "scrollbar":
                    scrollbarCtl = c;
                    break;
            }
        }
    }

    private void createPlaylistArea(TtSkin.Ctl ctl) {
        int x = ctl.left;
        int y = ctl.top;  // 使用皮肤定义的原始位置
        int w = ctl.right - ctl.left;

        // 计算底部边框高度
        int bottomMargin = 0;
        if (def.resizeRect != null && !def.resizeRect.isEmpty()) {
            String[] p = def.resizeRect.split(",");
            if (p.length >= 4) {
                bottomMargin = Integer.parseInt(p[3].trim());
            }
        }

        // 找到toolbar高度

        for (TtSkin.Ctl c : def.elements) {
            if ("toolbar".equals(c.tag)) {
                toolbarLeft = c.left;
                break;
            }
        }

        // 计算高度：窗口高度 - y - 底部边框
        int h = def.height - y - bottomMargin;

        Font listFont = new Font("Dialog", Font.PLAIN, 12); // 默认先用Dialog

        try {
            byte[] xmlData = org.ttplayer.util.UIUtils.loadSkinXml(skin, "PlayList");
            if (xmlData != null) {
                Document doc = org.ttplayer.util.UIUtils.parseXml(xmlData);
                if (doc != null) {
                    Element root = doc.getDocumentElement();
                    Element playlist = (Element) root.getElementsByTagName("PlayList").item(0);
                    if (playlist != null) {
                        colorText = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Text", colorText);
                        colorHilight = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Hilight", colorHilight);
                        colorBkgnd = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Bkgnd", colorBkgnd);
                        colorNumber = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Number", colorNumber);
                        colorDuration = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Duration", colorDuration);
                        colorSelect = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Select", colorSelect);
                        colorBkgnd2 = org.ttplayer.util.UIUtils.getColorAttribute(playlist, "Color_Bkgnd2", colorBkgnd2);
                    }
                }
            }
        } catch (Exception e) {
        }

        // 使用通用中文字体工具
        listFont = FontUtils.getDefaultChineseFont(12);

        int leftWidth = (int) (w * 0.35);
        refreshLeftList();
        scrollLeft = new TransparentScrollPane(listLeft);

        SkinScrollBarUI sbUI = createScrollbarUI();
        SkinScrollBarUI sbUI2 = createScrollbarUI();

        createPlaylistMenu();

        listLeft.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPlaylistMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPlaylistMenu(e);
            }
        });

        listLeft.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedIndex = listLeft.getSelectedIndex();
                if (selectedIndex >= 0 && playlistManager != null) {
                    playlistManager.setCurrentPlaylist(selectedIndex);
                    refreshRightList();
                }
            }
        });
        scrollLeft.setBounds(x, y, leftWidth, h);
        scrollLeft.setBorder(null);
        scrollLeft.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollLeft.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollLeft.getVerticalScrollBar().setUI(sbUI);
        scrollLeft.getVerticalScrollBar().setOpaque(false);
        getContentPane().add(scrollLeft);
        addControl(scrollLeft, ctl);

        refreshRightList();
        scrollRight = new TransparentScrollPane(listRight);
        scrollRight.setBounds(x + leftWidth, y, w - leftWidth, h);
        scrollRight.setBorder(null);
        scrollRight.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollRight.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollRight.getVerticalScrollBar().setUI(sbUI2);
        scrollRight.getVerticalScrollBar().setOpaque(false);
        getContentPane().add(scrollRight);
        addControl(scrollRight, ctl);

        listRight.setMouseClickListener(new org.ttplayer.controls.VirtualList.MouseClickListener() {
            @Override
            public void onMouseClicked(MouseEvent e, int row) {
            }
            @Override
            public void onMouseDoubleClicked(MouseEvent e, int row) {
                doPlaySelected();
            }
            @Override
            public void onPopupTrigger(MouseEvent e, int row) {
                showRightListMenu(e, row);
            }
        });

        listRight.setToolTipProvider(row -> {
            if (playlistManager != null && playlistManager.getCurrentPlaylist() != null &&
                row >= 0 && row < playlistManager.getCurrentPlaylist().songs.size()) {
                Song song = playlistManager.getCurrentPlaylist().songs.get(row);
                return buildSongTip(song);
            }
            return null;
        });

        if (toolbar != null) getContentPane().setComponentZOrder(toolbar, 0);
        getContentPane().setComponentZOrder(scrollRight, 1);
        getContentPane().setComponentZOrder(scrollLeft, 2);
        // 分割条置顶于列表之上，保证可点击拖动
        createDivider();
        getContentPane().setComponentZOrder(divider, 3);
        if (btnClose != null) getContentPane().setComponentZOrder(btnClose, 4);
        syncDividerLayout();
    }

    private TtToolbar createToolbar(TtSkin.Ctl ctl) {
        byte[] bmp = skin.getBmp(ctl.image);
        if (bmp == null) return null;

        byte[] hotBmp = ctl.hotImage != null ? skin.getBmp(ctl.hotImage) : null;
        toolbar = new TtToolbar(bmp, hotBmp, 7);
        toolbar.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        getContentPane().add(toolbar);
        addControl(toolbar, ctl);

        createMenus();
        if (toolbar.getButton(0) != null) toolbar.getButton(0).setMenu(addMenu);
        if (toolbar.getButton(1) != null) toolbar.getButton(1).setMenu(delMenu);
        if (toolbar.getButton(2) != null) toolbar.getButton(2).setMenu(sortMenu);
        if (toolbar.getButton(3) != null) toolbar.getButton(3).setMenu(findMenu);
        if (toolbar.getButton(4) != null) toolbar.getButton(4).setMenu(modeMenu);
        if (toolbar.getButton(5) != null) toolbar.getButton(5).setMenu(optMenu);
        if (toolbar.getButton(6) != null) toolbar.getButton(6).setMenu(mainMenu);
        return toolbar;
    }

    private TtMenuCatalog.Actions menuActions;

    public void setMenuActions(TtMenuCatalog.Actions actions) { menuActions = actions; }

    @Override
    public void execute(String action) {
        if (menuActions != null) menuActions.execute(action);
        else performMenuAction(action);
    }

    @Override
    public boolean checked(String action) {
        if (action.startsWith("list:")) {
            int index = Integer.parseInt(action.substring(5));
            return playlistManager != null && index < playlistManager.getAllPlaylists().size()
                    && playlistManager.getAllPlaylists().get(index) == playlistManager.getCurrentPlaylist();
        }
        return menuActions != null && menuActions.checked(action);
    }

    @Override
    public boolean enabled(String action) {
        boolean hasList = playlistManager != null && playlistManager.getCurrentPlaylist() != null;
        if (java.util.Arrays.asList("delete", "delete_other", "batch_info", "properties", "open_file_location", "replaygain_scan", "replaygain_update").contains(action))
            return hasList && listRight != null && listRight.getSelectedIndices().length > 0;
        return menuActions == null || menuActions.enabled(action);
    }

    @Override
    public String[] playlists() {
        return playlistManager == null ? new String[0] : playlistManager.getAllPlaylists().stream()
                .map(list -> list.name).toArray(String[]::new);
    }

    private void createMenus() {
        addMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.ADD, this);
        delMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.DELETE, this);
        sortMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.SORT, this);
        findMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.FIND, this);
        modeMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.MODE, this);
        optMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.OPTIONS, this);
        mainMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.MAIN, this);
    }

    @Override
    protected void repositionControls() {
        super.repositionControls();
        syncDividerLayout();
    }

    /** 依据 splitRatio 计算左右列表与分割条的位置（拖动/缩放均走这里） */
    private void syncDividerLayout() {
        if (playlistCtl == null || scrollLeft == null || scrollRight == null) return;
        int left = playlistCtl.left;
        int top = playlistCtl.top;
        int rightMargin = bgW - playlistCtl.right;
        int bottomMargin = bgH - playlistCtl.bottom;
        int w = getWidth() - left - rightMargin;
        int h = getHeight() - top - bottomMargin;

        int lw = (int) (w * splitRatio);
        if (lw < MIN_LEFT) lw = MIN_LEFT;
        if (lw > w - MIN_RIGHT) lw = Math.max(MIN_LEFT, w - MIN_RIGHT);
        int rw = w - lw;

        if (divider != null) {
            divider.setBounds(left + lw - SPLIT_W / 2, top, SPLIT_W, h);
        }
        scrollLeft.setBounds(left, top, lw - SPLIT_W / 2, h);
        scrollRight.setBounds(left + lw + SPLIT_W / 2, top, rw - SPLIT_W / 2, h);
        // 尺寸变化立即重排滚动布局，让两个列表宽度/滚动条同步
        scrollLeft.revalidate();
        scrollRight.revalidate();
        if (divider != null) divider.repaint();
    }

    /** tooltip：顶部始终显示列表中的歌曲名称（标题或文件名），下方为详情 */
    private String buildSongTip(Song song) {
        String displayName = (song.title != null && !song.title.isEmpty()) ? song.title : song.toString();
        String html = org.ttplayer.util.UIUtils.generateSongTooltip(song);
        if (html == null) html = "";
        String detail = html.replaceFirst("^<html>", "").replaceFirst("</html>$", "");
        int br = detail.indexOf("<br>");
        if (br >= 0) detail = detail.substring(br + 4);   // 去掉已内嵌的标题行，避免重复
        return "<html><b>" + escapeHtml(displayName) + "</b><br>" + detail + "</html>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private void createDivider() {
        divider = new JPanel();
        divider.putClientProperty("innerDrag", Boolean.TRUE);
        divider.setOpaque(true);
        divider.setBackground(new Color(0, 0, 0, 70));
        divider.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR));
        java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                // 悬停高亮：蓝色强调
                divider.setBackground(new Color(22, 119, 255, 170));
                divider.repaint();
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                divider.setBackground(new Color(0, 0, 0, 70));
                divider.repaint();
            }
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                startScreenX = e.getXOnScreen();
                startLeftWidth = scrollLeft != null ? scrollLeft.getWidth() : 0;
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                if (startScreenX < 0 || scrollLeft == null || playlistCtl == null) return;
                int dx = e.getXOnScreen() - startScreenX;
                int left = playlistCtl.left;
                int rightMargin = bgW - playlistCtl.right;
                int w = getWidth() - left - rightMargin;
                if (w <= 0) return;
                int lw = startLeftWidth + dx;
                if (lw < MIN_LEFT) lw = MIN_LEFT;
                if (lw > w - MIN_RIGHT) lw = Math.max(MIN_LEFT, w - MIN_RIGHT);
                splitRatio = (double) lw / w;
                syncDividerLayout();
            }
        };
        divider.addMouseListener(ma);
        divider.addMouseMotionListener(ma);
        getContentPane().add(divider);
    }

    // ===== 左侧播放列表菜单 =====
    private void createPlaylistMenu() {
        playlistMenu = TtMenuCatalog.create(TtMenuCatalog.Kind.LIST, this);
    }

    private void doSwitchList() {
        int index = listLeft.getSelectedIndex();
        if (index >= 0) { playlistManager.setCurrentPlaylist(index); refreshRightList(); }
    }
    private void doNewList() {
        String name = JOptionPane.showInputDialog(this, org.ttplayer.util.Messages.get("dialog.newListPrompt"), org.ttplayer.util.Messages.get("dialog.newListTitle"), JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) { playlistManager.addPlaylist(new Playlist(name)); refreshLeftList(); }
    }
    private void doAddList() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(org.ttplayer.util.Messages.get("dialog.importPlaylistTitle"));
        javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
            org.ttplayer.util.Messages.get("dialog.importFilter"), "m3u", "pls");
        chooser.setFileFilter(filter);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f == null || !f.exists()) return;

        List<Song> songs = new java.util.ArrayList<>();
        String name = f.getName();
        if (name.endsWith(".m3u")) name = name.substring(0, name.length() - 4);
        else if (name.endsWith(".pls")) name = name.substring(0, name.length() - 4);

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                song: {
                    if (line.startsWith("File")) {
                        int eq = line.indexOf('=');
                        if (eq > 0) line = line.substring(eq + 1).trim(); else break song;
                    }
                    File sf = new File(line);
                    if (!sf.isAbsolute()) sf = new File(f.getParent(), line);
                    if (sf.exists()) songs.add(new Song(sf.getAbsolutePath()));
                }
            }
        } catch (IOException ignored) {}

        if (!songs.isEmpty()) {
            playlistManager.addPlaylist(new Playlist(name, songs));
            refreshLeftList();
            JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.importedPrefix") + songs.size() + org.ttplayer.util.Messages.get("dialog.importedSuffix"), org.ttplayer.util.Messages.get("dialog.importedTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void doSaveList() {
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null || pl.songs.isEmpty()) { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.emptyList"), org.ttplayer.util.Messages.get("dialog.save"), JOptionPane.WARNING_MESSAGE); return; }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(org.ttplayer.util.Messages.get("dialog.savePlaylist"));
        chooser.setSelectedFile(new File(pl.name + ".m3u"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = chooser.getSelectedFile();
        if (f == null) return;
        if (!f.getName().endsWith(".m3u")) f = new File(f.getAbsolutePath() + ".m3u");

        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.println("#EXTM3U");
            for (Song s : pl.songs) {
                String info = (s.artist != null ? s.artist : "") + " - " + (s.title != null ? s.title : s.getFileName());
                pw.println("#EXTINF:-1," + info);
                pw.println(s.filePath);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.saveFailedPrefix") + e.getMessage(), org.ttplayer.util.Messages.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doSaveAllLists() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(org.ttplayer.util.Messages.get("dialog.chooseSaveDir"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File dir = chooser.getSelectedFile();
        if (dir == null) return;
        int count = 0;
        for (Playlist pl : playlistManager.getAllPlaylists()) {
            if (pl.songs.isEmpty()) continue;
            try (PrintWriter pw = new PrintWriter(new File(dir, sanitizeName(pl.name) + ".m3u"), "UTF-8")) {
                pw.println("#EXTM3U");
                for (Song s : pl.songs) {
                    String info = (s.artist != null ? s.artist : "") + " - " + (s.title != null ? s.title : s.getFileName());
                    pw.println("#EXTINF:-1," + info);
                    pw.println(s.filePath);
                }
                count++;
            } catch (IOException ignored) {}
        }
        JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.savedCountPrefix") + count + org.ttplayer.util.Messages.get("dialog.savedCountMid") + dir, org.ttplayer.util.Messages.get("dialog.saveAll"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void doDeleteList() {
        int index = listLeft.getSelectedIndex();
        if (index >= 0 && playlistManager.getPlaylistCount() > 1) {
            if (JOptionPane.showConfirmDialog(this, org.ttplayer.util.Messages.get("dialog.deleteListConfirm"), org.ttplayer.util.Messages.get("dialog.deleteListTitle"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                playlistManager.removePlaylist(index); refreshLeftList(); refreshRightList();
            }
        }
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    private void doRenameList() {
        int index = listLeft.getSelectedIndex();
        if (index >= 0) {
            Playlist pl = playlistManager.getPlaylist(index);
            String name = JOptionPane.showInputDialog(this, org.ttplayer.util.Messages.get("dialog.renamePrompt"), pl.name);
            if (name != null && !name.trim().isEmpty() && !name.equals(pl.name)) { playlistManager.renamePlaylist(index, name); refreshLeftList(); }
        }
    }
    private void doSortPlaylists() { Collections.sort(playlistManager.getAllPlaylists(), (a, b) -> a.name.compareToIgnoreCase(b.name)); refreshLeftList(); }

    private SkinScrollBarUI createScrollbarUI() {
        SkinScrollBarUI ui = new SkinScrollBarUI(Color.decode("#202020"), Color.decode("#0080ff"));
        if (scrollbarCtl != null) {
            byte[] btnBmp = scrollbarCtl.buttonsImage != null ? skin.getBmp(scrollbarCtl.buttonsImage) : null;
            byte[] thumbBmp = scrollbarCtl.thumbImage != null ? skin.getBmp(scrollbarCtl.thumbImage) : null;
            byte[] barBmp = scrollbarCtl.barImage != null ? skin.getBmp(scrollbarCtl.barImage) : null;
            ui.setSkinImages(btnBmp, thumbBmp, barBmp, skin.getTransparentColor(),
                    scrollbarCtl.thumbResizeCenter, scrollbarCtl.thumbResizeTile);

        }
        return ui;
    }

    private int lastFindStart = -1;
    private final java.util.List<Integer> foundIndices = new java.util.ArrayList<>();

    private void doAddFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            List<File> files = new ArrayList<>();
            for (File file : chooser.getSelectedFiles()) {
                if (file.isFile()) files.add(file);
            }
            loadSongsAsync(null, files, false);
        }
    }
    private void doAddFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadSongsAsync(chooser.getSelectedFile(), null, false);
        }
    }
    private void collectAudioFiles(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                collectAudioFiles(f, out);
            } else {
                String n = f.getName().toLowerCase();
                for (String ext : org.ttplayer.util.FileAssociationService.EXTENSIONS) {
                    if (n.endsWith(ext)) { out.add(f); break; }
                }
            }
        }
    }

    /**
     * 后台扫描/加载歌曲，弹置顶进度框显示当前文件与进度（i/total），可取消。
     * dir != null 时先在后台递归收集文件（进度条为不确定状态），再逐个读取元数据。
     */
    private void loadSongsAsync(final File dir, final List<File> files, final boolean playFirst) {
        if (dir == null && (files == null || files.isEmpty())) return;
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null) return;

        JDialog dlg = new JDialog(this, org.ttplayer.util.Messages.get("dialog.loadingSongs"), JDialog.ModalityType.MODELESS);
        dlg.setResizable(false);
        dlg.setAlwaysOnTop(true);
        dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setIndeterminate(true);
        bar.setString("");
        JLabel label = new JLabel(dir != null ? org.ttplayer.util.Messages.get("dialog.scanningDir") : org.ttplayer.util.Messages.get("dialog.loading"), SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13));
        JButton cancelBtn = new JButton(org.ttplayer.util.Messages.get("dialog.cancel"));
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(label, BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        btnPanel.add(cancelBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        dlg.setContentPane(panel);
        dlg.pack();
        dlg.setSize(420, 150);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
        dlg.toFront();

        final List<Song> songs = new ArrayList<>();
        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() {
                List<File> toLoad = files;
                if (dir != null) {
                    toLoad = new ArrayList<>();
                    collectAudioFiles(dir, toLoad);
                }
                if (toLoad == null || toLoad.isEmpty()) return null;
                for (int i = 0; i < toLoad.size(); i++) {
                    if (isCancelled()) break;
                    File f = toLoad.get(i);
                    publish(new Object[]{i + 1, toLoad.size(), f.getName()});
                    songs.add(new Song(f.getAbsolutePath()));
                }
                return null;
            }
            @Override
            protected void process(List<Object[]> chunks) {
                Object[] p = chunks.get(chunks.size() - 1);
                bar.setIndeterminate(false);
                bar.setMaximum((Integer) p[1]);
                bar.setValue((Integer) p[0]);
                bar.setString(p[0] + "/" + p[1]);
                label.setText(org.ttplayer.util.Messages.get("dialog.loadingPrefix") + p[2] + "  (" + p[0] + "/" + p[1] + ")");
            }
            @Override
            protected void done() {
                if (dlg.isDisplayable()) dlg.dispose();
                // 取消后保留已加载的部分歌曲
                int firstIdx = -1;
                if (!songs.isEmpty()) {
                    firstIdx = pl.songs.size();
                    pl.songs.addAll(songs);
                }
                refreshRightList();
                savePlaylistConfig();
                if (playFirst && firstIdx >= 0 && playerEngine != null) {
                    if (pl == playlistManager.getCurrentPlaylist()) {
                        playerEngine.play(firstIdx);
                    } else {
                        // 加载过程中切换了列表，索引可能已失效，直接播放 Song
                        playerEngine.play(songs.get(0));
                    }
                }
            }
        };
        cancelBtn.addActionListener(e -> worker.cancel(true));
        worker.execute();
    }

    /** 拖放歌曲：加入播放列表并立即播放第一个 */
    public void loadSongsAndPlay(List<File> files) {
        if (files == null || files.isEmpty()) return;
        loadSongsAsync(null, files, true);
    }
    public void addAudioFiles(List<File> files, boolean play) { if (files != null && !files.isEmpty()) loadSongsAsync(null, files, play); }

    private void doAddUrl() {
        String url = JOptionPane.showInputDialog(this, org.ttplayer.util.Messages.get("dialog.addUrlPrompt"), org.ttplayer.util.Messages.get("dialog.addUrlTitle"), JOptionPane.PLAIN_MESSAGE);
        if (url != null && !url.trim().isEmpty()) {
            Song s = new Song(url.trim()); s.title = org.ttplayer.util.Messages.get("dialog.networkAudio");
            playlistManager.getCurrentPlaylist().addSong(s); refreshRightList();
        }
    }
    private void doAddSearchResult() { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.setSearchPath"), org.ttplayer.util.Messages.get("dialog.hint"), JOptionPane.INFORMATION_MESSAGE); }

    private void doDeleteSelected() {
        int[] indices = listRight.getSelectedIndices();
        if (indices.length == 0) return;
        Playlist pl = playlistManager.getCurrentPlaylist();
        for (int i = indices.length - 1; i >= 0; i--) pl.removeSong(indices[i]);
        refreshRightList(); savePlaylistConfig();
    }
    private void doDeleteNotSelected() {
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null || pl.songs.isEmpty()) return;
        int[] sel = listRight.getSelectedIndices();
        if (sel.length == 0) { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.selectSongsToKeep"), org.ttplayer.util.Messages.get("dialog.hint"), JOptionPane.INFORMATION_MESSAGE); return; }
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (int i : sel) keep.add(i);
        java.util.List<Song> keepSongs = new java.util.ArrayList<>();
        for (int i = 0; i < pl.songs.size(); i++) { if (keep.contains(i)) keepSongs.add(pl.songs.get(i)); }
        pl.songs.clear(); pl.songs.addAll(keepSongs); refreshRightList(); savePlaylistConfig();
    }
    private void doDeleteDuplicates() {
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.List<Song> unique = new java.util.ArrayList<>();
        int dupCount = 0;
        for (Song s : pl.songs) {
            String key = (s.filePath != null) ? s.filePath : s.title;
            if (!seen.contains(key)) { seen.add(key); unique.add(s); }
            else { dupCount++; }
        }
        if (dupCount > 0) { pl.songs.clear(); pl.songs.addAll(unique); refreshRightList(); JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.deletedDuplicatesPrefix") + dupCount + org.ttplayer.util.Messages.get("dialog.deletedDuplicatesSuffix"), org.ttplayer.util.Messages.get("menu.deleteDuplicates"), JOptionPane.INFORMATION_MESSAGE); }
        else { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.noDuplicates"), org.ttplayer.util.Messages.get("menu.deleteDuplicates"), JOptionPane.INFORMATION_MESSAGE); }
    }
    private void doClearList() {
        if (JOptionPane.showConfirmDialog(this, org.ttplayer.util.Messages.get("dialog.clearListConfirm"), org.ttplayer.util.Messages.get("dialog.clearListTitle"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            playlistManager.getCurrentPlaylist().clear(); refreshRightList(); savePlaylistConfig();
        }
    }

    private void doSortByField(String field) {
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null || pl.songs.size() <= 1) return;
        pl.songs.sort((a, b) -> {
            String va = "", vb = "";
            switch (field) {
                case "title": va = nvl(a.title); vb = nvl(b.title); break;
                case "artist": va = nvl(a.artist); vb = nvl(b.artist); break;
                case "filename": va = a.getFileName().toLowerCase(); vb = b.getFileName().toLowerCase(); break;
                case "path": va = nvl(a.filePath); vb = nvl(b.filePath); break;
            }
            return va.compareTo(vb);
        });
        refreshRightList();
    }
    private void doReverse() { Playlist pl = playlistManager.getCurrentPlaylist(); if (pl != null) { Collections.reverse(pl.songs); refreshRightList(); } }
    private void doRandomSort() { Playlist pl = playlistManager.getCurrentPlaylist(); if (pl != null) { Collections.shuffle(pl.songs); refreshRightList(); } }
    private static String nvl(String s) { return s != null ? s : ""; }

    private void doFind() {
        String kw = JOptionPane.showInputDialog(this, org.ttplayer.util.Messages.get("dialog.findPrompt"), org.ttplayer.util.Messages.get("menu.find"), JOptionPane.PLAIN_MESSAGE);
        if (kw == null || kw.trim().isEmpty()) return;
        String searchKeyword = kw.toLowerCase();
        foundIndices.clear();
        Playlist pl = playlistManager.getCurrentPlaylist();
        if (pl == null) return;
        for (int i = 0; i < pl.songs.size(); i++) {
            String txt = (nvl(pl.songs.get(i).title) + " " + nvl(pl.songs.get(i).artist)).toLowerCase();
            if (txt.contains(searchKeyword)) foundIndices.add(i);
        }
        if (foundIndices.isEmpty()) { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.noMatch"), org.ttplayer.util.Messages.get("dialog.findResultTitle"), JOptionPane.INFORMATION_MESSAGE); }
        else {
            lastFindStart = 0; listRight.setSelectedIndex(foundIndices.get(0)); listRight.ensureIndexIsVisible(foundIndices.get(0));
            JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.foundCountPrefix") + foundIndices.size() + org.ttplayer.util.Messages.get("dialog.foundCountSuffix"), org.ttplayer.util.Messages.get("dialog.findResultTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }
    private void doFindNext() {
        if (foundIndices.isEmpty()) { doFind(); return; }
        lastFindStart = (lastFindStart + 1) % foundIndices.size();
        int idx = foundIndices.get(lastFindStart); listRight.setSelectedIndex(idx); listRight.ensureIndexIsVisible(idx);
    }
    private void doLocateCurrent() {
        Playlist playing = playerEngine == null ? null : playerEngine.getPlayingPlaylist();
        int playlistIndex = playing == null || playlistManager == null ? -1 : playlistManager.getAllPlaylists().indexOf(playing);
        if (playlistIndex >= 0) {
            playlistManager.setCurrentPlaylist(playlistIndex);
            refreshLeftList();
            refreshRightList();
        }
        int idx = playing == null || playerEngine == null ? -1 : playing.songs.indexOf(playerEngine.getCurrentSong());
        if (idx >= 0) {
            listRight.setSelectedIndex(idx);
            listRight.ensureIndexIsVisible(idx);
        } else {
            JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.noPlaying"), org.ttplayer.util.Messages.get("dialog.locateTitle"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public boolean performMenuAction(String action) {
        if (action.startsWith("list:")) {
            playlistManager.setCurrentPlaylist(Integer.parseInt(action.substring(5)));
            refreshLeftList();
            refreshRightList();
            savePlaylistConfig();
            return true;
        }
        if (action.startsWith("language:")) {
            changeLanguage(Messages.fromTag(action.substring(9)));
            return true;
        }
        switch (action) {
            case "open": doAddFile(); break;
            case "folder": case "add_search": doAddFolder(); break;
            case "add_url": doAddUrl(); break;
            case "delete": doDeleteSelected(); break;
            case "delete_other": doDeleteNotSelected(); break;
            case "delete_duplicates": doDeleteDuplicates(); break;
            case "clear_list": doClearList(); break;
            case "sort_title": doSortByField("title"); break;
            case "sort_artist": doSortByField("artist"); break;
            case "sort_filename": doSortByField("filename"); break;
            case "sort_path": doSortByField("path"); break;
            case "sort_reverse": doReverse(); break;
            case "sort_random": doRandomSort(); break;
            case "find": doFind(); break;
            case "find_next": doFindNext(); break;
            case "locate": doLocateCurrent(); break;
            case "new_list": doNewList(); break;
            case "import": doAddList(); break;
            case "export": doSaveList(); break;
            case "delete_list": doDeleteList(); break;
            case "save_all": doSaveAllLists(); break;
            case "rename_list": doRenameList(); break;
            case "sort_lists": doSortPlaylists(); break;
            case "playlist_options": doPlaylistOptions(); break;
            case "batch_info": doBatchInfo(); break;
            case "filename_format": doFileNameFormat(); break;
            case "select_all": if (listRight != null) listRight.selectAll(); break;
            case "select_invert": if (listRight != null) listRight.invertSelection(); break;
            case "properties": showProperties(); break;
            case "open_file_location": openFileLocation(); break;
            case "skin": doSkin(); break;
            case "open_skin": doOpenSkin(); break;
            case "preferences": doOptions(); break;
            case "lyric": doToggleLyric(); break;
            case "equalizer": doToggleEqualizer(); break;
            case "desktop": doToggleDesktopLyric(); break;
            case "minimode": doMiniMode(); break;
            case "about": doAbout(); break;
            default: return false;
        }
        if (action.startsWith("sort_") || java.util.Arrays.asList("delete", "delete_other", "delete_duplicates", "clear_list",
                "new_list", "import", "delete_list", "rename_list", "sort_lists", "add_url").contains(action)) savePlaylistConfig();
        return true;
    }

    public List<Song> getSelectedSongs() {
        List<Song> selected = new ArrayList<>();
        Playlist current = playlistManager == null ? null : playlistManager.getCurrentPlaylist();
        if (current != null && listRight != null) {
            for (int index : listRight.getSelectedIndices()) {
                if (index >= 0 && index < current.songs.size()) selected.add(current.songs.get(index));
            }
        }
        return selected;
    }

    private void showProperties() {
        Song song = getContextSong();
        if (song == null) return;
        String[][] values = {
            {Messages.get("properties.title"), song.title}, {Messages.get("properties.artist"), song.artist},
            {Messages.get("properties.album"), song.album}, {Messages.get("properties.year"), song.year},
            {Messages.get("properties.genre"), song.genre}, {Messages.get("properties.duration"), song.duration},
            {Messages.get("properties.path"), song.filePath}, {Messages.get("properties.comment"), song.comment}
        };
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
        for (String[] value : values) {
            panel.add(new JLabel(value[0]));
            JTextField field = new JTextField(value[1] == null ? "" : value[1], 30);
            field.setEditable(false);
            panel.add(field);
        }
        JOptionPane.showMessageDialog(this, panel, Messages.get("menu.properties"), JOptionPane.PLAIN_MESSAGE);
    }

    private void openFileLocation() {
        Song song = getContextSong();
        if (song == null || song.filePath == null) return;
        File file = new File(song.filePath).getAbsoluteFile();
        try {
            if (!file.exists()) throw new IOException(Messages.get("player.fileNotFound"));
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
            else if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.getParentFile());
            else throw new IOException(Messages.get("dialog.fileLocationUnavailable"));
        } catch (IOException | UnsupportedOperationException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), Messages.get("menu.openFileLocation"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doPlaylistOptions() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 5));
        JTextField prefixField = new JTextField(MenuSettings.get("playlistPrefix", "TTPlayer"));
        panel.add(new JLabel(org.ttplayer.util.Messages.get("dialog.fileAssocPrefix")));
        panel.add(prefixField);
        if (JOptionPane.showConfirmDialog(this, panel, Messages.get("dialog.playlistOptionsTitle"), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) MenuSettings.set("playlistPrefix", prefixField.getText().trim());
    }

    private void doBatchInfo() {
        int[] sel = listRight.getSelectedIndices();
        if (sel.length == 0) { JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("dialog.selectSongFirst"), org.ttplayer.util.Messages.get("dialog.hint"), JOptionPane.WARNING_MESSAGE); return; }
        Playlist pl = playlistManager.getCurrentPlaylist();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(sel.length, 50); i++) {
            Song s = pl.songs.get(sel[i]);
            sb.append((i + 1)).append(". ");
            if (s.artist != null) sb.append(s.artist).append(" - ");
            sb.append(s.title != null ? s.title : s.getFileName()).append("\n");
        }
        if (sel.length > 50) sb.append(org.ttplayer.util.Messages.get("dialog.countSuffixPrefix") + sel.length + org.ttplayer.util.Messages.get("dialog.countSuffix"));
        JTextArea ta = new JTextArea(sb.toString(), 15, 40);
        ta.setEditable(false);
        ta.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JOptionPane.showMessageDialog(this, new JScrollPane(ta), org.ttplayer.util.Messages.get("dialog.selectedSongInfoPrefix") + sel.length + org.ttplayer.util.Messages.get("dialog.selectedSongInfoSuffix"), JOptionPane.PLAIN_MESSAGE);
    }

    private void doFileNameFormat() {
        String[] templates = {
            "%artist% - %title%",
            "%track% - %title%",
            "%artist% - %album% - %title%",
            "%title%",
            "%filename%"
        };
        String sel = (String) JOptionPane.showInputDialog(this,
            org.ttplayer.util.Messages.get("dialog.filenameFormatMsg"),
            org.ttplayer.util.Messages.get("dialog.filenameFormatTitle"),
            JOptionPane.PLAIN_MESSAGE, null, templates, MenuSettings.get("filenameFormat", templates[0]));
        if (sel == null) return;
        MenuSettings.set("filenameFormat", sel);
        refreshRightList();
    }

    public void doOptions() {
        if (menuActions != null) menuActions.execute("preferences");
    }

    public void doSkin() {
        if (menuActions != null) menuActions.execute("skin");
    }

    public void doToggleEqualizer() {
        if (equalizerWindow != null) {
            equalizerWindow.setVisible(!equalizerWindow.isVisible());
        }
    }

    public void doToggleLyric() {
        if (lyricWindow != null) {
            lyricWindow.setVisible(!lyricWindow.isVisible());
        }
    }

    public void doMiniMode() {
        if (miniModeListener != null) {
            miniModeListener.onToggleMiniMode();
        }
    }

    public void doToggleDesktopLyric() {
        if (desktopLyricWindow != null) {
            desktopLyricWindow.setVisible(!desktopLyricWindow.isVisible());
        }
    }

    public void doAbout() {
        JOptionPane.showMessageDialog(this,
            org.ttplayer.util.Messages.get("about.text") +
            org.ttplayer.util.Messages.get("about.opensource"), org.ttplayer.util.Messages.get("about.title"),
            JOptionPane.INFORMATION_MESSAGE); }

    private void refreshLeftList() {
        if (playlistManager == null) return;
        DefaultListModel<String> modelLeft = new DefaultListModel<>();
        List<Playlist> allPlaylists = playlistManager.getAllPlaylists();
        int currentIndex = -1;
        Playlist currentPlaylist = playlistManager.getCurrentPlaylist();
        for (int i = 0; i < allPlaylists.size(); i++) {
            Playlist pl = allPlaylists.get(i);
            modelLeft.addElement(pl.name);
            if (pl == currentPlaylist) {
                currentIndex = i;
            }
        }
        if (listLeft == null) {
            listLeft = new TransparentJList<>(modelLeft);
            listLeft.setCellRenderer(new TransparentListCellRenderer());
        } else { listLeft.setModel(modelLeft); }
        // 选中当前播放列表
        if (currentIndex >= 0) {
            listLeft.setSelectedIndex(currentIndex);
            listLeft.ensureIndexIsVisible(currentIndex);
        }
        // 确保颜色总是最新的
        listLeft.setForeground(colorText);
        listLeft.setSelectionBackground(colorSelect);
        listLeft.setSelectionForeground(colorHilight);
        listLeft.setFont(FontUtils.getDefaultChineseFont(12));
    }
    private void refreshRightList() {
        if (playlistManager == null) return;
        List<String> displayItems = new ArrayList<>();
        List<Song> songList = new ArrayList<>();
        Playlist current = playlistManager.getCurrentPlaylist();
        if (current != null) {
            int i = 1;
            for (Song song : current.songs) {
                StringBuilder sb = new StringBuilder();
                String format = MenuSettings.get("filenameFormat", "");
                String title = format.isEmpty() ? song.toString() : format.replace("%artist%", song.artist == null ? "" : song.artist)
                        .replace("%title%", song.title == null ? song.getFileName() : song.title)
                        .replace("%album%", song.album == null ? "" : song.album)
                        .replace("%filename%", song.getFileName()).replace("%track%", String.valueOf(i));
                sb.append(i++).append(". ").append(title);
                // 如果有时长，加上时长
                if (song.duration != null && !song.duration.isEmpty()) {
                    sb.append(" (").append(song.duration).append(")");
                }
                displayItems.add(sb.toString());
                songList.add(song);
            }
        }
        if (listRight == null) {
            listRight = new org.ttplayer.controls.VirtualList();
        }
        listRight.setData(displayItems, songList);
        // 确保颜色总是最新的
        listRight.setColors(colorText, colorHilight, colorSelect, colorBkgnd, colorBkgnd2,
                                 colorNumber, colorDuration);
        listRight.setFont(FontUtils.getDefaultChineseFont(12));

        // 在后台线程补读没有duration的歌曲
        if (current != null) {
            for (Song song : current.songs) {
                if (song.duration == null || song.duration.isEmpty()) {
                    new Thread(() -> song.refreshMetadata()).start();
                }
            }
        }

        // 列表刷新后保持当前播放歌曲被选中
        syncPlayingHighlight();
    }

    private static class TransparentJList<E> extends JList<E> {
        public TransparentJList(ListModel<E> model) {
            super(model);
            org.ttplayer.util.UIUtils.setTransparent(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            // 不绘制背景
            super.paintComponent(g);
        }
    }

    private void showPlaylistMenu(MouseEvent e) {
        int row = listLeft.locationToIndex(e.getPoint());
        if (row >= 0) listLeft.setSelectedIndex(row);
        playlistMenu.show(listLeft, e.getX(), e.getY());
    }
    /**
     * 创建一个完整的右键菜单（供主窗口等复用）。
     */
    public JPopupMenu createRightClickMenu() {
        return TtMenuCatalog.create(TtMenuCatalog.Kind.MAIN, this);
    }

    private void doOpenSkin() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("TTPlayer (*.skn)", "skn"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String specification = org.ttplayer.util.SkinLoader.findSkinPath(chooser.getSelectedFile());
        if (specification != null && org.ttplayer.util.SkinLoader.loadSkinFrom(new TtSkin(), specification)) {
            if (skinChangeListener != null) skinChangeListener.onSkinChanged(specification);
        } else JOptionPane.showMessageDialog(this, Messages.get("dialog.invalidSkin"), Messages.get("menu.openSkin"), JOptionPane.ERROR_MESSAGE);
    }

    private void showRightListMenu(MouseEvent event, int row) {
        if (row >= 0 && !listRight.isSelectedIndex(row)) listRight.setSelectedIndex(row);
        else if (row >= 0) listRight.setLeadSelectionIndex(row);
        TtMenuCatalog.create(TtMenuCatalog.Kind.PLAYLIST_CONTEXT, this)
                .show(listRight, event.getX(), event.getY());
    }

    private Song getContextSong() {
        Playlist current = playlistManager == null ? null : playlistManager.getCurrentPlaylist();
        int index = listRight == null ? -1 : listRight.getSelectedIndex();
        return current == null || index < 0 || index >= current.songs.size() ? null : current.songs.get(index);
    }

    static TtSkin.WindowDef findWindow(TtSkin skin) {
        return LyricWindow.findWindow(skin, "playlist_window");
    }

    private static class TransparentScrollPane extends JScrollPane {
        public TransparentScrollPane(Component view) {
            super(view);
            org.ttplayer.util.UIUtils.setTransparent(this);
            org.ttplayer.util.UIUtils.setTransparent(getViewport());
            setBorder(null);

            // 创建完全透明的角落组件
            JPanel corner = new JPanel();
            org.ttplayer.util.UIUtils.setTransparent(corner);
            setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, corner);
        }

        @Override
        protected JViewport createViewport() {
            return new TransparentViewport();
        }


        private static class TransparentViewport extends JViewport {
            public TransparentViewport() {
                org.ttplayer.util.UIUtils.setTransparent(this);
            }

            @Override
            protected void paintComponent(Graphics g) {
                // 不绘制背景，直接调用 super 以便子组件绘制
                super.paintComponent(g);
            }
        }
    }

    private class TransparentListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (isSelected) {
                setOpaque(true);
                setBackground(colorSelect);
                setForeground(colorHilight);
            } else {
                org.ttplayer.util.UIUtils.setTransparent(this);
                setForeground(colorText);
            }

            // 设置工具提示
            if (playlistManager != null && playlistManager.getCurrentPlaylist() != null &&
                index >= 0 && index < playlistManager.getCurrentPlaylist().songs.size()) {
                Song song = playlistManager.getCurrentPlaylist().songs.get(index);
                setToolTipText(org.ttplayer.util.UIUtils.generateSongTooltip(song));
            }

            return this;
        }
    }
}
