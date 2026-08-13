package org.ttplayer.audio;

import java.util.Arrays;

/**
 * 实时频谱 FFT — 移植自 c_ttplayer 的 player_window_render_viz.c / mini_window_viz.c。
 *
 * C 版使用 FFmpeg 的 av_tx(AV_TX_FLOAT_RDFT) 做实输入 FFT，输出前 N/2 个频点幅度。
 * Java 端没有 av_tx 绑定，这里用 radix-2 复数迭代 FFT 实现相同数学：Hann 窗 →
 * RDFT → {re,im} 复数对排列 → sqrt(re²+im²) 幅度谱。与 AV_TX_FLOAT_RDFT 频谱
 * 输出一致（out[0]=DC、out[1]=Nyquist，之后每 2 个 float 一组复数）。
 */
public class TtRdft {

    public static final int FFT_N = 1024;

    private final float[] hann = new float[FFT_N];
    private final double[] re = new double[FFT_N];
    private final double[] im = new double[FFT_N];
    private final float[] out = new float[FFT_N * 2];
    private final float[] mag = new float[FFT_N / 2];

    public TtRdft() {
        for (int i = 0; i < FFT_N; i++) {
            hann[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_N - 1)));
        }
    }

    /**
     * 计算 FFT 幅度谱（前 FFT_N/2 个 bin）。
     * 对应 C: compute_spectrum() / mini_compute_spectrum()。
     * @param pcm s16le 字节流
     * @param bytes 有效字节数
     * @return 长度 FFT_N/2 的幅度数组
     */
    public float[] spectrum(byte[] pcm, int bytes) {
        int got = Math.min(bytes / 2, pcm.length / 2);
        int start = got - FFT_N;
        if (start < 0) start = 0;

        Arrays.fill(re, 0);
        for (int i = 0; i < FFT_N; i++) {
            int idx = start + i;
            if (idx < got) {
                int off = idx * 2;
                short s = (short) ((pcm[off + 1] << 8) | (pcm[off] & 0xFF));
                re[i] = (s / 32768.0) * hann[i];
            }
        }
        Arrays.fill(im, 0);

        // 复数 FFT（数学等价 av_tx float RDFT 的正变换）
        fft(re, im);

        // 按 av_tx float RDFT 输出重排：out[0]=re0, out[1]=reN/2, 之后 out[2j]=re_j, out[2j+1]=im_j
        out[0] = (float) re[0];
        out[1] = (float) re[FFT_N / 2];
        for (int j = 1; j < FFT_N / 2; j++) {
            out[2 * j] = (float) re[j];
            out[2 * j + 1] = (float) im[j];
        }

        for (int i = 0; i < FFT_N / 2; i++) {
            float r = out[2 * i];
            float c = out[2 * i + 1];
            mag[i] = (float) Math.sqrt(r * r + c * c);
        }
        return mag;
    }

    /** 原 TtFFT 的 radix-2 迭代复数 FFT */
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
            for (int i = 0; i < n; i += len) {
                double cwr = 1, cwi = 0;
                int halfLen = len >> 1;
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