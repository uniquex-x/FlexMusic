#include "NativeDownloader.h"

#include <cerrno>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>

#include "DataSourceSpec.h"
#include "FileIoRegistry.h"
#include "logger.h"

namespace flexmusic {
namespace download {

namespace {

constexpr char kDownloadTag[] = "NativeDownloader";
constexpr int64_t kDownloadBufferSize = 32 * 1024;

bool isLocalSource(const std::string& sourceUrl) {
    const std::string scheme = io::resolveScheme(sourceUrl);
    return scheme.empty() || scheme == "file";
}

bool writeAll(std::FILE* outputFile,
              const uint8_t* buffer,
              int64_t size,
              std::string* errorMessage) {
    if (outputFile == nullptr || buffer == nullptr || size <= 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "Download output file is invalid";
        }
        return false;
    }
    const size_t writtenSize = std::fwrite(buffer, 1, static_cast<size_t>(size), outputFile);
    if (writtenSize == static_cast<size_t>(size)) {
        return true;
    }
    if (errorMessage != nullptr) {
        *errorMessage = std::string("Write download file failed: ") + std::strerror(errno);
    }
    return false;
}

void cleanupFile(const std::string& path) {
    if (!path.empty()) {
        std::remove(path.c_str());
    }
}

} // namespace

bool NativeDownloader::download(const DownloadRequest& request, std::string* errorMessage) {
    const auto log = utils::levelLog(kDownloadTag);
    const auto startedAt = std::chrono::steady_clock::now();
    if (request.sourceId.empty() || request.sourceUrl.empty() || request.targetPath.empty()) {
        if (errorMessage != nullptr) {
            *errorMessage = "Download request is incomplete";
        }
        return false;
    }

    io::DataSourceSpec spec;
    spec.sourceId = request.sourceId;
    spec.originalUrl = request.sourceUrl;
    spec.resolvedUrl = request.sourceUrl;
    spec.userAgent = request.userAgent;
    spec.liveStream = false;
    spec.localSource = isLocalSource(request.sourceUrl);
    spec.seekable = true;

    std::string openError;
    std::unique_ptr<io::IFileIo> fileIo = io::FileIoRegistry::createAndOpen(spec, &openError);
    if (fileIo == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = openError.empty() ? "Open download source failed" : openError;
        }
        log.e("open failed sourceId=%s url=%s error=%s",
              request.sourceId.c_str(),
              request.sourceUrl.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    log.i("open success sourceId=%s url=%s backend=%s target=%s",
          request.sourceId.c_str(),
          request.sourceUrl.c_str(),
          fileIo->implementationName(),
          request.targetPath.c_str());

    const std::string tempPath = request.targetPath + ".download";
    cleanupFile(tempPath);
    std::FILE* outputFile = std::fopen(tempPath.c_str(), "wb");
    if (outputFile == nullptr) {
        fileIo->close();
        if (errorMessage != nullptr) {
            *errorMessage = std::string("Create download output failed: ") + std::strerror(errno);
        }
        log.e("create output failed sourceId=%s target=%s error=%s",
              request.sourceId.c_str(),
              request.targetPath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    int64_t totalBytes = 0;
    bool success = true;
    std::string writeError;
    uint8_t buffer[kDownloadBufferSize] = {0};
    while (true) {
        const int64_t readSize = fileIo->read(buffer, kDownloadBufferSize);
        if (readSize < 0) {
            success = false;
            if (errorMessage != nullptr) {
                *errorMessage = "Read download source failed";
            }
            break;
        }
        if (readSize == 0) {
            break;
        }
        if (!writeAll(outputFile, buffer, readSize, &writeError)) {
            success = false;
            if (errorMessage != nullptr) {
                *errorMessage = writeError;
            }
            break;
        }
        totalBytes += readSize;
    }

    if (std::fclose(outputFile) != 0 && success) {
        success = false;
        if (errorMessage != nullptr) {
            *errorMessage = std::string("Flush download output failed: ") + std::strerror(errno);
        }
    }
    outputFile = nullptr;
    fileIo->close();

    if (!success) {
        cleanupFile(tempPath);
        log.e("download failed sourceId=%s url=%s backend=%s bytes=%lld error=%s",
              request.sourceId.c_str(),
              request.sourceUrl.c_str(),
              fileIo->implementationName(),
              static_cast<long long>(totalBytes),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    cleanupFile(request.targetPath);
    if (std::rename(tempPath.c_str(), request.targetPath.c_str()) != 0) {
        cleanupFile(tempPath);
        if (errorMessage != nullptr) {
            *errorMessage = std::string("Move download output failed: ") + std::strerror(errno);
        }
        log.e("rename failed sourceId=%s target=%s error=%s",
              request.sourceId.c_str(),
              request.targetPath.c_str(),
              errorMessage != nullptr ? errorMessage->c_str() : "");
        return false;
    }

    const auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - startedAt).count();
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    log.i("download success sourceId=%s url=%s target=%s bytes=%lld elapsedMs=%lld",
          request.sourceId.c_str(),
          request.sourceUrl.c_str(),
          request.targetPath.c_str(),
          static_cast<long long>(totalBytes),
          static_cast<long long>(elapsedMs));
    return true;
}

} // namespace download
} // namespace flexmusic
