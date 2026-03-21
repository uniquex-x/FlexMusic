#ifndef FLEXMUSIC_AUDIO_TRANSCODER_H
#define FLEXMUSIC_AUDIO_TRANSCODER_H

#include <string>

namespace flexmusic {
namespace transcode {

enum class OutputFormat {
    MP3 = 0,
    FLAC = 1,
    OGG = 2,
    WAV = 3
};

struct TranscodeRequest {
    std::string sourcePath;
    std::string targetPath;
    OutputFormat outputFormat = OutputFormat::MP3;
    int bitrateKbps = 320;
    int sampleRate = 0;
};

class AudioTranscoder final {
public:
    static bool transcode(const TranscodeRequest& request, std::string* errorMessage);
};

} // namespace transcode
} // namespace flexmusic

#endif // FLEXMUSIC_AUDIO_TRANSCODER_H
