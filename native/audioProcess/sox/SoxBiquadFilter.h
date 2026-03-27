/*
 * Derived from SoX biquad filter design logic:
 * https://github.com/chirlu/sox (commit 42b3557e13e0fe01a83465b672d89faddbe65f49)
 * Original libSoX biquad sources are distributed under LGPL-2.1-or-later.
 */

#ifndef FLEXMUSIC_SOX_BIQUAD_FILTER_H
#define FLEXMUSIC_SOX_BIQUAD_FILTER_H

#include <cstddef>
#include <cstdint>
#include <vector>

namespace flexmusic {
namespace audio {

class SoxBiquadFilter final {
public:
    SoxBiquadFilter() = default;

    /**
     * Configure a low-shelf filter.
     *
     * @param sampleRate input PCM sample rate in Hz.
     * @param channelCount channel count used to size per-channel state.
     * @param frequencyHz shelf turning point in Hz.
     * @param slope shelf steepness, where smaller values sound more gradual.
     * @param gainDb gain in dB, positive boosts and negative cuts.
     */
    void configureLowShelf(int sampleRate, int channelCount, double frequencyHz, double slope, double gainDb);

    /**
     * Configure a high-shelf filter.
     *
     * @param sampleRate input PCM sample rate in Hz.
     * @param channelCount channel count used to size per-channel state.
     * @param frequencyHz shelf turning point in Hz.
     * @param slope shelf steepness, where smaller values sound more gradual.
     * @param gainDb gain in dB, positive boosts and negative cuts.
     */
    void configureHighShelf(int sampleRate, int channelCount, double frequencyHz, double slope, double gainDb);

    /**
     * Configure a peaking EQ band.
     *
     * @param sampleRate input PCM sample rate in Hz.
     * @param channelCount channel count used to size per-channel state.
     * @param frequencyHz center frequency in Hz.
     * @param q band width factor, larger values make the band narrower.
     * @param gainDb gain in dB, positive boosts and negative cuts.
     */
    void configurePeakingEq(int sampleRate, int channelCount, double frequencyHz, double q, double gainDb);
    void reset();
    void processInPlace(int16_t* samples, std::size_t frameCount, int channelCount);

private:
    enum class DesignType {
        LOW_SHELF,
        HIGH_SHELF,
        PEAKING_EQ
    };

    void configure(int sampleRate,
                   int channelCount,
                   double frequencyHz,
                   double widthOrSlope,
                   double gainDb,
                   DesignType designType);
    void applyNormalizedCoefficients(double rawB0,
                                     double rawB1,
                                     double rawB2,
                                     double rawA0,
                                     double rawA1,
                                     double rawA2);
    void ensureChannelState(int channelCount);
    static int16_t clipToPcm16(double value);

    double b0_ = 1.0;
    double b1_ = 0.0;
    double b2_ = 0.0;
    double a1_ = 0.0;
    double a2_ = 0.0;
    std::vector<double> input1_;
    std::vector<double> input2_;
    std::vector<double> output1_;
    std::vector<double> output2_;
};

} // namespace audio
} // namespace flexmusic

#endif // FLEXMUSIC_SOX_BIQUAD_FILTER_H
