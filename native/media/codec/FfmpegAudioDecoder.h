#ifndef FLEXMUSIC_FFMPEG_AUDIO_DECODER_H
#define FLEXMUSIC_FFMPEG_AUDIO_DECODER_H

#include <string>
#include <vector>

#include "../demux/FfmpegDemuxer.h"
#include "../packet/EncodedPacket.h"
#include "../packet/PcmFrame.h"

struct AVChannelLayout;
struct AVCodecContext;
struct AVFrame;
struct SwrContext;

namespace flexmusic {
namespace media {
namespace codec {

class FfmpegAudioDecoder final {
public:
    FfmpegAudioDecoder();
    ~FfmpegAudioDecoder();

    bool open(const demux::AudioStreamInfo& streamInfo, std::string* errorMessage);
    bool decodePacket(const EncodedPacket& encodedPacket,
                      std::vector<PcmFrame>* outputFrames,
                      std::string* errorMessage);
    bool flush(std::vector<PcmFrame>* outputFrames, std::string* errorMessage);
    void close();

private:
    bool drainFrames(std::vector<PcmFrame>* outputFrames, std::string* errorMessage);

    AVCodecContext* codecContext_ = nullptr;
    AVFrame* frame_ = nullptr;
    SwrContext* swrContext_ = nullptr;
    AVChannelLayout* outputChannelLayout_ = nullptr;
    AVRational* timeBase_ = nullptr;
    int outputSampleRate_ = 0;
    int outputChannelCount_ = 0;
    bool firstOutputFrameLogged_ = false;
};

} // namespace codec
} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_FFMPEG_AUDIO_DECODER_H
