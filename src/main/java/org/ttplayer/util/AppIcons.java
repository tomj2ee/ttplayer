package org.ttplayer.util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppIcons {
    private static final List<BufferedImage> IMAGES = load();

    private AppIcons() {}

    public static List<Image> getWindowImages() {
        return new ArrayList<Image>(IMAGES);
    }

    public static BufferedImage getLargestImage() {
        return IMAGES.isEmpty() ? null : IMAGES.get(IMAGES.size() - 1);
    }

    public static BufferedImage getImage(int size) {
        int target = Math.max(1, Math.min(1024, size));
        BufferedImage source = getLargestImage();
        if (source == null) return null;
        for (BufferedImage candidate : IMAGES) {
            if (candidate.getWidth() >= target) {
                source = candidate;
                break;
            }
        }
        if (source.getWidth() == target && source.getHeight() == target) return source;
        BufferedImage image = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setComposite(AlphaComposite.Src);
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvas.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        canvas.drawImage(source, 0, 0, target, target, null);
        canvas.dispose();
        return image;
    }

    public static BufferedImage getTrayImage(Dimension size) {
        int width = Math.max(1, Math.min(1024, size.width));
        int height = Math.max(1, Math.min(1024, size.height));
        BufferedImage icon = getImage(Math.min(width, height));
        if (icon == null || width == height) return icon;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setComposite(AlphaComposite.Src);
        canvas.drawImage(icon, (width - icon.getWidth()) / 2, (height - icon.getHeight()) / 2, null);
        canvas.dispose();
        return image;
    }

    private static List<BufferedImage> load() {
        List<BufferedImage> images = new ArrayList<>();
        for (int size : new int[]{16, 32, 48, 64, 128, 256, 512, 1024}) {
            String path = "/ico/ttplayer_" + size + "x" + size + "_32bpp.png";
            try (InputStream stream = AppIcons.class.getResourceAsStream(path)) {
                if (stream == null) continue;
                BufferedImage image = ImageIO.read(stream);
                if (image != null && image.getWidth() == size && image.getHeight() == size) images.add(image);
            } catch (IOException ignored) {}
        }
        return Collections.unmodifiableList(images);
    }
}
