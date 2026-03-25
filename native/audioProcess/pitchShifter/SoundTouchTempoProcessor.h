#ifndef FLEXMUSIC_SOUND_TOUCH_TEMPO_PROCESSOR_H
#define FLEXMUSIC_SOUND_TOUCH_TEMPO_PROCESSOR_H

#include <cstdint>
#include <string>
#include <vector>

#include "media/packet/PcmFrame.h"
#include "third_party/soundtouch/include/SoundTouch.h"

namespace flexmusic {
namespace audio {

class SoundTouchTempoProcessor final {
public:
    SoundTouchTempoProcessor();
    ~SoundTouchTempoProcessor() = default;

    void setPlaybackSpeed(float playbackSpeed);
    void clear();
    bool processFrame(const flexmusic::media::PcmFrame& inputFrame,
                      std::vector<flexmusic::media::PcmFrame>* outputFrames,
                      std::string* errorMessage);
    bool flush(std::vector<flexmusic::media::PcmFrame>* outputFrames,
               std::string* errorMessage);

private:
    bool isPassthroughMode() const;
    void configureIfNeeded(int sampleRate, int channelCount, int64_t startPositionMs);
    void drainAvailableSamples(std::vector<flexmusic::media::PcmFrame>* outputFrames);
    static int64_t scaleSourceDurationMs(unsigned int sampleCount,
                                         int sampleRate,
                                         float playbackSpeed);

    soundtouch::SoundTouch soundTouch_;
    float playbackSpeed_ = 1.0f;
    int sampleRate_ = 0;
    int channelCount_ = 0;
    int64_t nextSourcePositionMs_ = 0;
    bool positionInitialized_ = false;
};

} // namespace audio
} // namespace flexmusic

#endif // FLEXMUSIC_SOUND_TOUCH_TEMPO_PROCESSOR_H
