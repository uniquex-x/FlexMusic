/*
 * Derived from SoX biquad filter design logic:
 * https://github.com/chirlu/sox (commit 42b3557e13e0fe01a83465b672d89faddbe65f49)
 * Original libSoX biquad sources are distributed under LGPL-2.1-or-later.
 */

#include "SoxAudioEffectProcessor.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdlib>

#include "logger.h"

namespace flexmusic {
namespace audio {

namespace {

constexpr char kSoxAudioEffectTag[] = "SoxAudioEffect";

const char* profileIdToString(int profileId) {
    switch (profileId) {
        case SoxAudioEffectProcessor::PROFILE_BASS_BOOST:
            return "bass_boost";
        case SoxAudioEffectProcessor::PROFILE_VOCAL_BOOST:
            return "vocal_boost";
        case SoxAudioEffectProcessor::PROFILE_TREBLE_BOOST:
            return "treble_boost";
        case SoxAudioEffectProcessor::PROFILE_WARM:
            return "warm";
        case SoxAudioEffectProcessor::PROFILE_BRIGHT:
            return "bright";
        case SoxAudioEffectProcessor::PROFILE_ACOUSTIC:
            return "acoustic";
        case SoxAudioEffectProcessor::PROFILE_PODCAST:
            return "podcast";
        case SoxAudioEffectProcessor::PROFILE_NIGHT:
            return "night";
        case SoxAudioEffectProcessor::PROFILE_OFF:
        default:
            return "off";
    }
}

int computePeakAbsSample(const int16_t* samples, std::size_t sampleCount) {
    if (samples == nullptr) {
        return 0;
    }
    int peak = 0;
    for (std::size_t index = 0; index < sampleCount; ++index) {
        peak = std::max(peak, std::abs(static_cast<int>(samples[index])));
    }
    return peak;
}

} // namespace

int SoxAudioEffectProcessor::sanitizeProfileId(int profileId) {
    switch (profileId) {
        case PROFILE_BASS_BOOST:
        case PROFILE_VOCAL_BOOST:
        case PROFILE_TREBLE_BOOST:
        case PROFILE_WARM:
        case PROFILE_BRIGHT:
        case PROFILE_ACOUSTIC:
        case PROFILE_PODCAST:
        case PROFILE_NIGHT:
            return profileId;
        case PROFILE_OFF:
        default:
            return PROFILE_OFF;
    }
}

void SoxAudioEffectProcessor::setProfileId(int profileId) {
    const int safeProfileId = sanitizeProfileId(profileId);
    if (profileId_ == safeProfileId) {
        firstProcessedFrameLogged_ = false;
        flexmusic::utils::levelLog(kSoxAudioEffectTag).i(
                "reapply profile=%s(%d)",
                profileIdToString(safeProfileId),
                safeProfileId);
        clear();
        return;
    }
    flexmusic::utils::levelLog(kSoxAudioEffectTag).i(
            "profile change from=%s(%d) to=%s(%d)",
            profileIdToString(profileId_),
            profileId_,
            profileIdToString(safeProfileId),
            safeProfileId);
    profileId_ = safeProfileId;
    configurationDirty_ = true;
    firstProcessedFrameLogged_ = false;
    clear();
}

int SoxAudioEffectProcessor::getProfileId() const {
    return profileId_;
}

void SoxAudioEffectProcessor::clear() {
    for (SoxBiquadFilter& filter : filters_) {
        filter.reset();
    }
}

bool SoxAudioEffectProcessor::processFrame(const flexmusic::media::PcmFrame& inputFrame,
                                           flexmusic::media::PcmFrame* outputFrame,
                                           std::string* errorMessage) {
    if (outputFrame == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Output frame is null";
        }
        return false;
    }
    *outputFrame = inputFrame;
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    if (profileId_ == PROFILE_OFF || inputFrame.data.empty()) {
        return true;
    }
    if (inputFrame.sampleRate <= 0 || inputFrame.channelCount <= 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "Invalid PCM frame format";
        }
        return false;
    }
    if (inputFrame.data.size() % sizeof(int16_t) != 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "PCM frame byte count is not aligned to 16-bit samples";
        }
        return false;
    }
    const std::size_t sampleCount = inputFrame.data.size() / sizeof(int16_t);
    if (sampleCount % static_cast<std::size_t>(inputFrame.channelCount) != 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "PCM frame sample count does not match channel layout";
        }
        return false;
    }
    configureIfNeeded(inputFrame.sampleRate, inputFrame.channelCount);
    const bool shouldLogProcessedFrame = !firstProcessedFrameLogged_;
    const int16_t* inputSamples = reinterpret_cast<const int16_t*>(inputFrame.data.data());
    const int inputPeakAbs = shouldLogProcessedFrame
            ? computePeakAbsSample(inputSamples, sampleCount)
            : 0;
    int16_t* pcmSamples = reinterpret_cast<int16_t*>(outputFrame->data.data());
    const std::size_t frameCount = sampleCount / static_cast<std::size_t>(inputFrame.channelCount);
    for (SoxBiquadFilter& filter : filters_) {
        filter.processInPlace(pcmSamples, frameCount, inputFrame.channelCount);
    }
    if (shouldLogProcessedFrame) {
        const int outputPeakAbs = computePeakAbsSample(pcmSamples, sampleCount);
        flexmusic::utils::levelLog(kSoxAudioEffectTag).i(
                "first processed frame profile=%s(%d) sampleRate=%d channels=%d frames=%zu filters=%zu inputPeak=%d outputPeak=%d positionMs=%lld",
                profileIdToString(profileId_),
                profileId_,
                inputFrame.sampleRate,
                inputFrame.channelCount,
                frameCount,
                filters_.size(),
                inputPeakAbs,
                outputPeakAbs,
                static_cast<long long>(inputFrame.positionMs));
        if (outputPeakAbs >= 32000) {
            flexmusic::utils::levelLog(kSoxAudioEffectTag).w(
                    "processed frame near clip profile=%s(%d) outputPeak=%d sampleRate=%d channels=%d",
                    profileIdToString(profileId_),
                    profileId_,
                    outputPeakAbs,
                    inputFrame.sampleRate,
                    inputFrame.channelCount);
        }
        firstProcessedFrameLogged_ = true;
    }
    return true;
}

void SoxAudioEffectProcessor::configureIfNeeded(int sampleRate, int channelCount) {
    if (!configurationDirty_
            && configuredSampleRate_ == sampleRate
            && configuredChannelCount_ == channelCount) {
        return;
    }
    rebuildFilterChain(sampleRate, channelCount);
    configuredSampleRate_ = sampleRate;
    configuredChannelCount_ = channelCount;
    configurationDirty_ = false;
}

void SoxAudioEffectProcessor::rebuildFilterChain(int sampleRate, int channelCount) {
    filters_.clear();
    if (profileId_ == PROFILE_OFF) {
        flexmusic::utils::levelLog(kSoxAudioEffectTag).i(
                "rebuild chain profile=%s(%d) sampleRate=%d channels=%d filters=0",
                profileIdToString(profileId_),
                profileId_,
                sampleRate,
                channelCount);
        return;
    }

    // Tune presets for clearly audible shifts without introducing a full
    // parametric EQ UI. 中文：这里是“预设型音色”，不是可调节多段均衡器。
    // Call order:
    // - shelf: (sampleRate, channelCount, frequencyHz, slope, gainDb)
    // - peaking: (sampleRate, channelCount, frequencyHz, q, gainDb)
    // 中文：参数顺序分别是频率/斜率/增益，或频率/Q 值/增益。
    SoxBiquadFilter filter;
    switch (profileId_) {
        case PROFILE_BASS_BOOST:
            // Lift sub/low bass first, then add some punch around upper bass.
            // 中文：先抬低频下潜，再补一点低频冲击感。
            filter.configureLowShelf(sampleRate, channelCount, 95.0, 0.75, 8.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 180.0, 1.0, 3.0);
            filters_.push_back(filter);
            break;
        case PROFILE_VOCAL_BOOST:
            // Trim low-end masking, push vocal presence, then add a little air.
            // 中文：削弱低频遮蔽，抬人声存在感，再补一点空气感。
            filter.configureLowShelf(sampleRate, channelCount, 180.0, 0.9, -2.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 2600.0, 1.0, 6.5);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 6200.0, 0.9, 2.0);
            filters_.push_back(filter);
            break;
        case PROFILE_TREBLE_BOOST:
            // Reduce some low-end weight so the boosted highs stay obvious.
            // 中文：先轻削低频，避免高频增强后仍被整体厚度盖住。
            filter.configureLowShelf(sampleRate, channelCount, 180.0, 0.9, -1.5);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 3400.0, 0.75, 7.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 7200.0, 1.0, 2.5);
            filters_.push_back(filter);
            break;
        case PROFILE_WARM:
            // Add body, soften presence, and slightly darken the top end.
            // 中文：增加厚度，压一点存在感，并轻微收暗高频。
            filter.configureLowShelf(sampleRate, channelCount, 180.0, 0.8, 4.5);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 900.0, 1.1, -1.5);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 4600.0, 0.9, -2.5);
            filters_.push_back(filter);
            break;
        case PROFILE_BRIGHT:
            // Bright is not just more treble; keep low-end slightly lighter too.
            // 中文：明亮不只是加高频，也要让低频稍微轻一点。
            filter.configureLowShelf(sampleRate, channelCount, 160.0, 0.9, -1.5);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 3600.0, 0.75, 6.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 2200.0, 1.0, 2.5);
            filters_.push_back(filter);
            break;
        case PROFILE_ACOUSTIC:
            // Preserve warmth while opening articulation for strings and plucks.
            // 中文：保留原声厚度，同时提高清晰度和拨弦质感。
            filter.configureLowShelf(sampleRate, channelCount, 140.0, 0.9, 2.5);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 3200.0, 1.1, 3.5);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 6800.0, 0.85, 2.0);
            filters_.push_back(filter);
            break;
        case PROFILE_PODCAST:
            // Speech preset: remove rumble, focus intelligibility bands, add air.
            // 中文：播客预设先去低频轰鸣，再强调可懂度频段和齿音亮度。
            filter.configureLowShelf(sampleRate, channelCount, 140.0, 0.9, -4.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 1800.0, 1.0, 6.5);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 3600.0, 1.0, 3.0);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 6200.0, 0.85, 2.0);
            filters_.push_back(filter);
            break;
        case PROFILE_NIGHT:
            // Keep some low-end comfort but reduce bite for softer late-night use.
            // 中文：保留一点低频包裹感，同时削弱刺耳感，适合夜听。
            filter.configureLowShelf(sampleRate, channelCount, 150.0, 0.85, 3.0);
            filters_.push_back(filter);
            filter.configurePeakingEq(sampleRate, channelCount, 2800.0, 1.0, -2.0);
            filters_.push_back(filter);
            filter.configureHighShelf(sampleRate, channelCount, 4200.0, 0.85, -5.0);
            filters_.push_back(filter);
            break;
        case PROFILE_OFF:
        default:
            break;
    }
    flexmusic::utils::levelLog(kSoxAudioEffectTag).i(
            "rebuild chain profile=%s(%d) sampleRate=%d channels=%d filters=%zu",
            profileIdToString(profileId_),
            profileId_,
            sampleRate,
            channelCount,
            filters_.size());
}

} // namespace audio
} // namespace flexmusic
