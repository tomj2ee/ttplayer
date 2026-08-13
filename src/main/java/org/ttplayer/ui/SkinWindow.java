package org.ttplayer.ui;

import org.ttplayer.skin.TtSkin;
import org.ttplayer.controls.TtButton;
import org.ttplayer.controls.TtLed;
import org.ttplayer.util.ColorUtils;
import org.ttplayer.util.FontUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 千千静听皮肤窗口基类。
 * 加载一张背景 BMP，按透明色镂空，支持九宫格缩放和边缘拖拽。
 */
public class SkinWindow extends JFrame {

    public final TtSkin skin;
    public final TtSkin.WindowDef def;
    protected final Color transparentColor;
    private BufferedImage bgImage;

    protected final boolean isMain;

    private int[] ninePatch;
    private int tile;
    protected int bgW, bgH;

    private int minW = 100, minH = 60;

    private List<ControlInfo> controls = new ArrayList<>();

    private static class ControlInfo {
        Component comp;
        TtSkin.Ctl ctl;
        ControlInfo(Component comp, TtSkin.Ctl ctl) {
            this.comp = comp;
            this.ctl = ctl;
        }
    }

    // ================================================================
    //  构造
    // ================================================================

    public SkinWindow(TtSkin skin, TtSkin.WindowDef def, boolean isMain) {
        super(def != null ? def.name : "Window");
        this.skin = skin;
        this.def = def;
        this.transparentColor = skin != null ? skin.getTransparentColor() : new Color(255, 0, 255);
        this.isMain = isMain;
        if (def == null) return;
        if (!isMain) {
            setType(Window.Type.UTILITY);
        }
        setUndecorated(true);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        loadBg();
        setupResizeRect();
        if (bgW > 0 && bgH > 0) {
            setSize(bgW, bgH);
        } else if (def.width > 0 && def.height > 0) {
            setSize(def.width, def.height);
        } else {
            setSize(268, 165);
        }
        setContentPane(new BgPanel());
        setBackground(new Color(0, 0, 0, 0));
        buildControls();
        if (hasResizeRect()) {
            new EdgeResizer(this);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                public void componentResized(java.awt.event.ComponentEvent e) {
                    repositionControls();
                    updateWindowShape();
                }
            });
        } else {
            setResizable(false);
        }
    }

    protected void buildControls() {}

    protected void updateWindowShape() {}

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    public void setMinSize(int w, int h) { minW = w; minH = h; }

    // ================================================================
    //  控件重新定位
    // ================================================================

    protected void repositionControls() {
        int newW = getWidth();
        int newH = getHeight();
        int deltaW = newW - bgW;
        int deltaH = newH - bgH;

        for (ControlInfo ci : controls) {
            repositionControl(ci, deltaW, deltaH);
        }
    }

    private void repositionControl(ControlInfo ci, int deltaW, int deltaH) {
        TtSkin.Ctl ctl = ci.ctl;
        Component comp = ci.comp;

        // 对于按钮，保持它们当前的大小（由图片决定），只调整位置
        int origW = comp.getWidth();
        int origH = comp.getHeight();

        // 但对于非按钮（如标签、文本），使用配置中的大小
        if (!(comp instanceof TtButton)) {
            origW = ctl.right - ctl.left;
            origH = ctl.bottom - ctl.top;
        }

        int newX = ctl.left;
        int newY = ctl.top;

        if (ctl.align != null) {
            String align = ctl.align.toLowerCase();

            // ---- 水平定位：fill > right > center > left ----
            if (hasAlign(align, "fill", "stretch", "hfill", "xfill", "width", "expand")) {
                // 拉伸填满水平：左距 = ctl.left，右距 = bgW - ctl.right
                int leftMargin = ctl.left;
                int rightMargin = bgW - ctl.right;
                newX = leftMargin;
                origW = Math.max(1, getWidth() - leftMargin - rightMargin);
            } else if (hasAlign(align, "right")) {
                // 保持到右边缘的距离不变：右边距 = bgW - ctl.right
                int rightMargin = bgW - ctl.right;
                newX = getWidth() - rightMargin - origW;
            } else if (hasAlign(align, "center") && !hasAlign(align, "centery", "centervert", "vcenter")) {
                // 水平居中
                newX = (getWidth() - origW) / 2;
            } else if (hasAlign(align, "left")) {
                newX = ctl.left;
            }

            // ---- 垂直定位：fill > bottom > middle > top ----
            if (hasAlign(align, "fill", "stretch", "vfill", "yfill", "height", "expand")) {
                // 拉伸填满垂直：上距 = ctl.top，下距 = bgH - ctl.bottom
                int topMargin = ctl.top;
                int bottomMargin = bgH - ctl.bottom;
                newY = topMargin;
                origH = Math.max(1, getHeight() - topMargin - bottomMargin);
            } else if (hasAlign(align, "bottom")) {
                // 保持到底部的距离不变：底边距 = bgH - ctl.bottom
                int bottomMargin = bgH - ctl.bottom;
                newY = getHeight() - bottomMargin - origH;
            } else if (hasAlign(align, "middle", "centery", "centervert", "vertcenter", "vcenter")) {
                // 垂直居中
                newY = (getHeight() - origH) / 2;
            } else if (hasAlign(align, "top")) {
                newY = ctl.top;
            }
        }

        comp.setBounds(newX, newY, origW, origH);
        comp.setPreferredSize(new Dimension(origW, origH));
    }

    /** align 字符串是否包含任一 token（忽略大小写） */
    private static boolean hasAlign(String align, String... tokens) {
        if (align == null) return false;
        for (String t : tokens) {
            if (align.contains(t)) return true;
        }
        return false;
    }

    // ================================================================
    //  BMP 加载与镂空
    // ================================================================

    private void loadBg() {
        if (def.image == null || def.image.isEmpty()) return;
        byte[] data = skin.getBmp(def.image);
        if (data == null) return;
        bgImage = decodeBmp(data, transparentColor);
        if (bgImage != null) {
            bgW = bgImage.getWidth();
            bgH = bgImage.getHeight();
            Color bgFillColor = null;
            for (int y = 0; y < Math.min(bgH, 5) && bgFillColor == null; y++) {
                for (int x = 0; x < Math.min(bgW, 5); x++) {
                    int a = (bgImage.getRGB(x, y) >> 24) & 0xFF;
                    if (a > 240) {
                        bgFillColor = new Color(bgImage.getRGB(x, y));
                        break;
                    }
                }
            }

        }
    }

    private boolean hasResizeRect() {
        return def.resizeRect != null && !def.resizeRect.isEmpty();
    }

    private void setupResizeRect() {
        tile = def.resizeTile;
        String rr = def.resizeRect;
        if (rr == null || rr.isEmpty()) {
            ninePatch = new int[]{0, 0, 0, 0};
            return;
        }
        String[] p = rr.split(",");
        int nl_coord = Integer.parseInt(p[0].trim());
        int nt_coord = Integer.parseInt(p[1].trim());
        int nr_coord = Integer.parseInt(p[2].trim());
        int nb_coord = Integer.parseInt(p[3].trim());

        int nr_width = (bgW > 0) ? (bgW - nr_coord) : 0;
        int nb_height = (bgH > 0) ? (bgH - nb_coord) : 0;

        ninePatch = new int[]{nl_coord, nt_coord, nr_width, nb_height};
    }

    // ================================================================
    //  背景面板
    // ================================================================

    private class BgPanel extends JPanel {
        BgPanel() {
            setOpaque(false);
            setLayout(null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (bgImage != null) {
                drawNinePatch(g2, bgImage, getWidth(), getHeight(),
                        ninePatch[0], ninePatch[1], ninePatch[2], ninePatch[3]);
            } else {
                g2.setColor(new Color(60, 63, 65));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.dispose();
        }
    }

    // ================================================================
    //  九宫格绘制
    // ================================================================

    public static void drawNinePatch(Graphics2D g, BufferedImage src,
                                      int dstW, int dstH,
                                      int nl, int nt, int nr, int nb) {
        int sw = src.getWidth();
        int sh = src.getHeight();

        if (nl == 0 && nt == 0 && nr == 0 && nb == 0) {
            g.drawImage(src, 0, 0, dstW, dstH, null);
            return;
        }

        g.drawImage(src, 0, 0, nl, nt, 0, 0, nl, nt, null);
        g.drawImage(src, nl, 0, dstW - nr, nt, nl, 0, sw - nr, nt, null);
        g.drawImage(src, dstW - nr, 0, dstW, nt, sw - nr, 0, sw, nt, null);
        g.drawImage(src, 0, nt, nl, dstH - nb, 0, nt, nl, sh - nb, null);
        g.drawImage(src, nl, nt, dstW - nr, dstH - nb, nl, nt, sw - nr, sh - nb, null);
        g.drawImage(src, dstW - nr, nt, dstW, dstH - nb, sw - nr, nt, sw, sh - nb, null);
        g.drawImage(src, 0, dstH - nb, nl, dstH, 0, sh - nb, nl, sh, null);
        g.drawImage(src, nl, dstH - nb, dstW - nr, dstH, nl, sh - nb, sw - nr, sh, null);
        g.drawImage(src, dstW - nr, dstH - nb, dstW, dstH, sw - nr, sh - nb, sw, sh, null);
    }

    // ================================================================
    //  边缘拖拽调整大小
    // ================================================================

    private class EdgeResizer extends MouseAdapter {
        private final JFrame frame;
        private int edge;
        private int startX, startY, startW, startH, frameX, frameY;

        EdgeResizer(JFrame frame) {
            this.frame = frame;
            JPanel glass = new JPanel(null) {
                { setOpaque(false); }
                @Override
                public boolean contains(int x, int y) {
                    int w = getWidth(), h = getHeight(), m = 6;
                    return x < m || x > w - m || y < m || y > h - m;
                }
            };
            glass.addMouseListener(this);
            glass.addMouseMotionListener(this);
            frame.setGlassPane(glass);
            glass.setVisible(true);
        }

        @Override public void mouseMoved(MouseEvent e) {
            edge = hitEdge(e);
            frame.setCursor(cursorFor(edge));
        }

        @Override
        public void mousePressed(MouseEvent e) {
            edge = hitEdge(e);
            startX = e.getXOnScreen();
            startY = e.getYOnScreen();
            startW = frame.getWidth();
            startH = frame.getHeight();
            frameX = frame.getX();
            frameY = frame.getY();
        }

        @Override public void mouseEntered(MouseEvent e) {
            edge = hitEdge(e);
            frame.setCursor(cursorFor(edge));
        }

        //@Override
        public void mouseDragged(MouseEvent e) {
            super.mouseDragged(e);
            if (edge == 0) return;
            int dx = e.getXOnScreen() - startX;
            int dy = e.getYOnScreen() - startY;
            Rectangle screen = frame.getGraphicsConfiguration().getBounds();
            int x = frame.getX(), y = frame.getY(), w = frame.getWidth(), h = frame.getHeight();
            int rightEdge = frameX + startW;
            int bottomEdge = frameY + startH;

            if ((edge & 1) != 0) {
                x = Math.max(screen.x, Math.min(frameX + dx, rightEdge - minW));
                w = rightEdge - x;
            }
            if ((edge & 2) != 0) {
                w = Math.max(minW, Math.min(startW + dx, screen.x + screen.width - frameX));
            }
            if ((edge & 4) != 0) {
                y = Math.max(screen.y, Math.min(frameY + dy, bottomEdge - minH));
                h = bottomEdge - y;
            }
            if ((edge & 8) != 0) {
                h = Math.max(minH, Math.min(startH + dy, screen.y + screen.height - frameY));
            }

            frame.setBounds(x, y, w, h);
        }

        @Override public void mouseReleased(MouseEvent e) {
            edge = 0;
            frame.setCursor(Cursor.getDefaultCursor());
        }

        @Override public void mouseExited(MouseEvent e) {
            frame.setCursor(Cursor.getDefaultCursor());
        }

        private int hitEdge(MouseEvent e) {
            int x = e.getX(), y = e.getY(), w = getWidth(), h = getHeight(), m = 6;
            int e2 = 0;
            if (x < m) e2 |= 1;
            if (x > w - m) e2 |= 2;
            if (y < m) e2 |= 4;
            if (y > h - m) e2 |= 8;
            return e2;
        }

        private Cursor cursorFor(int e2) {
            int[] cs = {Cursor.DEFAULT_CURSOR, Cursor.W_RESIZE_CURSOR, Cursor.E_RESIZE_CURSOR,
                    Cursor.DEFAULT_CURSOR, Cursor.N_RESIZE_CURSOR, Cursor.NW_RESIZE_CURSOR,
                    Cursor.NE_RESIZE_CURSOR, Cursor.DEFAULT_CURSOR, Cursor.S_RESIZE_CURSOR,
                    Cursor.SW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR};
            return Cursor.getPredefinedCursor(e2 < cs.length ? cs[e2] : Cursor.DEFAULT_CURSOR);
        }
    }

    // ================================================================
    //  工具：创建按钮、LED、文本 info
    // ================================================================

    protected TtButton createButton(TtSkin.Ctl ctl) {
        byte[] bmp = skin.getBmp(ctl.image);
        if (bmp == null) return null;

        // 先解码图片以获得实际的帧大小
        BufferedImage src = decodeBmp(bmp, transparentColor);
        int frameW = ctl.frameWidth;
        int frameH = ctl.frameHeight;
        if (src != null) {
            frameW = src.getWidth() / 4;
            frameH = src.getHeight();
        }

        TtButton btn = new TtButton(bmp, 0, 0, frameW, frameH);
        btn.setBounds(ctl.left, ctl.top, frameW, frameH);
        getContentPane().add(btn);
        controls.add(new ControlInfo(btn, ctl));
        return btn;
    }

    protected TtLed createLed(TtSkin.Ctl ctl) {
        byte[] bmp = skin.getBmp(ctl.image);
        TtLed led;
        if (bmp != null) {
            led = new TtLed(bmp, transparentColor);
        } else {
            led = new TtLed(null);
        }
        // 使用皮肤配置的宽度，但确保足够
        int width = ctl.right - ctl.left;
        int height = Math.max(16,ctl.bottom - ctl.top);
        led.setBounds(ctl.left, ctl.top, width, height);
        led.setPreferredSize(new Dimension(width, height));
        led.setText("00:00");
        if ("right".equals(ctl.align)) led.setAlignRight(true);
        getContentPane().add(led);
        controls.add(new ControlInfo(led, ctl));
        return led;
    }

    protected JLabel createTitleImage(TtSkin.Ctl ctl) {
        byte[] bmp = skin.getBmp(ctl.image);
        if (bmp == null) return null;
        BufferedImage img = SkinWindow.decodeBmp(bmp, getSkinTransparentColor());
        if (img == null) return null;
        JLabel lb = new JLabel(new ImageIcon(img));
        int w = ctl.right - ctl.left;
        int h = ctl.bottom - ctl.top;
        int x = ctl.left;
        int y = ctl.top;
        if (ctl.align != null) {
            String align = ctl.align.toLowerCase();
            if (align.contains("center"))
                x = (bgW - w) / 2;
            else if (align.contains("right"))
                x = bgW - w - (bgW - ctl.right);
        }
        lb.setBounds(x, y, w, h);
        getContentPane().add(lb);
        addControl(lb, ctl);
        return lb;
    }

    protected Color getSkinTransparentColor() { return transparentColor; }

    protected JLabel createInfo(TtSkin.Ctl ctl) {
        JLabel lb = new JLabel();
        lb.setBounds(ctl.left, ctl.top, ctl.right - ctl.left, ctl.bottom - ctl.top);
        if (ctl.color != null && !ctl.color.isEmpty()) {
            Color color = ColorUtils.decode(ctl.color);
            if (color != null) lb.setForeground(color);
        }
        if (ctl.bkgnd != null && !ctl.bkgnd.isEmpty()) {
            Color color = ColorUtils.decode(ctl.bkgnd);
            if (color != null) {
                lb.setOpaque(true);
                lb.setBackground(color);
            }
        }
        int fontSize = ctl.fontSize > 0 ? ctl.fontSize : 12;
        if (ctl.font != null && !ctl.font.isEmpty()) {
            lb.setFont(FontUtils.getChineseFont(ctl.font, Font.PLAIN, fontSize));
        } else {
            lb.setFont(FontUtils.getDefaultChineseFont(fontSize));
        }
        lb.setHorizontalAlignment(parseAlign(ctl.align));
        getContentPane().add(lb);
        controls.add(new ControlInfo(lb, ctl));
        return lb;
    }

    protected void  addControl(Component comp, TtSkin.Ctl ctl) {
        controls.add(new ControlInfo(comp, ctl));
    }

    private int parseAlign(String a) {
        if (a == null) return SwingConstants.LEFT;
        if (a.contains("right")) return SwingConstants.RIGHT;
        if (a.contains("center")) return SwingConstants.CENTER;
        return SwingConstants.LEFT;
    }

    // ================================================================
    //  BMP 解码
    // ================================================================

    public static BufferedImage decodeBmp(byte[] data, Color transp) {
        BufferedImage img = decodeWithImageIO(data, transp);
        if (img != null) return img;

        try {
            // 基本数据验证
            if (data == null || data.length < 54) {
                return null;
            }

            int offset = readInt(data, 10);
            int w = readInt(data, 18);
            int hRaw = readInt(data, 22);
            int bpp = readShort(data, 28);
            int absH = Math.abs(hRaw);
            boolean bottomUp = hRaw > 0;
            int dibSize = readInt(data, 14);
            int palOffset = 14 + dibSize;
            int rowSize = ((w * bpp + 31) / 32) * 4;

            // 验证参数有效性
            if (w <= 0 || w > 4096 || absH <= 0 || absH > 4096) {
                return null;
            }
            if (bpp != 8 && bpp != 24 && bpp != 32) {
                return null;
            }
            if (offset < 0 || offset >= data.length) {
                return null;
            }
            if (palOffset < 0 || palOffset >= data.length) {
                return null;
            }

            int tr = transp.getRed(), tg = transp.getGreen(), tb = transp.getBlue();

            img = new BufferedImage(w, absH, BufferedImage.TYPE_INT_ARGB);

            if (bpp == 8) {
                int[] pal = new int[256];
                for (int i = 0; i < 256 && palOffset + i * 4 + 3 < data.length; i++) {
                    int b = data[palOffset + i * 4] & 0xFF;
                    int g = data[palOffset + i * 4 + 1] & 0xFF;
                    int r = data[palOffset + i * 4 + 2] & 0xFF;
                    pal[i] = (r == tr && g == tg && b == tb) ? 0x00FF00FF : (0xFF000000 | (r << 16) | (g << 8) | b);
                }

                for (int y = 0; y < absH; y++) {
                    int srcY = bottomUp ? (absH - 1 - y) : y;
                    int rowStart = offset + srcY * rowSize;
                    if (rowStart < 0 || rowStart + w >= data.length) continue;
                    for (int x = 0; x < w; x++) {
                        int idx = data[rowStart + x] & 0xFF;
                        img.setRGB(x, y, pal[idx]);
                    }
                }
            } else if (bpp == 24) {
                for (int y = 0; y < absH; y++) {
                    int srcY = bottomUp ? (absH - 1 - y) : y;
                    int rowStart = offset + srcY * rowSize;
                    if (rowStart < 0 || rowStart + w * 3 >= data.length) continue;
                    for (int x = 0; x < w; x++) {
                        int b = data[rowStart + x * 3] & 0xFF;
                        int g = data[rowStart + x * 3 + 1] & 0xFF;
                        int r = data[rowStart + x * 3 + 2] & 0xFF;
                        if (r == tr && g == tg && b == tb) {
                            img.setRGB(x, y, 0x00FF00FF);
                        } else {
                            img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                        }
                    }
                }
            } else {
                for (int y = 0; y < absH; y++) {
                    int srcY = bottomUp ? (absH - 1 - y) : y;
                    int rowStart = offset + srcY * rowSize;
                    if (rowStart < 0 || rowStart + w * 4 >= data.length) continue;
                    for (int x = 0; x < w; x++) {
                        int b = data[rowStart + x * 4] & 0xFF;
                        int g = data[rowStart + x * 4 + 1] & 0xFF;
                        int r = data[rowStart + x * 4 + 2] & 0xFF;
                        int a = data[rowStart + x * 4 + 3] & 0xFF;
                        if (r == tr && g == tg && b == tb) a = 0;
                        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
            }
            return img;
        } catch (Exception e) {
            // 静默处理异常，避免崩溃
            return null;
        }
    }

    private static BufferedImage decodeWithImageIO(byte[] data, Color transp) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return null;
            if (transp == null) return img;

            int tr = transp.getRed(), tg = transp.getGreen(), tb = transp.getBlue();
            int iw = img.getWidth(), ih = img.getHeight();

            boolean needAlpha = false;
            outer: for (int y = 0; y < Math.min(ih, 50); y++) {
                for (int x = 0; x < Math.min(iw, 50); x++) {
                    int rgb = img.getRGB(x, y);
                    if (((rgb >> 16) & 0xFF) == tr && ((rgb >> 8) & 0xFF) == tg && (rgb & 0xFF) == tb) {
                        needAlpha = true; break outer;
                    }
                }
            }
            if (!needAlpha) {
                if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
                    BufferedImage conv = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
                    conv.getGraphics().drawImage(img, 0, 0, null);
                    return conv;
                }
                return img;
            }
            BufferedImage result = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < ih; y++) {
                for (int x = 0; x < iw; x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r == tr && g == tg && b == tb) {
                        result.setRGB(x, y, 0x00FF00FF);
                    } else {
                        result.setRGB(x, y, 0xFF000000 | (rgb & 0xFFFFFF));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static int readInt(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24);
    }

    private static int readShort(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
    }
}
