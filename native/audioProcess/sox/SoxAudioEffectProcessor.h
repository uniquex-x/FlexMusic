/*
 * Derived from SoX biquad filter design logic:
 * https://github.com/chirlu/sox (commit 42b3557e13e0fe01a83465b672d89faddbe65f49)
 * Original libSoX biquad sources are distributed under LGPL-2.1-or-later.
 */

#ifndef FLEXMUSIC_SOX_AUDIO_EFFECT_PROCESSOR_H
#define FLEXMUSIC_SOX_AUDIO_EFFECT_PROCESSOR_H

#include <string>
#include <vector>

#include "PcmFrame.h"
#include "SoxBiquadFilter.h"

namespace flexmusic {
namespace audio {

class SoxAudioEffectProcessor final {
public:
    enum ProfileId {
        PROFILE_OFF = 0,
        PROFILE_BASS_BOOST = 1,
        PROFILE_VOCAL_BOOST = 2,
        PROFILE_TREBLE_BOOST = 3,
        PROFILE_WARM = 4,
        PROFILE_BRIGHT = 5,
        PROFILE_ACOUSTIC = 6,
        PROFILE_PODCAST = 7,
        PROFILE_NIGHT = 8
    };

    SoxAudioEffectProcessor() = default;

    static int sanitizeProfileId(int profileId);

    void setProfileId(int profileId);
    int getProfileId() const;
    void clear();
    bool processFrame(const flexmusic::media::PcmFrame& inputFrame,
                      flexmusic::media::PcmFrame* outputFrame,
                      std::string* errorMessage);

private:
    void configureIfNeeded(int sampleRate, int channelCount);
    void rebuildFilterChain(int sampleRate, int channelCount);

    int profileId_ = PROFILE_OFF;
    int configuredSampleRate_ = 0;
    int configuredChannelCount_ = 0;
    bool configurationDirty_ = true;
    bool firstProcessedFrameLogged_ = false;
    std::vector<SoxBiquadFilter> filters_;
};

} // namespace audio
} // namespace flexmusic

#endif // FLEXMUSIC_SOX_AUDIO_EFFECT_PROCESSOR_H
