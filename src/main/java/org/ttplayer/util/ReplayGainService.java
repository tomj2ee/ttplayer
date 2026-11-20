package org.ttplayer.util;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

public final class ReplayGainService {
    private ReplayGainService() {}
    public static final class Result {
        public final Path path;
        public final String gain, peak;
        private final long size;
        private final FileTime modified;
        private Result(Path path, double gain, double peak, long size, FileTime modified) {
            this.path = path;
            this.gain = String.format(Locale.ROOT, "%+.2f dB", gain);
            this.peak = String.format(Locale.ROOT, "%.6f", peak);
            this.size = size;
            this.modified = modified;
        }
    }

    public static Result scan(Path path, BooleanSupplier cancelled) throws IOException {
        long size = Files.size(path);
        FileTime modified = Files.getLastModifiedTime(path);
        double squares = 0, peak = 0;
        long samples = 0;
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile())) {
            grabber.setSampleFormat(AV_SAMPLE_FMT_FLT);
            grabber.setSampleRate(44100);
            grabber.setAudioChannels(1);
            grabber.start();
            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                checkCancelled(cancelled);
                if (frame.samples == null || !(frame.samples[0] instanceof FloatBuffer)) continue;
                FloatBuffer buffer = ((FloatBuffer) frame.samples[0]).duplicate();
                while (buffer.hasRemaining()) {
                    double sample = buffer.get();
                    if (!Double.isFinite(sample)) continue;
                    squares += sample * sample;
                    peak = Math.max(peak, Math.abs(sample));
                    samples++;
                }
            }
        } catch (Exception error) { throw new IOException(error.getMessage(), error); }
        checkCancelled(cancelled);
        if (samples == 0) throw new IOException(Messages.get("replaygain.noSamples"));
        double rms = Math.sqrt(squares / samples);
        double gain = Math.max(-60, Math.min(60, -18 - 20 * Math.log10(Math.max(rms, 1e-9))));
        return new Result(path, gain, peak, size, modified);
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new InterruptedIOException("Cancelled");
    }

    private static void check(int result) throws IOException {
        if (result < 0) throw new IOException("FFmpeg: " + result);
    }

    private static AVDictionary metadata(AVDictionary source, Result gain) throws IOException {
        AVDictionary result = new AVDictionary((Pointer) null);
        check(av_dict_copy(result, source, 0));
        check(av_dict_set(result, "replaygain_track_gain", gain.gain, 0));
        check(av_dict_set(result, "replaygain_track_peak", gain.peak, 0));
        return result;
    }

    private static void unchanged(Result result) throws IOException {
        if (Files.size(result.path) != result.size || !Files.getLastModifiedTime(result.path).equals(result.modified))
            throw new IOException(Messages.get("replaygain.changed"));
    }

    public static void write(Result result, BooleanSupplier cancelled, BooleanSupplier playing) throws IOException {
        Path path = result.path.toAbsolutePath();
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path) || !Files.isWritable(path))
            throw new IOException(Messages.get("replaygain.readonly"));
        String filename = path.getFileName().toString();
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String[] extensions = {"mp3", "flac", "wav", "ogg", "opus", "m4a", "mp4", "aiff", "aif"};
        String[] muxers = {"mp3", "flac", "wav", "ogg", "opus", "ipod", "mp4", "aiff", "aiff"};
        int format = java.util.Arrays.asList(extensions).indexOf(extension);
        if (format < 0) throw new IOException(Messages.get("replaygain.readonly"));
        if (playing.getAsBoolean()) throw new IOException(Messages.get("replaygain.playing"));
        unchanged(result);
        if ("wav".equals(extension)) { writeWave(result, cancelled, playing); return; }
        Path temporary = Files.createTempFile(path.getParent(), ".ttplayer-gain-", "." + extension);
        AVFormatContext input = new AVFormatContext((Pointer) null);
        AVFormatContext output = new AVFormatContext((Pointer) null);
        AVIOContext stream = new AVIOContext((Pointer) null);
        AVPacket packet = null;
        AVDictionary options = new AVDictionary((Pointer) null);
        try {
            check(avformat_open_input(input, path.toString(), null, null));
            check(avformat_find_stream_info(input, (AVDictionary) null));
            if (input.nb_chapters() != 0) throw new IOException(Messages.get("replaygain.readonly"));
            check(avformat_alloc_output_context2(output, null, muxers[format], temporary.toString()));
            output.metadata(metadata(input.metadata(), result));
            for (int index = 0; index < input.nb_streams(); index++) {
                AVStream source = input.streams(index);
                if (source.codecpar().codec_type() != AVMEDIA_TYPE_AUDIO && (source.disposition() & AV_DISPOSITION_ATTACHED_PIC) == 0)
                    throw new IOException(Messages.get("replaygain.readonly"));
                AVStream destination = avformat_new_stream(output, null);
                if (destination == null || destination.isNull()) throw new IOException("FFmpeg: stream");
                check(avcodec_parameters_copy(destination.codecpar(), source.codecpar()));
                destination.codecpar().codec_tag(0);
                destination.time_base(source.time_base()).disposition(source.disposition()).id(source.id());
                if (source.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) destination.metadata(metadata(source.metadata(), result));
                else {
                    AVDictionary tags = new AVDictionary((Pointer) null);
                    check(av_dict_copy(tags, source.metadata(), 0));
                    destination.metadata(tags);
                }
            }
            check(avio_open(stream, temporary.toString(), AVIO_FLAG_WRITE));
            output.pb(stream);
            if ("mp3".equals(extension) || extension.startsWith("aif")) check(av_dict_set(options, "id3v2_version", "3", 0));
            if ("m4a".equals(extension) || "mp4".equals(extension)) check(av_dict_set(options, "movflags", "use_metadata_tags", 0));
            if ("wav".equals(extension) || extension.startsWith("aif")) check(av_dict_set(options, "write_id3v2", "1", 0));
            check(avformat_write_header(output, options));
            packet = av_packet_alloc();
            int read;
            while ((read = av_read_frame(input, packet)) >= 0) {
                checkCancelled(cancelled);
                AVStream source = input.streams(packet.stream_index());
                AVStream destination = output.streams(packet.stream_index());
                av_packet_rescale_ts(packet, source.time_base(), destination.time_base());
                packet.pos(-1);
                check(av_interleaved_write_frame(output, packet));
                av_packet_unref(packet);
            }
            if (read != AVERROR_EOF) check(read);
            check(av_write_trailer(output));
            avio_flush(stream);
            check(stream.error());
            check(avio_closep(stream));
            output.pb(null);
            avformat_close_input(input);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            verifyTags(temporary, result);
            checkCancelled(cancelled);
            if (playing.getAsBoolean()) throw new IOException(Messages.get("replaygain.playing"));
            unchanged(result);
            if (Files.getFileStore(path).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(path));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (packet != null) av_packet_free(packet);
            av_dict_free(options);
            if (!input.isNull()) avformat_close_input(input);
            if (!stream.isNull()) avio_closep(stream);
            if (!output.isNull()) { output.pb(null); avformat_free_context(output); }
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyTags(Path path, Result result) throws IOException {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(path.toFile())) {
            grabber.start();
            java.util.Map<String, String> tags = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            tags.putAll(grabber.getMetadata());
            tags.putAll(grabber.getAudioMetadata());
            if (!result.gain.equals(tags.get("replaygain_track_gain")) || !result.peak.equals(tags.get("replaygain_track_peak")))
                throw new IOException(Messages.get("replaygain.readonly"));
        } catch (Exception error) { throw new IOException(error.getMessage(), error); }
    }

    private static byte[] gainFrames(Result result) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        for (String[] tag : new String[][] {{"replaygain_track_gain", result.gain}, {"replaygain_track_peak", result.peak}}) {
            byte[] name = tag[0].getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            byte[] value = tag[1].getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            output.writeBytes("TXXX"); output.writeInt(name.length + value.length + 2); output.writeShort(0);
            output.writeByte(0); output.write(name); output.writeByte(0); output.write(value);
        }
        return bytes.toByteArray();
    }

    private static void writeWave(Result result, BooleanSupplier cancelled, BooleanSupplier playing) throws IOException {
        checkCancelled(cancelled);
        Path path = result.path.toAbsolutePath();
        if (result.size > 0xffffff00L) throw new IOException(Messages.get("replaygain.readonly"));
        Path temporary = Files.createTempFile(path.getParent(), ".ttplayer-gain-", ".wav");
        try {
            Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
            try (RandomAccessFile file = new RandomAccessFile(temporary.toFile(), "rw")) {
                if (file.length() < 12 || file.readInt() != 0x52494646) throw new IOException(Messages.get("replaygain.readonly"));
                long declared = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt())) + 8;
                if (declared != file.length() || file.readInt() != 0x57415645) throw new IOException(Messages.get("replaygain.readonly"));
                byte[] frames = gainFrames(result);
                int length = frames.length + 10;
                file.seek(file.length());
                file.writeBytes("id3 "); file.writeInt(Integer.reverseBytes(length));
                file.writeBytes("ID3"); file.write(new byte[] {3, 0, 0});
                for (int shift = 21; shift >= 0; shift -= 7) file.write((frames.length >> shift) & 0x7f);
                file.write(frames);
                if ((length & 1) != 0) file.write(0);
                long size = file.length() - 8;
                file.seek(4); file.writeInt(Integer.reverseBytes((int) size)); file.getFD().sync();
            }
            verifyTags(temporary, result);
            checkCancelled(cancelled);
            if (playing.getAsBoolean()) throw new IOException(Messages.get("replaygain.playing"));
            unchanged(result);
            if (Files.getFileStore(path).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(path));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
