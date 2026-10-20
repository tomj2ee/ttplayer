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
 *
 * <h3>性能设计（关键）</h3>
 * <p>本组件以 50~60fps 连续重绘，因此渲染路径<b>必须零堆分配</b>：</p>
 * <ul>
 *   <li>所有绘制写入一个复用的 {@code int[] px} 像素画布（TYPE_INT_RGB），
 *       每帧结束时一次性 {@code setRGB} 提交，避免逐像素建立 {@link Color} 对象。</li>
 *   <li>残影衰减直接在该 {@code int[]} 上原地做整数插值，不再每帧 {@code new int[w*h]}。</li>
 *   <li>色带查表用整数运算（{@code lerpRgb}），不产生临时对象。</li>
 * </ul>
 * <p>改造前实测每帧分配 61~69KB（≈3.3MB/s 垃圾），频繁 young GC 导致 EDT 卡顿、
 * 频谱观感「慢」；改造后每帧分配约 0 字节，帧率稳定。</p>
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

    // ---- 无分配画布 ----
    private BufferedImage backBuffer;
    private int[] px;                 // 复用的像素画布（ARGB/RGB 打包 int）
    private int cw, ch;               // 画布尺寸
    private boolean bufferPrimed = false;

    // 当前帧色带端点缓存
    private int gTop, gMid, gBtm;

    // AREA 模式复用的临时数组（避免每帧分配）
    private float[] areaEnv = new float[0];
    private float[] areaPk  = new float[0];
    private int[]   areaRowCol = new int[0];

    // 默认配色（静态常量：确保 cTop()/cMid()/... 永不分配对象）
    private static final Color DEF_TOP  = new Color(18, 114, 175);
    private static final Color DEF_MID  = new Color(40, 143, 189);
    private static final Color DEF_BTM  = new Color(16, 215, 255);

    public TtVisualizer() {
        setOpaque(true);
    }

    // ============ 皮肤配色 ============

    private Color cTop()  { return skinColorTop  != null ? skinColorTop  : DEF_TOP; }
    private Color cMid()  { return skinColorMid  != null ? skinColorMid  : DEF_MID; }
    private Color cBtm()  { return skinColorBtm  != null ? skinColorBtm  : DEF_BTM; }
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
        bufferPrimed = false;   // 下一帧整屏刷背景色
        repaint();
    }

    /**
     * 立即清空显示画面（不等待柱体自然衰减）。
     *
     * <p>用于「上一首/下一首/停止」等换曲场景：仅调用 {@link #reset()} 只是把
     * 分析状态归零，画面仍会保留最后一帧的柱体；本方法直接把画布刷成背景色，
     * 避免用户看到「上一首歌的频谱还挂在那里」。</p>
     */
    public void clearDisplay() {
        spectrum.reset();
        pcmCount = 0;
        if (px != null) {
            Arrays.fill(px, argb(backgroundColor));
            // 立即提交到屏幕，保证换曲瞬间就看不到旧频谱
            if (backBuffer != null) {
                Graphics gg = getGraphics();
                if (gg != null) {
                    try {
                        gg.drawImage(backBuffer, 0, 0, null);
                    } finally {
                        gg.dispose();
                    }
                }
            }
        }
        bufferPrimed = true;    // 画布已是纯背景色，可直接参与后续残影衰减
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
        if (backBuffer == null || cw != w || ch != h) {
            backBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            // 直接持有图像底层栅格数组：绘制即写入图像，无需每帧 setRGB 拷贝（零拷贝）
            px = ((java.awt.image.DataBufferInt)
                    backBuffer.getRaster().getDataBuffer()).getData();
            cw = w;
            ch = h;
            bufferPrimed = false;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        ensureBuffer(w, h);

        // 残影衰减：只有「上一帧已绘制」时才做拖尾插值；
        // 否则（首帧 / 切换模式 / 换肤 / 尺寸变化）必须整屏刷成背景色，
        // 否则旧模式的画面会残留在画布上。
        if (skinBlur && bufferPrimed) {
            fadePixels(fadeAmount());
        } else {
            Arrays.fill(px, argb(backgroundColor));
        }
        bufferPrimed = true;

        // 原版为 2003 年像素级渲染：不做任何抗锯齿，保证 1px 峰线锐利不发虚。
        // 此处直接写像素，天然无抗锯齿。
        switch (mode) {
            case BARS:        drawBars(false); break;
            case MIRROR_BARS: drawBars(true);  break;
            case LINE:        drawLine();      break;
            case AREA:        drawArea();      break;
            case LED:         drawLed();       break;
            case WAVE:        drawWave();      break;
        }

        // px 就是图像底层栅格：绘制已完成，直接绘制到组件（零拷贝、零分配）
        g.drawImage(backBuffer, 0, 0, null);
    }

    /** 每帧向背景色恢复的比例。BlurSpeed 越大恢复越快、拖尾越短。 */
    private float fadeAmount() {
        float t = 0.18f + skinBlurSpeed * 0.09f;
        if (t < 0.05f) t = 0.05f;
        if (t > 1f) t = 1f;
        return t;
    }

    /**
     * 把整帧向背景色插值，产生残影衰减。
     * 原地改写复用画布，<b>不分配</b>——这是本组件性能的关键点之一。
     */
    private void fadePixels(float t) {
        int bg = argb(backgroundColor);
        int br = (bg >> 16) & 0xFF, bgn = (bg >> 8) & 0xFF, bb = bg & 0xFF;
        float keep = 1f - t;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF, gc = (p >> 8) & 0xFF, b = p & 0xFF;
            r  = br  + (int) ((r  - br)  * keep);
            gc = bgn + (int) ((gc - bgn) * keep);
            b  = bb  + (int) ((b  - bb)  * keep);
            px[i] = (r << 16) | (gc << 8) | b;
        }
    }

    // ============ 画布原语（全部直接写 int[]，零分配） ============

    private void put(int x, int y, int rgb) {
        if (x >= 0 && x < cw && y >= 0 && y < ch) px[y * cw + x] = rgb;
    }

    private void fillRectPx(int x, int y, int w, int h, int rgb) {
        int x2 = Math.min(cw, x + w), y2 = Math.min(ch, y + h);
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        for (int yy = y; yy < y2; yy++) {
            int base = yy * cw;
            for (int xx = x; xx < x2; xx++) px[base + xx] = rgb;
        }
    }

    private void vlinePx(int x, int y0, int y1, int rgb) {
        if (x < 0 || x >= cw) return;
        if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
        if (y0 < 0) y0 = 0;
        if (y1 >= ch) y1 = ch - 1;
        for (int y = y0; y <= y1; y++) px[y * cw + x] = rgb;
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

    private void drawBars(boolean mirror) {
        int w = cw, h = ch;
        int bars = barCountFor(w);

        gTop = argb(cTop());
        gMid = argb(cMid());
        gBtm = argb(cBtm());
        int peakCol = argb(cPeak());

        float bw = (float) w / bars;
        // 原版几何：柱宽 6px、间隙 3px → 柱占周期 2/3；Wide 模式间隙更小
        float gapRatio = skinWide ? 0.12f : 0.30f;
        float gap = Math.max(1f, bw * gapRatio);
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
            // 峰线宽度：柱宽 6px → 2px；更窄则 1px（原版就是这种「很细很小」的线）
            int peakW = colW >= 6 ? 2 : 1;

            if (bh > 0) {
                // 原版：柱顶 1px 为 SpectrumPeakColor 高光帽，
                // 其余像素按「柱内相对高度」着色（顶部→品红，底部→蓝）。
                int cap = bh >= 3 ? 1 : bh;
                if (cap > 0) fillRectPx(x, baseY - bh, colW, cap, peakCol);
                int denom = Math.max(1, bh - 1);
                for (int r = cap; r < bh; r++) {              // r=0 为柱顶
                    int py = baseY - bh + r;
                    if (py < 0 || py >= h) continue;
                    int col = bandColorAt((float) r / denom);
                    int base = py * cw;
                    int x2 = Math.min(cw, x + colW);
                    for (int xx = x < 0 ? 0 : x; xx < x2; xx++) px[base + xx] = col;
                }
            }
            // 峰值保持线：原版是「很细」的 1px 线，居中于柱内，不跨满柱宽。
            // （放大实测：原版峰线宽度 ≤ 柱宽的 1/3，且无抗锯齿，呈锐利细线）
            int ph = (int) (pk * span);
            if (ph > bh + 1) {
                int py = baseY - ph;
                if (py >= 0 && py < h) {
                    int cx = x + (colW - peakW) / 2;
                    fillRectPx(cx, py, peakW, 1, peakCol);
                }
            }
            // 镜像下半（半透明对称柱）
            if (mirror && bh > 0) {
                int down = (int) (bh * 0.85f);
                int denom2 = Math.max(1, down - 1);
                for (int y = 0; y < down; y++) {
                    int py = baseY + y;
                    if (py < 0 || py >= h) continue;
                    int col = blendRgb(bandColorAt(1f - (float) y / denom2), 0.55f);
                    int base = py * cw;
                    int x2 = Math.min(cw, x + colW);
                    for (int xx = x < 0 ? 0 : x; xx < x2; xx++) px[base + xx] = col;
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

    private void drawLine() {
        int w = cw, h = ch;
        int n = Math.min(TtSpectrum.BANDS, Math.max(8, w / 4));
        int col = argb(cTop());
        int ppx = 0, ppy = h;
        for (int i = 0; i < n; i++) {
            float lv = spectrum.levelResampled(i, n);
            int x = (int) ((float) i / Math.max(1, n - 1) * w);
            int y = h - (int) (lv * h * 0.95f);
            linePx(ppx, ppy, x, y, col);
            ppx = x; ppy = y;
        }
        linePx(ppx, ppy, w - 1, h, col);
    }

    /**
     * AREA 模式 —— 原版第二种显示模式：面积填充 + 极细针状峰值线。
     *
     * <p>实测截图（背景纯黑 {@code (0,0,0)}，显示区 ~208×68）：</p>
     * <ul>
     *   <li>填充色为**整块显示区的绝对竖直三段渐变**（不随柱高锚定）：
     *       顶=Top(品红) → 中=Mid(黄) → 底=Btm(蓝)。因此矮面积只呈现下半段
     *       的绿蓝色，高面积才露出顶部品红。</li>
     *   <li>峰值线不是横线也不是点，而是**竖直的 1px 针状细线**：由峰值保持
     *       产生，从当前包络顶向上挑出一根很细很短的白色针。实测竖段长度分布
     *       集中在 1px（150/231 列），故强度极低、观感「很小、很细」。
     *       针的密度约每 2.4px 一根 → 按 48 段逐段渲染。</li>
     * </ul>
     */
    private void drawArea() {
        int w = cw, h = ch;
        if (w < 2) return;

        gTop = argb(cTop());
        gMid = argb(cMid());
        gBtm = argb(cBtm());
        int peakCol = argb(cPeak());

        // 原版该模式按全部 48 段渲染（针很密），不套用柱状模式的 9px 周期
        int bars = TtSpectrum.BANDS;
        float bw = (float) w / bars;

        // 1) 逐段求包络高度与峰值（复用数组，不分配）
        if (areaEnv.length < bars) { areaEnv = new float[bars]; areaPk = new float[bars]; }
        if (areaRowCol.length < h) areaRowCol = new int[h];
        float[] ys = areaEnv, pk = areaPk;
        for (int i = 0; i < bars; i++) {
            float lv = spectrum.levelResampled(i, bars);
            ys[i] = h - lv * h * 0.95f;
            pk[i] = resamplePeak(i, bars);
        }

        // 2) 面积填充：用「屏幕绝对 Y」取色（顶端品红、中段黄、底部蓝）
        //    色带按行预先算好，避免逐像素 lerp 重复计算。
        int[] rowCol = areaRowCol;
        int denom = Math.max(1, h - 1);
        for (int py = 0; py < h; py++) rowCol[py] = bandColorAt((float) py / denom);

        for (int i = 0; i < bars; i++) {
            int x = Math.round(i * bw);
            int x2 = Math.round((i + 1) * bw);
            int width = Math.max(1, x2 - x);
            int top = Math.max(0, (int) ys[i]);
            int xe = Math.min(cw, x + width);
            int xs = x < 0 ? 0 : x;
            for (int py = top; py < h; py++) {
                int col = rowCol[py];
                int base = py * cw;
                for (int xx = xs; xx < xe; xx++) px[base + xx] = col;
            }
        }

        // 3) 峰值针：竖直 1px 极细白线，从包络顶向上挑出（原版「很小很细」的针）
        int pxCol = blendRgb(peakCol, 0.88f);
        for (int i = 0; i < bars; i++) {
            int envTop = (int) ys[i];
            int pkTop  = h - (int) (pk[i] * h);
            if (envTop - pkTop < 1) continue;      // 无落差则无针
            int x = Math.round(i * bw + bw * 0.5f);
            if (x < 0 || x >= w) continue;
            vlinePx(x, Math.max(0, pkTop), envTop - 1, pxCol);
        }
    }

    private void drawLed() {
        int w = cw, h = ch;
        int cell = skinWide ? 5 : 6, gap = 2;
        int cols = Math.max(2, w / (cell + gap));
        int rows = Math.max(2, h / (cell + gap));
        int empty = blendRgb(0xFFFFFF, 14f / 255f);
        int cT = argb(cTop()), cM = argb(cMid()), cB = argb(cBtm()), cP = argb(cPeak());
        for (int i = 0; i < cols; i++) {
            float lv = spectrum.levelResampled(i, cols);
            int filled = (int) (lv * rows);
            int x = i * (cell + gap);
            for (int j = 0; j < rows; j++) {
                int y = h - (j + 1) * (cell + gap);
                if (j < filled) {
                    float t = (float) j / rows;
                    int col = t < 0.5f ? lerpRgb(cB, cM, t * 2) : lerpRgb(cM, cT, (t - 0.5f) * 2);
                    if (j == filled - 1) col = cP;
                    fillRectPx(x, y, cell, cell, col);
                } else {
                    fillRectPx(x, y, cell, cell, empty);
                }
            }
        }
    }

    private void drawWave() {
        int w = cw, h = ch;
        int got = pcmCount;
        if (got < 2) return;
        int n = Math.max(16, Math.min(w, got));
        int col = argb(cTop());
        int ppx = 0, ppy = h / 2;
        for (int i = 1; i < n; i++) {
            int idx = Math.min(i * got / n, got - 1);
            int x = i * w / n;
            int y = h / 2 + (int) (pcmSamples[idx] * h * 0.42f);
            linePx(ppx, ppy, x, y, col);
            ppx = x; ppy = y;
        }
    }

    /** Bresenham 直线（无抗锯齿，与原版像素级画法一致）。 */
    private void linePx(int x0, int y0, int x1, int y1, int rgb) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            put(x0, y0, rgb);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    // ============ 颜色工具（全部整数运算，零分配） ============

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

    /** 把颜色向背景色混合：keep=1 保持原色，keep=0 完全变成背景色。 */
    private int blendRgb(int c, float keep) {
        return lerpRgb(argb(backgroundColor), c, keep);
    }
}