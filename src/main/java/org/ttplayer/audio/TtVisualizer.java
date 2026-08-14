package org.ttplayer.audio;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * 频谱可视化面板 — 完整移植自 c_ttplayer（player_window_render_viz*.c / mini_window_viz.c）。
 *
 * 8 种频谱类型（对应 C render_visualization 的 mode 0..7）：
 *   BARS      柱状频谱        WAVE       多层波形 + 渐变填充
 *   MIRROR_BARS 镜像柱         PARTICLES  低音喷发 + 中频粒子
 *   LINE      频谱折线        AREA       平滑面积图
 *   RADAR     环形雷达        LED        LED 点阵
 *
 * FFT 使用与 FFmpeg AV_TX_FLOAT_RDFT 数学等价的 TtRdft。
 */
public class TtVisualizer extends javax.swing.JComponent {

    public enum Mode {
        BARS, WAVE, MIRROR_BARS, PARTICLES, LINE, AREA, RADAR, LED
    }

    private Mode mode = Mode.BARS;
    private TtRdft rdft = new TtRdft();

    /** 频谱区域背景：必须不透明，否则会透出窗口下层（白闪/透明） */
    public Color backgroundColor = new Color(16, 22, 30);

    public Color skinColorTop;
    public Color skinColorMid;
    public Color skinColorBtm;
    public Color skinColorPeak;
    public Color skinColorBlur;

    public TtVisualizer() {
        setOpaque(true);
    }

    // 当前 FFT 幅度谱（前 FFT_N/2 个 bin）
    private float[] spectrumBins = new float[TtRdft.FFT_N / 2];
    private boolean hasSpectrum = false;

    // 最近一帧 PCM 样本（供 WAVE / PARTICLES 使用）
    private float[] pcmSamples = new float[0];
    private int pcmCount = 0;

    // C g_smooth_level[128]：柱/折线/面积/雷达共用
    private final float[] smoothLevels = new float[128];

    // ============ 皮肤配色 ============

    private Color getSkinTop()  { return skinColorTop  != null ? skinColorTop  : new Color(120, 255, 120); }
    private Color getSkinMid()  { return skinColorMid  != null ? skinColorMid  : new Color(255, 255, 80);  }
    private Color getSkinBtm()  { return skinColorBtm  != null ? skinColorBtm  : new Color(80, 120, 255);  }
    private Color getSkinPeak() { return skinColorPeak != null ? skinColorPeak : Color.WHITE; }
    private Color getSkinBlur() { return skinColorBlur != null ? skinColorBlur : new Color(80, 120, 255); }

    // ============ 公共接口 ============

    public void setMode(Mode mode) { this.mode = mode; repaint(); }
    public Mode getMode() { return mode; }

    public void cycleMode() {
        Mode[] modes = Mode.values();
        mode = modes[(mode.ordinal() + 1) % modes.length];
        repaint();
    }

    /**
     * 喂入 PCM 数据（s16le 小端），计算幅度谱并缓存波形样本。
     */
    public void updateData(byte[] pcmData) {
        if (pcmData != null && pcmData.length > 8) {
            spectrumBins = rdft.spectrum(pcmData, pcmData.length);
            hasSpectrum = true;

            int n = pcmData.length / 2;
            if (pcmSamples.length < n) pcmSamples = new float[n];
            for (int i = 0; i < n; i++) {
                int off = i * 2;
                short s = (short) ((pcmData[off + 1] << 8) | (pcmData[off] & 0xFF));
                pcmSamples[i] = s / 32768.0f;
            }
            pcmCount = n;
        } else {
            hasSpectrum = false;
            Arrays.fill(spectrumBins, 0f);
            pcmSamples = new float[0];
            pcmCount = 0;
        }
        repaint();
    }

    // ============ 频谱数学 ============

    /** C spectrum_level：bin 幅度均值 → ×100 → log1p/log1p(100) 压缩 */
    private float spectrumLevel(int binStart, int binEnd) {
        float mag = 0;
        int count = 0;
        int half = TtRdft.FFT_N / 2;
        // 跳过 DC bin(0)：直流偏置会把第一根柱顶高，制造"左边高"假象
        int start = Math.max(1, binStart);
        for (int b = start; b < binEnd && b < half; b++) {
            mag += spectrumBins[b];
            count++;
        }
        if (count > 0) mag /= (float) count;
        float level = 0;
        if (mag > 0) {
            float scaled = mag * 100.0f;
            level = (float) (Math.log1p(scaled) / Math.log1p(100.0));
        }
        if (level > 1.0f) level = 1.0f;
        if (level < 0.02f) level = 0.0f;
        return level;
    }

    /** C smooth_level：att 0.5 / decay 0.9 */
    private float smoothLevel(int idx, float rawLevel) {
        float prev = smoothLevels[idx];
        float v = (rawLevel >= prev) ? prev + (rawLevel - prev) * 0.5f : prev * 0.9f;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        smoothLevels[idx] = v;
        return v;
    }

    /** C theme_color：在调色板线性插值 */
    private Color themeColor(int index, int total, Color[] palette) {
        float t = (total > 1) ? (float) index / (total - 1) : 0f;
        if (t > 1) t = 1;
        float pos = t * (palette.length - 1);
        int lo = (int) pos;
        int hi = lo + 1;
        if (hi >= palette.length) hi = palette.length - 1;
        float k = pos - lo;
        Color a = palette[lo], b = palette[hi];
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * k),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
            (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** 取 spectrumLevel 且应用平滑（trim 保证不会因频率段重叠而重复平滑） */
    private float levelFor(int binStart, int binEnd, int idx) {
        float raw = hasSpectrum ? spectrumLevel(binStart, binEnd) : 0f;
        return smoothLevel(idx, raw);
    }

    // ============ 渲染分发 ============

    // ============ 双缓存 ============

    /** 离屏后缓冲：整帧渲染完成后一次性 blit，避免逐元素绘制闪烁 */
    private BufferedImage backBuffer;

    private void ensureBuffer(int w, int h) {
        if (backBuffer == null || backBuffer.getWidth() != w || backBuffer.getHeight() != h) {
            backBuffer = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        ensureBuffer(w, h);
        Graphics2D bg = backBuffer.createGraphics();
        try {
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 先铺不透明背景，避免频谱区域透出窗口下层内容（白闪/透明感）
            bg.setColor(backgroundColor);
            bg.fillRect(0, 0, w, h);

            switch (mode) {
                case BARS:        drawBars(bg); break;
                case WAVE:        drawWave(bg); break;
                case MIRROR_BARS: drawMirrorBars(bg); break;
                case PARTICLES:   drawParticles(bg); break;
                case LINE:        drawLine(bg); break;
                case AREA:        drawArea(bg); break;
                case RADAR:       drawRadar(bg); break;
                case LED:         drawLed(bg); break;
            }
        } finally {
            bg.dispose();
        }
        g.drawImage(backBuffer, 0, 0, null);
    }

    // ============ mode 0: 柱状频谱（C viz_bars） ============

    private void drawBars(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int barCount = w / 6;
        if (barCount < 4) barCount = 4;
        if (barCount > 32) barCount = 32;

        float barW = (float) w / barCount;
        float gap = barW * 0.2f;
        if (gap < 1f) gap = 1f;

        Color btm = getSkinBtm(), mid = getSkinMid(), top = getSkinTop();
        Color peak = getSkinPeak();
        Color[] palette = {btm, mid, top, peak};

        int baseY = h;
        int half = TtRdft.FFT_N / 2;
        for (int i = 0; i < barCount; i++) {
            int binStart = i * half / barCount;
            int binEnd = (i + 1) * half / barCount;
            if (binEnd <= binStart) binEnd = binStart + 1;

            float level = levelFor(binStart, binEnd, i);
            int barH = (int) (level * h * 0.9f);
            if (barH < 1) barH = 1;

            Color col = themeColor(i, barCount, palette);
            float bx = i * barW + gap / 2;
            float bw = barW - gap;

            // 垂直渐变 6 段：底部实色 → 顶部半透明（α 255 → 85）
            int segs = 6;
            for (int s = 0; s < segs; s++) {
                int segTop = baseY - barH + s * barH / segs;
                int segH = barH / segs;
                if (s == segs - 1) segH = barH - s * barH / segs;
                if (segH <= 0) continue;
                float v = (float) s / segs;
                int alpha = (int) (255 - v * 170);
                g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), alpha));
                g2.fillRect((int) bx, segTop, (int) bw, segH);
            }

            // 柱顶高亮
            if (barH >= 3 && level > 0.15f) {
                g2.setColor(new Color(peak.getRed(), peak.getGreen(), peak.getBlue(), 230));
                g2.fillRect((int) bx, baseY - barH, (int) bw, 2);
            }
        }
    }

    // ============ mode 1: 波形（C viz_wave） ============

    private void drawWave(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int got = pcmCount;
        int steps = w;
        if (steps > got) steps = got;
        if (steps < 8) steps = 8;
        if (steps > 512) steps = 512;

        int n = steps;
        float[] pts = new float[n];
        for (int i = 0; i < n; i++) {
            int idx = got > 0 ? i * got / n : 0;
            if (idx >= got) idx = got - 1;
            if (idx < 0) idx = 0;
            pts[i] = got > 0 ? pcmSamples[idx] : 0.0f;
        }

        int centerY = h / 2;
        float scale = h * 0.4f;
        Color top = getSkinTop();

        // 多层叠加波形（3 层）
        for (int layer = 2; layer >= 0; layer--) {
            float alpha = layer == 0 ? 1.0f : (layer == 1 ? 0.4f : 0.15f);
            float lscale = scale * (1 + layer * 0.3f);
            int offset = layer * 2;
            g2.setColor(new Color(top.getRed(), top.getGreen(), top.getBlue(), (int) (255 * alpha)));
            int prevX = 0;
            int prevY = centerY + (int) (pts[0] * lscale) + offset;
            for (int i = 1; i < n; i++) {
                int x = i * w / n;
                int y = centerY + (int) (pts[i] * lscale) + offset;
                g2.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }
        }

        // 主波形下方渐变填充
        Color btm = getSkinBtm();
        int segs = 6;
        for (int s = 0; s < segs; s++) {
            float v0 = (float) s / segs;
            float v1 = (float) (s + 1) / segs;
            int alpha = (int) (40 * (1.0f - v0));
            g2.setColor(new Color(btm.getRed(), btm.getGreen(), btm.getBlue(), alpha));
            int y0 = centerY + (int) (scale * (0.5f - v0));
            int y1 = centerY + (int) (scale * (0.5f - v1));
            if (alpha <= 0) continue;
            for (int i = 0; i < n; i++) {
                int x0 = i * w / n;
                int x1 = (i + 1) * w / n;
                int y = centerY + (int) (pts[i] * scale);
                int topY = Math.min(y, y0);
                g2.fillRect(x0, topY, x1 - x0, y1 - topY);
            }
        }
    }

    // ============ mode 2: 镜像柱（C viz_mirror_bars） ============

    private void drawMirrorBars(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int barCount = w / 4;
        if (barCount < 4) barCount = 4;
        if (barCount > 40) barCount = 40;

        Color[] palette = {getSkinBtm(), getSkinMid(), getSkinTop()};
        float barW = (float) w / barCount;
        float gap = barW * 0.2f;
        if (gap < 1) gap = 1;
        int midY = h / 2;
        int half = TtRdft.FFT_N / 2;

        for (int i = 0; i < barCount; i++) {
            int binStart = i * half / barCount;
            int binEnd = (i + 1) * half / barCount;
            if (binEnd <= binStart) binEnd = binStart + 1;
            float level = levelFor(binStart, binEnd, i);
            int barH = (int) (level * h * 0.45f);
            if (barH < 1) barH = 1;

            Color col = themeColor(i, barCount, palette);
            float bx = i * barW + gap / 2;
            float bw = barW - gap;

            // 上镜像（实色）
            g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 220));
            g2.fillRect((int) bx, midY - barH, (int) bw, barH);
            // 下镜像（半透明）
            g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 140));
            g2.fillRect((int) bx, midY, (int) bw, barH);
        }
    }

    // ============ mode 3: 粒子（C viz_particles） ============

    private static class VizParticle {
        float x, y, vx, vy;
        int r, g, b;
        float life, size;
    }

    private static final int MAX_PARTICLES = 400;
    private final VizParticle[] particles = new VizParticle[MAX_PARTICLES];
    private int particleCount = 0;

    private void drawParticles(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        int got = pcmCount;

        // 低音能量：前 8 个采样均值
        float bass = 0;
        int bs = Math.min(8, got);
        for (int i = 0; i < bs; i++) bass += pcmSamples[i];
        bass = (bass / 8) * 0.5f + 0.5f;
        if (bass < 0) bass = 0;
        if (bass > 1) bass = 1;

        // 中频能量：spectrum[4..32)
        float mid = 0;
        for (int i = 4; i < 32 && i < TtRdft.FFT_N / 2; i++) mid += spectrumBins[i];
        mid /= 28.0f;
        if (mid > 1) mid = 1;

        Color[] palette = {getSkinBtm(), getSkinTop()};

        // 低音：粒子从中心喷发
        if (bass > 0.4f) {
            int count = (int) (bass * 8);
            for (int c = 0; c < count && particleCount < MAX_PARTICLES; c++) {
                VizParticle p = new VizParticle();
                p.x = cx + (random() % 100 - 50) / 5.0f;
                p.y = cy + (random() % 100 - 50) / 5.0f;
                float ang = (float) (random() % 628) / 100.0f;
                float sp = (1.0f + (random() % 40) / 10.0f) * bass;
                p.vx = (float) Math.cos(ang) * sp;
                p.vy = (float) Math.sin(ang) * sp;
                p.life = 1.0f;
                p.size = 2.0f + (random() % 40) / 10.0f * bass;
                int ci = random() % 64;
                Color col = themeColor(ci, 64, palette);
                p.r = col.getRed(); p.g = col.getGreen(); p.b = col.getBlue();
                particles[particleCount++] = p;
            }
        }

        // 中频：从边缘飞向中心
        if (mid > 0.15f && got > 0) {
            int count = (int) (mid * 6);
            for (int c = 0; c < count && particleCount < MAX_PARTICLES; c++) {
                VizParticle p = new VizParticle();
                int edge = random() % 4;
                if (edge == 0) { p.x = 0; p.y = random() % h; }
                else if (edge == 1) { p.x = w; p.y = random() % h; }
                else if (edge == 2) { p.x = random() % w; p.y = 0; }
                else { p.x = random() % w; p.y = h; }
                float dx = cx - p.x, dy = cy - p.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < 1) dist = 1;
                float sp = mid * 3.0f;
                p.vx = (dx / dist) * sp;
                p.vy = (dy / dist) * sp;
                p.life = 1.0f;
                p.size = 1.5f + (random() % 30) / 20.0f;
                int ci = random() % 64;
                Color col = themeColor(ci, 64, palette);
                p.r = col.getRed(); p.g = col.getGreen(); p.b = col.getBlue();
                particles[particleCount++] = p;
            }
        }

        // 更新并绘制
        int write = 0;
        for (int i = 0; i < particleCount; i++) {
            VizParticle p = particles[i];
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.02f;   // 重力
            p.vx *= 0.99f;
            p.life -= 0.015f;
            p.size *= 0.995f;
            if (p.life <= 0) continue;

            if (write != i) particles[write] = p;
            write++;
            int alpha = (int) (p.life * 200);
            g2.setColor(new Color(p.r, p.g, p.b, alpha));
            float sz = p.size > 0.5f ? p.size : 0.5f;
            g2.fillRect((int) p.x, (int) p.y, (int) sz, (int) sz);
        }
        particleCount = write;
    }

    private int random() {
        // 避免每次调用 Math.random 的开销；直接复用 Math.random()
        return (int) (Math.random() * Integer.MAX_VALUE);
    }

    // ============ mode 4: 频谱折线（C viz_line） ============

    private void drawLine(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int points = w / 4;
        if (points < 8) points = 8;
        if (points > 64) points = 64;
        int half = TtRdft.FFT_N / 2;

        Color top = getSkinTop();
        g2.setColor(top);

        int prevX = 0;
        int prevY = h;
        for (int i = 0; i < points; i++) {
            int binStart = i * half / points;
            int binEnd = (i + 1) * half / points;
            if (binEnd <= binStart) binEnd = binStart + 1;
            float level = levelFor(binStart, binEnd, i);
            int x = (int) ((float) i / points * w);
            int y = h - (int) (level * h * 0.9f);
            g2.drawLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
        g2.drawLine(prevX, prevY, w, h);
    }

    // ============ mode 5: 面积图（C viz_area） ============

    private void drawArea(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        if (w < 2) w = 2;
        int half = TtRdft.FFT_N / 2;
        int BINS = 64;

        float[] lvl = new float[BINS];
        for (int i = 0; i < BINS; i++) {
            int binStart = i * half / BINS;
            int binEnd = (i + 1) * half / BINS;
            if (binEnd <= binStart) binEnd = binStart + 1;
            lvl[i] = levelFor(binStart, binEnd, i);
        }

        // 每个像素列的顶部曲线 y
        float[] ys = new float[w];
        float bottom = (float) h;
        for (int x = 0; x < w; x++) {
            float fx = (float) x / (w - 1);
            float fi = fx * (BINS - 1);
            int lo = (int) fi;
            int hi = lo + 1;
            if (hi >= BINS) hi = BINS - 1;
            float k = fi - lo;
            float level = lvl[lo] + (lvl[hi] - lvl[lo]) * k;
            float y = bottom - level * h * 0.9f;
            if (y < 0) y = 0;
            ys[x] = y;
        }

        // 渐变填充（top → btm）
        Color top = getSkinTop();
        Color btm = getSkinBtm();
        g2.setPaint(new GradientPaint(0, 0, new Color(top.getRed(), top.getGreen(), top.getBlue(), 210),
                                      0, h, new Color(btm.getRed(), btm.getGreen(), btm.getBlue(), 60)));
        Path2D.Float area = new Path2D.Float();
        area.moveTo(0, ys[0]);
        for (int x = 1; x < w; x++) area.lineTo(x, ys[x]);
        area.lineTo(w - 1, bottom);
        area.lineTo(0, bottom);
        area.closePath();
        g2.fill(area);

        // 顶线描边：peak 双层（外光晕 + 主线）
        Color peak = getSkinPeak();
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(peak.getRed(), peak.getGreen(), peak.getBlue(), 90));
        Path2D.Float glow = new Path2D.Float();
        glow.moveTo(0, ys[0] - 1);
        for (int x = 1; x < w; x++) glow.lineTo(x, ys[x] - 1);
        g2.draw(glow);

        g2.setColor(peak);
        Path2D.Float main = new Path2D.Float();
        main.moveTo(0, ys[0]);
        for (int x = 1; x < w; x++) main.lineTo(x, ys[x]);
        g2.draw(main);
    }

    // ============ mode 6: 雷达（C viz_radar） ============

    private void drawRadar(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int AXES = 12, RINGS = 4, SEGS = 40;
        float cx = w / 2.0f, cy = h / 2.0f;
        float radius = Math.min(w, h) * 0.5f - 6.0f;
        if (radius < 10) radius = 10;

        Color btm = getSkinBtm();

        // 同心圆环 + 轴辐（btm 色低透明度网格）
        for (int r = 1; r <= RINGS; r++) {
            float rr = radius * r / RINGS;
            int a = 16 + r * 15;
            g2.setColor(new Color(btm.getRed(), btm.getGreen(), btm.getBlue(), a));
            for (int s = 0; s < SEGS; s++) {
                float a0 = (float) (2 * Math.PI * s / SEGS);
                float a1 = (float) (2 * Math.PI * (s + 1) / SEGS);
                g2.drawLine((int) (cx + Math.cos(a0) * rr), (int) (cy + Math.sin(a0) * rr),
                            (int) (cx + Math.cos(a1) * rr), (int) (cy + Math.sin(a1) * rr));
            }
        }
        g2.setColor(new Color(btm.getRed(), btm.getGreen(), btm.getBlue(), 40));
        for (int i = 0; i < AXES; i++) {
            float ang = (float) (-Math.PI / 2 + 2 * Math.PI * i / AXES);
            g2.drawLine((int) cx, (int) cy,
                        (int) (cx + Math.cos(ang) * radius), (int) (cy + Math.sin(ang) * radius));
        }

        // 每轴 level → 半径（平滑）
        float[] lvl = new float[AXES];
        int half = TtRdft.FFT_N / 2;
        for (int i = 0; i < AXES; i++) {
            int binStart = i * half / AXES;
            int binEnd = (i + 1) * half / AXES;
            if (binEnd <= binStart) binEnd = binStart + 1;
            lvl[i] = levelFor(binStart, binEnd, i);
        }

        // 中心放射渐变填充（top 色）
        Color top = getSkinTop();
        Path2D.Float fan = new Path2D.Float();
        fan.moveTo(cx, cy);
        for (int i = 0; i <= AXES; i++) {
            int ii = i % AXES;
            float ang = (float) (-Math.PI / 2 + 2 * Math.PI * ii / AXES);
            float rr = lvl[ii] * radius * 0.92f;
            fan.lineTo(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
        }
        fan.closePath();
        g2.setColor(new Color(top.getRed(), top.getGreen(), top.getBlue(), 70));
        g2.fill(fan);

        // 多边形描边：peak 双层
        Color peak = getSkinPeak();
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(peak.getRed(), peak.getGreen(), peak.getBlue(), 90));
        Path2D.Float glow = new Path2D.Float();
        for (int i = 0; i <= AXES; i++) {
            int ii = i % AXES;
            float ang = (float) (-Math.PI / 2 + 2 * Math.PI * ii / AXES);
            float rr = lvl[ii] * radius * 0.92f + 1;
            if (i == 0) glow.moveTo(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
            else glow.lineTo(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
        }
        glow.closePath();
        g2.draw(glow);
        g2.setColor(peak);
        Path2D.Float main = new Path2D.Float();
        for (int i = 0; i <= AXES; i++) {
            int ii = i % AXES;
            float ang = (float) (-Math.PI / 2 + 2 * Math.PI * ii / AXES);
            float rr = lvl[ii] * radius * 0.92f;
            if (i == 0) main.moveTo(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
            else main.lineTo(cx + (float) Math.cos(ang) * rr, cy + (float) Math.sin(ang) * rr);
        }
        main.closePath();
        g2.draw(main);
    }

    // ============ mode 7: LED 点阵（C viz_led） ============

    private void drawLed(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        int cell = 6, gap = 2;
        int cols = w / (cell + gap);
        int rows = h / (cell + gap);
        if (cols < 2) cols = 2;
        if (rows < 2) rows = 2;

        Color[] palette = {getSkinBtm(), getSkinMid(), getSkinTop()};
        int half = TtRdft.FFT_N / 2;

        for (int i = 0; i < cols; i++) {
            int binStart = i * half / cols;
            int binEnd = (i + 1) * half / cols;
            if (binEnd <= binStart) binEnd = binStart + 1;
            float level = levelFor(binStart, binEnd, i);
            int filled = (int) (level * rows);
            if (filled > rows) filled = rows;

            for (int j = 0; j < rows; j++) {
                float ry = h - (j + 1) * (cell + gap);
                int rx = i * (cell + gap);
                if (j < filled) {
                    Color c = themeColor(j, rows, palette);
                    g2.setColor(c);
                    g2.fillRect(rx, (int) ry, cell, cell);
                    if (j == filled - 1) {  // 顶部点亮格高亮为白色
                        g2.setColor(new Color(255, 255, 255, 235));
                        g2.fillRect(rx, (int) ry, cell, cell);
                    }
                } else {
                    g2.setColor(new Color(35, 35, 35, 110));
                    g2.fillRect(rx, (int) ry, cell, cell);
                }
            }
        }
    }
}