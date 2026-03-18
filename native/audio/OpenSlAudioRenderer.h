#ifndef FLEXMUSIC_OPEN_SL_AUDIO_RENDERER_H
#define FLEXMUSIC_OPEN_SL_AUDIO_RENDERER_H

#include <condition_variable>
#include <chrono>
#include <cstdint>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

#include "../media/packet/PcmFrame.h"

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

namespace flexmusic {
namespace audio {

class OpenSlAudioRenderer final {
public:
    OpenSlAudioRenderer();
    ~OpenSlAudioRenderer();

    bool open(int sampleRate, int channelCount, std::string* errorMessage);
    bool enqueueFrame(flexmusic::media::PcmFrame frame, std::string* errorMessage);
    void play();
    void pause();
    void flush();
    void stop();
    void setVolume(float volume);
    void waitForDrain();
    bool isOpen() const;

private:
    static void onBufferQueueCallback(SLAndroidSimpleBufferQueueItf bufferQueueItf, void* context);
    void handleBufferConsumed();
    void destroyObjects();

    mutable std::mutex mutex_;
    std::condition_variable bufferSlotCondition_;
    std::condition_variable drainCondition_;
    SLObjectItf engineObject_ = nullptr;
    SLEngineItf engineInterface_ = nullptr;
    SLObjectItf outputMixObject_ = nullptr;
    SLObjectItf playerObject_ = nullptr;
    SLPlayItf playInterface_ = nullptr;
    SLAndroidSimpleBufferQueueItf bufferQueueInterface_ = nullptr;
    SLVolumeItf volumeInterface_ = nullptr;
    std::deque<std::vector<uint8_t>> inFlightBuffers_;
    int availableBufferSlots_ = 2;
    bool opened_ = false;
    bool firstBufferEnqueuedLogged_ = false;
    bool firstBufferConsumedLogged_ = false;
    std::chrono::steady_clock::time_point openStartedAt_{};
};

} // namespace audio
} // namespace flexmusic

#endif // FLEXMUSIC_OPEN_SL_AUDIO_RENDERER_H
