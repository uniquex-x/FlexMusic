package com.example.flexmusicplayer.model;

import java.io.Serializable;

public class TranscodeSettings implements Serializable {
    public enum OutputFormat {
        MP3,
        VORBIS,
        FLAC
    }

    public enum Bitrate {
        BITRATE_128(128),
        BITRATE_192(192),
        BITRATE_256(256),
        BITRATE_320(320);

        private final int value;

        Bitrate(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public String getDisplayName() {
            return value + " kbps";
        }
    }

    public enum SampleRate {
        RATE_44100(44100),
        RATE_48000(48000),
        RATE_96000(96000);

        private final int value;

        SampleRate(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public String getDisplayName() {
            if (value == 44100) return "44.1 kHz";
            if (value == 48000) return "48 kHz";
            return "96 kHz";
        }
    }

    private boolean enabled;
    private OutputFormat outputFormat;
    private Bitrate bitrate;
    private SampleRate sampleRate;
    private boolean preserveMetadata;

    public TranscodeSettings() {
        this.enabled = false;
        this.outputFormat = OutputFormat.MP3;
        this.bitrate = Bitrate.BITRATE_256;
        this.sampleRate = SampleRate.RATE_44100;
        this.preserveMetadata = true;
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    public Bitrate getBitrate() {
        return bitrate;
    }

    public void setBitrate(Bitrate bitrate) {
        this.bitrate = bitrate;
    }

    public SampleRate getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(SampleRate sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isPreserveMetadata() {
        return preserveMetadata;
    }

    public void setPreserveMetadata(boolean preserveMetadata) {
        this.preserveMetadata = preserveMetadata;
    }

    public String getFileExtension() {
        switch (outputFormat) {
            case MP3:
                return "mp3";
            case VORBIS:
                return "ogg";
            case FLAC:
                return "flac";
            default:
                return "mp3";
        }
    }

    public String getMimeType() {
        switch (outputFormat) {
            case MP3:
                return "audio/mpeg";
            case VORBIS:
                return "audio/ogg";
            case FLAC:
                return "audio/flac";
            default:
                return "audio/mpeg";
        }
    }
}
