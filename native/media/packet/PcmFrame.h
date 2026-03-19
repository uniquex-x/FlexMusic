#ifndef FLEXMUSIC_PCM_FRAME_H
#define FLEXMUSIC_PCM_FRAME_H

#include <cstdint>
#include <vector>

namespace flexmusic {
namespace media {

struct PcmFrame {
    std::vector<uint8_t> data;
    int sampleRate = 0;
    int channelCount = 0;
    int64_t positionMs = 0;
    int64_t durationMs = 0;
    bool endOfStream = false;
    int serial = 0;
};

} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_PCM_FRAME_H
