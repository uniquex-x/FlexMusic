#include "OpenSlAudioRenderer.h"

#include "../core/logger/logger.h"

#include <algorithm>
#include <chrono>
#include <cmath>

namespace flexmusic {
namespace audio {

namespace {

constexpr char kOpenSlTag[] = "OpenSlRenderer";

SLuint32 resolveChannelMask(int channelCount) {
    return channelCount == 1 ? SL_SPEAKER_FRONT_CENTER : (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT);
}

SLmillibel resolveMillibel(float volume) {
    if (volume <= 0.0f) {
        return SL_MILLIBEL_MIN;
    }
    const float amplitude = std::max(0.0001f, std::min(volume, 1.0f));
    return static_cast<SLmillibel>(2000.0f * std::log10(amplitude));
}

} // namespace

OpenSlAudioRenderer::OpenSlAudioRenderer() = default;

OpenSlAudioRenderer::~OpenSlAudioRenderer() {
    stop();
}

bool OpenSlAudioRenderer::open(int sampleRate, int channelCount, std::string* errorMessage) {
    stop();
    const auto log = flexmusic::core::levelLog(kOpenSlTag);
    openStartedAt_ = std::chrono::steady_clock::now();
    firstBufferEnqueuedLogged_ = false;
    firstBufferConsumedLogged_ = false;

    SLresult result = slCreateEngine(&engineObject_, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        if (errorMessage != nullptr) {
            *errorMessage = "Create OpenSL engine failed";
        }
        log.e("create engine failed result=%d", result);
        return false;
    }

    result = (*engineObject_)->Realize(engineObject_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Realize OpenSL engine failed";
        }
        log.e("realize engine failed result=%d", result);
        return false;
    }

    result = (*engineObject_)->GetInterface(engineObject_, SL_IID_ENGINE, &engineInterface_);
    if (result != SL_RESULT_SUCCESS || engineInterface_ == nullptr) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Get OpenSL engine interface failed";
        }
        log.e("get engine interface failed result=%d", result);
        return false;
    }

    result = (*engineInterface_)->CreateOutputMix(engineInterface_, &outputMixObject_, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Create output mix failed";
        }
        log.e("create output mix failed result=%d", result);
        return false;
    }

    result = (*outputMixObject_)->Realize(outputMixObject_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Realize output mix failed";
        }
        log.e("realize output mix failed result=%d", result);
        return false;
    }

    SLDataLocator_AndroidSimpleBufferQueue queueLocator = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            2
    };
    SLDataFormat_PCM format = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(channelCount),
            static_cast<SLuint32>(sampleRate * 1000),
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            resolveChannelMask(channelCount),
            SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSource dataSource = {&queueLocator, &format};
    SLDataLocator_OutputMix outputLocator = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject_};
    SLDataSink dataSink = {&outputLocator, nullptr};

    const SLInterfaceID interfaceIds[] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean interfaceRequired[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*engineInterface_)->CreateAudioPlayer(
            engineInterface_,
            &playerObject_,
            &dataSource,
            &dataSink,
            2,
            interfaceIds,
            interfaceRequired);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Create audio player failed";
        }
        log.e("create audio player failed result=%d sampleRate=%d channels=%d",
              result,
              sampleRate,
              channelCount);
        return false;
    }

    result = (*playerObject_)->Realize(playerObject_, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Realize audio player failed";
        }
        log.e("realize audio player failed result=%d", result);
        return false;
    }

    result = (*playerObject_)->GetInterface(playerObject_, SL_IID_PLAY, &playInterface_);
    if (result != SL_RESULT_SUCCESS || playInterface_ == nullptr) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Get play interface failed";
        }
        log.e("get play interface failed result=%d", result);
        return false;
    }

    result = (*playerObject_)->GetInterface(playerObject_, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &bufferQueueInterface_);
    if (result != SL_RESULT_SUCCESS || bufferQueueInterface_ == nullptr) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Get buffer queue interface failed";
        }
        log.e("get buffer queue interface failed result=%d", result);
        return false;
    }

    (*playerObject_)->GetInterface(playerObject_, SL_IID_VOLUME, &volumeInterface_);
    result = (*bufferQueueInterface_)->RegisterCallback(
            bufferQueueInterface_,
            &OpenSlAudioRenderer::onBufferQueueCallback,
            this);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Register buffer queue callback failed";
        }
        log.e("register callback failed result=%d", result);
        return false;
    }

    result = (*playInterface_)->SetPlayState(playInterface_, SL_PLAYSTATE_PLAYING);
    if (result != SL_RESULT_SUCCESS) {
        destroyObjects();
        if (errorMessage != nullptr) {
            *errorMessage = "Set initial play state failed";
        }
        log.e("set initial play state failed result=%d", result);
        return false;
    }

    {
        std::lock_guard<std::mutex> lock(mutex_);
        inFlightBuffers_.clear();
        availableBufferSlots_ = 2;
        opened_ = true;
    }
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - openStartedAt_).count();
    log.i("opened sampleRate=%d channels=%d elapsedMs=%lld",
          sampleRate,
          channelCount,
          static_cast<long long>(elapsedMs));
    return true;
}

bool OpenSlAudioRenderer::enqueueFrame(flexmusic::media::PcmFrame frame, std::string* errorMessage) {
    std::unique_lock<std::mutex> lock(mutex_);
    bufferSlotCondition_.wait(lock, [this]() {
        return !opened_ || availableBufferSlots_ > 0;
    });
    if (!opened_ || bufferQueueInterface_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Audio renderer is closed";
        }
        return false;
    }

    inFlightBuffers_.push_back(std::move(frame.data));
    std::vector<uint8_t>& buffer = inFlightBuffers_.back();
    availableBufferSlots_--;
    lock.unlock();

    const SLresult result = (*bufferQueueInterface_)->Enqueue(
            bufferQueueInterface_,
            buffer.data(),
            static_cast<SLuint32>(buffer.size()));
    if (result != SL_RESULT_SUCCESS) {
        lock.lock();
        availableBufferSlots_++;
        inFlightBuffers_.pop_back();
        lock.unlock();
        bufferSlotCondition_.notify_all();
        if (errorMessage != nullptr) {
            *errorMessage = "Enqueue PCM buffer failed";
        }
        flexmusic::core::levelLog(kOpenSlTag).e("enqueue buffer failed result=%d size=%u",
                                                result,
                                                static_cast<unsigned int>(buffer.size()));
        return false;
    }

    if (!firstBufferEnqueuedLogged_) {
        firstBufferEnqueuedLogged_ = true;
        const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - openStartedAt_).count();
        flexmusic::core::levelLog(kOpenSlTag).i("first buffer enqueued bytes=%zu elapsedMs=%lld",
                                                buffer.size(),
                                                static_cast<long long>(elapsedMs));
    }

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void OpenSlAudioRenderer::play() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (opened_ && playInterface_ != nullptr) {
        (*playInterface_)->SetPlayState(playInterface_, SL_PLAYSTATE_PLAYING);
    }
}

void OpenSlAudioRenderer::pause() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (opened_ && playInterface_ != nullptr) {
        (*playInterface_)->SetPlayState(playInterface_, SL_PLAYSTATE_PAUSED);
    }
}

void OpenSlAudioRenderer::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (bufferQueueInterface_ != nullptr) {
        (*bufferQueueInterface_)->Clear(bufferQueueInterface_);
    }
    inFlightBuffers_.clear();
    availableBufferSlots_ = 2;
    bufferSlotCondition_.notify_all();
    drainCondition_.notify_all();
}

void OpenSlAudioRenderer::stop() {
    {
        std::lock_guard<std::mutex> lock(mutex_);
        opened_ = false;
        bufferSlotCondition_.notify_all();
        drainCondition_.notify_all();
    }
    destroyObjects();
}

void OpenSlAudioRenderer::setVolume(float volume) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (volumeInterface_ != nullptr) {
        (*volumeInterface_)->SetVolumeLevel(volumeInterface_, resolveMillibel(volume));
    }
}

void OpenSlAudioRenderer::waitForDrain() {
    std::unique_lock<std::mutex> lock(mutex_);
    drainCondition_.wait(lock, [this]() {
        return !opened_ || inFlightBuffers_.empty();
    });
}

bool OpenSlAudioRenderer::isOpen() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return opened_;
}

void OpenSlAudioRenderer::onBufferQueueCallback(SLAndroidSimpleBufferQueueItf bufferQueueItf, void* context) {
    (void) bufferQueueItf;
    if (context != nullptr) {
        static_cast<OpenSlAudioRenderer*>(context)->handleBufferConsumed();
    }
}

void OpenSlAudioRenderer::handleBufferConsumed() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!firstBufferConsumedLogged_) {
        firstBufferConsumedLogged_ = true;
        const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - openStartedAt_).count();
        flexmusic::core::levelLog(kOpenSlTag).i("first buffer consumed elapsedMs=%lld",
                                                static_cast<long long>(elapsedMs));
    }
    if (!inFlightBuffers_.empty()) {
        inFlightBuffers_.pop_front();
    }
    availableBufferSlots_ = std::min(availableBufferSlots_ + 1, 2);
    bufferSlotCondition_.notify_all();
    if (inFlightBuffers_.empty()) {
        drainCondition_.notify_all();
    }
}

void OpenSlAudioRenderer::destroyObjects() {
    if (playerObject_ != nullptr) {
        (*playerObject_)->Destroy(playerObject_);
        playerObject_ = nullptr;
        playInterface_ = nullptr;
        bufferQueueInterface_ = nullptr;
        volumeInterface_ = nullptr;
    }
    if (outputMixObject_ != nullptr) {
        (*outputMixObject_)->Destroy(outputMixObject_);
        outputMixObject_ = nullptr;
    }
    if (engineObject_ != nullptr) {
        (*engineObject_)->Destroy(engineObject_);
        engineObject_ = nullptr;
        engineInterface_ = nullptr;
    }
}

} // namespace audio
} // namespace flexmusic
