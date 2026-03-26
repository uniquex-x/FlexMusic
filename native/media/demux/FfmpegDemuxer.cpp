#include "FfmpegDemuxer.h"

#include "logger.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <memory>
#include <string>

#include "AvioDataSource.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/error.h>
}

namespace flexmusic {
namespace media {
namespace demux {

namespace {

constexpr char kDemuxerTag[] = "FfmpegDemuxer";

std::string avErrorToString(int errorCode) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(errorCode, buffer, sizeof(buffer));
    return std::string(buffer);
}

std::string toLowerCopy(const std::string& value) {
    std::string result = value;
    std::transform(result.begin(), result.end(), result.begin(), [](unsigned char current) {
        return static_cast<char>(std::tolower(current));
    });
    return result;
}

bool containsManifestHint(const std::string& resolvedUrl, const std::string& contentType) {
    if (resolvedUrl.find(".m3u8") != std::string::npos
            || resolvedUrl.find(".m3u") != std::string::npos
            || resolvedUrl.find(".pls") != std::string::npos
            || resolvedUrl.find(".xspf") != std::string::npos
            || resolvedUrl.find("playlist") != std::string::npos) {
        return true;
    }
    return contentType.find("mpegurl") != std::string::npos
            || contentType.find("vnd.apple.mpegurl") != std::string::npos
            || contentType.find("application/x-mpegurl") != std::string::npos;
}

bool hasProgressiveAudioHint(const std::string& resolvedUrl, const std::string& contentType) {
    return resolvedUrl.find(".mp3") != std::string::npos
            || resolvedUrl.find(".aac") != std::string::npos
            || resolvedUrl.find(".m4a") != std::string::npos
            || resolvedUrl.find(".ogg") != std::string::npos
            || resolvedUrl.find(".opus") != std::string::npos
            || contentType.find("audio/") != std::string::npos;
}

const AVInputFormat* resolveInputFormatHint(const flexmusic::io::DataSourceSpec& spec,
                                            std::string* formatHintName) {
    const std::string resolvedUrl = toLowerCopy(spec.resolvedUrl);
    const std::string contentType = toLowerCopy(spec.contentType);
    if (containsManifestHint(resolvedUrl, contentType)) {
        return nullptr;
    }
    if (resolvedUrl.find(".mp3") != std::string::npos
            || contentType.find("audio/mpeg") != std::string::npos
            || contentType.find("audio/mp3") != std::string::npos) {
        if (formatHintName != nullptr) {
            *formatHintName = "mp3";
        }
        return av_find_input_format("mp3");
    }
    if (resolvedUrl.find(".aac") != std::string::npos
            || contentType.find("audio/aac") != std::string::npos
            || contentType.find("audio/aacp") != std::string::npos) {
        if (formatHintName != nullptr) {
            *formatHintName = "aac";
        }
        return av_find_input_format("aac");
    }
    if (resolvedUrl.find(".ogg") != std::string::npos
            || contentType.find("audio/ogg") != std::string::npos
            || contentType.find("application/ogg") != std::string::npos) {
        if (formatHintName != nullptr) {
            *formatHintName = "ogg";
        }
        return av_find_input_format("ogg");
    }
    return nullptr;
}

bool isProgressiveAudioLowLatencyCandidate(const flexmusic::io::DataSourceSpec& spec) {
    const std::string scheme = flexmusic::io::resolveScheme(spec.resolvedUrl);
    if (spec.seekable || (scheme != "http" && scheme != "https")) {
        return false;
    }

    const std::string resolvedUrl = toLowerCopy(spec.resolvedUrl);
    const std::string contentType = toLowerCopy(spec.contentType);
    if (containsManifestHint(resolvedUrl, contentType)) {
        return false;
    }
    return hasProgressiveAudioHint(resolvedUrl, contentType);
}

} // namespace

FfmpegDemuxer::FfmpegDemuxer() = default;

FfmpegDemuxer::~FfmpegDemuxer() {
    close();
}

bool FfmpegDemuxer::open(source::AvioDataSource* dataSource, std::string* errorMessage) {
    close();
    const auto log = flexmusic::utils::levelLog(kDemuxerTag);
    const auto startedAt = std::chrono::steady_clock::now();
    if (dataSource == nullptr || dataSource->context() == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "AVIO data source is not ready";
        }
        return false;
    }

    formatContext_ = avformat_alloc_context();
    if (formatContext_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Allocate format context failed";
        }
        return false;
    }

    formatContext_->pb = dataSource->context();
    formatContext_->flags |= AVFMT_FLAG_CUSTOM_IO;
    formatContext_->pb->seekable = dataSource->spec().seekable ? AVIO_SEEKABLE_NORMAL : 0;

    AVDictionary* options = nullptr;
    const bool lowLatencyOpen = isProgressiveAudioLowLatencyCandidate(dataSource->spec());
    std::string inputFormatHintName;
    const AVInputFormat* inputFormatHint = resolveInputFormatHint(
            dataSource->spec(),
            &inputFormatHintName);
    av_dict_set(&options, "probesize", lowLatencyOpen ? "4096" : "32768", 0);
    av_dict_set(&options, "analyzeduration", lowLatencyOpen ? "0" : "200000", 0);
    if (lowLatencyOpen) {
        av_dict_set(&options, "fpsprobesize", "0", 0);
        av_dict_set(&options, "max_probe_packets", "1", 0);
    }
    av_dict_set(&options, "fflags", "nobuffer", 0);
    av_dict_set(&options, "flush_packets", "1", 0);
    log.i("open tuning sourceId=%s customIo=1 hintUrl=%s lowLatency=%d formatHint=%s",
          dataSource->spec().sourceId.c_str(),
          dataSource->spec().resolvedUrl.c_str(),
          lowLatencyOpen ? 1 : 0,
          inputFormatHintName.empty() ? "none" : inputFormatHintName.c_str());

    const auto openInputStartedAt = std::chrono::steady_clock::now();
    // The actual bytes come from the custom AVIO context above. Keep the URL only as a
    // demux hint so FFmpeg can still use extension/content heuristics for format selection.
    const char* formatHintUrl = dataSource->spec().resolvedUrl.empty()
            ? nullptr
            : dataSource->spec().resolvedUrl.c_str();
    int result = avformat_open_input(&formatContext_, formatHintUrl, inputFormatHint, &options);
    av_dict_free(&options);
    if (result < 0 || formatContext_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result < 0 ? result : AVERROR_UNKNOWN);
        }
        const auto openInputElapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - openInputStartedAt).count();
        log.e("open input failed sourceId=%s customIo=1 hintUrl=%s elapsedMs=%lld error=%s",
              dataSource->spec().sourceId.c_str(),
              dataSource->spec().resolvedUrl.c_str(),
              static_cast<long long>(openInputElapsedMs),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        close();
        return false;
    }

    const auto openInputElapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - openInputStartedAt).count();
    const auto streamInfoStartedAt = std::chrono::steady_clock::now();
    result = avformat_find_stream_info(formatContext_, nullptr);
    if (result < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        const auto streamInfoElapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - streamInfoStartedAt).count();
        log.e("find stream info failed sourceId=%s url=%s openInputMs=%lld streamInfoMs=%lld error=%s",
              dataSource->spec().sourceId.c_str(),
              dataSource->spec().resolvedUrl.c_str(),
              static_cast<long long>(openInputElapsedMs),
              static_cast<long long>(streamInfoElapsedMs),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        close();
        return false;
    }
    const auto streamInfoElapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - streamInfoStartedAt).count();

    const AVStream* stream = nullptr;
    for (unsigned int index = 0; index < formatContext_->nb_streams; ++index) {
        const AVStream* candidate = formatContext_->streams[index];
        if (candidate != nullptr && candidate->codecpar != nullptr
                && candidate->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            stream = candidate;
            streamInfo_.streamIndex = static_cast<int>(index);
            break;
        }
    }

    if (stream == nullptr || streamInfo_.streamIndex < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "No audio stream found";
        }
        log.e("no audio stream sourceId=%s url=%s",
              dataSource->spec().sourceId.c_str(),
              dataSource->spec().resolvedUrl.c_str());
        close();
        return false;
    }

    streamInfo_.codecParameters = avcodec_parameters_alloc();
    if (streamInfo_.codecParameters == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "Allocate codec parameters failed";
        }
        close();
        return false;
    }
    avcodec_parameters_copy(streamInfo_.codecParameters, stream->codecpar);

    streamInfo_.sampleRate = stream->codecpar->sample_rate;
    streamInfo_.channelCount = stream->codecpar->ch_layout.nb_channels;
    timeBase_ = new AVRational(stream->time_base);
    streamInfo_.timeBase = timeBase_;
    durationMs_ = formatContext_->duration > 0
            ? av_rescale_q(formatContext_->duration, AV_TIME_BASE_Q, AVRational{1, 1000})
            : 0;
    streamInfo_.durationMs = durationMs_;
    seekable_ = (formatContext_->pb != nullptr && (formatContext_->pb->seekable & AVIO_SEEKABLE_NORMAL) != 0);
    const auto totalElapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - startedAt).count();
    log.i("opened sourceId=%s customIo=1 hintUrl=%s streamIndex=%d sampleRate=%d channels=%d durationMs=%lld seekable=%d openInputMs=%lld streamInfoMs=%lld totalMs=%lld",
          dataSource->spec().sourceId.c_str(),
          dataSource->spec().resolvedUrl.c_str(),
          streamInfo_.streamIndex,
          streamInfo_.sampleRate,
          streamInfo_.channelCount,
          static_cast<long long>(durationMs_),
          seekable_ ? 1 : 0,
          static_cast<long long>(openInputElapsedMs),
          static_cast<long long>(streamInfoElapsedMs),
          static_cast<long long>(totalElapsedMs));

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

bool FfmpegDemuxer::readPacket(flexmusic::media::EncodedPacket* outputPacket, std::string* errorMessage) {
    const auto log = flexmusic::utils::levelLog(kDemuxerTag);
    if (outputPacket == nullptr || formatContext_ == nullptr || streamInfo_.streamIndex < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "Demuxer is not opened";
        }
        return false;
    }

    outputPacket->endOfStream = false;
    outputPacket->packet = nullptr;

    while (true) {
        AVPacket* packet = av_packet_alloc();
        if (packet == nullptr) {
            if (errorMessage != nullptr) {
                *errorMessage = "Allocate packet failed";
            }
            return false;
        }

        int result = av_read_frame(formatContext_, packet);
        if (result == AVERROR_EOF) {
            av_packet_free(&packet);
            outputPacket->endOfStream = true;
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return true;
        }
        if (result < 0) {
            av_packet_free(&packet);
            if (errorMessage != nullptr) {
                *errorMessage = avErrorToString(result);
            }
            log.e("read frame failed streamIndex=%d error=%s",
                  streamInfo_.streamIndex,
                  errorMessage != nullptr ? errorMessage->c_str() : "");
            return false;
        }
        if (packet->stream_index != streamInfo_.streamIndex) {
            av_packet_free(&packet);
            continue;
        }

        outputPacket->packet = packet;
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return true;
    }
}

bool FfmpegDemuxer::seekTo(int64_t positionMs, std::string* errorMessage) {
    const auto log = flexmusic::utils::levelLog(kDemuxerTag);
    if (!seekable_ || formatContext_ == nullptr || streamInfo_.streamIndex < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "Current data source is not seekable";
        }
        return false;
    }

    const AVRational timeBase = *timeBase_;
    const int64_t target = av_rescale_q(positionMs, AVRational{1, 1000}, timeBase);
    const int result = av_seek_frame(formatContext_, streamInfo_.streamIndex, target, AVSEEK_FLAG_BACKWARD);
    if (result < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result);
        }
        log.e("seek failed targetMs=%lld error=%s",
              static_cast<long long>(positionMs),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }
    avformat_flush(formatContext_);
    log.i("seek success targetMs=%lld streamIndex=%d",
          static_cast<long long>(positionMs),
          streamInfo_.streamIndex);

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void FfmpegDemuxer::close() {
    if (formatContext_ != nullptr) {
        avformat_close_input(&formatContext_);
    }
    if (streamInfo_.codecParameters != nullptr) {
        avcodec_parameters_free(&streamInfo_.codecParameters);
    }
    if (timeBase_ != nullptr) {
        delete timeBase_;
        timeBase_ = nullptr;
    }
    streamInfo_ = AudioStreamInfo();
    durationMs_ = 0;
    seekable_ = false;
}

const AudioStreamInfo& FfmpegDemuxer::audioStreamInfo() const {
    return streamInfo_;
}

int64_t FfmpegDemuxer::durationMs() const {
    return durationMs_;
}

bool FfmpegDemuxer::isSeekable() const {
    return seekable_;
}

} // namespace demux
} // namespace media
} // namespace flexmusic
