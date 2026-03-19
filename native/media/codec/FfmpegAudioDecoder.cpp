#include "FfmpegAudioDecoder.h"

#include "../../core/logger/logger.h"

#include <algorithm>
#include <chrono>
#include <memory>
#include <string>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/error.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
}

namespace flexmusic {
namespace media {
namespace codec {

namespace {

constexpr char kDecoderTag[] = "FfmpegDecoder";

std::string avErrorToString(int errorCode) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(errorCode, buffer, sizeof(buffer));
    return std::string(buffer);
}

int64_t packetToPositionMs(const AVFrame* frame, const AVRational* timeBase) {
    if (frame == nullptr || timeBase == nullptr || frame->best_effort_timestamp == AV_NOPTS_VALUE) {
        return 0;
    }
    return av_rescale_q(frame->best_effort_timestamp, *timeBase, AVRational{1, 1000});
}

} // namespace

FfmpegAudioDecoder::FfmpegAudioDecoder() = default;

FfmpegAudioDecoder::~FfmpegAudioDecoder() {
    close();
}

bool FfmpegAudioDecoder::open(const demux::AudioStreamInfo& streamInfo, std::string* errorMessage) {
    close();
    const auto log = flexmusic::core::levelLog(kDecoderTag);
    const auto startedAt = std::chrono::steady_clock::now();
    firstOutputFrameLogged_ = false;
    if (streamInfo.codecParameters == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Audio codec parameters are empty";
        }
        return false;
    }

    const AVCodec* codec = avcodec_find_decoder(streamInfo.codecParameters->codec_id);
    if (codec == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Find decoder failed";
        }
        log.e("find decoder failed codecId=%d", streamInfo.codecParameters->codec_id);
        return false;
    }

    codecContext_ = avcodec_alloc_context3(codec);
    frame_ = av_frame_alloc();
    outputChannelLayout_ = new AVChannelLayout();
    timeBase_ = new AVRational(*streamInfo.timeBase);
    if (codecContext_ == nullptr || frame_ == nullptr || outputChannelLayout_ == nullptr || timeBase_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Allocate decoder resources failed";
        }
        close();
        return false;
    }

    int result = avcodec_parameters_to_context(codecContext_, streamInfo.codecParameters);
    if (result < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        close();
        return false;
    }

    result = avcodec_open2(codecContext_, codec, nullptr);
    if (result < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        log.e("open decoder failed codec=%s error=%s",
              codec->name,
              errorMessage != nullptr ? errorMessage->c_str() : "");
        close();
        return false;
    }

    outputSampleRate_ = codecContext_->sample_rate > 0 ? codecContext_->sample_rate : streamInfo.sampleRate;
    outputChannelCount_ = std::clamp(codecContext_->ch_layout.nb_channels > 0
                    ? codecContext_->ch_layout.nb_channels
                    : streamInfo.channelCount,
            1,
            2);
    av_channel_layout_default(outputChannelLayout_, outputChannelCount_);

    result = swr_alloc_set_opts2(
            &swrContext_,
            outputChannelLayout_,
            AV_SAMPLE_FMT_S16,
            outputSampleRate_,
            &codecContext_->ch_layout,
            codecContext_->sample_fmt,
            codecContext_->sample_rate,
            0,
            nullptr);
    if (result < 0 || swrContext_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result < 0 ? result : AVERROR_UNKNOWN);
        }
        close();
        return false;
    }

    result = swr_init(swrContext_);
    if (result < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        log.e("init swr failed error=%s", errorMessage != nullptr ? errorMessage->c_str() : "");
        close();
        return false;
    }
    const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - startedAt).count();
    log.i("opened codec=%s sampleRate=%d channels=%d elapsedMs=%lld",
          codec->name,
          outputSampleRate_,
          outputChannelCount_,
          static_cast<long long>(elapsedMs));

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

bool FfmpegAudioDecoder::decodePacket(const EncodedPacket& encodedPacket,
                                      std::vector<PcmFrame>* outputFrames,
                                      std::string* errorMessage) {
    if (codecContext_ == nullptr || outputFrames == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Decoder is not ready";
        }
        return false;
    }

    int result = avcodec_send_packet(codecContext_, encodedPacket.packet);
    if (result < 0 && result != AVERROR(EAGAIN) && result != AVERROR_EOF) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        flexmusic::core::levelLog(kDecoderTag).e("send packet failed error=%s",
                                                 errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }
    return drainFrames(outputFrames, errorMessage);
}

bool FfmpegAudioDecoder::flush(std::vector<PcmFrame>* outputFrames, std::string* errorMessage) {
    if (codecContext_ == nullptr || outputFrames == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Decoder is not ready";
        }
        return false;
    }
    int result = avcodec_send_packet(codecContext_, nullptr);
    if (result < 0 && result != AVERROR_EOF) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        flexmusic::core::levelLog(kDecoderTag).e("flush failed error=%s",
                                                 errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }
    return drainFrames(outputFrames, errorMessage);
}

bool FfmpegAudioDecoder::reset(std::string* errorMessage) {
    if (codecContext_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Decoder is not ready";
        }
        return false;
    }

    avcodec_flush_buffers(codecContext_);
    if (swrContext_ != nullptr) {
        swr_close(swrContext_);
        const int result = swr_init(swrContext_);
        if (result < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = avErrorToString(result);
            }
            flexmusic::core::levelLog(kDecoderTag).e("reset swr failed error=%s",
                                                     errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }
    }
    av_frame_unref(frame_);
    firstOutputFrameLogged_ = false;
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

bool FfmpegAudioDecoder::drainFrames(std::vector<PcmFrame>* outputFrames, std::string* errorMessage) {
    while (true) {
        const int result = avcodec_receive_frame(codecContext_, frame_);
        if (result == AVERROR(EAGAIN) || result == AVERROR_EOF) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        if (result < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = avErrorToString(result);
            }
            flexmusic::core::levelLog(kDecoderTag).e("receive frame failed error=%s",
                                                     errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }

        const int outputSamples = av_rescale_rnd(
                swr_get_delay(swrContext_, codecContext_->sample_rate) + frame_->nb_samples,
                outputSampleRate_,
                codecContext_->sample_rate,
                AV_ROUND_UP);
        PcmFrame pcmFrame;
        pcmFrame.sampleRate = outputSampleRate_;
        pcmFrame.channelCount = outputChannelCount_;
        pcmFrame.positionMs = packetToPositionMs(frame_, timeBase_);
        pcmFrame.durationMs = outputSamples > 0
                ? static_cast<int64_t>(outputSamples) * 1000 / outputSampleRate_
                : 0;
        pcmFrame.data.resize(static_cast<std::size_t>(outputSamples) * outputChannelCount_ * sizeof(int16_t));

        uint8_t* destination = pcmFrame.data.data();
        int convertedSamples = swr_convert(
                swrContext_,
                &destination,
                outputSamples,
                const_cast<const uint8_t**>(frame_->extended_data),
                frame_->nb_samples);
        if (convertedSamples < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = avErrorToString(convertedSamples);
            }
            flexmusic::core::levelLog(kDecoderTag).e("convert samples failed error=%s",
                                                     errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }

        pcmFrame.data.resize(static_cast<std::size_t>(convertedSamples) * outputChannelCount_ * sizeof(int16_t));
        if (!firstOutputFrameLogged_) {
            firstOutputFrameLogged_ = true;
            flexmusic::core::levelLog(kDecoderTag).i(
                    "first decoded frame positionMs=%lld durationMs=%lld samples=%d bytes=%zu",
                    static_cast<long long>(pcmFrame.positionMs),
                    static_cast<long long>(pcmFrame.durationMs),
                    convertedSamples,
                    pcmFrame.data.size());
        }
        outputFrames->push_back(std::move(pcmFrame));
        av_frame_unref(frame_);
    }
}

void FfmpegAudioDecoder::close() {
    if (frame_ != nullptr) {
        av_frame_free(&frame_);
    }
    if (codecContext_ != nullptr) {
        avcodec_free_context(&codecContext_);
    }
    if (swrContext_ != nullptr) {
        swr_free(&swrContext_);
    }
    if (outputChannelLayout_ != nullptr) {
        av_channel_layout_uninit(outputChannelLayout_);
        delete outputChannelLayout_;
        outputChannelLayout_ = nullptr;
    }
    if (timeBase_ != nullptr) {
        delete timeBase_;
        timeBase_ = nullptr;
    }
    outputSampleRate_ = 0;
    outputChannelCount_ = 0;
}

} // namespace codec
} // namespace media
} // namespace flexmusic
