package org.ttplayer.engine;

import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVReplayGain;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import java.util.Map;
import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_REPLAYGAIN;
import static org.bytedeco.ffmpeg.global.avformat.av_stream_get_side_data;

public final class TrackReplayGain {
    public double decibels = Double.NaN, peak = Double.NaN;

    public boolean isTagged() { return Double.isFinite(decibels); }

    public static TrackReplayGain read(FFmpegFrameGrabber grabber) {
        TrackReplayGain gain = new TrackReplayGain();
        gain.readTags(grabber.getMetadata());
        gain.readTags(grabber.getAudioMetadata());
        int streamIndex = grabber.getAudioStream();
        if (streamIndex >= 0 && streamIndex < grabber.getFormatContext().nb_streams()) {
            AVStream stream = grabber.getFormatContext().streams(streamIndex);
            try (SizeTPointer size = new SizeTPointer(1)) {
                BytePointer data = av_stream_get_side_data(stream, AV_PKT_DATA_REPLAYGAIN, size);
                if (data != null && !data.isNull() && size.get() >= 16) {
                    AVReplayGain tags = new AVReplayGain(data);
                    if (tags.track_gain() != Integer.MIN_VALUE) gain.decibels = tags.track_gain() / 100000.0;
                    if (tags.track_peak() != 0) gain.peak = Integer.toUnsignedLong(tags.track_peak()) / 100000.0;
                }
            }
        }
        return gain;
    }

    private void readTags(Map<String, String> tags) {
        if (tags == null) return;
        for (Map.Entry<String, String> tag : tags.entrySet()) {
            try {
                if ("replaygain_track_gain".equalsIgnoreCase(tag.getKey()))
                    decibels = Double.parseDouble(tag.getValue().replaceAll("(?i)\\s*dB\\s*$", "").trim());
                if ("replaygain_track_peak".equalsIgnoreCase(tag.getKey())) peak = Double.parseDouble(tag.getValue().trim());
            } catch (NumberFormatException ignored) {}
        }
    }
}
