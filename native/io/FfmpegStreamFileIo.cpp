#include "FfmpegStreamFileIo.h"

#include <mutex>
#include <memory>
#include <string>

extern "C" {
#include <libavformat/avio.h>
#include <libavformat/avformat.h>
#include <libavutil/dict.h>
#include <libavutil/error.h>
}

namespace flexmusic {
namespace io {

namespace {

std::once_flag g_ffmpegNetworkInitFlag;

void ensureFfmpegNetworkInitialized() {
    std::call_once(g_ffmpegNetworkInitFlag, []() {
        avformat_network_init();
    });
}

std::string avErrorToString(int errorCode) {
    char buffer[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(errorCode, buffer, sizeof(buffer));
    return std::string(buffer);
}

} // namespace

FfmpegStreamFileIo::FfmpegStreamFileIo() = default;

FfmpegStreamFileIo::~FfmpegStreamFileIo() {
    close();
}

bool FfmpegStreamFileIo::open(const DataSourceSpec& spec, std::string* errorMessage) {
    close();
    ensureFfmpegNetworkInitialized();

    AVDictionary* options = nullptr;
    if (!spec.userAgent.empty()) {
        av_dict_set(&options, "user_agent", spec.userAgent.c_str(), 0);
    }
    av_dict_set(&options, "reconnect", "1", 0);
    av_dict_set(&options, "reconnect_streamed", "1", 0);
    av_dict_set(&options, "timeout", "5000000", 0);

    int result = avio_open2(&ioContext_, spec.resolvedUrl.c_str(), AVIO_FLAG_READ, nullptr, &options);
    av_dict_free(&options);
    if (result < 0 || ioContext_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = avErrorToString(result < 0 ? result : AVERROR_UNKNOWN);
        }
        close();
        return false;
    }

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void FfmpegStreamFileIo::close() {
    if (ioContext_ != nullptr) {
        avio_closep(&ioContext_);
    }
}

bool FfmpegStreamFileIo::isOpen() const {
    return ioContext_ != nullptr;
}

int64_t FfmpegStreamFileIo::read(uint8_t* buffer, int64_t bufferSize) {
    if (ioContext_ == nullptr || buffer == nullptr || bufferSize <= 0) {
        return -1;
    }
    return avio_read(ioContext_, buffer, static_cast<int>(bufferSize));
}

int64_t FfmpegStreamFileIo::seek(int64_t offset, int whence) {
    if (ioContext_ == nullptr) {
        return -1;
    }
    return avio_seek(ioContext_, offset, whence);
}

const char* FfmpegStreamFileIo::implementationName() const {
    return "ffmpeg";
}

bool FfmpegStreamFileIoFactory::supports(const DataSourceSpec& spec) const {
    std::string scheme = resolveScheme(spec.resolvedUrl);
    return scheme == "http" || scheme == "https";
}

std::unique_ptr<IFileIo> FfmpegStreamFileIoFactory::create() const {
    return std::make_unique<FfmpegStreamFileIo>();
}

} // namespace io
} // namespace flexmusic
