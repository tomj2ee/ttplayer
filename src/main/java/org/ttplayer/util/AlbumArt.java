package org.ttplayer.util;

import org.ttplayer.model.Song;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

public final class AlbumArt {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private AlbumArt() {}

    public static BufferedImage load(Song song) {
        if (song == null || song.filePath == null || Thread.currentThread().isInterrupted()) return null;
        File audio = new File(song.filePath);
        if (!audio.isFile()) return null;
        String stem = audio.getName().replaceFirst("\\.[^.]+$", "");
        File[] siblings = audio.getAbsoluteFile().getParentFile().listFiles();
        if (siblings != null) for (String name : new String[] {stem, "cover", "folder", "front", "album"}) {
            for (String extension : new String[] {"jpg", "jpeg", "png", "bmp"}) {
                if (Thread.currentThread().isInterrupted()) return null;
                for (File candidate : siblings) if (candidate.getName().equalsIgnoreCase(name + "." + extension) &&
                        candidate.isFile() && candidate.length() <= MAX_BYTES) {
                    try (InputStream input = new FileInputStream(candidate)) {
                        BufferedImage image = decode(input);
                        if (image != null) return image;
                    } catch (IOException ignored) {}
                }
            }
        }
        if (Thread.currentThread().isInterrupted()) return null;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audio)) {
            grabber.setOption("protocol_whitelist", "file");
            grabber.setOption("probesize", "2097152");
            grabber.setOption("analyzeduration", "1000000");
            grabber.start();
            AVFormatContext context = grabber.getFormatContext();
            for (int index = 0; index < context.nb_streams(); index++) {
                if (Thread.currentThread().isInterrupted()) return null;
                AVPacket packet = context.streams(index).attached_pic();
                int size = packet.size();
                if (size > 0 && size <= MAX_BYTES && packet.data() != null && !packet.data().isNull()) {
                    byte[] bytes = new byte[size]; packet.data().get(bytes);
                    BufferedImage image = decode(new ByteArrayInputStream(bytes));
                    if (image != null) return image;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static BufferedImage decode(InputStream input) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 16000000) return null;
                BufferedImage source = reader.read(0);
                int crop = Math.min(width, height), side = Math.min(crop, 512);
                BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, side, side, (width - crop) / 2, (height - crop) / 2,
                        (width + crop) / 2, (height + crop) / 2, null);
                graphics.dispose();
                return image;
            } finally { reader.dispose(); }
        }
    }
}
