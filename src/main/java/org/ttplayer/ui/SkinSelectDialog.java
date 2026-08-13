package org.ttplayer.ui;

import org.ttplayer.util.Messages;
import org.ttplayer.util.SkinLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 皮肤选择对话框（原布局保留）
 * 仅操作按钮使用 Ant Design 风格（AntButton）
 */
public class SkinSelectDialog extends JDialog {

    // ============ Ant Design 配色（按钮用） ============
    private static final Color PRIMARY        = new Color(22, 119, 255);
    private static final Color PRIMARY_HOVER  = new Color(64, 150, 255);
    private static final Color PRIMARY_ACTIVE = new Color(9, 88, 217);
    private static final Color TEXT_MAIN      = new Color(0, 0, 0, 216);
    private static final Color TEXT_SECOND    = new Color(0, 0, 0, 110);
    private static final Color TEXT_DISABLED  = new Color(0, 0, 0, 64);
    private static final Color BORDER         = new Color(217, 217, 217);
    private static final int ARC = 6;

    private final List<SkinEntry> entries = new ArrayList<>();
    private JList<String> list;
    private JLabel nameLabel;
    private JLabel authorLabel;
    private AntButton applyBtn;
    private SkinEntry selected;

    private JPanel previewPanel;
    private java.awt.image.BufferedImage previewImg;

    /** 选中的皮肤 spec，形如 "classpath:skin/xxx.skn" 或 "fs:/abs/path/xxx.skn" */
    public String selectedSkinSpec;

    /** 从资源加载并设置对话框图标（PNG 优先，ICO 兜底） */
    private void setDialogIcon() {
        try {
            String[] candidates = {
                "ico/ttplayer_16x16_32bpp.png",
                "ico/ttplayer_32x32_32bpp.png",
                "ico/TTPlayer.ico"
            };
            for (String path : candidates) {
                java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(path);
                if (is == null) continue;
                Image img = javax.imageio.ImageIO.read(is);
                if (img != null) {
                    setIconImage(img);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    public SkinSelectDialog(Window owner) {
        super(owner, org.ttplayer.util.Messages.get("skinSelect.title"), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // 设置窗口图标
        setDialogIcon();

        scanSkins();

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, org.ttplayer.util.Messages.get("skinSelect.notFound"), org.ttplayer.util.Messages.get("skinSelect.title"), JOptionPane.INFORMATION_MESSAGE);
            dispose();
            return;
        }

        initComponents();
        pack();
        setSize(Math.max(500, getWidth()), Math.max(380, getHeight()));
        setLocationRelativeTo(owner);

        if (!entries.isEmpty()) list.setSelectedIndex(0);
    }

    private void scanSkins() {
        Set<String> seen = new HashSet<>();
        for (String spec : SkinLoader.listClasspathSkins()) {
            String name = spec.substring(spec.lastIndexOf('/') + 1);
            if (!seen.add(name)) continue;
            SkinEntry entry = readSkinInfo(spec);
            if (entry != null) entries.add(entry);
        }
        entries.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
    }

    private SkinEntry readSkinInfo(String spec) {
        SkinEntry entry = new SkinEntry();
        entry.spec = spec;
        String name = spec.substring(spec.lastIndexOf('/') + 1);
        entry.displayName = name.endsWith(".skn") ? name.substring(0, name.length() - 4) : name;

        Map<String, byte[]> zip = readZipEntries(spec);
        if (zip == null) return null;

        byte[] xmlData = zip.get("Skin.xml");
        if (xmlData == null) xmlData = zip.get("skin.xml");
        if (xmlData != null) {
            String xmlStr = decodeXmlText(xmlData);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<skin[^>]*\\sname\\s*=\\s*\"([^\"]*)\"").matcher(xmlStr);
            if (m.find()) entry.skinName = m.group(1);
            m = java.util.regex.Pattern.compile(
                "<skin[^>]*\\sauthor\\s*=\\s*\"([^\"]*)\"").matcher(xmlStr);
            if (m.find()) entry.author = m.group(1);
            m = java.util.regex.Pattern.compile(
                "<player_window[^>]*\\simage\\s*=\\s*\"([^\"]*)\"").matcher(xmlStr);
            if (m.find() && m.group(1) != null && !m.group(1).isEmpty()) {
                entry.previewData = zip.get(m.group(1));
            }
        }
        return entry;
    }

    private Map<String, byte[]> readZipEntries(String spec) {
        try {
            InputStream is;
            if (spec.startsWith("classpath:")) {
                is = getClass().getClassLoader().getResourceAsStream(spec.substring(10));
            } else if (spec.startsWith("fs:")) {
                is = new FileInputStream(spec.substring(3));
            } else {
                return null;
            }
            if (is == null) return null;
            try (ZipInputStream zis = new ZipInputStream(is)) {
                Map<String, byte[]> map = new HashMap<>();
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (!e.isDirectory()) map.put(e.getName(), readAll(zis));
                }
                return map;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(65536);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String decodeXmlText(byte[] data) {
        if (data.length >= 3 && data[0] == (byte)0xEF && data[1] == (byte)0xBB && data[2] == (byte)0xBF)
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        String head = new String(data, 0, Math.min(200, data.length), StandardCharsets.US_ASCII);
        if (head.contains("encoding=")) {
            if (head.contains("GBK") || head.contains("gb2312"))
                return new String(data, Charset.forName("GBK"));
            if (head.contains("UTF-8") || head.contains("utf-8"))
                return new String(data, StandardCharsets.UTF_8);
        }
        String utf8 = new String(data, StandardCharsets.UTF_8);
        for (char c : utf8.toCharArray()) { if (c >= 0x4E00 && c <= 0x9FFF) return utf8; }
        return new String(data, Charset.forName("GBK"));
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (SkinEntry e : entries) model.addElement(e.displayName);
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(new Font("Dialog", Font.PLAIN, 13));
        list.setFixedCellHeight(24);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(180, 0));
        root.add(listScroll, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(10, 10));

        previewPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (previewImg != null) {
                    int pw = getWidth(), ph = getHeight();
                    int iw = previewImg.getWidth(), ih = previewImg.getHeight();
                    float scale = Math.min((float) pw / iw, (float) ph / ih);
                    int dw = Math.round(iw * scale), dh = Math.round(ih * scale);
                    int dx = (pw - dw) / 2, dy = (ph - dh) / 2;
                    g.drawImage(previewImg, dx, dy, dw, dh, null);
                } else {
                    g.setColor(new Color(40, 40, 40));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Color.GRAY);
                    FontMetrics fm = g.getFontMetrics();
                    String txt = org.ttplayer.util.Messages.get("skinSelect.noPreview");
                    g.drawString(txt, (getWidth() - fm.stringWidth(txt)) / 2, getHeight() / 2 + fm.getAscent() / 2);
                }
            }
        };
        previewPanel.setOpaque(true);
        previewPanel.setBackground(new Color(30, 30, 30));
        previewPanel.setPreferredSize(new Dimension(268, 165));
        previewPanel.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));
        right.add(previewPanel, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        nameLabel = new JLabel(" ");
        nameLabel.setFont(new Font("Dialog", Font.BOLD, 14));
        authorLabel = new JLabel(" ");
        authorLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        authorLabel.setForeground(Color.GRAY);
        infoPanel.add(nameLabel);
        infoPanel.add(authorLabel);
        right.add(infoPanel, BorderLayout.SOUTH);

        root.add(right, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 5));
        applyBtn = new AntButton(org.ttplayer.util.Messages.get("skinSelect.switch"), true);
        applyBtn.setPreferredSize(new Dimension(100, 30));
        applyBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < entries.size()) {
                selectedSkinSpec = entries.get(idx).spec;
                dispose();
            }
        });
        btnPanel.add(applyBtn);

        AntButton cancelBtn = new AntButton(org.ttplayer.util.Messages.get("dialog.cancel"), false);
        cancelBtn.setPreferredSize(new Dimension(100, 30));
        cancelBtn.addActionListener(e -> dispose());
        btnPanel.add(cancelBtn);
        root.add(btnPanel, BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= entries.size()) return;
            selected = entries.get(idx);
            updatePreview(selected);
        });
    }

    private void updatePreview(SkinEntry entry) {
        nameLabel.setText(entry.skinName != null ? entry.skinName : entry.displayName);
        authorLabel.setText(entry.author != null ? org.ttplayer.util.Messages.get("skinSelect.authorPrefix") + entry.author : "");

        previewImg = null;
        if (entry.previewData != null) {
            try {
                previewImg = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(entry.previewData));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        previewPanel.repaint();
    }

    // ================================================================
    //  antd 按钮（AntButton：primary 蓝色主按钮 / 默认白底灰边）
    // ================================================================

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
            setFont(new Font("Dialog", Font.PLAIN, 13));
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
            Color c = isEnabled() ? (primary ? Color.WHITE : (hover ? PRIMARY : TEXT_MAIN)) : TEXT_DISABLED;
            gt.setColor(c);
            gt.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h + fm.getAscent()) / 2 - 2);
            gt.dispose();
        }
    }

    private static class SkinEntry {
        String spec;
        String displayName;
        String skinName;
        String author;
        byte[] previewData;
    }
}