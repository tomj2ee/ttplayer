package org.ttplayer.engine;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class VideoFrameBuffer {
    private final Java2DFrameConverter converter = new Java2DFrameConverter();
    private BufferedImage image;
    private int width;
    private int height;
    private double pixelAspect = 1;
    private double rotation;

    synchronized void configure(int width, int height, double pixelAspect, double rotation) {
        this.width = width;
        this.height = height;
        this.pixelAspect = Double.isFinite(pixelAspect) && pixelAspect > 0 ? pixelAspect : 1;
        this.rotation = Double.isFinite(rotation) ? Math.toRadians(-rotation) : 0;
        image = null;
    }

    synchronized void update(Frame frame) {
        image = converter.convert(frame);
    }

    synchronized void clear() {
        image = null;
    }

    public synchronized boolean hasFrame() {
        return image != null;
    }

    public synchronized int getDisplayWidth() {
        return Math.max(1, (int) Math.round(Math.abs(width * pixelAspect * Math.cos(rotation))
                + Math.abs(height * Math.sin(rotation))));
    }

    public synchronized int getDisplayHeight() {
        return Math.max(1, (int) Math.round(Math.abs(width * pixelAspect * Math.sin(rotation))
                + Math.abs(height * Math.cos(rotation))));
    }

    public synchronized void paint(Graphics2D graphics, int availableWidth, int availableHeight) {
        if (image == null || availableWidth <= 0 || availableHeight <= 0) return;
        double scale = Math.min((double) availableWidth / getDisplayWidth(),
                (double) availableHeight / getDisplayHeight());
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        canvas.translate(availableWidth / 2.0, availableHeight / 2.0);
        canvas.scale(scale, scale);
        canvas.rotate(rotation);
        canvas.scale(pixelAspect, 1);
        canvas.drawImage(image, -image.getWidth() / 2, -image.getHeight() / 2, null);
        canvas.dispose();
    }
}
