package org.ttplayer.audio;

import org.ttplayer.util.UIUtils;
import org.w3c.dom.Element;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** Java port of the C visualizer. */
public class TtVisualizer extends JComponent {
    private enum Effect { BARS, ORIGINAL_SCOPE, ORIGINAL_GOOM, GOOM, MOTION }

    public enum Mode {
        BARS(30, Effect.BARS, 0),
        ORIGINAL_SCOPE(64, Effect.ORIGINAL_SCOPE, 0),
        ORIGINAL_GOOM(65, Effect.ORIGINAL_GOOM, 0),
        GOOM_PARTICLES_SCOPE(59, Effect.GOOM, 0),
        MOTION_32(41, Effect.MOTION, TtMotionSpectrum.BANDS_32),
        MOTION_GRAPH(44, Effect.MOTION, TtMotionSpectrum.GRAPH),
        MOTION_MIRROR(46, Effect.MOTION, TtMotionSpectrum.MIRROR);

        public final int id;
        private final Effect effect;
        private final int preset;

        Mode(int id, Effect effect, int preset) {
            this.id = id;
            this.effect = effect;
            this.preset = preset;
        }
    }

    private final TtSpectrum spectrum = new TtSpectrum();
    private final TtVisualStyle style = new TtVisualStyle();
    private final TtMotionSpectrum motion = new TtMotionSpectrum();
    private final TtOriginalScope originalScope = new TtOriginalScope();
    private final TtClassicSpectrum classic = new TtClassicSpectrum();
    private final TtClassicSpectrum.Column[] projected = new TtClassicSpectrum.Column[TtMotionSpectrum.MAX_BANDS];
    private final float[] peaks = new float[48];
    private final short[] scopePcm = new short[512];
    private final int[] palette = new int[256];
    private TtGoom2Visual goom;
    private TtOriginalGoom originalGoom;
    private Mode mode = Mode.BARS;
    private Mode renderedMode;
    private BufferedImage image;
    private int[] pixels;
    private int canvasWidth;
    private int canvasHeight;
    private float elapsedSeconds = 1f / 60f;
    private float frameScale = elapsedSeconds / 0.03f;
    private float feedbackSeconds;
    private float classicSeconds;
    private float originalSeconds;
    private long lastDataNanos;
    private long lastRenderNanos;

    public Color backgroundColor = new Color(16, 22, 30);
    public Color skinColorTop;
    public Color skinColorMid;
    public Color skinColorBtm;
    public Color skinColorPeak;
    public Color skinColorBlur;
    public boolean skinWide;
    public boolean skinBlur = true;
    public int skinBlurSpeed = 3;

    public TtVisualizer() {
        setOpaque(true);
        for (int index = 0; index < projected.length; index++) projected[index] = new TtClassicSpectrum.Column();
    }

    public void setMode(Mode nextMode) {
        mode = nextMode == null ? Mode.BARS : nextMode;
        repaint();
    }

    public Mode getMode() {
        return mode;
    }

    public void cycleMode() {
        Mode[] modes = Mode.values();
        setMode(modes[(mode.ordinal() + 1) % modes.length]);
    }

    public void updateData(byte[] pcmData) {
        long now = System.nanoTime();
        float elapsed = lastDataNanos == 0 ? 1f / 60f
                : clamp((now - lastDataNanos) / 1_000_000_000f, 0.001f, 0.1f);
        lastDataNanos = now;
        spectrum.analyze(pcmData, pcmData == null ? 0 : pcmData.length, elapsed);
        repaint();
    }

    public void reset() {
        spectrum.reset();
        resetRenderer();
        repaint();
    }

    public void clearDisplay() {
        spectrum.reset();
        resetRenderer();
        if (pixels != null) Arrays.fill(pixels, 0);
        repaint();
    }

    public void applyVisualSkin(Element visual) {
        if (visual == null) return;
        skinColorTop = UIUtils.getColorAttribute(visual, "SpectrumTopColor", skinColorTop);
        skinColorMid = UIUtils.getColorAttribute(visual, "SpectrumMidColor", skinColorMid);
        skinColorBtm = UIUtils.getColorAttribute(visual, "SpectrumBtmColor", skinColorBtm);
        skinColorPeak = UIUtils.getColorAttribute(visual, "SpectrumPeakColor", skinColorPeak);
        skinColorBlur = UIUtils.getColorAttribute(visual, "BlurScopeColor", skinColorBlur);
        skinWide = "1".equals(UIUtils.getAttribute(visual, "SpectrumWide", skinWide ? "1" : "0").trim());
        skinBlur = "1".equals(UIUtils.getAttribute(visual, "Blur", skinBlur ? "1" : "0").trim());
        try {
            skinBlurSpeed = Integer.parseInt(UIUtils.getAttribute(
                    visual, "BlurSpeed", String.valueOf(skinBlurSpeed)).trim());
        } catch (NumberFormatException ignored) {
        }
        style.top = skinColorTop != null ? skinColorTop : new Color(18, 114, 175);
        style.middle = skinColorMid != null ? skinColorMid : new Color(40, 143, 189);
        style.bottom = skinColorBtm != null ? skinColorBtm : new Color(16, 215, 255);
        style.peak = skinColorPeak != null ? skinColorPeak : Color.WHITE;
        style.scope = skinColorBlur != null ? skinColorBlur : Color.CYAN;
        style.wide = skinWide;
        style.blur = skinBlur;
        style.blurSpeed = Math.max(1, Math.min(10, skinBlurSpeed));
        renderedMode = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        ensureImage(width, height);
        long now = System.nanoTime();
        elapsedSeconds = lastRenderNanos == 0 ? 1f / 60f
                : clamp((now - lastRenderNanos) / 1_000_000_000f, 0.001f, 0.1f);
        lastRenderNanos = now;
        frameScale = elapsedSeconds / 0.03f;
        render();
        graphics.setColor(backgroundColor);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(image, 0, 0, null);
    }

    private void ensureImage(int width, int height) {
        if (image != null && canvasWidth == width && canvasHeight == height) return;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        canvasWidth = width;
        canvasHeight = height;
        renderedMode = null;
        Arrays.fill(peaks, 0f);
    }

    private void resetRenderer() {
        motion.reset();
        originalScope.reset();
        classic.reset();
        goom = null;
        originalGoom = null;
        renderedMode = null;
        classicSeconds = originalSeconds = feedbackSeconds = 0f;
        lastDataNanos = lastRenderNanos = 0L;
        Arrays.fill(peaks, 0f);
        if (pixels != null) Arrays.fill(pixels, 0);
    }

    private void render() {
        boolean modeChanged = renderedMode != mode;
        if (modeChanged) {
            if (mode.effect != Effect.GOOM) goom = null;
            if (mode.effect != Effect.ORIGINAL_GOOM) originalGoom = null;
            if (mode.effect != Effect.ORIGINAL_SCOPE) originalScope.reset();
        }
        if (mode.effect == Effect.GOOM) {
            if (modeChanged) feedbackSeconds = 0f;
            else {
                feedbackSeconds += elapsedSeconds;
                if (feedbackSeconds < 0.03f) return;
                feedbackSeconds %= 0.03f;
            }
            frameScale = 1f;
        }
        switch (mode.effect) {
            case BARS:
                drawOriginalBars(TtMotionSpectrum.PRESET_COUNT);
                break;
            case ORIGINAL_SCOPE:
                drawOriginalScope();
                break;
            case ORIGINAL_GOOM:
                drawOriginalGoom();
                break;
            case GOOM:
                drawGoom();
                break;
            case MOTION:
                drawMotion(mode.preset);
                break;
            default:
                break;
        }
        renderedMode = mode;
    }

    private void drawMotion(int preset) {
        if (renderedMode != mode) motion.reset();
        if (preset != TtMotionSpectrum.GRAPH) drawOriginalBars(preset);
        else motion.render(pixels, canvasWidth, canvasHeight, spectrum.bars(), preset, style, elapsedSeconds);
    }

    private int originalSteps() {
        if (renderedMode != mode) originalSeconds = 0.04f;
        else originalSeconds += elapsedSeconds;
        int steps = 0;
        while (originalSeconds + 0.000001f >= 0.04f) {
            originalSeconds = Math.max(0f, originalSeconds - 0.04f);
            steps++;
        }
        return steps;
    }

    private void drawOriginalBars(int preset) {
        boolean fine = preset == TtMotionSpectrum.PRESET_COUNT;
        boolean mirror = preset == TtMotionSpectrum.MIRROR;
        int span = canvasHeight;
        if (renderedMode != mode) {
            classic.reset();
            classicSeconds = 0.04f;
        } else {
            classicSeconds += elapsedSeconds;
        }
        boolean updated = false;
        while (classicSeconds + 0.000001f >= 0.04f) {
            classic.step(canvasWidth, span, spectrum.classicBins());
            classicSeconds = Math.max(0f, classicSeconds - 0.04f);
            updated = true;
        }
        if (!updated) return;
        Arrays.fill(pixels, 0);
        int frequencyWidth = mirror ? (canvasWidth + 1) / 2 : canvasWidth;
        int bands = Math.min(32, Math.min(frequencyWidth, classic.count));
        for (TtClassicSpectrum.Column column : projected) {
            column.height = column.age = column.peak = column.previousPeak = 0;
        }
        if (!fine) {
            for (int band = 0; band < bands; band++) {
                int first = band * classic.count / bands;
                int end = (band + 1) * classic.count / bands;
                for (int source = first; source < end; source++) {
                    TtClassicSpectrum.Column column = classic.columns[source];
                    projected[band].height = Math.max(projected[band].height, column.height);
                    projected[band].peak = Math.max(projected[band].peak, column.peak);
                    projected[band].previousPeak = Math.max(projected[band].previousPeak, column.previousPeak);
                }
            }
        }
        int columns = fine ? classic.count : canvasWidth;
        for (int index = 0; index < columns; index++) {
            if (fine && style.wide && index % 4 == 3) continue;
            TtClassicSpectrum.Column column;
            if (fine) {
                column = classic.columns[index];
            } else {
                int frequencyColumn = mirror
                        ? (index < frequencyWidth ? frequencyWidth - 1 - index : index - canvasWidth / 2)
                        : index;
                int band = frequencyColumn * bands / frequencyWidth;
                int first = (band * frequencyWidth + bands - 1) / bands;
                int end = ((band + 1) * frequencyWidth + bands - 1) / bands;
                if (end - first >= 3 && frequencyColumn == end - 1) continue;
                column = projected[band];
            }
            drawClassicColumn(index, span, column);
        }
    }

    private void drawClassicColumn(int columnIndex, int span, TtClassicSpectrum.Column column) {
        for (int row = span - column.height; row < span; row++) {
            put(columnIndex, row, classicGradient(row, span));
        }
        int peakRow = span - column.peak - 1;
        if (peakRow > 0 && peakRow < span) put(columnIndex, peakRow, pack(style.peak));
        if (!style.wide && column.peak < column.previousPeak) {
            for (int offset = 1; offset <= 3 && peakRow - offset > 0; offset++) {
                int alpha = (4 - offset) * 255 / 4;
                put(columnIndex, peakRow - offset, alpha << 24 | pack(style.peak) & 0x00ffffff);
            }
        }
    }

    private int classicGradient(int row, int height) {
        int split = height * 3 / 5;
        return row < split
                ? blend(style.top, style.middle, (float) row / Math.max(1, split))
                : blend(style.middle, style.bottom, (float) (row - split) / Math.max(1, height - split));
    }

    private void put(int left, int top, int color) {
        if (left >= 0 && left < canvasWidth && top >= 0 && top < canvasHeight) {
            pixels[top * canvasWidth + left] = color;
        }
    }

    private static int pack(Color color) {
        return color.getAlpha() << 24 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
    }

    private static int blend(Color first, Color second, float ratio) {
        int red = (int) (first.getRed() + (second.getRed() - first.getRed()) * ratio);
        int green = (int) (first.getGreen() + (second.getGreen() - first.getGreen()) * ratio);
        int blue = (int) (first.getBlue() + (second.getBlue() - first.getBlue()) * ratio);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private void drawOriginalScope() {
        if (renderedMode != mode) originalScope.reset();
        int width = Math.min(512, canvasWidth);
        int height = Math.min(384, canvasHeight);
        short[][] voice = spectrum.voice();
        for (int frame = 0; frame < 512; frame++) {
            scopePcm[frame] = (short) (((int) voice[0][frame] + (int) voice[1][frame]) / 2);
        }
        int steps = originalSteps();
        for (int step = 0; step < steps; step++) {
            originalScope.step(width, height, scopePcm, style.blur, Math.max(0, Math.min(255, style.blurSpeed)));
        }
        Color color = style.scope.getAlpha() != 0 ? style.scope : style.bottom;
        int stride = width + 2;
        int rgb = pack(color) & 0x00ffffff;
        for (int row = 0; row < canvasHeight; row++) {
            int sourceRow = height - row * height / canvasHeight;
            for (int column = 0; column < canvasWidth; column++) {
                int sourceColumn = 1 + column * width / canvasWidth;
                int level = originalScope.levels[sourceRow * stride + sourceColumn] & 0xff;
                int alpha = level == 0 ? 0 : level < 255 ? level + 1 : 255;
                pixels[row * canvasWidth + column] = alpha == 0 ? 0 : alpha << 24 | rgb;
            }
        }
    }

    private void drawOriginalGoom() {
        if (renderedMode != mode) originalGoom = null;
        if (originalGoom == null) originalGoom = new TtOriginalGoom();
        if (renderedMode != mode) Arrays.fill(pixels, 0);
        int steps = originalSteps();
        for (int step = 0; step < steps; step++) {
            originalGoom.render(pixels, canvasWidth, canvasHeight, spectrum.voice());
        }
    }

    private void drawGoom() {
        if (renderedMode != mode) goom = null;
        if (goom == null) goom = new TtGoom2Visual();
        goom.render(pixels, canvasWidth, canvasHeight, spectrum.bars(), spectrum.waveform());
        buildGoomPalette();
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            int brightness = (54 * (pixel >>> 16 & 255) + 183 * (pixel >>> 8 & 255) + 19 * (pixel & 255)) >> 8;
            pixels[index] = palette[brightness];
        }
    }

    private void buildGoomPalette() {
        Color[] anchors = {Color.BLACK, style.bottom, style.middle, style.top, style.peak};
        for (int index = 0; index < palette.length; index++) {
            int segment = index / 64;
            float span = segment == 3 ? 63f : 64f;
            palette[index] = blend(anchors[segment], anchors[segment + 1], (index - segment * 64) / span);
        }
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
