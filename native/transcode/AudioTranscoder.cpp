#include "transcode/AudioTranscoder.h"

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

extern "C" {
#include <FLAC/stream_encoder.h>
#include <lame.h>
#include <ogg/ogg.h>
#include <vorbis/vorbisenc.h>
#include <libavcodec/packet.h>
}

#include "logger.h"
#include "DataSourceSpec.h"
#include "PassthroughFileIo.h"
#include "FfmpegAudioDecoder.h"
#include "FfmpegDemuxer.h"
#include "EncodedPacket.h"
#include "PcmFrame.h"
#include "AvioDataSource.h"

namespace flexmusic {
namespace transcode {

namespace {

constexpr char kTranscodeTag[] = "TranscodeEngine";

void releasePacket(AVPacket** packet) {
    if (packet != nullptr && *packet != nullptr) {
        av_packet_free(packet);
    }
}

std::string formatToString(OutputFormat format) {
    switch (format) {
        case OutputFormat::FLAC:
            return "flac";
        case OutputFormat::OGG:
            return "ogg";
        case OutputFormat::WAV:
            return "wav";
        case OutputFormat::MP3:
        default:
            return "mp3";
    }
}

class OutputEncoder final {
public:
    ~OutputEncoder() {
        close();
    }

    bool open(const std::string& outputPath,
              OutputFormat format,
              int sampleRate,
              int channelCount,
              int bitrateKbps,
              std::string* errorMessage) {
        close();
        outputPath_ = outputPath;
        format_ = format;
        sampleRate_ = sampleRate;
        channelCount_ = channelCount;
        bitrateKbps_ = bitrateKbps;
        switch (format_) {
            case OutputFormat::FLAC:
                return openFlac(errorMessage);
            case OutputFormat::OGG:
                return openOgg(errorMessage);
            case OutputFormat::WAV:
                return openWav(errorMessage);
            case OutputFormat::MP3:
            default:
                return openMp3(errorMessage);
        }
    }

    bool encodeFrame(const flexmusic::media::PcmFrame& frame, std::string* errorMessage) {
        if (frame.data.empty()) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        switch (format_) {
            case OutputFormat::FLAC:
                return encodeFlac(frame, errorMessage);
            case OutputFormat::OGG:
                return encodeOgg(frame, errorMessage);
            case OutputFormat::WAV:
                return encodeWav(frame, errorMessage);
            case OutputFormat::MP3:
            default:
                return encodeMp3(frame, errorMessage);
        }
    }

    bool finish(std::string* errorMessage) {
        switch (format_) {
            case OutputFormat::FLAC:
                return finishFlac(errorMessage);
            case OutputFormat::OGG:
                return finishOgg(errorMessage);
            case OutputFormat::WAV:
                return finishWav(errorMessage);
            case OutputFormat::MP3:
            default:
                return finishMp3(errorMessage);
        }
    }

    void close() {
        finishPending_ = false;
        if (lameContext_ != nullptr) {
            lame_close(lameContext_);
            lameContext_ = nullptr;
        }
        if (flacEncoder_ != nullptr) {
            FLAC__stream_encoder_delete(flacEncoder_);
            flacEncoder_ = nullptr;
        }
        if (oggInitialized_) {
            ogg_stream_clear(&oggStreamState_);
            vorbis_block_clear(&vorbisBlock_);
            vorbis_dsp_clear(&vorbisDspState_);
            vorbis_comment_clear(&vorbisComment_);
            vorbis_info_clear(&vorbisInfo_);
            oggInitialized_ = false;
        }
        if (outputFile_ != nullptr) {
            std::fclose(outputFile_);
            outputFile_ = nullptr;
        }
        wavDataBytes_ = 0;
    }

private:
    bool writeFully(const void* buffer, size_t size, std::string* errorMessage) {
        if (outputFile_ == nullptr) {
            if (errorMessage != nullptr) {
                *errorMessage = "Output file is not opened";
            }
            return false;
        }
        if (size == 0) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        const size_t written = std::fwrite(buffer, 1, size, outputFile_);
        if (written != size) {
            if (errorMessage != nullptr) {
                *errorMessage = std::string("Write output failed: ") + std::strerror(errno);
            }
            return false;
        }
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    bool openOutputFile(std::string* errorMessage) {
        outputFile_ = std::fopen(outputPath_.c_str(), "wb");
        if (outputFile_ == nullptr) {
            if (errorMessage != nullptr) {
                *errorMessage = std::string("Open output file failed: ") + std::strerror(errno);
            }
            return false;
        }
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    bool openMp3(std::string* errorMessage) {
        if (!openOutputFile(errorMessage)) {
            return false;
        }
        lameContext_ = lame_init();
        if (lameContext_ == nullptr) {
            if (errorMessage != nullptr) {
                *errorMessage = "Initialize LAME failed";
            }
            return false;
        }
        lame_set_in_samplerate(lameContext_, sampleRate_);
        lame_set_out_samplerate(lameContext_, sampleRate_);
        lame_set_num_channels(lameContext_, channelCount_);
        lame_set_brate(lameContext_, bitrateKbps_ > 0 ? bitrateKbps_ : 320);
        lame_set_quality(lameContext_, 2);
        if (lame_init_params(lameContext_) < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Initialize LAME parameters failed";
            }
            return false;
        }
        return true;
    }

    bool encodeMp3(const flexmusic::media::PcmFrame& frame, std::string* errorMessage) {
        const int samplesPerChannel = static_cast<int>(frame.data.size() / (sizeof(int16_t) * channelCount_));
        if (samplesPerChannel <= 0) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        const auto* pcm = reinterpret_cast<const short int*>(frame.data.data());
        const int mp3BufferSize = static_cast<int>(1.25 * samplesPerChannel + 7200);
        std::vector<unsigned char> encodedBuffer(static_cast<size_t>(mp3BufferSize));
        int encodedBytes = 0;
        if (channelCount_ == 1) {
            encodedBytes = lame_encode_buffer(
                    lameContext_,
                    pcm,
                    pcm,
                    samplesPerChannel,
                    encodedBuffer.data(),
                    mp3BufferSize);
        } else {
            encodedBytes = lame_encode_buffer_interleaved(
                    lameContext_,
                    const_cast<short int*>(pcm),
                    samplesPerChannel,
                    encodedBuffer.data(),
                    mp3BufferSize);
        }
        if (encodedBytes < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Encode MP3 frame failed";
            }
            return false;
        }
        return writeFully(encodedBuffer.data(), static_cast<size_t>(encodedBytes), errorMessage);
    }

    bool finishMp3(std::string* errorMessage) {
        if (lameContext_ == nullptr || finishPending_) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        finishPending_ = true;
        std::vector<unsigned char> encodedBuffer(7200);
        const int encodedBytes = lame_encode_flush(
                lameContext_,
                encodedBuffer.data(),
                static_cast<int>(encodedBuffer.size()));
        if (encodedBytes < 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Flush MP3 encoder failed";
            }
            return false;
        }
        return writeFully(encodedBuffer.data(), static_cast<size_t>(encodedBytes), errorMessage);
    }

    bool openFlac(std::string* errorMessage) {
        flacEncoder_ = FLAC__stream_encoder_new();
        if (flacEncoder_ == nullptr) {
            if (errorMessage != nullptr) {
                *errorMessage = "Create FLAC encoder failed";
            }
            return false;
        }
        FLAC__stream_encoder_set_channels(flacEncoder_, channelCount_);
        FLAC__stream_encoder_set_bits_per_sample(flacEncoder_, 16);
        FLAC__stream_encoder_set_sample_rate(flacEncoder_, sampleRate_);
        FLAC__stream_encoder_set_compression_level(flacEncoder_, 5);
        const FLAC__StreamEncoderInitStatus initStatus =
                FLAC__stream_encoder_init_file(flacEncoder_, outputPath_.c_str(), nullptr, nullptr);
        if (initStatus != FLAC__STREAM_ENCODER_INIT_STATUS_OK) {
            if (errorMessage != nullptr) {
                *errorMessage = FLAC__StreamEncoderInitStatusString[initStatus];
            }
            return false;
        }
        return true;
    }

    bool encodeFlac(const flexmusic::media::PcmFrame& frame, std::string* errorMessage) {
        const size_t sampleCount = frame.data.size() / sizeof(int16_t);
        if (sampleCount == 0) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        std::vector<FLAC__int32> flacSamples(sampleCount);
        const auto* pcm = reinterpret_cast<const int16_t*>(frame.data.data());
        for (size_t index = 0; index < sampleCount; index++) {
            flacSamples[index] = pcm[index];
        }
        const FLAC__bool ok = FLAC__stream_encoder_process_interleaved(
                flacEncoder_,
                flacSamples.data(),
                static_cast<uint32_t>(sampleCount / static_cast<size_t>(channelCount_)));
        if (!ok) {
            if (errorMessage != nullptr) {
                *errorMessage = FLAC__StreamEncoderStateString[FLAC__stream_encoder_get_state(flacEncoder_)];
            }
            return false;
        }
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    bool finishFlac(std::string* errorMessage) {
        if (flacEncoder_ == nullptr || finishPending_) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        finishPending_ = true;
        if (!FLAC__stream_encoder_finish(flacEncoder_)) {
            if (errorMessage != nullptr) {
                *errorMessage = FLAC__StreamEncoderStateString[FLAC__stream_encoder_get_state(flacEncoder_)];
            }
            return false;
        }
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }

    bool openOgg(std::string* errorMessage) {
        if (!openOutputFile(errorMessage)) {
            return false;
        }
        vorbis_info_init(&vorbisInfo_);
        const int initResult = vorbis_encode_init(
                &vorbisInfo_,
                channelCount_,
                sampleRate_,
                -1,
                (bitrateKbps_ > 0 ? bitrateKbps_ : 192) * 1000,
                -1);
        if (initResult != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Initialize OGG encoder failed";
            }
            return false;
        }
        vorbis_comment_init(&vorbisComment_);
        vorbis_comment_add_tag(&vorbisComment_, const_cast<char*>("ENCODER"), const_cast<char*>("FlexMusic"));
        if (vorbis_analysis_init(&vorbisDspState_, &vorbisInfo_) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Initialize OGG analysis state failed";
            }
            return false;
        }
        if (vorbis_block_init(&vorbisDspState_, &vorbisBlock_) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Initialize OGG block failed";
            }
            return false;
        }
        ogg_stream_init(&oggStreamState_, std::rand());
        oggInitialized_ = true;
        ogg_packet headerPacket;
        ogg_packet commentPacket;
        ogg_packet codePacket;
        if (vorbis_analysis_headerout(
                &vorbisDspState_,
                &vorbisComment_,
                &headerPacket,
                &commentPacket,
                &codePacket) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Build OGG header failed";
            }
            return false;
        }
        ogg_stream_packetin(&oggStreamState_, &headerPacket);
        ogg_stream_packetin(&oggStreamState_, &commentPacket);
        ogg_stream_packetin(&oggStreamState_, &codePacket);
        while (true) {
            ogg_page page;
            const int result = ogg_stream_flush(&oggStreamState_, &page);
            if (result == 0) {
                break;
            }
            if (!writeFully(page.header, static_cast<size_t>(page.header_len), errorMessage)
                    || !writeFully(page.body, static_cast<size_t>(page.body_len), errorMessage)) {
                return false;
            }
        }
        return true;
    }

    bool drainOggPages(bool forceFlushPages, std::string* errorMessage) {
        while (vorbis_analysis_blockout(&vorbisDspState_, &vorbisBlock_) == 1) {
            vorbis_analysis(&vorbisBlock_, nullptr);
            vorbis_bitrate_addblock(&vorbisBlock_);
            ogg_packet packet;
            while (vorbis_bitrate_flushpacket(&vorbisDspState_, &packet)) {
                ogg_stream_packetin(&oggStreamState_, &packet);
                while (true) {
                    ogg_page page;
                    const int result = forceFlushPages
                            ? ogg_stream_flush(&oggStreamState_, &page)
                            : ogg_stream_pageout(&oggStreamState_, &page);
                    if (result == 0) {
                        break;
                    }
                    if (!writeFully(page.header, static_cast<size_t>(page.header_len), errorMessage)
                            || !writeFully(page.body, static_cast<size_t>(page.body_len), errorMessage)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    bool encodeOgg(const flexmusic::media::PcmFrame& frame, std::string* errorMessage) {
        const int sampleCountPerChannel = static_cast<int>(frame.data.size() / (sizeof(int16_t) * channelCount_));
        if (sampleCountPerChannel <= 0) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        float** analysisBuffer = vorbis_analysis_buffer(&vorbisDspState_, sampleCountPerChannel);
        const auto* pcm = reinterpret_cast<const int16_t*>(frame.data.data());
        for (int sampleIndex = 0; sampleIndex < sampleCountPerChannel; sampleIndex++) {
            for (int channelIndex = 0; channelIndex < channelCount_; channelIndex++) {
                const int16_t sample = pcm[sampleIndex * channelCount_ + channelIndex];
                analysisBuffer[channelIndex][sampleIndex] = static_cast<float>(sample) / 32768.0f;
            }
        }
        vorbis_analysis_wrote(&vorbisDspState_, sampleCountPerChannel);
        return drainOggPages(false, errorMessage);
    }

    bool finishOgg(std::string* errorMessage) {
        if (!oggInitialized_ || finishPending_) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        finishPending_ = true;
        vorbis_analysis_wrote(&vorbisDspState_, 0);
        return drainOggPages(true, errorMessage);
    }

    bool writeLe16(uint16_t value, std::string* errorMessage) {
        uint8_t bytes[2] = {
                static_cast<uint8_t>(value & 0xFFu),
                static_cast<uint8_t>((value >> 8) & 0xFFu)
        };
        return writeFully(bytes, sizeof(bytes), errorMessage);
    }

    bool writeLe32(uint32_t value, std::string* errorMessage) {
        uint8_t bytes[4] = {
                static_cast<uint8_t>(value & 0xFFu),
                static_cast<uint8_t>((value >> 8) & 0xFFu),
                static_cast<uint8_t>((value >> 16) & 0xFFu),
                static_cast<uint8_t>((value >> 24) & 0xFFu)
        };
        return writeFully(bytes, sizeof(bytes), errorMessage);
    }

    bool openWav(std::string* errorMessage) {
        if (!openOutputFile(errorMessage)) {
            return false;
        }
        if (!writeFully("RIFF", 4, errorMessage)
                || !writeLe32(0, errorMessage)
                || !writeFully("WAVE", 4, errorMessage)
                || !writeFully("fmt ", 4, errorMessage)
                || !writeLe32(16, errorMessage)
                || !writeLe16(1, errorMessage)
                || !writeLe16(static_cast<uint16_t>(channelCount_), errorMessage)
                || !writeLe32(static_cast<uint32_t>(sampleRate_), errorMessage)
                || !writeLe32(static_cast<uint32_t>(sampleRate_ * channelCount_ * sizeof(int16_t)), errorMessage)
                || !writeLe16(static_cast<uint16_t>(channelCount_ * sizeof(int16_t)), errorMessage)
                || !writeLe16(16, errorMessage)
                || !writeFully("data", 4, errorMessage)
                || !writeLe32(0, errorMessage)) {
            return false;
        }
        return true;
    }

    bool encodeWav(const flexmusic::media::PcmFrame& frame, std::string* errorMessage) {
        wavDataBytes_ += static_cast<uint32_t>(frame.data.size());
        return writeFully(frame.data.data(), frame.data.size(), errorMessage);
    }

    bool finishWav(std::string* errorMessage) {
        if (outputFile_ == nullptr || finishPending_) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        finishPending_ = true;
        if (std::fseek(outputFile_, 4, SEEK_SET) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Seek WAV header failed";
            }
            return false;
        }
        uint32_t riffSize = 36u + wavDataBytes_;
        if (!writeLe32(riffSize, errorMessage)) {
            return false;
        }
        if (std::fseek(outputFile_, 40, SEEK_SET) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Seek WAV data header failed";
            }
            return false;
        }
        if (!writeLe32(wavDataBytes_, errorMessage)) {
            return false;
        }
        if (std::fflush(outputFile_) != 0) {
            if (errorMessage != nullptr) {
                *errorMessage = "Flush WAV output failed";
            }
            return false;
        }
        return true;
    }

    std::string outputPath_;
    OutputFormat format_ = OutputFormat::MP3;
    int sampleRate_ = 0;
    int channelCount_ = 0;
    int bitrateKbps_ = 0;
    bool finishPending_ = false;
    std::FILE* outputFile_ = nullptr;
    lame_t lameContext_ = nullptr;
    FLAC__StreamEncoder* flacEncoder_ = nullptr;
    ogg_stream_state oggStreamState_;
    vorbis_info vorbisInfo_;
    vorbis_comment vorbisComment_;
    vorbis_dsp_state vorbisDspState_;
    vorbis_block vorbisBlock_;
    bool oggInitialized_ = false;
    uint32_t wavDataBytes_ = 0;
};

bool processDecodedFrames(const std::vector<flexmusic::media::PcmFrame>& decodedFrames,
                         const TranscodeRequest& request,
                         OutputEncoder* encoder,
                         bool* encoderOpened,
                         bool* wroteAnyFrame,
                         std::string* errorMessage) {
    if (encoder == nullptr || encoderOpened == nullptr || wroteAnyFrame == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Encoder state is invalid";
        }
        return false;
    }
    for (const flexmusic::media::PcmFrame& frame : decodedFrames) {
        if (frame.data.empty()) {
            continue;
        }
        if (request.sampleRate > 0 && request.sampleRate != frame.sampleRate) {
            if (errorMessage != nullptr) {
                *errorMessage = "Sample rate conversion is not supported yet";
            }
            return false;
        }
        if (!*encoderOpened) {
            const int effectiveSampleRate = request.sampleRate > 0 ? request.sampleRate : frame.sampleRate;
            if (!encoder->open(
                    request.targetPath,
                    request.outputFormat,
                    effectiveSampleRate,
                    frame.channelCount,
                    request.bitrateKbps,
                    errorMessage)) {
                return false;
            }
            *encoderOpened = true;
        }
        if (!encoder->encodeFrame(frame, errorMessage)) {
            return false;
        }
        *wroteAnyFrame = true;
    }
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

} // namespace

bool AudioTranscoder::transcode(const TranscodeRequest& request, std::string* errorMessage) {
    const auto log = flexmusic::utils::levelLog(kTranscodeTag);
    if (request.sourcePath.empty() || request.targetPath.empty()) {
        if (errorMessage != nullptr) {
            *errorMessage = "Transcode source or target path is empty";
        }
        return false;
    }

    const auto startedAt = std::chrono::steady_clock::now();
    log.i("transcode start source=%s target=%s format=%s bitrateKbps=%d sampleRate=%d",
          request.sourcePath.c_str(),
          request.targetPath.c_str(),
          formatToString(request.outputFormat).c_str(),
          request.bitrateKbps,
          request.sampleRate);

    flexmusic::io::DataSourceSpec dataSourceSpec;
    dataSourceSpec.sourceId = "transcode:" + request.sourcePath;
    dataSourceSpec.originalUrl = request.sourcePath;
    dataSourceSpec.resolvedUrl = request.sourcePath;
    dataSourceSpec.localSource = true;
    dataSourceSpec.seekable = true;

    flexmusic::media::source::AvioDataSource dataSource;
    std::string stageError;
    if (!dataSource.open(std::make_unique<flexmusic::io::PassthroughFileIo>(), dataSourceSpec, &stageError)) {
        if (errorMessage != nullptr) {
            *errorMessage = stageError.empty() ? "Open transcode source failed" : stageError;
        }
        log.e("transcode stage=source-open source=%s error=%s",
              request.sourcePath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    flexmusic::media::demux::FfmpegDemuxer demuxer;
    if (!demuxer.open(&dataSource, &stageError)) {
        if (errorMessage != nullptr) {
            *errorMessage = stageError.empty() ? "Open demuxer failed" : stageError;
        }
        log.e("transcode stage=demux-open source=%s error=%s",
              request.sourcePath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    flexmusic::media::codec::FfmpegAudioDecoder decoder;
    if (!decoder.open(demuxer.audioStreamInfo(), &stageError)) {
        if (errorMessage != nullptr) {
            *errorMessage = stageError.empty() ? "Open decoder failed" : stageError;
        }
        log.e("transcode stage=decoder-open source=%s error=%s",
              request.sourcePath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    OutputEncoder encoder;
    bool encoderOpened = false;
    bool wroteAnyFrame = false;
    while (true) {
        flexmusic::media::EncodedPacket encodedPacket;
        if (!demuxer.readPacket(&encodedPacket, &stageError)) {
            if (errorMessage != nullptr) {
                *errorMessage = stageError.empty() ? "Read packet failed" : stageError;
            }
            log.e("transcode stage=packet-read source=%s error=%s",
                  request.sourcePath.c_str(),
                  errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }

        std::vector<flexmusic::media::PcmFrame> decodedFrames;
        bool decodeOk = true;
        if (encodedPacket.endOfStream) {
            decodeOk = decoder.flush(&decodedFrames, &stageError);
        } else {
            decodeOk = decoder.decodePacket(encodedPacket, &decodedFrames, &stageError);
        }
        releasePacket(&encodedPacket.packet);
        if (!decodeOk) {
            if (errorMessage != nullptr) {
                *errorMessage = stageError.empty() ? "Decode packet failed" : stageError;
            }
            log.e("transcode stage=decode source=%s error=%s",
                  request.sourcePath.c_str(),
                  errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }
        if (!processDecodedFrames(decodedFrames, request, &encoder, &encoderOpened, &wroteAnyFrame, errorMessage)) {
            log.e("transcode stage=encode source=%s error=%s",
                  request.sourcePath.c_str(),
                  errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }
        if (encodedPacket.endOfStream) {
            break;
        }
    }

    if (!encoderOpened || !wroteAnyFrame) {
        if (errorMessage != nullptr) {
            *errorMessage = "No audio frames decoded for transcode";
        }
        log.e("transcode stage=empty-output source=%s error=%s",
              request.sourcePath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }
    if (!encoder.finish(errorMessage)) {
        log.e("transcode stage=encoder-finish source=%s error=%s",
              request.sourcePath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - startedAt).count();
    log.i("transcode complete source=%s target=%s format=%s elapsedMs=%lld",
          request.sourcePath.c_str(),
          request.targetPath.c_str(),
          formatToString(request.outputFormat).c_str(),
          static_cast<long long>(elapsedMs));
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

} // namespace transcode
} // namespace flexmusic
