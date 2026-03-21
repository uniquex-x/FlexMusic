package com.example.feature_transcode;

import java.io.Serializable;

public class TranscodeSettings implements Serializable {
    public enum OutputFormat {
        MP3,
        FLAC,
        OGG,
        WAV;

        public String getFileExtension() {
            switch (this) {
                case FLAC:
                    return "flac";
                case OGG:
                    return "ogg";
                case WAV:
                    return "wav";
                case MP3:
                default:
                    return "mp3";
            }
        }

        public String getMimeType() {
            switch (this) {
                case FLAC:
                    return "audio/flac";
                case OGG:
                    return "audio/ogg";
                case WAV:
                    return "audio/wav";
                case MP3:
                default:
                    return "audio/mpeg";
            }
        }
    }
}
