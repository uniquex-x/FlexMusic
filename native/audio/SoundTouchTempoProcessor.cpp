#include "audio/SoundTouchTempoProcessor.h"

#include <algorithm>
#include <cmath>

#include "core/logger/logger.h"

namespace flexmusic {
namespace audio {

namespace {

constexpr char kSoundTouchProcessorTag[] = "SoundTouchProcessor";
constexpr unsigned int kDrainChunkSampleCount = 2048;
constexpr float kPassthroughEpsilon = 0.001f;

} // namespace

SoundTouchTempoProcessor::SoundTouchTempoProcessor() {
    soundTouch_.setRate(1.0f);
    soundTouch_.setPitch(1.0f);
    soundTouch_.setTempo(1.0f);
    soundTouch_.setSetting(SETTING_USE_AA_FILTER, 1);
    soundTouch_.setSetting(SETTING_USE_QUICKSEEK, 1);
}

void SoundTouchTempoProcessor::setPlaybackSpeed(float playbackSpeed) {
    const bool wasPassthrough = isPassthroughMode();
    playbackSpeed_ = std::max(0.5f, std::min(2.0f, playbackSpeed));
    const bool isPassthrough = isPassthroughMode();
    if (wasPassthrough != isPassthrough) {
        soundTouch_.clear();
        positionInitialized_ = false;
        nextSourcePositionMs_ = 0;
    }
    soundTouch_.setTempo(playbackSpeed_);
    flexmusic::core::levelLog(kSoundTouchProcessorTag).i("set tempo speed=%.2f", playbackSpeed_);
}

void SoundTouchTempoProcessor::clear() {
    soundTouch_.clear();
    sampleRate_ = 0;
    channelCount_ = 0;
    nextSourcePositionMs_ = 0;
    positionInitialized_ = false;
}

bool SoundTouchTempoProcessor::processFrame(const flexmusic::media::PcmFrame& inputFrame,
                                            std::vector<flexmusic::media::PcmFrame>* outputFrames,
                                            std::string* errorMessage) {
    if (outputFrames == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "SoundTouch output buffer is null";
        }
        return false;
    }
    if (inputFrame.sampleRate <= 0 || inputFrame.channelCount <= 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "SoundTouch input format is invalid";
        }
        return false;
    }

    const std::size_t bytesPerFrame = static_cast<std::size_t>(inputFrame.channelCount) * sizeof(int16_t);
    if (bytesPerFrame == 0 || inputFrame.data.size() % bytesPerFrame != 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "SoundTouch PCM frame size is invalid";
        }
        return false;
    }
    if (isPassthroughMode()) {
        outputFrames->push_back(inputFrame);
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    configureIfNeeded(inputFrame.sampleRate, inputFrame.channelCount, inputFrame.positionMs);
    if (!positionInitialized_) {
        nextSourcePositionMs_ = inputFrame.positionMs;
        positionInitialized_ = true;
    }

    const unsigned int sampleCount = static_cast<unsigned int>(inputFrame.data.size() / bytesPerFrame);
    if (sampleCount == 0) {
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    static_assert(sizeof(soundtouch::SAMPLETYPE) == sizeof(int16_t),
                  "SoundTouch must be compiled with 16-bit integer samples");
    soundTouch_.putSamples(reinterpret_cast<const soundtouch::SAMPLETYPE*>(inputFrame.data.data()), sampleCount);
    drainAvailableSamples(outputFrames);

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

bool SoundTouchTempoProcessor::flush(std::vector<flexmusic::media::PcmFrame>* outputFrames,
                                     std::string* errorMessage) {
    if (outputFrames == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "SoundTouch flush buffer is null";
        }
        return false;
    }
    if (sampleRate_ <= 0 || channelCount_ <= 0 || !positionInitialized_) {
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    soundTouch_.flush();
    drainAvailableSamples(outputFrames);
    clear();

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

bool SoundTouchTempoProcessor::isPassthroughMode() const {
    return std::fabs(playbackSpeed_ - 1.0f) < kPassthroughEpsilon;
}

void SoundTouchTempoProcessor::configureIfNeeded(int sampleRate, int channelCount, int64_t startPositionMs) {
    if (sampleRate == sampleRate_ && channelCount == channelCount_) {
        if (!positionInitialized_) {
            nextSourcePositionMs_ = startPositionMs;
            positionInitialized_ = true;
        }
        return;
    }

    soundTouch_.clear();
    sampleRate_ = sampleRate;
    channelCount_ = channelCount;
    nextSourcePositionMs_ = startPositionMs;
    positionInitialized_ = true;
    soundTouch_.setSampleRate(static_cast<unsigned int>(sampleRate_));
    soundTouch_.setChannels(static_cast<unsigned int>(channelCount_));
    soundTouch_.setRate(1.0f);
    soundTouch_.setPitch(1.0f);
    soundTouch_.setTempo(playbackSpeed_);
    flexmusic::core::levelLog(kSoundTouchProcessorTag).i(
            "configure sampleRate=%d channels=%d speed=%.2f startPositionMs=%lld",
            sampleRate_,
            channelCount_,
            playbackSpeed_,
            static_cast<long long>(startPositionMs));
}

void SoundTouchTempoProcessor::drainAvailableSamples(std::vector<flexmusic::media::PcmFrame>* outputFrames) {
    if (outputFrames == nullptr || sampleRate_ <= 0 || channelCount_ <= 0) {
        return;
    }

    while (true) {
        const unsigned int availableSamples = soundTouch_.numSamples();
        if (availableSamples == 0) {
            return;
        }

        const unsigned int readSamples = std::min(availableSamples, kDrainChunkSampleCount);
        flexmusic::media::PcmFrame outputFrame;
        outputFrame.sampleRate = sampleRate_;
        outputFrame.channelCount = channelCount_;
        outputFrame.positionMs = nextSourcePositionMs_;
        outputFrame.data.resize(static_cast<std::size_t>(readSamples) * channelCount_ * sizeof(int16_t));

        const unsigned int receivedSamples = soundTouch_.receiveSamples(
                reinterpret_cast<soundtouch::SAMPLETYPE*>(outputFrame.data.data()),
                readSamples);
        if (receivedSamples == 0) {
            return;
        }

        outputFrame.data.resize(static_cast<std::size_t>(receivedSamples) * channelCount_ * sizeof(int16_t));
        outputFrame.durationMs = scaleSourceDurationMs(receivedSamples, sampleRate_, playbackSpeed_);
        nextSourcePositionMs_ += outputFrame.durationMs;
        outputFrames->push_back(std::move(outputFrame));
    }
}

int64_t SoundTouchTempoProcessor::scaleSourceDurationMs(unsigned int sampleCount,
                                                        int sampleRate,
                                                        float playbackSpeed) {
    if (sampleCount == 0 || sampleRate <= 0) {
        return 0;
    }
    const double sourceDurationMs = static_cast<double>(sampleCount) * 1000.0
            * std::max(0.5f, std::min(2.0f, playbackSpeed))
            / static_cast<double>(sampleRate);
    return std::max<int64_t>(1, static_cast<int64_t>(std::llround(sourceDurationMs)));
}

} // namespace audio
} // namespace flexmusic
