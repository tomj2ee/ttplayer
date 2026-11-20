package org.ttplayer.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConversionSettings {
    public enum Format {
        FLAC("flac", "flac", "flac"), WAV("wav", "wav", "pcm_s16le"),
        OGG("ogg", "ogg", "vorbis"), OPUS("opus", "opus", "libopus"),
        M4A("m4a", "ipod", "aac"), AAC("aac", "adts", "aac");
        public final String extension, muxer, codec;
        Format(String extension, String muxer, String codec) {
            this.extension = extension; this.muxer = muxer; this.codec = codec;
        }
        public boolean isLossless() { return this == FLAC || this == WAV; }
        @Override public String toString() { return Messages.get("options.conversion.format." + name().toLowerCase(java.util.Locale.ROOT)); }
    }

    public Format format = Format.FLAC;
    public int quality = 192;
    public boolean skipExisting = true;
    public String directory = "";

    public static ConversionSettings load() {
        ConversionSettings settings = new ConversionSettings();
        try { settings.format = Format.valueOf(MenuSettings.get("conversion.format", "FLAC")); }
        catch (IllegalArgumentException ignored) {}
        settings.quality = Math.max(32, Math.min(320, MenuSettings.number("conversion.quality", 192)));
        settings.skipExisting = MenuSettings.flag("conversion.skipExisting", true);
        settings.directory = MenuSettings.get("conversion.directory", "");
        return settings;
    }

    public void save() {
        if (format == null) format = Format.FLAC;
        quality = Math.max(32, Math.min(320, quality));
        if (directory == null) directory = "";
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("conversion.format", format.name()); values.put("conversion.quality", quality);
        values.put("conversion.skipExisting", skipExisting); values.put("conversion.directory", directory);
        MenuSettings.setAll(values);
    }
}
