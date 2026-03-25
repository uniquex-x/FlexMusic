#include "AvioDataSource.h"

#include "logger.h"

#include <cerrno>
#include <chrono>

extern "C" {
#include <libavformat/avio.h>
#include <libavutil/error.h>
#include <libavutil/mem.h>
}

namespace flexmusic {
namespace media {
namespace source {

namespace {

constexpr int kAvioBufferSize = 32 * 1024;
constexpr int kLowLatencyAvioBufferSize = 4 * 1024;
constexpr char kAvioTag[] = "AvioDataSource";

int resolveAvioBufferSize(const flexmusic::io::DataSourceSpec& spec) {
    std::string scheme = flexmusic::io::resolveScheme(spec.resolvedUrl);
    if (!spec.seekable && (scheme == "http" || scheme == "https")) {
        return kLowLatencyAvioBufferSize;
    }
    return kAvioBufferSize;
}

} // namespace

AvioDataSource::AvioDataSource() = default;

AvioDataSource::~AvioDataSource() {
    close();
}

bool AvioDataSource::open(std::unique_ptr<flexmusic::io::IFileIo> fileIo,
                          const flexmusic::io::DataSourceSpec& spec,
                          std::string* errorMessage) {
    close();
    const auto startedAt = std::chrono::steady_clock::now();
    const auto log = flexmusic::utils::levelLog(kAvioTag);
    if (fileIo == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = "File IO implementation is null";
        }
        return false;
    }

    std::string openError;
    if (!fileIo->open(spec, &openError)) {
        if (errorMessage != nullptr) {
            *errorMessage = openError.empty() ? "Open data source failed" : openError;
        }
        const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - startedAt).count();
        log.e("open failed sourceId=%s url=%s impl=%s elapsedMs=%lld error=%s",
              spec.sourceId.c_str(),
              spec.resolvedUrl.c_str(),
              fileIo->implementationName(),
              static_cast<long long>(elapsedMs),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    const int avioBufferSize = resolveAvioBufferSize(spec);
    buffer_ = static_cast<uint8_t*>(av_malloc(avioBufferSize));
    if (buffer_ == nullptr) {
        fileIo->close();
        if (errorMessage != nullptr) {
            *errorMessage = "Allocate AVIO buffer failed";
        }
        return false;
    }

    fileIo_ = std::move(fileIo);
    spec_ = spec;
    context_ = avio_alloc_context(
            buffer_,
            avioBufferSize,
            0,
            this,
            &AvioDataSource::readPacket,
            nullptr,
            spec.seekable ? &AvioDataSource::seek : nullptr);
    if (context_ == nullptr) {
        fileIo_->close();
        fileIo_.reset();
        av_freep(&buffer_);
        if (errorMessage != nullptr) {
            *errorMessage = "Allocate AVIO context failed";
        }
        return false;
    }

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - startedAt).count();
    log.i("open success sourceId=%s url=%s impl=%s elapsedMs=%lld seekable=%d avioBuffer=%d",
          spec.sourceId.c_str(),
          spec.resolvedUrl.c_str(),
          fileIo_->implementationName(),
          static_cast<long long>(elapsedMs),
          spec.seekable ? 1 : 0,
          avioBufferSize);
    return true;
}

void AvioDataSource::close() {
    if (context_ != nullptr) {
        avio_context_free(&context_);
        buffer_ = nullptr;
    } else if (buffer_ != nullptr) {
        av_freep(&buffer_);
    }
    if (fileIo_ != nullptr) {
        fileIo_->close();
        fileIo_.reset();
    }
}

AVIOContext* AvioDataSource::context() const {
    return context_;
}

const flexmusic::io::DataSourceSpec& AvioDataSource::spec() const {
    return spec_;
}

int AvioDataSource::readPacket(void* opaque, uint8_t* buffer, int bufferSize) {
    if (opaque == nullptr || buffer == nullptr || bufferSize <= 0) {
        return AVERROR(EINVAL);
    }
    AvioDataSource* dataSource = static_cast<AvioDataSource*>(opaque);
    if (dataSource->fileIo_ == nullptr) {
        return AVERROR_EOF;
    }
    int64_t readSize = dataSource->fileIo_->read(buffer, bufferSize);
    if (readSize == 0) {
        return AVERROR_EOF;
    }
    if (readSize < 0) {
        return AVERROR(EIO);
    }
    return static_cast<int>(readSize);
}

int64_t AvioDataSource::seek(void* opaque, int64_t offset, int whence) {
    if (opaque == nullptr) {
        return AVERROR(EINVAL);
    }
    AvioDataSource* dataSource = static_cast<AvioDataSource*>(opaque);
    if (dataSource->fileIo_ == nullptr) {
        return AVERROR(EIO);
    }
    return dataSource->fileIo_->seek(offset, whence);
}

} // namespace source
} // namespace media
} // namespace flexmusic
