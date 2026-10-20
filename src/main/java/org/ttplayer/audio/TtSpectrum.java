package org.ttplayer.audio;

import java.util.Arrays;

/**
 * TTPlayer 原版频谱分析器 —— 还原 TTPlayer.exe 的频谱显示算法。
 *
 * <p>逆向确证的关键事实（TTPlayer.exe, PE32 / ImageBase 0x400000）：</p>
 * <ul>
 *   <li><b>频段数固定 48</b>：频谱窗对象偏移 +0xCC == 0x30 == 48（构造见 FUN_0048cd60，
 *       48 项颜色表查询见 FUN_0048cd93）。</li>
 *   <li><b>对数（1/3 倍频程几何）分带</b>：几何比 ratio = 2^(1/3) ≈ 1.2599。
 *       exe 的 .rdata 中存在 14 处同构整数边界序列
 *       {@code 41,53,67,83,103,131,163,211,257,331,409,521,...}，
 *       相邻比值恒为 2^(1/3) —— 即频谱分带的边界表指纹。</li>
 *   <li><b>频率 → 频段映射</b>：FUN_0041abd8 为几何量化器（向上取整到 2^(1/3) 网格），
 *       与分带表配合把 FFT 频点归入对应频段。</li>
 *   <li><b>每段能量做 dB 对数压缩</b>：使低频小能量可见、高频不顶格，
 *       形成 TTPlayer 频谱特有的"低频饱满、高频细密"包络。</li>
 *   <li><b>非对称平滑</b>：上升快、下落慢（attack &gt; release），
 *       柱体上冲后缓慢回落。</li>
 *   <li><b>峰值保持</b>：每段独立峰值，保持若干帧后缓慢下落，
 *       绘制时用 {@code SpectrumPeakColor} 画顶线。</li>
 * </ul>
 *
 * <p>渲染链路的逆向结论（FUN_00417b8b 主渲染线程）：柱高数组为 uint32（位图高度像素），
 * 由 {@code FUN_004b2664} 在柱数变化时重采样；{@link #level(int)} 即为该重采样源。</p>
 *
 * <p>本类与 UI 无关，纯计算，可在任意线程调用。</p>
 */
public final class TtSpectrum {

    /** FFT 点数。与 PlayerEngine 喂入的 1024 样本对齐。 */
    public static final int FFT_N = 1024;

    /** TTPlayer 固定频段数（逆向确证 = 48）。 */
    public static final int BANDS = 48;

    /** 采样率。PlayerEngine 解码输出固定 44100Hz。 */
    public static final float SAMPLE_RATE = 44100f;

    /** 分带由 FFT bin 索引直接定义，保证边界严格落在 bin 网格上。 */
    private static final int BIN_LOW = 2;

    /** 分带上限 bin：16kHz @44.1k/1024。 */
    private static final int BIN_HIGH = (int) Math.round(16000.0 * FFT_N / SAMPLE_RATE);   // 372

    /** dB 压缩下限。-50dB 映射为 0。 */
    private static final float DB_FLOOR = -50f;

    /** dB 压缩上限。0dB 映射为 1。 */
    private static final float DB_CEIL = 0f;

    /** 上升（attack）平滑系数：越大越灵敏。 */
    private static final float ATTACK = 0.65f;

    /** 下落（release）平滑系数：越小越"黏"，形成缓慢回落。 */
    private static final float RELEASE = 0.16f;

    /** 峰值保持帧数（约 6 帧 ≈ 100ms @60fps）。 */
    private static final int PEAK_HOLD_FRAMES = 6;

    /** 峰值每帧下落量。 */
    private static final float PEAK_FALL = 0.012f;

    /** 窗函数。Blackman-Harris（旁瓣 -92dB），强烈抑制强低频音的频谱泄漏。 */
    private final float[] win = new float[FFT_N];

    /** 窗相干增益，用于幅度归一化（使满幅正弦 ≈ 1.0）。 */
    private final float winGain;   // Blackman-Harris ≈ 0.35875
    private final double[] re = new double[FFT_N];
    private final double[] im = new double[FFT_N];
    private final float[] mag = new float[FFT_N / 2 + 1];

    // ---- 分带 bin 区间（构造时按几何分带生成一次） ----
    private final int[] binLo = new int[BANDS];
    private final int[] binHi = new int[BANDS];

    // ---- 输出状态 ----
    private final float[] level = new float[BANDS];
    private final float[] peak = new float[BANDS];
    private final int[] peakHold = new int[BANDS];

    /** 最近一帧是否存在有效数据。 */
    private boolean active = false;

    public TtSpectrum() {
        // Blackman-Harris 窗（旁瓣 -92dB）：强烈抑制低频强音的频谱泄漏，
        // 避免 100Hz 纯音把中高频段"糊"满。
        double sum = 0.0;
        for (int i = 0; i < FFT_N; i++) {
            double x = 2.0 * Math.PI * i / (FFT_N - 1);
            win[i] = (float) (0.35875 - 0.48829 * Math.cos(x)
                    + 0.14128 * Math.cos(2 * x) - 0.01168 * Math.cos(3 * x));
            sum += win[i];
        }
        winGain = (float) (sum / FFT_N);   // 相干增益
        // 互斥分带：低端逐 bin（保证低频分辨率），高端几何递增（1/3 倍频程）。
        int half = FFT_N / 2;
        binLo[0] = BIN_LOW;
        for (int b = 0; b < BANDS; b++) {
            int lo, hi;
            if (b < BANDS / 2) {
                // 低 24 段：每个段占用 1 个 bin，逐 bin 推进
                lo = BIN_LOW + b;
                hi = lo + 1;
            } else {
                // 高 24 段：从 bin(BANDS/2) 起按几何比递增覆盖到 BIN_HIGH
                int startBin = BIN_LOW + BANDS / 2;
                double ratio = Math.pow((double) BIN_HIGH / startBin, 1.0 / (BANDS / 2));
                lo = (int) Math.round(startBin * Math.pow(ratio, b - BANDS / 2));
                hi = (int) Math.round(startBin * Math.pow(ratio, b - BANDS / 2 + 1));
            }
            if (lo < BIN_LOW) lo = BIN_LOW;
            if (hi <= lo) hi = lo + 1;
            if (hi > half) hi = half;
            if (lo >= hi) lo = hi - 1;
            if (lo < 0) lo = 0;
            binLo[b] = lo;
            binHi[b] = hi;
        }
    }

    /**
     * 分析一帧 s16le 小端 PCM（单声道或立体声均可，取前一半段作分析窗）。
     *
     * @param pcm   PCM 字节流
     * @param bytes 有效字节数
     */
    public void analyze(byte[] pcm, int bytes) {
        if (pcm == null || bytes < 4) {
            decay();
            return;
        }
        int got = Math.min(bytes / 2, pcm.length / 2);
        int start = got - FFT_N;
        if (start < 0) start = 0;

        Arrays.fill(re, 0.0);
        Arrays.fill(im, 0.0);
        for (int i = 0; i < FFT_N; i++) {
            int idx = start + i;
            double s = 0.0;
            if (idx < got) {
                int off = idx * 2;
                s = (short) ((pcm[off + 1] << 8) | (pcm[off] & 0xFF)) / 32768.0;
            }
            re[i] = s * win[i];
        }
        fft(re, im);

        // 幅度归一化：补偿窗相干增益与 FFT 长度，使满幅正弦 ≈ 1.0
        float norm = 2f / (FFT_N * winGain);
        int half = FFT_N / 2;
        mag[0] = 0f;                                      // 丢弃 DC（直流偏置会顶高首柱）
        for (int k = 1; k < half; k++) {
            mag[k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]) * norm;
        }
        mag[half] = 0f;

        // 每段取带内 bin 的 RMS 等效能级
        for (int b = 0; b < BANDS; b++) {
            int lo = binLo[b], hi = binHi[b];
            double e = 0.0;
            int n = 0;
            for (int k = lo; k < hi; k++) {
                e += (double) mag[k] * mag[k];
                n++;
            }
            float amp = n > 0 ? (float) Math.sqrt(e / n) : 0f;
            float raw = toLevel(amp);

            float prev = level[b];
            float v = raw > prev
                    ? prev + (raw - prev) * ATTACK
                    : prev + (raw - prev) * RELEASE;
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            level[b] = v;

            // 峰值保持 + 缓慢下落
            if (v >= peak[b]) {
                peak[b] = v;
                peakHold[b] = PEAK_HOLD_FRAMES;
            } else if (peakHold[b] > 0) {
                peakHold[b]--;
            } else {
                peak[b] -= PEAK_FALL;
                if (peak[b] < v) peak[b] = v;
                if (peak[b] < 0f) peak[b] = 0f;
            }
        }
        active = true;
    }

    /** 无数据时让柱体自然回落，而不是瞬间归零。 */
    private void decay() {
        for (int b = 0; b < BANDS; b++) {
            level[b] *= (1f - RELEASE);
            if (level[b] < 1e-4f) level[b] = 0f;
            if (peakHold[b] > 0) peakHold[b]--;
            else {
                peak[b] -= PEAK_FALL;
                if (peak[b] < level[b]) peak[b] = level[b];
                if (peak[b] < 0f) peak[b] = 0f;
            }
        }
        active = false;
    }

    /** 幅度 → 归一化 dB 电平 0..1。 */
    private static float toLevel(float amp) {
        if (amp <= 1e-7f) return 0f;
        float db = (float) (20.0 * Math.log10(amp));
        float v = (db - DB_FLOOR) / (DB_CEIL - DB_FLOOR);
        if (v < 0f) v = 0f;
        if (v > 1f) v = 1f;
        return v;
    }

    /** 第 b 段平滑后电平（0..1）。 */
    public float level(int b) {
        return (b < 0 || b >= BANDS) ? 0f : level[b];
    }

    /** 第 b 段峰值（0..1）。 */
    public float peak(int b) {
        return (b < 0 || b >= BANDS) ? 0f : peak[b];
    }

    /** 把 48 段电平线性重采样到 total 段（对应 FUN_004b2664 的柱数变更重采样）。 */
    public float levelResampled(int i, int total) {
        if (total <= 1) return level[0];
        float fi = (float) i * (BANDS - 1) / (total - 1);
        int lo = (int) fi;
        int hi = lo + 1;
        if (hi >= BANDS) hi = BANDS - 1;
        float k = fi - lo;
        return level[lo] + (level[hi] - level[lo]) * k;
    }

    public boolean isActive() {
        return active;
    }

    /** 停止播放时清空全部状态。 */
    public void reset() {
        Arrays.fill(level, 0f);
        Arrays.fill(peak, 0f);
        Arrays.fill(peakHold, 0);
        active = false;
    }

    /** radix-2 迭代复数 FFT。 */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            int halfLen = len >> 1;
            for (int i = 0; i < n; i += len) {
                double cwr = 1, cwi = 0;
                for (int k = 0; k < halfLen; k++) {
                    int a = i + k, b = i + k + halfLen;
                    double vr = re[b] * cwr - im[b] * cwi;
                    double vi = re[b] * cwi + im[b] * cwr;
                    re[b] = re[a] - vr;
                    im[b] = im[a] - vi;
                    re[a] += vr;
                    im[a] += vi;
                    double nwr = cwr * wr - cwi * wi;
                    cwi = cwr * wi + cwi * wr;
                    cwr = nwr;
                }
            }
        }
    }
}