package org.ttplayer.ui;

import org.ttplayer.model.Song;
import org.ttplayer.lyrics.LyricSearchService;
import org.ttplayer.lyrics.SongResult;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import org.ttplayer.util.Messages;

/**
 * 歌词搜索对话框 — Ant Design 风格
 * 主色 #1677ff / 白色卡片 / 圆角控件 / 浅灰页面背景
 */
public class LyricSearchDialog extends JDialog {

    // ============ Ant Design 配色 ============
    private static final Color PRIMARY        = new Color(22, 119, 255);      // #1677ff
    private static final Color PRIMARY_HOVER  = new Color(64, 150, 255);      // #4096ff
    private static final Color PRIMARY_ACTIVE = new Color(9, 88, 217);        // #0958d9
    private static final Color TEXT_MAIN      = new Color(0, 0, 0, 216);     // rgba(0,0,0,0.85)
    private static final Color TEXT_SECOND    = new Color(0, 0, 0, 110);     // rgba(0,0,0,0.45)
    private static final Color TEXT_DISABLED  = new Color(0, 0, 0, 64);      // rgba(0,0,0,0.25)
    private static final Color BORDER         = new Color(217, 217, 217);     // #d9d9d9
    private static final Color LINE           = new Color(240, 240, 240);     // #f0f0f0
    private static final Color BG_PAGE        = new Color(240, 242, 245);     // #f0f2f5
    private static final Color BG_HEADER      = new Color(250, 250, 250);     // #fafafa
    private static final Color SELECTED_ROW   = new Color(230, 244, 255);     // #e6f4ff
    private static final Color HOVER_ROW      = new Color(250, 250, 250);     // #fafafa

    private Song currentSong;
    private JTextField artistField;
    private JTextField titleField;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JTextField savePathField;
    private AntCheckBox associateCheckBox;
    private AntButton downloadButton;
    private AntButton searchButton;
    private List<SongResult> currentResults;
    private Runnable onLyricDownloaded;

    private int hoverRow = -1;

    public LyricSearchDialog(Window owner, Song song) {
        super(owner, Messages.get("lyricSearch.title"), ModalityType.APPLICATION_MODAL);
        this.currentSong = song;
        initComponents();
        if (song != null) {
            artistField.setText(song.artist != null ? song.artist : "");
            titleField.setText(song.title != null ? song.title : "");
        }
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    public void setOnLyricDownloaded(Runnable r) { this.onLyricDownloaded = r; }

    private void initComponents() {
        // ---- 整体页面背景 ----
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BG_PAGE);
        setContentPane(page);

        // ---- 白色卡片 ----
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(
                new javax.swing.border.LineBorder(new Color(0, 0, 0, 32), 1),
                new EmptyBorder(0, 0, 0, 0)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 0);
        card.setPreferredSize(new Dimension(580, 540));
        page.add(card, gbc);
        card.add(buildBody(), BorderLayout.CENTER);
        card.add(buildFooter(), BorderLayout.SOUTH);
    }



    // ============ 中部：搜索表单 + 结果表格 ============
    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(4, 20, 0, 20));
        body.add(buildSearchRow(), BorderLayout.NORTH);
        body.add(buildTable(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildSearchRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Color.WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;

        // 歌手
        c.gridx = 0; c.weightx = 0;
        row.add(newLabel(Messages.get("lyricSearch.singerLabel")), c);
        c.gridx = 1; c.weightx = 1; c.insets = new Insets(0, 8, 0, 16);
        artistField = new AntTextField();
        artistField.setPreferredSize(new Dimension(0, 32));
        row.add(artistField, c);

        // 歌名
        c.gridx = 2; c.weightx = 0; c.insets = new Insets(0, 0, 0, 0);
        row.add(newLabel(Messages.get("lyricSearch.songLabel")), c);
        c.gridx = 3; c.weightx = 1; c.insets = new Insets(0, 8, 0, 0);
        titleField = new AntTextField();
        titleField.setPreferredSize(new Dimension(0, 32));
        row.add(titleField, c);

        // 搜索
        c.gridx = 4; c.weightx = 0; c.insets = new Insets(0, 12, 0, 0);
        searchButton = new AntButton(Messages.get("lyricSearch.search"), true);
        searchButton.setPreferredSize(new Dimension(84, 32));
        searchButton.addActionListener(e -> doSearch());
        row.add(searchButton, c);

        return row;
    }

    private JPanel buildTable() {
        String[] columns = {
            Messages.get("lyricSearch.singerColumn"),
            Messages.get("lyricSearch.songColumn"),
            Messages.get("lyricSearch.albumColumn")
        };
        tableModel = new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.setOpaque(true);
        resultTable.setBackground(Color.WHITE);
        resultTable.setForeground(TEXT_MAIN);
        resultTable.setShowGrid(false);
        resultTable.setRowHeight(36);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setSelectionBackground(SELECTED_ROW);
        resultTable.setSelectionForeground(TEXT_MAIN);
        resultTable.setIntercellSpacing(new Dimension(0, 0));
        resultTable.setFocusable(false);
        resultTable.setFont(new Font("Dialog", Font.PLAIN, 14));

        // 表头
        JTableHeader header = resultTable.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 38));
        header.setDefaultRenderer(new AntHeaderRenderer());
        header.setBackground(BG_HEADER);

        resultTable.setDefaultRenderer(Object.class, new AntCellRenderer());

        // 行悬停
        resultTable.addMouseMotionListener(new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                int r = resultTable.rowAtPoint(e.getPoint());
                if (r != hoverRow) {
                    int old = hoverRow;
                    hoverRow = r;
                    if (old >= 0) resultTable.repaint(resultTable.getCellRect(old, 0, true));
                    if (hoverRow >= 0) resultTable.repaint(resultTable.getCellRect(hoverRow, 0, true));
                }
            }
            public void mouseExited(MouseEvent e) {
                int old = hoverRow;
                hoverRow = -1;
                if (old >= 0) resultTable.repaint(resultTable.getCellRect(old, 0, true));
            }
        });

        resultTable.getSelectionModel().addListSelectionListener(e -> {
            downloadButton.setEnabled(resultTable.getSelectedRow() >= 0);
        });
        resultTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && resultTable.getSelectedRow() >= 0) {
                    doDownload();
                }
            }
        });

        setHeaderColumnWidths(resultTable);

        JScrollPane scroll = new JScrollPane(resultTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(true);
        scroll.setBackground(Color.WHITE);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBorder(new CompoundBorder(
                new javax.swing.border.LineBorder(LINE, 1),
                new EmptyBorder(1, 1, 1, 1)));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Color.WHITE);
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private void setHeaderColumnWidths(JTable table) {
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
    }

    private JLabel newLabel(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Dialog", Font.PLAIN, 14));
        lb.setForeground(TEXT_MAIN);
        return lb;
    }

    // ============ 底部：保存路径 + 操作按钮 ============
    private JPanel buildFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBackground(Color.WHITE);

        // ---- 保存路径行 ----
        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.setBackground(Color.WHITE);
        pathRow.setBorder(new EmptyBorder(16, 20, 0, 16));
        pathRow.add(newLabel(Messages.get("lyricSearch.saveAs")), BorderLayout.WEST);
        savePathField = new AntTextField();
        savePathField.setPreferredSize(new Dimension(0, 32));
        if (currentSong != null && currentSong.filePath != null) {
            try {
                File audioFile = new File(currentSong.filePath);
                String name = audioFile.getName();
                int dot = name.lastIndexOf('.');
                String baseName = (dot > 0 ? name.substring(0, dot) : name);
                savePathField.setText(baseName + ".lrc");
            } catch (Exception ignored) {}
        }
        pathRow.add(savePathField, BorderLayout.CENTER);
        AntButton browseButton = new AntButton("…", false);
        browseButton.setPreferredSize(new Dimension(44, 32));
        browseButton.addActionListener(e -> browseSavePath());
        pathRow.add(browseButton, BorderLayout.EAST);
        footer.add(pathRow);

        // ---- 关联保存 checkbox ----
        JPanel chkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        chkRow.setBackground(Color.WHITE);
        chkRow.setBorder(new EmptyBorder(4, 20, 0, 0));
        associateCheckBox = new AntCheckBox(Messages.get("lyricSearch.saveToSongDir"), true);
        chkRow.add(associateCheckBox);
        footer.add(chkRow);

        // ---- 底部按钮行 ----
        JPanel btnRow = new JPanel(new BorderLayout());
        btnRow.setBackground(Color.WHITE);
        btnRow.setBorder(new CompoundBorder(
                new javax.swing.border.MatteBorder(1, 0, 0, 0, LINE),
                new EmptyBorder(12, 20, 16, 16)));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setBackground(Color.WHITE);
        downloadButton = new AntButton(Messages.get("lyricSearch.download") + " 歌词", true);
        downloadButton.setEnabled(false);
        downloadButton.setPreferredSize(new Dimension(108, 34));
        downloadButton.addActionListener(e -> doDownload());
        btns.add(downloadButton);
        AntButton closeButton = new AntButton(Messages.get("lyricSearch.close"), false);
        closeButton.setPreferredSize(new Dimension(76, 34));
        closeButton.addActionListener(e -> dispose());
        btns.add(closeButton);
        btnRow.add(btns, BorderLayout.EAST);
        footer.add(btnRow);

        return footer;
    }

    // ============ 业务逻辑 ============

    private void doSearch() {
        String keyword = titleField.getText().trim();
        if (keyword.isEmpty()) {
            keyword = artistField.getText().trim();
        }
        if (keyword.isEmpty()) {
            keyword = (artistField.getText().trim() + " " + titleField.getText().trim()).trim();
        }
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this, Messages.get("lyricSearch.needKeyword"), Messages.get("dialog.hint"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String oldText = searchButton.getText();
        searchButton.setEnabled(false);
        searchButton.setText("…");
        tableModel.setRowCount(0);
        currentResults = null;
        downloadButton.setEnabled(false);
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        final String searchKeyword = keyword;

        SwingWorker<List<SongResult>, Void> worker = new SwingWorker<List<SongResult>, Void>() {
            protected List<SongResult> doInBackground() throws Exception {
                return LyricSearchService.searchSong(searchKeyword, 20);
            }
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                searchButton.setEnabled(true);
                searchButton.setText(oldText);
                try {
                    currentResults = get();
                    if (currentResults.isEmpty()) {
                        JOptionPane.showMessageDialog(LyricSearchDialog.this,
                            Messages.get("lyricSearch.notFoundPrefix") + searchKeyword + Messages.get("lyricSearch.notFoundSuffix"), Messages.get("lyricSearch.resultTitle"), JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        for (SongResult r : currentResults) {
                            tableModel.addRow(new Object[]{
                                r.getSinger() != null ? r.getSinger() : "未知",
                                r.getName() != null ? r.getName() : "",
                                r.getAlbum() != null ? r.getAlbum() : ""
                            });
                        }
                        resultTable.setRowSelectionInterval(0, 0);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(LyricSearchDialog.this,
                        Messages.get("lyricSearch.searchFailPrefix") + ex.getMessage(), Messages.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /** 与歌曲同名的 .lrc 文件名 */
    private static String lrcBaseName(File audioFile) {
        String name = audioFile.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".lrc";
    }

    private void browseSavePath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("lyricSearch.chooseSavePath"));
        chooser.setSelectedFile(new File(savePathField.getText()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".lrc")) path += ".lrc";
            savePathField.setText(path);
        }
    }

    private void doDownload() {
        int row = resultTable.getSelectedRow();
        if (row < 0 || currentResults == null || row >= currentResults.size()) {
            JOptionPane.showMessageDialog(this, Messages.get("lyricSearch.selectSongFirst"), Messages.get("dialog.hint"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 勾选"同时保存到歌曲目录"时，主保存目标为歌曲所在目录下的同名 .lrc
        final SongResult selected = currentResults.get(row);
        final File targetFile;
        if (associateCheckBox.isSelected() && currentSong != null && currentSong.filePath != null) {
            File audioFile = new File(currentSong.filePath);
            targetFile = new File(audioFile.getParent(), lrcBaseName(audioFile));
        } else {
            String savePath = savePathField.getText().trim();
            if (savePath.isEmpty()) {
                JOptionPane.showMessageDialog(this, Messages.get("lyricSearch.needSavePath"), Messages.get("dialog.hint"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            targetFile = new File(savePath);
        }
        setCursor(new Cursor(Cursor.WAIT_CURSOR));
        downloadButton.setEnabled(false);
        String oldText = downloadButton.getText();
        downloadButton.setText("…");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                return LyricSearchService.getLyricByMid(selected.getMid());
            }
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                downloadButton.setEnabled(true);
                downloadButton.setText(oldText);
                try {
                    String lrcText = get();
                    if (lrcText == null || lrcText.trim().isEmpty()) {
                        JOptionPane.showMessageDialog(LyricSearchDialog.this,
                            Messages.get("lyricSearch.noLyric"), Messages.get("dialog.hint"), JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    try (PrintWriter w = new PrintWriter(targetFile, "UTF-8")) {
                        w.print(lrcText);
                    }

                    JOptionPane.showMessageDialog(LyricSearchDialog.this,
                        Messages.get("lyricSearch.savedPrefix") + targetFile.getAbsolutePath(), Messages.get("lyricSearch.downloadSuccess"), JOptionPane.INFORMATION_MESSAGE);
                    dispose();

                    if (onLyricDownloaded != null) {
                        onLyricDownloaded.run();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LyricSearchDialog.this,
                        Messages.get("lyricSearch.downloadFailPrefix") + ex.getMessage(), Messages.get("dialog.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ================================================================
    //  Ant Design 风格控件
    // ================================================================

    private static final int ARC = 6;

    /** antd 主按钮 / 默认按钮 */
    private static class AntButton extends JButton {
        final boolean primary;
        boolean hover;
        boolean pressedIn;

        AntButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(new Font("Dialog", Font.PLAIN, 14));
            setForeground(primary ? Color.WHITE : TEXT_MAIN);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e)  { hover = false; pressedIn = false; repaint(); }
                public void mousePressed(MouseEvent e) { pressedIn = true; repaint(); }
                public void mouseReleased(MouseEvent e){ pressedIn = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, ARC, ARC);

            if (!isEnabled()) {
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fill(shape);
                g2.setColor(BORDER);
                g2.draw(shape);
            } else if (primary) {
                g2.setColor(pressedIn ? PRIMARY_ACTIVE : (hover ? PRIMARY_HOVER : PRIMARY));
                g2.fill(shape);
            } else {
                g2.setColor(pressedIn ? new Color(0, 0, 0, 20) : Color.WHITE);
                g2.fill(shape);
                g2.setColor(hover ? PRIMARY : BORDER);
                g2.draw(shape);
            }
            g2.dispose();

            Graphics2D gt = (Graphics2D) g.create();
            gt.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = gt.getFontMetrics();
            int tw = fm.stringWidth(getText());
            int th = fm.getAscent();
            Color c = isEnabled() ? (primary ? Color.WHITE : (hover ? PRIMARY : TEXT_MAIN))
                                  : TEXT_DISABLED;
            gt.setColor(c);
            gt.drawString(getText(), (w - tw) / 2, (h + th) / 2 - 2);
            gt.dispose();
        }
    }

    /** header 右上角圆形关闭按钮 */
    private static class AntIconButton extends JButton {
        boolean hover;

        AntIconButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(new Font("Dialog", Font.PLAIN, 14));
            setForeground(TEXT_SECOND);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        protected void onAction() {}

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g2.setColor(new Color(0, 0, 0, 20));
                g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, ARC + 2, ARC + 2));
            }
            g2.setColor(hover ? TEXT_MAIN : TEXT_SECOND);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent()) / 2 - 2);
            g2.dispose();
        }
    }

    /** antd 输入框：默认灰边，聚焦蓝边 */
    private static class AntTextField extends JTextField {
        private final Border normal = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(0, 10, 0, 10));
        private final Border focus = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY, 1),
                BorderFactory.createEmptyBorder(0, 10, 0, 10));

        AntTextField() {
            setFont(new Font("Dialog", Font.PLAIN, 14));
            setForeground(TEXT_MAIN);
            setBorder(normal);
            setCaretColor(PRIMARY);
            setSelectionColor(new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 50));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { setBorder(focus); repaint(); }
                public void focusLost(FocusEvent e)   { setBorder(normal); repaint(); }
            });
        }
    }

    /** antd 复选框：蓝色圆角勾选框 */
    private static class AntCheckBox extends JCheckBox {
        boolean hover;

        AntCheckBox(String text, boolean selected) {
            super(text);
            setSelected(selected);
            setOpaque(false);
            setFont(new Font("Dialog", Font.PLAIN, 14));
            setForeground(TEXT_MAIN);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(new AntCheckIcon(this));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }
    }

    private static class AntCheckIcon implements Icon {
        private final JCheckBox box;
        AntCheckIcon(JCheckBox box) { this.box = box; }

        public int getIconWidth()  { return 18; }
        public int getIconHeight() { return 18; }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = (box instanceof AntCheckBox) && ((AntCheckBox) box).hover;
            boolean sel = box.isSelected();
            RoundRectangle2D boxShape = new RoundRectangle2D.Float(x + 1, y + 1, 14, 14, 3, 3);
            if (sel) {
                g2.setColor(PRIMARY);
                g2.fill(boxShape);
                g2.setPaint(null);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(Color.WHITE);
                Path2D check = new Path2D.Float();
                check.moveTo(x + 4.2f, y + 8.2f);
                check.lineTo(x + 7.2f, y + 11f);
                check.lineTo(x + 12.5f, y + 5f);
                g2.draw(check);
            } else {
                g2.setColor(hover ? PRIMARY : BORDER);
                g2.draw(boxShape);
            }
            g2.dispose();
        }
    }

    // ============ antd 表格渲染 ============

    private static class AntHeaderRenderer implements TableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lb = new JLabel(value != null ? value.toString() : "");
            lb.setOpaque(true);
            lb.setBackground(BG_HEADER);
            lb.setForeground(TEXT_MAIN);
            lb.setFont(new Font("Dialog", Font.BOLD, 13));
            lb.setBorder(new CompoundBorder(
                    new javax.swing.border.MatteBorder(0, 0, 1, 0, LINE),
                    new EmptyBorder(0, 10, 0, 10)));
            lb.setHorizontalAlignment(SwingConstants.LEFT);
            return lb;
        }
    }

    private class AntCellRenderer implements TableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lb = new JLabel(value != null ? value.toString() : "");
            lb.setFont(new Font("Dialog", Font.PLAIN, 13));
            if (isSelected) {
                lb.setOpaque(true);
                lb.setBackground(SELECTED_ROW);
                lb.setForeground(TEXT_MAIN);
            } else if (row == hoverRow) {
                lb.setOpaque(true);
                lb.setBackground(HOVER_ROW);
                lb.setForeground(TEXT_MAIN);
            } else {
                lb.setOpaque(false);
                lb.setForeground(TEXT_MAIN);
            }
            lb.setBorder(new CompoundBorder(
                    new javax.swing.border.MatteBorder(0, 0, 1, 0, LINE),
                    new EmptyBorder(0, 10, 0, 0)));
            return lb;
        }
    }
}