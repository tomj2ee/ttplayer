package org.ttplayer.util;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avformat.AV_DISPOSITION_ATTACHED_PIC;

public final class MediaFileValidator {
    private static final Pattern URL = Pattern.compile("^[a-z][a-z0-9+.-]*://", Pattern.CASE_INSENSITIVE);

    private MediaFileValidator() {}

    public static boolean isInvalidLocalSource(String source) throws IOException {
        if (source == null || source.trim().isEmpty()) return true;
        if (URL.matcher(source.trim()).find()) return false;

        Path path;
        try {
            path = Paths.get(source);
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) return true;
            if (!Files.isReadable(path)) return false;
            if (attributes.size() == 0) return true;
        } catch (InvalidPathException | NoSuchFileException | NotDirectoryException error) {
            return true;
        } catch (IOException | SecurityException error) {
            return false;
        }

        FFmpegFrameGrabber.tryLoad();
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile())) {
            grabber.setOption("protocol_whitelist", "file");
            try {
                grabber.start();
                boolean video = grabber.hasVideo() && (grabber.getFormatContext().streams(grabber.getVideoStream())
                        .disposition() & AV_DISPOSITION_ATTACHED_PIC) == 0;
                if (video) return grabber.grabImage() == null;
                return !grabber.hasAudio() || grabber.grabSamples() == null;
            } catch (FFmpegFrameGrabber.Exception error) {
                return Files.isReadable(path) || Files.notExists(path);
            }
        }
    }
}
