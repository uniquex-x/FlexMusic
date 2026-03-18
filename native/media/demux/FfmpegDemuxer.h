#ifndef FLEXMUSIC_FFMPEG_DEMUXER_H
#define FLEXMUSIC_FFMPEG_DEMUXER_H

#include <cstdint>
#include <string>

#include "../packet/EncodedPacket.h"

struct AVCodecParameters;
struct AVFormatContext;
struct AVRational;

namespace flexmusic {
namespace media {
namespace source {
class AvioDataSource;
}
namespace demux {

struct AudioStreamInfo {
    AVCodecParameters* codecParameters = nullptr;
    int streamIndex = -1;
    int sampleRate = 0;
    int channelCount = 0;
    int64_t durationMs = 0;
    AVRational* timeBase = nullptr;
};

class FfmpegDemuxer final {
public:
    FfmpegDemuxer();
    ~FfmpegDemuxer();

    bool open(source::AvioDataSource* dataSource, std::string* errorMessage);
    bool readPacket(flexmusic::media::EncodedPacket* outputPacket, std::string* errorMessage);
    bool seekTo(int64_t positionMs, std::string* errorMessage);
    void close();

    const AudioStreamInfo& audioStreamInfo() const;
    int64_t durationMs() const;
    bool isSeekable() const;

private:
    AudioStreamInfo streamInfo_;
    AVFormatContext* formatContext_ = nullptr;
    AVRational* timeBase_ = nullptr;
    int64_t durationMs_ = 0;
    bool seekable_ = false;
};

} // namespace demux
} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_FFMPEG_DEMUXER_H
