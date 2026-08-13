package org.ttplayer.util;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 菜单图标工厂 — 程序化绘制 16x16 的美观小图标，供 JMenu/JMenuItem 使用。
 * 采用统一的配色和简洁的几何风格，保证在亮/暗皮肤下都有良好的辨识度。
 */
public class MenuIcons {

    private static final int S = 16;                      // 图标尺寸
    private static final Color ACCENT  = new Color(0x00, 0x80, 0xFF);   // 主色调（蓝）
    private static final Color ACCENT2 = new Color(0x00, 0xAA, 0x50);   // 辅色调（绿）
    private static final Color DANGER  = new Color(0xE0, 0x50, 0x50);   // 危险（红）
    private static final Color WARN    = new Color(0xF0, 0xA0, 0x20);   // 警告（橙）
    private static final Color DARK    = new Color(0xCC, 0xCC, 0xCC);   // 前景浅色
    private static final Color DARK2   = new Color(0x88, 0x88, 0x88);   // 前景次要色

    private MenuIcons() {}

    // ──── 播放控制 ────

    public static Icon play()          { return icon(g -> drawPlay(g, ACCENT)); }
    public static Icon pause()         { return icon(g -> drawPause(g, ACCENT)); }
    public static Icon previous()      { return icon(g -> drawPrevious(g, ACCENT)); }
    public static Icon next()          { return icon(g -> drawNext(g, ACCENT)); }
    public static Icon stop()          { return icon(g -> drawStop(g, DANGER)); }

    // ──── 添加操作 ────

    public static Icon add()           { return icon(g -> drawPlus(g, ACCENT2)); }
    public static Icon addFile()       { return icon(g -> { drawDoc(g, DARK); drawPlusMini(g, ACCENT2, 9, 9); }); }
    public static Icon addFolder()     { return icon(g -> { drawFolder(g, WARN); drawPlusMini(g, ACCENT2, 10, 9); }); }
    public static Icon addUrl()        { return icon(g -> { drawGlobe(g, ACCENT); drawPlusMini(g, ACCENT2, 10, 10); }); }
    public static Icon search()        { return icon(g -> drawMagnify(g, ACCENT)); }
    public static Icon addSearch()     { return icon(g -> { drawMagnify(g, ACCENT); drawPlusMini(g, ACCENT2, 11, 11); }); }

    // ──── 删除/清除 ────

    public static Icon delete()        { return icon(g -> drawX(g, DANGER)); }
    public static Icon delSelected()   { return icon(g -> { drawCheck(g, ACCENT); drawX(g, DANGER, 9, 3); }); }
    public static Icon delDup()        { return icon(g -> { drawOverlap(g, DARK, DARK2); drawX(g, DANGER, 10, 4); }); }
    public static Icon clearList()     { return icon(g -> drawTrash(g, DANGER)); }

    // ──── 排序 ────

    public static Icon sortTitle()     { return icon(g -> drawAZ(g, ACCENT)); }
    public static Icon sortArtist()    { return icon(g -> drawPerson(g, ACCENT)); }
    public static Icon sortFile()      { return icon(g -> drawDoc(g, DARK)); }
    public static Icon sortPath()      { return icon(g -> drawFolderTree(g, DARK)); }
    public static Icon reverse()       { return icon(g -> drawUpDown(g, ACCENT)); }
    public static Icon randomSort()    { return icon(g -> drawShuffle(g, ACCENT2)); }

    // ──── 查找 ────

    public static Icon find()          { return icon(g -> drawMagnify(g, ACCENT)); }
    public static Icon findNext()      { return icon(g -> { drawMagnify(g, ACCENT); drawArrowDown(g, ACCENT2, 11, 11); }); }
    public static Icon locate()        { return icon(g -> drawCrosshair(g, ACCENT2)); }

    // ──── 播放模式 ────

    public static Icon modeSequential(){ return icon(g -> drawList(g, ACCENT)); }
    public static Icon modelLoop()     { return icon(g -> drawLoop(g, ACCENT)); }
    public static Icon modeSingle()    { return icon(g -> drawOne(g, ACCENT)); }
    public static Icon modeRandom()    { return icon(g -> drawDice(g, ACCENT2)); }
    public static Icon pauseAfter()    { return icon(g -> drawPauseEnd(g, WARN)); }

    // ──── 选项/工具 ────

    public static Icon settings()      { return icon(g -> drawGear(g, DARK)); }
    public static Icon info()          { return icon(g -> drawInfoCircle(g, ACCENT)); }
    public static Icon edit()          { return icon(g -> drawPencil(g, ACCENT)); }
    public static Icon skin()          { return icon(g -> drawPalette(g, ACCENT2)); }
    public static Icon options()       { return icon(g -> drawGear(g, ACCENT)); }

    // ──── 窗口/视图 ────

    public static Icon showWindow()    { return icon(g -> drawWindow(g, ACCENT)); }
    public static Icon lyric()         { return icon(g -> drawMusicNote(g, ACCENT2)); }
    public static Icon equalizer()     { return icon(g -> drawSliders(g, ACCENT)); }
    public static Icon desktopLyric()  { return icon(g -> drawMonitor(g, ACCENT)); }
    public static Icon miniMode()      { return icon(g -> drawMinimize(g, DARK)); }

    // ──── 通用 ────

    public static Icon about()         { return icon(g -> drawInfoCircle(g, ACCENT)); }
    public static Icon exit()          { return icon(g -> drawPower(g, DANGER)); }
    public static Icon language()      { return icon(g -> drawGlobe(g, ACCENT)); }
    public static Icon playlist()      { return icon(g -> drawList(g, ACCENT2)); }
    public static Icon newList()       { return icon(g -> { drawDoc(g, DARK); drawStarMini(g, ACCENT2, 10, 3); }); }
    public static Icon saveList()      { return icon(g -> drawSave(g, ACCENT)); }
    public static Icon rename()        { return icon(g -> drawPencil(g, ACCENT)); }
    public static Icon switchList()    { return icon(g -> drawSwitch(g, ACCENT)); }
    public static Icon sortPlaylists() { return icon(g -> drawAZ(g, DARK)); }
    public static Icon addList()       { return icon(g -> { drawFolder(g, WARN); drawPlusMini(g, ACCENT2, 10, 9); }); }
    public static Icon saveAll()       { return icon(g -> drawSaveAll(g, ACCENT)); }

    // ═══════════════════════════════════════════
    //  内部绘制方法
    // ═══════════════════════════════════════════

    private static Icon icon(Drawer drawer) {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        drawer.draw(g);
        g.dispose();
        return new ImageIcon(img);
    }

    @FunctionalInterface
    private interface Drawer {
        void draw(Graphics2D g);
    }

    // ──── 基础形状 ────

    private static void drawPlay(Graphics2D g, Color c) {
        g.setColor(c);
        int[] xs = {4, 4, 13};
        int[] ys = {2, 14, 8};
        g.fillPolygon(xs, ys, 3);
    }

    private static void drawPause(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(4, 2, 3, 12, 2, 2);
        g.fillRoundRect(9, 2, 3, 12, 2, 2);
    }

    private static void drawPrevious(Graphics2D g, Color c) {
        g.setColor(c);
        int[] xs = {12, 12, 4};
        int[] ys = {2, 14, 8};
        g.fillPolygon(xs, ys, 3);
        g.fillRect(12, 2, 2, 12);
    }

    private static void drawNext(Graphics2D g, Color c) {
        g.setColor(c);
        int[] xs = {4, 4, 12};
        int[] ys = {2, 14, 8};
        g.fillPolygon(xs, ys, 3);
        g.fillRect(2, 2, 2, 12);
    }

    private static void drawStop(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(3, 3, 10, 10, 2, 2);
    }

    private static void drawPlus(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(8, 4, 8, 12);
        g.drawLine(4, 8, 12, 8);
    }

    private static void drawPlusMini(Graphics2D g, Color c, int cx, int cy) {
        g.setColor(c);
        g.fillRect(cx - 3, cy - 1, 6, 2);
        g.fillRect(cx - 1, cy - 3, 2, 6);
    }

    private static void drawX(Graphics2D g, Color c) {
        drawX(g, c, 4, 4);
    }

    private static void drawX(Graphics2D g, Color c, int x, int y) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x, y, x + 8, y + 8);
        g.drawLine(x + 8, y, x, y + 8);
    }

    private static void drawCheck(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 8, 6, 11);
        g.drawLine(6, 11, 12, 4);
    }

    // ──── 文档/文件夹 ────

    private static void drawDoc(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(3, 2, 10, 12, 2, 2);
        g.setColor(darker(c));
        g.fillRect(7, 2, 6, 3);
    }

    private static void drawFolder(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(2, 4, 12, 9, 1, 1);
        g.fillRect(2, 3, 7, 3);
    }

    private static void drawFolderTree(Graphics2D g, Color c) {
        drawFolder(g, c);
        g.setColor(c);
        g.drawLine(8, 3, 8, 2);
        g.drawLine(8, 2, 13, 2);
    }

    private static void drawOverlap(Graphics2D g, Color front, Color back) {
        g.setColor(back);
        g.fillRoundRect(5, 5, 8, 9, 2, 2);
        g.setColor(front);
        g.fillRoundRect(3, 2, 8, 9, 2, 2);
        g.fillRect(7, 2, 4, 3);
    }

    // ──── 地球 ────

    private static void drawGlobe(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(3, 3, 10, 10);
        g.drawLine(8, 3, 8, 13);
        g.drawLine(3, 8, 13, 8);
        g.drawArc(5, 3, 6, 10, 0, -180);
        g.drawArc(5, 3, 6, 10, 0, 180);
    }

    // ──── 放大镜 ────

    private static void drawMagnify(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(3, 3, 8, 8);
        g.drawLine(9, 9, 12, 12);
    }

    // ──── 垃圾桶 ────

    private static void drawTrash(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(5, 3, 6, 2, 1, 1);
        g.drawLine(4, 5, 4, 13);
        g.drawLine(12, 5, 12, 13);
        g.drawLine(4, 13, 12, 13);
        g.drawLine(5, 5, 5, 12);
        g.drawLine(11, 5, 11, 12);
    }

    // ──── 排序相关 ────

    private static void drawAZ(Graphics2D g, Color c) {
        g.setColor(c);
        g.setFont(new Font("Dialog", Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("A", 2, 9);
        g.drawString("Z", 7, 13);
    }

    private static void drawPerson(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillOval(5, 2, 6, 6);
        g.fillOval(3, 9, 10, 6);
    }

    private static void drawUpDown(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // 上箭头
        g.drawLine(4, 6, 8, 3);
        g.drawLine(8, 3, 12, 6);
        // 下箭头
        g.drawLine(4, 11, 8, 14);
        g.drawLine(8, 14, 12, 11);
    }

    private static void drawShuffle(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(2, 5, 8, 5);
        g.drawLine(8, 5, 12, 2);
        g.drawLine(12, 2, 12, 8);
        g.drawLine(14, 11, 8, 11);
        g.drawLine(8, 11, 4, 14);
        g.drawLine(4, 14, 4, 8);
        g.drawLine(8, 5, 8, 11);
    }

    // ──── 查找相关 ────

    private static void drawArrowDown(Graphics2D g, Color c, int x, int y) {
        g.setColor(c);
        int[] xs = {x, x + 3, x - 3};
        int[] ys = {y, y - 4, y - 4};
        g.fillPolygon(xs, ys, 3);
    }

    private static void drawCrosshair(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(4, 4, 8, 8);
        g.drawLine(8, 2, 8, 5);
        g.drawLine(8, 11, 8, 14);
        g.drawLine(2, 8, 5, 8);
        g.drawLine(11, 8, 14, 8);
    }

    // ──── 模式相关 ────

    private static void drawList(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 5, 13, 5);
        g.drawLine(3, 8, 13, 8);
        g.drawLine(3, 11, 13, 11);
    }

    private static void drawLoop(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(3, 3, 10, 10, 30, 300);
        int[] xs = {12, 10, 13};
        int[] ys = {3, 3, 6};
        g.fillPolygon(xs, ys, 3);
    }

    private static void drawOne(Graphics2D g, Color c) {
        g.setColor(c);
        g.setFont(new Font("Dialog", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String s = "1";
        g.drawString(s, (S - fm.stringWidth(s)) / 2, (S + fm.getAscent()) / 2);
    }

    private static void drawDice(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(3, 3, 10, 10, 2, 2);
        g.setColor(Color.WHITE);
        g.fillOval(5, 5, 2, 2);
        g.fillOval(10, 10, 2, 2);
    }

    private static void drawPauseEnd(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(3, 3, 4, 10, 2, 2);
        g.fillRoundRect(9, 3, 4, 10, 2, 2);
        // 终点小旗
        g.setColor(WARN);
        g.fillRect(13, 3, 2, 10);
    }

    // ──── 齿轮/选项 ────

    private static void drawGear(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(5, 5, 6, 6);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int x1 = (int)(8 + 4 * Math.cos(angle));
            int y1 = (int)(8 + 4 * Math.sin(angle));
            int x2 = (int)(8 + 6 * Math.cos(angle));
            int y2 = (int)(8 + 6 * Math.sin(angle));
            g.drawLine(x1, y1, x2, y2);
        }
    }

    private static void drawInfoCircle(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(3, 3, 10, 10);
        g.fillRect(7, 5, 2, 2);
        g.fillRect(7, 8, 2, 4);
    }

    private static void drawPencil(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 11, 6, 8);
        g.drawLine(6, 8, 11, 3);
        g.drawLine(4, 13, 7, 10);
    }

    private static void drawPalette(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillOval(5, 2, 8, 9);
        g.fillRect(4, 10, 10, 4);
        g.setColor(ACCENT2);
        g.fillOval(6, 4, 2, 2);
        g.setColor(DANGER);
        g.fillOval(10, 4, 2, 2);
        g.setColor(WARN);
        g.fillOval(8, 6, 2, 2);
    }

    // ──── 窗口/视图 ────

    private static void drawWindow(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(2, 3, 12, 10);
        g.drawLine(2, 6, 14, 6);
        g.drawLine(8, 6, 8, 13);
    }

    private static void drawMusicNote(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillOval(3, 10, 4, 3);
        g.fillRect(6, 3, 2, 9);
        g.fillOval(9, 9, 4, 3);
        g.fillRect(12, 4, 2, 7);
    }

    private static void drawSliders(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(4, 5, 4, 13);
        g.drawLine(8, 4, 8, 13);
        g.drawLine(12, 6, 12, 13);
        g.setColor(ACCENT2);
        g.fillOval(2, 4, 4, 4);
        g.fillOval(6, 8, 4, 4);
        g.fillOval(10, 3, 4, 4);
    }

    private static void drawMonitor(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(3, 2, 10, 8);
        g.drawLine(8, 10, 8, 13);
        g.drawLine(5, 13, 11, 13);
    }

    private static void drawMinimize(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(4, 11, 12, 11);
        g.fillRect(4, 11, 8, 2);
    }

    // ──── 其他 ────

    private static void drawPower(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(4, 3, 8, 8, 0, 270);
        g.drawLine(8, 2, 8, 7);
    }

    private static void drawSave(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(3, 2, 10, 12, 2, 2);
        g.setColor(darker(c));
        g.fillRect(3, 2, 10, 4);
        g.setColor(Color.WHITE);
        g.fillRect(6, 7, 4, 5);
    }

    private static void drawSaveAll(Graphics2D g, Color c) {
        g.setColor(c);
        g.fillRoundRect(2, 4, 7, 9, 2, 2);
        g.fillRoundRect(7, 1, 7, 9, 2, 2);
        g.setColor(darker(c));
        g.fillRect(2, 4, 7, 3);
        g.fillRect(7, 1, 7, 3);
    }

    private static void drawSwitch(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(3, 6, 10, 6);
        g.drawLine(10, 6, 12, 4);
        g.drawLine(10, 6, 12, 8);
        g.drawLine(13, 10, 6, 10);
        g.drawLine(6, 10, 4, 8);
        g.drawLine(6, 10, 4, 12);
    }

    private static void drawStarMini(Graphics2D g, Color c, int x, int y) {
        g.setColor(c);
        int[] xs = new int[10];
        int[] ys = new int[10];
        double cx = x + 2.5, cy = y + 2.5, r = 3;
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            double rad = (i % 2 == 0) ? r : r * 0.4;
            xs[i] = (int) Math.round(cx + rad * Math.cos(ang));
            ys[i] = (int) Math.round(cy + rad * Math.sin(ang));
        }
        g.fillPolygon(xs, ys, 10);
    }

    private static Color darker(Color c) {
        return new Color(
            Math.max(0, c.getRed()   - 40),
            Math.max(0, c.getGreen() - 40),
            Math.max(0, c.getBlue()  - 40),
            c.getAlpha()
        );
    }
}
