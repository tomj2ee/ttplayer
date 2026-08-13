package org.ttplayer.engine;

/**
 * 10 段图形均衡器 — Java biquad（双二阶 IIR 峰值滤波）实现。
 *
 * 频点对应千千静听常规 10 段：60 / 170 / 310 / 600 / 1K / 3K / 6K / 12K / 14K / 16K Hz。
 * 滑块 0..100 映射到 -12..+12 dB（50 → 0 dB）。
 * 每个声道一条 10 段级联链，交织立体声 s16le 原地处理。
 */
public class Equalizer {

    public static final int BANDS = 10;
    public static final double[] FREQS = {
        60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000
    };
    public static final double DB_RANGE = 12.0;

    private final double sampleRate;
    private final double q;

    private volatile boolean enabled = false;
    private volatile boolean dolbySurround = false;
    private volatile double preampDb = 0.0;
    private final double[] gainsDb = new double[BANDS];
    private volatile boolean active = false;

    private final Biquad[] chainL = new Biquad[BANDS];
    private final Biquad[] chainR = new Biquad[BANDS];

    public Equalizer() {
        this(44100, 1.0);
    }

    public Equalizer(double sampleRate, double q) {
        this.sampleRate = sampleRate;
        this.q = q;
        for (int i = 0; i < BANDS; i++) {
            chainL[i] = new Biquad();
            chainR[i] = new Biquad();
        }
    }

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    public void setDolbySurround(boolean on) { dolbySurround = on; }
    public boolean isDolbySurround() { return dolbySurround; }

    /** 一次性应用一组预设增益（10 段 dB 值） */
    public void applyPreset(double[] gains) {
        if (gains == null) return;
        int n = Math.min(BANDS, gains.length);
        for (int i = 0; i < n; i++) {
            setGainDb(i, gains[i]);
        }
    }

    public void setPreampDb(double db) {
        preampDb = db;
        updateActive();
    }

    public double getPreampDb() { return preampDb; }
    public double getGainDb(int band) {
        if (band < 0 || band >= BANDS) return 0;
        return gainsDb[band];
    }

    public void setGainDb(int band, double db) {
        if (band < 0 || band >= BANDS) return;
        gainsDb[band] = db;
        chainL[band].setPeak(FREQS[band], db, q, sampleRate);
        chainR[band].setPeak(FREQS[band], db, q, sampleRate);
        updateActive();
    }

    public void reset() {
        preampDb = 0;
        for (int i = 0; i < BANDS; i++) {
            gainsDb[i] = 0;
            chainL[i].reset();
            chainR[i].reset();
        }
        updateActive();
    }

    private void updateActive() {
        boolean any = preampDb != 0.0;
        for (int i = 0; i < BANDS && !any; i++) {
            any = gainsDb[i] != 0.0;
        }
        active = any;
    }

    /** 滑块 0..100 映射为 dB（50 → 0） */
    public static double sliderToDb(int value) {
        return (value - 50) * DB_RANGE / 50.0;
    }

    /**
     * 处理交织立体声 s16le 字节，原地修改。
     * 均衡滤波（启用且非全 0 增益时）与杜比环绕（启用时）均可独立生效；
     * 全部关闭时零开销直接返回。
     */
    public void process(byte[] pcm) {
        boolean doEq = enabled && active;
        boolean doDolby = dolbySurround;
        if ((!doEq && !doDolby) || pcm == null || pcm.length < 4) return;

        double preamp = Math.pow(10.0, preampDb / 20.0);
        int shorts = pcm.length / 2;

        for (int i = 0; i + 1 < shorts; i += 2) {
            int o = i * 2;
            short l = (short) ((pcm[o + 1] << 8) | (pcm[o] & 0xFF));
            short r = (short) ((pcm[o + 3] << 8) | (pcm[o + 2] & 0xFF));

            double ol = l * preamp;
            double orr = r * preamp;

            if (doEq) {
                for (int b = 0; b < BANDS; b++) {
                    ol = chainL[b].next(ol);
                    orr = chainR[b].next(orr);
                }
            }

            if (doDolby) {
                // 简化杜比环绕：左右声道交叉混合展宽声场
                double t = ol;
                ol = ol + 0.35 * orr;
                orr = orr + 0.35 * t;
            }

            short cl = (short) Math.max(-32768, Math.min(32767, Math.round(ol)));
            short cr = (short) Math.max(-32768, Math.min(32767, Math.round(orr)));

            pcm[o] = (byte) (cl & 0xFF);
            pcm[o + 1] = (byte) ((cl >> 8) & 0xFF);
            pcm[o + 2] = (byte) (cr & 0xFF);
            pcm[o + 3] = (byte) ((cr >> 8) & 0xFF);
        }
    }

    /** RBJ 音频均衡双二阶（peak filter），直 II 型 */
    static class Biquad {
        private double b0, b1, b2, a1, a2;
        private double z1, z2;

        Biquad() { reset(); }

        void reset() {
            b0 = 1; b1 = 0; b2 = 0; a1 = 0; a2 = 0;
            z1 = 0; z2 = 0;
        }

        void setPeak(double f0, double gDb, double Q, double fs) {
            double A  = Math.pow(10.0, gDb / 40.0);
            double w0 = 2 * Math.PI * f0 / fs;
            double cosw = Math.cos(w0);
            double sinw = Math.sin(w0);
            double alpha = sinw / (2 * Q);

            double b0t = (1 + alpha * A);
            double b1t = -2 * cosw;
            double b2t = (1 - alpha * A);
            double a0t = (1 + alpha / A);
            double a1t = -2 * cosw;
            double a2t = (1 - alpha / A);

            b0 = b0t / a0t;
            b1 = b1t / a0t;
            b2 = b2t / a0t;
            a1 = a1t / a0t;
            a2 = a2t / a0t;
            z1 = 0;
            z2 = 0;
        }

        double next(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }
}