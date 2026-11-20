package org.ttplayer.engine;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.File;

import static org.bytedeco.ffmpeg.global.avcodec.av_get_exact_bits_per_sample;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_get_name;

public final class AudioSourceInfo {
    public static final AudioSourceInfo EMPTY = new AudioSourceInfo(0, 0, 0, 0, 0, "");

    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;
    public final long bitRate;
    public final long fileSize;
    public final String codec;

    public AudioSourceInfo(int sampleRate, int channels, int bitsPerSample, long bitRate, long fileSize, String codec) {
        this.sampleRate = Math.max(0, sampleRate);
        this.channels = Math.max(0, channels);
        this.bitsPerSample = Math.max(0, bitsPerSample);
        this.bitRate = Math.max(0, bitRate);
        this.fileSize = Math.max(0, fileSize);
        this.codec = codec == null ? "" : codec;
    }

    public static AudioSourceInfo read(FFmpegFrameGrabber grabber, File file) {
        AVFormatContext context = grabber.getFormatContext();
        int streamIndex = grabber.getAudioStream();
        if (context == null || context.isNull() || streamIndex < 0 || streamIndex >= context.nb_streams()) return EMPTY;
        AVCodecParameters parameters = context.streams(streamIndex).codecpar();
        int bits = parameters.bits_per_raw_sample();
        if (bits <= 0) bits = av_get_exact_bits_per_sample(parameters.codec_id());
        long bitrate = parameters.bit_rate() > 0 ? parameters.bit_rate() : context.bit_rate();
        return new AudioSourceInfo(parameters.sample_rate(), parameters.ch_layout().nb_channels(), bits, bitrate,
                file != null && file.isFile() ? file.length() : 0, avcodec_get_name(parameters.codec_id()).getString());
    }
}
