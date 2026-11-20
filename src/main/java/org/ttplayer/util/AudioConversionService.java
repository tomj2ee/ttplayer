package org.ttplayer.util;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static org.bytedeco.ffmpeg.global.avutil.*;

public final class AudioConversionService {
    private AudioConversionService() {}

    public static final class Progress {
        public final int completed, total;
        public final String filename;
        public Progress(int completed, int total, String filename) {
            this.completed = completed; this.total = total; this.filename = filename;
        }
    }

    public static final class Result {
        public int succeeded, skipped, failed;
        public boolean cancelled;
        public String error = "";
    }

    public static Result convert(List<Path> files, ConversionSettings settings, BooleanSupplier cancelled, Consumer<Progress> progress) {
        Result result = new Result();
        for (int index = 0; index < files.size(); index++) {
            if (cancelled.getAsBoolean()) { result.cancelled = true; break; }
            Path source = files.get(index).toAbsolutePath().normalize();
            progress.accept(new Progress(index, files.size(), source.getFileName().toString()));
            try {
                Path output = destination(source, settings);
                if (settings.skipExisting && Files.exists(output)) result.skipped++;
                else { convertOne(source, output, settings, cancelled); result.succeeded++; }
            } catch (Exception error) {
                if (cancelled.getAsBoolean()) { result.cancelled = true; break; }
                result.failed++;
                result.error = source.getFileName() + ": " + error.getMessage();
            }
            progress.accept(new Progress(index + 1, files.size(), source.getFileName().toString()));
        }
        return result;
    }

    public static Path destination(Path source, ConversionSettings settings) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        Path parent = settings.directory.trim().isEmpty() ? source.toAbsolutePath().getParent() : Paths.get(settings.directory).toAbsolutePath();
        return parent.resolve(name + "." + settings.format.extension).normalize();
    }

    public static void convertOne(Path source, Path output, ConversionSettings settings, BooleanSupplier cancelled) throws Exception {
        source = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IOException(Messages.get("options.conversion.sourceMissing"));
        if (source.equals(output) || Files.exists(output) && Files.isSameFile(source, output))
            throw new IOException(Messages.get("options.conversion.sameSource"));
        if (Files.isSymbolicLink(output)) throw new IOException(Messages.get("options.conversion.unsafeOutput"));
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".ttplayer-convert-", "." + settings.format.extension);
        try {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(source.toFile())) {
                grabber.start();
                if (grabber.getAudioChannels() <= 0) throw new IOException(Messages.get("options.conversion.noAudio"));
                int channels = Math.max(1, Math.min(2, grabber.getAudioChannels()));
                int rate = settings.format == ConversionSettings.Format.OPUS ? 48000 : Math.max(8000, grabber.getSampleRate());
                if (settings.format == ConversionSettings.Format.AAC || settings.format == ConversionSettings.Format.M4A)
                    rate = Math.min(96000, rate);
                try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(temporary.toFile(), channels)) {
                    recorder.setFormat(settings.format.muxer);
                    recorder.setAudioCodecName(settings.format.codec);
                    recorder.setAudioChannels(channels);
                    recorder.setSampleRate(rate);
                    recorder.setSampleFormat(settings.format.isLossless() ? AV_SAMPLE_FMT_S16 : AV_SAMPLE_FMT_FLTP);
                    if (settings.format == ConversionSettings.Format.OPUS) recorder.setSampleFormat(AV_SAMPLE_FMT_FLT);
                    recorder.setAudioBitrate(Math.max(32, Math.min(320, settings.quality)) * 1000);
                    if (settings.format == ConversionSettings.Format.OGG) recorder.setAudioOption("strict", "-2");
                    recorder.setMetadata(grabber.getMetadata());
                    recorder.setAudioMetadata(grabber.getAudioMetadata());
                    recorder.start();
                    Frame frame;
                    while ((frame = grabber.grabSamples()) != null) {
                        if (cancelled.getAsBoolean()) throw new InterruptedException();
                        if (frame.samples != null) recorder.recordSamples(grabber.getSampleRate(), grabber.getAudioChannels(), frame.samples);
                    }
                    recorder.stop();
                }
            }
            if (cancelled.getAsBoolean()) throw new InterruptedException();
            if (settings.skipExisting) Files.move(temporary, output);
            else Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
