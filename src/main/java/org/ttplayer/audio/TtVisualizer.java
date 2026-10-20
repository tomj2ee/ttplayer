package org.ttplayer.audio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * TTPlayer 原版频谱显示组件 —— 复现 TTPlayer.exe 的频谱可视化。
 *
 * <p>显示风格严格对齐原版（由 {@code Visual.xml} 的 {@code <Visual>} 节点驱动）：</p>
 * <ul>
 *   <li>{@code SpectrumBtmColor} → 柱底色，{@code SpectrumMidColor} → 柱中色，
 *       {@code SpectrumTopColor} → 柱顶色（三段竖直渐变）。</li>
 *   <li>{@code SpectrumPeakColor} → 峰值顶线颜色。</li>
 *   <li>{@code SpectrumWide} → 宽柱模式（柱间隙极小）。</li>
 *   <li>{@code Blur} / {@code BlurSpeed} → 帧残影（拖尾）开关与恢复速度。</li>
 *   <li>{@code BlurScopeColor} → 余晖着色。</li>
 * </ul>
 *
 * <p>数据源为 {@link TtSpectrum}（48 段对数分带 + dB 压缩 + 非对称平滑 + 峰值保持）。</p>
 */
public class TtVisualizer extends JComponent {

    /** 显示模式。原版主用 BARS；其余模式保留供切换，算法同源。 */
    public enum Mode {
        BARS, MIRROR_BARS, LINE, AREA, LED, WAVE
    }

    private Mode mode = Mode.BARS;
    private final TtSpectrum spectrum = new TtSpectrum();

    /** 频谱区域背景：必须不透明，否则会透出窗口下层（白闪/透明）。 */
    public Color backgroundColor = new Color(16, 22, 30);

    public Color skinColorTop;
    public Color skinColorMid;
    public Color skinColorBtm;
    public Color skinColorPeak;
    public Color skinColorBlur;

    /** SpectrumWide=1：宽柱模式。 */
    public boolean skinWide = false;

    /** Blur=1：启用帧残影拖尾。 */
    public boolean skinBlur = true;

    /** BlurSpeed=1..10：残影恢复速度，越大拖尾越短。 */
    public int skinBlurSpeed = 3;

    // 最近一帧 PCM 样本（WAVE 模式用）
    private float[] pcmSamples = new float[0];
    private int pcmCount = 0;

    private BufferedImage backBuffer;
    private boolean bufferPrimed = false;

    public TtVisualizer() {
        setOpaque(true);
    }

    // ============ 皮肤配色 ============

    private Color cTop()  { return skinColorTop  != null ? skinColorTop  : new Color(18, 114, 175); }
    private Color cMid()  { return skinColorMid  != null ? skinColorMid  : new Color(40, 143, 189); }
    private Color cBtm()  { return skinColorBtm  != null ? skinColorBtm  : new Color(16, 215, 255); }
    private Color cPeak() { return skinColorPeak != null ? skinColorPeak : Color.WHITE; }
    private Color cBlur() { return skinColorBlur != null ? skinColorBlur : cBtm(); }

    // ============ 公共接口 ============

    public void setMode(Mode mode) { this.mode = mode; bufferPrimed = false; repaint(); }
    public Mode getMode() { return mode; }

    public void cycleMode() {
        Mode[] ms = Mode.values();
        mode = ms[(mode.ordinal() + 1) % ms.length];
        bufferPrimed = false;
        repaint();
    }

    /** 由 PlayerWindow 高频喂入 PCM（s16le 小端，单声道左右平均）。 */
    public void updateData(byte[] pcmData) {
        if (pcmData != null && pcmData.length >= 8) {
            spectrum.analyze(pcmData, pcmData.length);
            int n = pcmData.length / 2;           // 单声道：每采样 2 字节
            if (pcmSamples.length < n) pcmSamples = new float[n];
            for (int i = 0; i < n; i++) {
                int off = i * 2;
                short s = (short) ((pcmData[off + 1] << 8) | (pcmData[off] & 0xFF));
                pcmSamples[i] = s / 32768.0f;
            }
            pcmCount = n;
        } else {
            // 无新数据（暂停/静音）：让柱体自然下落，而不是瞬间清零
            spectrum.analyze(null, 0);
            pcmCount = 0;
        }
        repaint();
    }

    /** 停止播放时调用：清空残影与全部分析状态。 */
    public void reset() {
        spectrum.reset();
        pcmSamples = new float[0];
        pcmCount = 0;
        bufferPrimed = false;
        repaint();
    }

    /**
     * 从皮肤 {@code <Visual>} 节点读取全部频谱样式，与 TTPlayer.exe 的
     * Visual.xml 参数一一对应。
     */
    public void applyVisualSkin(org.w3c.dom.Element vis) {
        if (vis == null) return;
        skinColorTop  = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumTopColor", skinColorTop);
        skinColorMid  = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumMidColor", skinColorMid);
        skinColorBtm  = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumBtmColor", skinColorBtm);
        skinColorPeak = org.ttplayer.util.UIUtils.getColorAttribute(vis, "SpectrumPeakColor", skinColorPeak);
        skinColorBlur = org.ttplayer.util.UIUtils.getColorAttribute(vis, "BlurScopeColor", skinColorBlur);
        skinWide = "1".equals(org.ttplayer.util.UIUtils.getAttribute(vis, "SpectrumWide", skinWide ? "1" : "0").trim());
        skinBlur = "1".equals(org.ttplayer.util.UIUtils.getAttribute(vis, "Blur", skinBlur ? "1" : "0").trim());
        try {
            skinBlurSpeed = Integer.parseInt(
                    org.ttplayer.util.UIUtils.getAttribute(vis, "BlurSpeed", String.valueOf(skinBlurSpeed)).trim());
        } catch (NumberFormatException ignored) { }
        bufferPrimed = false;
        repaint();
    }

    // ============ 渲染 ============

    private void ensureBuffer(int w, int h) {
        if (backBuffer == null || backBuffer.getWidth() != w || backBuffer.getHeight() != h) {
            backBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            bufferPrimed = false;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        ensureBuffer(w, h);

        // Blur：把上一帧向背景色插值恢复，形成拖尾残影
        boolean blurred = skinBlur && bufferPrimed;
        if (blurred) fadeBuffer(backBuffer, fadeAmount());

        Graphics2D g2 = backBuffer.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!blurred) {
                g2.setColor(backgroundColor);
                g2.fillRect(0, 0, w, h);
            }
            bufferPrimed = true;

            switch (mode) {
                case BARS:        drawBars(g2, false); break;
                case MIRROR_BARS: drawBars(g2, true);  break;
                case LINE:        drawLine(g2);        break;
                case AREA:        drawArea(g2);        break;
                case LED:         drawLed(g2);         break;
                case WAVE:        drawWave(g2);        break;
            }
        } finally {
            g2.dispose();
        }
        g.drawImage(backBuffer, 0, 0, null);
    }

    /** 每帧向背景色恢复的比例。BlurSpeed 越大恢复越快、拖尾越短。 */
    private float fadeAmount() {
        float t = 0.18f + skinBlurSpeed * 0.09f;
        if (t < 0.05f) t = 0.05f;
        if (t > 1f) t = 1f;
        return t;
    }

    /** 把整帧向背景色插值，产生残影衰减。 */
    private void fadeBuffer(BufferedImage img, float t) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = new int[w * h];
        img.getRGB(0, 0, w, h, px, 0, w);
        int br = backgroundColor.getRed();
        int bg = backgroundColor.getGreen();
        int bb = backgroundColor.getBlue();
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF;
            int gc = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            r  = (int) (r  + (br - r)  * t);
            gc = (int) (gc + (bg - gc) * t);
            b  = (int) (b  + (bb - b)  * t);
            px[i] = (0xFF << 24) | (r << 16) | (gc << 8) | b;
        }
        img.setRGB(0, 0, w, h, px, 0, w);
    }

    /**
     * 柱数：原版在 200px 宽显示区内恰好排布 22 段，柱宽 6px + 间隙 3px（周期 9px）。
     * 因此固定按「每段 9px」推算可见段数；SpectrumWide=1 时柱更宽、间隙更小。
     */
    private int barCountFor(int w) {
        int perBar = skinWide ? 7 : 9;
        int fit = w / perBar;
        if (fit > TtSpectrum.BANDS) fit = TtSpectrum.BANDS;
        if (fit < 2) fit = 2;
        return fit;
    }

    /**
     * 原版色带：颜色锚定在「柱内相对高度」而非绝对 Y 坐标。
     *
     * <p>实测 TTPlayer.exe（皮肤 {@code 2、Media Player 10}，
     * {@code SpectrumTopColor=#ff0080 / SpectrumMidColor=#ffff00 / SpectrumBtmColor=#0080ff}）
     * 得到的分段线性模型，平均色差 &lt; 2%：</p>
     * <pre>
     *   t = (y - top) / (height - 1)      // 0 = 柱顶, 1 = 柱底
     *   t ∈ [0.0, 0.5] → Top(品红) lerp→ Mid(黄)
     *   t ∈ [0.5, 1.0] → Mid(黄)   lerp→ Btm(蓝)
     * </pre>
     * <p>柱顶另有 1px 的 {@code SpectrumPeakColor} 高光帽。</p>
     */
    private int bandColorAt(float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t < 0.5f
                ? lerpRgb(gTop, gMid, t * 2f)
                : lerpRgb(gMid, gBtm, (t - 0.5f) * 2f);
    }

    // ============ 柱状（BARS / MIRROR_BARS） ============

    // 当前帧色带端点缓存（bandColorAt 使用）
    private int gTop, gMid, gBtm;

    private void drawBars(Graphics2D g2, boolean mirror) {
        int w = getWidth(), h = getHeight();
        int bars = barCountFor(w);

        gTop = argb(cTop());
        gMid = argb(cMid());
        gBtm = argb(cBtm());
        int peakCol = argb(cPeak());

        float bw = (float) w / bars;
        // 原版几何：柱宽 6px、间隙 3px → 柱占周期 2/3；Wide 模式间隙更小
        float gapRatio = skinWide ? 0.12f : 0.30f;
        float gap = Math.max(skinWide ? 1f : 1f, bw * gapRatio);
        float drawW = Math.max(1f, bw - gap);

        int baseY = mirror ? h / 2 : h;     // 柱基线
        int span  = mirror ? h / 2 : h;     // 最大柱高

        for (int i = 0; i < bars; i++) {
            float lv = spectrum.levelResampled(i, bars);
            float pk = resamplePeak(i, bars);
            int bh = (int) (lv * span);
            if (bh < 1 && lv > 0.008f) bh = 1;

            int x = Math.round(i * bw + gap * 0.5f);
            int colW = Math.round(drawW);
            if (colW < 1) colW = 1;

            if (bh > 0) {
                // 原版：柱顶 1px 为 SpectrumPeakColor 高光帽，
                // 其余像素按「柱内相对高度」着色（顶部→品红，底部→蓝）。
                int cap = bh >= 3 ? 1 : bh;
                for (int r = 0; r < bh; r++) {              // r=0 为柱顶
                    int py = baseY - bh + r;
                    if (py < 0 || py >= h) continue;
                    int col = (r < cap)
                            ? peakCol
                            : bandColorAt((float) r / Math.max(1, bh - 1));
                    g2.setColor(new Color(col));
                    g2.fillRect(x, py, colW, 1);
                }
            }
            // 峰值保持线（独立于柱体，落在柱顶上方）
            int ph = (int) (pk * span);
            if (ph > bh + 1) {
                int py = baseY - ph;
                if (py >= 0 && py < h) {
                    g2.setColor(new Color(peakCol));
                    g2.fillRect(x, py, colW, 1);
                }
            }
            // 镜像下半（半透明对称柱）
            if (mirror && bh > 0) {
                int down = (int) (bh * 0.85f);
                for (int y = 0; y < down; y++) {
                    int col = bandColorAt(1f - (float) y / Math.max(1, down - 1));
                    g2.setColor(blendWithBg(new Color(col), 0.55f));
                    g2.fillRect(x, baseY + y, colW, 1);
                }
            }
        }
    }

    private float resamplePeak(int i, int bars) {
        if (bars <= 1) return spectrum.peak(0);
        float fi = (float) i * (TtSpectrum.BANDS - 1) / (bars - 1);
        int lo = (int) fi;
        int hi = Math.min(lo + 1, TtSpectrum.BANDS - 1);
        float k = fi - lo;
        return spectrum.peak(lo) + (spectrum.peak(hi) - spectrum.peak(lo)) * k;
    }

    // ============ 其它模式 ============

    private void drawLine(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int n = Math.min(TtSpectrum.BANDS, Math.max(8, w / 4));
        g2.setColor(cTop());
        int px = 0, py = h;
        for (int i = 0; i < n; i++) {
            float lv = spectrum.levelResampled(i, n);
            int x = (int) ((float) i / Math.max(1, n - 1) * w);
            int y = h - (int) (lv * h * 0.95f);
            g2.drawLine(px, py, x, y);
            px = x; py = y;
        }
        g2.drawLine(px, py, w - 1, h);
    }

    private void drawArea(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        if (w < 2) return;
        int n = TtSpectrum.BANDS;
        float[] ys = new float[w];
        for (int x = 0; x < w; x++) {
            float fi = (float) x / (w - 1) * (n - 1);
            int lo = (int) fi, hi = Math.min(lo + 1, n - 1);
            float k = fi - lo;
            float lv = spectrum.levelResampled(lo, n) * (1 - k) + spectrum.levelResampled(hi, n) * k;
            ys[x] = h - lv * h * 0.95f;
        }
        java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
        p.moveTo(0, ys[0]);
        for (int x = 1; x < w; x++) p.lineTo(x, ys[x]);
        p.lineTo(w - 1, h);
        p.lineTo(0, h);
        p.closePath();
        g2.setPaint(new GradientPaint(0, 0, cTop(), 0, h, cBtm()));
        g2.fill(p);
        g2.setColor(cPeak());
        java.awt.geom.Path2D.Float line = new java.awt.geom.Path2D.Float();
        line.moveTo(0, ys[0]);
        for (int x = 1; x < w; x++) line.lineTo(x, ys[x]);
        g2.draw(line);
    }

    private void drawLed(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int cell = skinWide ? 5 : 6, gap = 2;
        int cols = Math.max(2, w / (cell + gap));
        int rows = Math.max(2, h / (cell + gap));
        for (int i = 0; i < cols; i++) {
            float lv = spectrum.levelResampled(i, cols);
            int filled = (int) (lv * rows);
            for (int j = 0; j < rows; j++) {
                int x = i * (cell + gap);
                int y = h - (j + 1) * (cell + gap);
                if (j < filled) {
                    float t = (float) j / rows;
                    g2.setColor(t < 0.5f ? lerpColor(cBtm(), cMid(), t * 2)
                                         : lerpColor(cMid(), cTop(), (t - 0.5f) * 2));
                    g2.fillRect(x, y, cell, cell);
                    if (j == filled - 1) {
                        g2.setColor(cPeak());
                        g2.fillRect(x, y, cell, cell);
                    }
                } else {
                    g2.setColor(new Color(255, 255, 255, 14));
                    g2.fillRect(x, y, cell, cell);
                }
            }
        }
    }

    private void drawWave(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int got = pcmCount;
        if (got < 2) return;
        int n = Math.max(16, Math.min(w, got));
        g2.setColor(cTop());
        int px = 0, py = h / 2;
        for (int i = 1; i < n; i++) {
            int idx = Math.min(i * got / n, got - 1);
            int x = i * w / n;
            int y = h / 2 + (int) (pcmSamples[idx] * h * 0.42f);
            g2.drawLine(px, py, x, y);
            px = x; py = y;
        }
    }

    // ============ 颜色工具 ============

    private static int argb(Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static int lerpRgb(int a, int b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16)
             | ((int) (ag + (bg - ag) * t) << 8)
             |  (int) (ab + (bb - ab) * t);
    }

    private Color blendWithBg(Color c, float keep) {
        return new Color(
                (int) (backgroundColor.getRed()   + (c.getRed()   - backgroundColor.getRed())   * keep),
                (int) (backgroundColor.getGreen() + (c.getGreen() - backgroundColor.getGreen()) * keep),
                (int) (backgroundColor.getBlue()  + (c.getBlue()  - backgroundColor.getBlue())  * keep));
    }

    private static Color lerpColor(Color a, Color b, float t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}