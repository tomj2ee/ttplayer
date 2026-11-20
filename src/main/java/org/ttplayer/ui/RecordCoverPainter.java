package org.ttplayer.ui;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

final class RecordCoverPainter {
    private RecordCoverPainter() {}

    static Rectangle2D.Float place(Rectangle2D.Float bounds, boolean hasLyrics, Rectangle2D.Float text) {
        text.setRect(bounds);
        float width = bounds.width, height = bounds.height;
        if (width < 100 || height < 100) return null;
        float side;
        if (!hasLyrics) {
            side = Math.min(width * 0.96f, (height - 32) * 0.96f);
            text.setRect(bounds.x, bounds.y + height - 32, width, 32);
            return new Rectangle2D.Float(bounds.x + (width - side) / 2, bounds.y + (height - 32 - side) / 2, side, side);
        }
        if (height >= 240 && height >= width * 0.75f) {
            side = Math.min(width * 0.74f, height * 0.48f);
            text.setRect(bounds.x, bounds.y + 28 + side + 16, width, height - 28 - side - 16);
            return new Rectangle2D.Float(bounds.x + (width - side) / 2, bounds.y + 28, side, side);
        }
        side = Math.min(height * 0.72f, width * 0.38f);
        text.setRect(bounds.x + side + 24, bounds.y, width - side - 24, height);
        return new Rectangle2D.Float(bounds.x + 8, bounds.y + (height - side) / 2, side, side);
    }

    static Ellipse2D.Float recordBounds(Rectangle2D.Float stage) {
        return new Ellipse2D.Float(stage.x + stage.width * 0.04f, stage.y + stage.width * 0.07f,
                stage.width * 0.92f, stage.width * 0.92f);
    }

    static void paint(Graphics2D graphics, Rectangle2D.Float stage, BufferedImage image,
                      float rotation, float engaged, boolean playing, boolean hovered) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        Ellipse2D.Float cover = recordBounds(stage);
        float side = cover.width, radius = side / 2;
        float centerX = cover.x + radius, centerY = cover.y + radius;
        canvas.setPaint(new RadialGradientPaint(centerX, centerY, radius * 1.07f, new float[] {0, 0.925f, 1},
                new Color[] {new Color(0, true), new Color(166, 173, 186, 31), new Color(0, true)}));
        disk(canvas, centerX, centerY, radius * 1.07f);
        canvas.setColor(new Color(0x292b30));
        disk(canvas, centerX, centerY, radius);
        canvas.setColor(new Color(0x0c0d0f));
        disk(canvas, centerX, centerY, Math.max(0, radius - 1.2f));
        for (int sector = 0; sector < 192; sector++) {
            double angle = sector * Math.PI * 2 / 192;
            double reflection = Math.cos(angle + 0.72);
            double light = Math.pow(Math.abs(reflection), 12) * (reflection > 0 ? 0.068 : 0.038) +
                    Math.pow(Math.abs(Math.cos(angle + 0.48)), 54) * 0.012;
            canvas.setColor(new Color(255, 255, 255, (int) (light * 255)));
            canvas.fill(new Arc2D.Float(cover.x, cover.y, side, side, (float) (-angle * 180 / Math.PI), -1.9f, Arc2D.PIE));
        }
        ring(canvas, centerX, centerY, radius * 0.966f, Math.max(0.5f, side * 0.002f), new Color(41, 43, 48, 153));
        for (int groove = 0; groove < 40; groove++) {
            ring(canvas, centerX, centerY, radius * (0.95f - groove * 0.0072f),
                    Math.max(0.42f, side * 0.0011f), new Color(36, 38, 43, groove % 5 == 0 ? 128 : 66));
        }
        canvas.setColor(new Color(0x040506));
        disk(canvas, centerX, centerY, side * 0.316f);
        ring(canvas, centerX, centerY, side * 0.31f, side * 0.003f, new Color(64, 66, 71, 148));
        Graphics2D label = (Graphics2D) canvas.create();
        label.translate(centerX, centerY);
        label.rotate(rotation * Math.PI * 2);
        float labelSize = side * 0.61f;
        if (image != null) {
            label.clip(new Ellipse2D.Float(-labelSize / 2, -labelSize / 2, labelSize, labelSize));
            label.drawImage(image, Math.round(-labelSize / 2), Math.round(-labelSize / 2),
                    Math.round(labelSize), Math.round(labelSize), null);
        } else {
            label.setPaint(new RadialGradientPaint(0, 0, side * 0.303f, new float[] {0, 1},
                    new Color[] {new Color(0x572129), new Color(0x331318)}));
            disk(label, 0, 0, side * 0.303f);
            ring(label, 0, 0, side * 0.2845f, side * 0.001f, new Color(191, 115, 117, 61));
            Color ink = new Color(0xd1abad);
            label.setColor(ink);
            disk(label, -side * 0.052f, side * 0.052f, side * 0.025f);
            disk(label, side * 0.052f, side * 0.031f, side * 0.025f);
            line(label, -side * 0.036f, side * 0.052f, -side * 0.036f, -side * 0.063f, side * 0.011f, ink);
            line(label, side * 0.068f, side * 0.031f, side * 0.068f, -side * 0.084f, side * 0.011f, ink);
            line(label, -side * 0.036f, -side * 0.063f, side * 0.068f, -side * 0.084f, side * 0.019f, ink);
        }
        label.dispose();
        canvas.setColor(new Color(15, 17, 19, 230));
        disk(canvas, centerX, centerY, Math.max(2, side * 0.008f));
        canvas.setColor(new Color(0xb5bac4));
        disk(canvas, centerX, centerY, Math.max(0.8f, side * 0.003f));
        tonearm(canvas, stage, engaged);
        if (!playing || hovered) {
            float button = Math.max(12, Math.min(26, side * 0.068f));
            canvas.setColor(new Color(6, 8, 10, 158));
            disk(canvas, centerX, centerY, button);
            ring(canvas, centerX, centerY, button, 0.7f, new Color(255, 255, 255, 184));
            canvas.setColor(new Color(255, 255, 255, 242));
            if (playing) {
                for (int bar = 0; bar < 2; bar++) {
                    float position = centerX + (bar == 0 ? -0.19f : 0.19f) * button;
                    line(canvas, position, centerY - button * 0.32f, position, centerY + button * 0.32f,
                            button * 0.14f, canvas.getColor());
                }
            } else {
                Path2D.Float triangle = new Path2D.Float();
                triangle.moveTo(centerX - button * 0.2f, centerY - button * 0.35f);
                triangle.lineTo(centerX + button * 0.38f, centerY);
                triangle.lineTo(centerX - button * 0.2f, centerY + button * 0.35f);
                triangle.closePath();
                canvas.fill(triangle);
            }
        }
        canvas.dispose();
    }

    private static void tonearm(Graphics2D graphics, Rectangle2D.Float stage, float engaged) {
        Graphics2D arm = (Graphics2D) graphics.create();
        float side = stage.width;
        arm.translate(stage.x + side * 0.56f, stage.y + side * 0.035f);
        arm.rotate(-0.66 * (1 - engaged));
        float[] positions = {0, 0, 0, 0.075f, 0.135f, 0.24f, 0.18f, 0.29f};
        float width = Math.max(1.8f, side * 0.01f);
        float[] widths = {width + 3, width, width * 0.72f, width * 0.23f};
        Color[] colors = {new Color(0, 0, 0, 71), new Color(0x52575e), new Color(0xbabfc7), new Color(250, 250, 252, 230)};
        for (int layer = 0; layer < 4; layer++) {
            float offsetX = layer == 0 ? 2 : layer == 3 ? -width * 0.18f : 0;
            float offsetY = layer == 0 ? 3 : 0;
            for (int point = 0; point < 6; point += 2)
                line(arm, positions[point] * side + offsetX, positions[point + 1] * side + offsetY,
                        positions[point + 2] * side + offsetX, positions[point + 3] * side + offsetY, widths[layer], colors[layer]);
        }
        line(arm, side * 0.18f, side * 0.29f, side * 0.211f, side * 0.33f, side * 0.033f, new Color(0x212429));
        line(arm, side * 0.18f, side * 0.29f, side * 0.211f, side * 0.33f, side * 0.023f, new Color(0x575c63));
        line(arm, side * 0.211f, side * 0.33f, side * 0.217f, side * 0.339f, side * 0.017f, new Color(0xb82930));
        arm.dispose();
        float pivotX = stage.x + side * 0.56f, pivotY = stage.y + side * 0.035f;
        graphics.setColor(new Color(0x7a8085));
        disk(graphics, pivotX, pivotY, side * 0.027f);
        graphics.setColor(new Color(0x1a1c21));
        disk(graphics, pivotX, pivotY, side * 0.02f);
        ring(graphics, pivotX, pivotY, side * 0.0145f, side * 0.003f, new Color(184, 189, 196, 230));
        graphics.setColor(new Color(0xbdc2c9));
        disk(graphics, pivotX, pivotY, side * 0.006f);
    }

    private static void disk(Graphics2D graphics, float centerX, float centerY, float radius) {
        graphics.fill(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    private static void ring(Graphics2D graphics, float centerX, float centerY, float radius, float width, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(0.1f, width)));
        graphics.draw(new Ellipse2D.Float(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    private static void line(Graphics2D graphics, float fromX, float fromY, float toX, float toY, float width, Color color) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(Math.max(0.1f, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(new Line2D.Float(fromX, fromY, toX, toY));
    }
}
