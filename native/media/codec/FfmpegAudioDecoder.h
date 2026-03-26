#ifndef FLEXMUSIC_FFMPEG_AUDIO_DECODER_H
#define FLEXMUSIC_FFMPEG_AUDIO_DECODER_H

#include <string>
#include <vector>

#include "FfmpegDemuxer.h"
#include "EncodedPacket.h"
#include "PcmFrame.h"

struct AVChannelLayout;
struct AVCodecContext;
struct AVFrame;
struct SwrContext;

namespace flexmusic {
namespace media {
namespace codec {

enum class DecodePacketStatus {
    OK,
    INVALID_PACKET,
    FATAL_ERROR
};

class FfmpegAudioDecoder final {
public:
    FfmpegAudioDecoder();
    ~FfmpegAudioDecoder();

    bool open(const demux::AudioStreamInfo& streamInfo, std::string* errorMessage);
    DecodePacketStatus decodePacket(const EncodedPacket& encodedPacket,
                                    std::vector<PcmFrame>* outputFrames,
                                    std::string* errorMessage);
    bool flush(std::vector<PcmFrame>* outputFrames, std::string* errorMessage);
    bool reset(std::string* errorMessage);
    void close();

private:
    DecodePacketStatus drainFrames(std::vector<PcmFrame>* outputFrames, std::string* errorMessage);

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
