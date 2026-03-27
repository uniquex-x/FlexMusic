/*
 * Derived from SoX biquad filter design logic:
 * https://github.com/chirlu/sox (commit 42b3557e13e0fe01a83465b672d89faddbe65f49)
 * Original libSoX biquad sources are distributed under LGPL-2.1-or-later.
 */

#include "SoxBiquadFilter.h"

#include <algorithm>
#include <cmath>

namespace flexmusic {
namespace audio {

namespace {

constexpr double kPi = 3.14159265358979323846;

double toAmplitude(double gainDb) {
    return std::exp(gainDb / 40.0 * std::log(10.0));
}

} // namespace

void SoxBiquadFilter::configureLowShelf(int sampleRate,
                                        int channelCount,
                                        double frequencyHz,
                                        double slope,
                                        double gainDb) {
    configure(sampleRate, channelCount, frequencyHz, slope, gainDb, DesignType::LOW_SHELF);
}

void SoxBiquadFilter::configureHighShelf(int sampleRate,
                                         int channelCount,
                                         double frequencyHz,
                                         double slope,
                                         double gainDb) {
    configure(sampleRate, channelCount, frequencyHz, slope, gainDb, DesignType::HIGH_SHELF);
}

void SoxBiquadFilter::configurePeakingEq(int sampleRate,
                                         int channelCount,
                                         double frequencyHz,
                                         double q,
                                         double gainDb) {
    configure(sampleRate, channelCount, frequencyHz, q, gainDb, DesignType::PEAKING_EQ);
}

void SoxBiquadFilter::reset() {
    std::fill(input1_.begin(), input1_.end(), 0.0);
    std::fill(input2_.begin(), input2_.end(), 0.0);
    std::fill(output1_.begin(), output1_.end(), 0.0);
    std::fill(output2_.begin(), output2_.end(), 0.0);
}

void SoxBiquadFilter::processInPlace(int16_t* samples, std::size_t frameCount, int channelCount) {
    if (samples == nullptr || frameCount == 0 || channelCount <= 0) {
        return;
    }
    ensureChannelState(channelCount);
    for (std::size_t frameIndex = 0; frameIndex < frameCount; ++frameIndex) {
        for (int channelIndex = 0; channelIndex < channelCount; ++channelIndex) {
            const std::size_t sampleIndex = frameIndex * static_cast<std::size_t>(channelCount) + channelIndex;
            const double inputSample = static_cast<double>(samples[sampleIndex]);
            const double outputSample = inputSample * b0_
                    + input1_[channelIndex] * b1_
                    + input2_[channelIndex] * b2_
                    - output1_[channelIndex] * a1_
                    - output2_[channelIndex] * a2_;
            input2_[channelIndex] = input1_[channelIndex];
            input1_[channelIndex] = inputSample;
            output2_[channelIndex] = output1_[channelIndex];
            output1_[channelIndex] = outputSample;
            samples[sampleIndex] = clipToPcm16(outputSample);
        }
    }
}

void SoxBiquadFilter::configure(int sampleRate,
                                int channelCount,
                                double frequencyHz,
                                double widthOrSlope,
                                double gainDb,
                                DesignType designType) {
    ensureChannelState(channelCount);
    const double safeSampleRate = std::max(sampleRate, 1);
    const double safeFrequencyHz = std::max(1.0, std::min(frequencyHz, safeSampleRate * 0.49));
    const double safeWidthOrSlope = std::max(0.01, widthOrSlope);
    const double w0 = 2.0 * kPi * safeFrequencyHz / safeSampleRate;
    const double sinW0 = std::sin(w0);
    const double cosW0 = std::cos(w0);
    const double amplitude = toAmplitude(gainDb);

    double rawB0 = 1.0;
    double rawB1 = 0.0;
    double rawB2 = 0.0;
    double rawA0 = 1.0;
    double rawA1 = 0.0;
    double rawA2 = 0.0;

    if (designType == DesignType::PEAKING_EQ) {
        const double alpha = sinW0 / (2.0 * safeWidthOrSlope);
        rawB0 = 1.0 + alpha * amplitude;
        rawB1 = -2.0 * cosW0;
        rawB2 = 1.0 - alpha * amplitude;
        rawA0 = 1.0 + alpha / amplitude;
        rawA1 = -2.0 * cosW0;
        rawA2 = 1.0 - alpha / amplitude;
    } else {
        const double alpha = sinW0 / 2.0
                * std::sqrt((amplitude + 1.0 / amplitude) * (1.0 / safeWidthOrSlope - 1.0) + 2.0);
        const double beta = 2.0 * std::sqrt(amplitude) * alpha;
        if (designType == DesignType::LOW_SHELF) {
            rawB0 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosW0 + beta);
            rawB1 = 2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cosW0);
            rawB2 = amplitude * ((amplitude + 1.0) - (amplitude - 1.0) * cosW0 - beta);
            rawA0 = (amplitude + 1.0) + (amplitude - 1.0) * cosW0 + beta;
            rawA1 = -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cosW0);
            rawA2 = (amplitude + 1.0) + (amplitude - 1.0) * cosW0 - beta;
        } else {
            rawB0 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosW0 + beta);
            rawB1 = -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cosW0);
            rawB2 = amplitude * ((amplitude + 1.0) + (amplitude - 1.0) * cosW0 - beta);
            rawA0 = (amplitude + 1.0) - (amplitude - 1.0) * cosW0 + beta;
            rawA1 = 2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cosW0);
            rawA2 = (amplitude + 1.0) - (amplitude - 1.0) * cosW0 - beta;
        }
    }
    applyNormalizedCoefficients(rawB0, rawB1, rawB2, rawA0, rawA1, rawA2);
    reset();
}

void SoxBiquadFilter::applyNormalizedCoefficients(double rawB0,
                                                  double rawB1,
                                                  double rawB2,
                                                  double rawA0,
                                                  double rawA1,
                                                  double rawA2) {
    const double safeA0 = std::abs(rawA0) < 1e-9 ? 1.0 : rawA0;
    b0_ = rawB0 / safeA0;
    b1_ = rawB1 / safeA0;
    b2_ = rawB2 / safeA0;
    a1_ = rawA1 / safeA0;
    a2_ = rawA2 / safeA0;
}

void SoxBiquadFilter::ensureChannelState(int channelCount) {
    const std::size_t safeChannelCount = static_cast<std::size_t>(std::max(channelCount, 1));
    if (input1_.size() == safeChannelCount) {
        return;
    }
    input1_.assign(safeChannelCount, 0.0);
    input2_.assign(safeChannelCount, 0.0);
    output1_.assign(safeChannelCount, 0.0);
    output2_.assign(safeChannelCount, 0.0);
}

int16_t SoxBiquadFilter::clipToPcm16(double value) {
    const double safeValue = std::max(-32768.0, std::min(32767.0, value));
    return static_cast<int16_t>(std::lrint(safeValue));
}

} // namespace audio
} // namespace flexmusic
