#include "PassthroughFileIo.h"

#include <cerrno>
#include <cstring>
#include <memory>
#include <string>

extern "C" {
#include <libavformat/avio.h>
}

namespace flexmusic {
namespace io {

namespace {

std::string resolvePath(const DataSourceSpec& spec) {
    const std::string scheme = resolveScheme(spec.resolvedUrl);
    if (scheme == "file") {
        return spec.resolvedUrl.substr(7);
    }
    return spec.resolvedUrl;
}

} // namespace

bool PassthroughFileIo::open(const DataSourceSpec& spec, std::string* errorMessage) {
    close();
    const std::string path = resolvePath(spec);
    if (path.empty()) {
        if (errorMessage != nullptr) {
            *errorMessage = "Local file path is empty";
        }
        return false;
    }

    file_ = std::fopen(path.c_str(), "rb");
    if (file_ == nullptr) {
        if (errorMessage != nullptr) {
            *errorMessage = std::string("Open local file failed: ") + std::strerror(errno);
        }
        return false;
    }
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    opened_ = true;
    return true;
}

void PassthroughFileIo::close() {
    if (file_ != nullptr) {
        std::fclose(file_);
        file_ = nullptr;
    }
    opened_ = false;
}

bool PassthroughFileIo::isOpen() const {
    return opened_;
}

int64_t PassthroughFileIo::read(uint8_t* buffer, int64_t bufferSize) {
    if (file_ == nullptr || buffer == nullptr || bufferSize <= 0) {
        return -1;
    }
    return static_cast<int64_t>(std::fread(buffer, 1, static_cast<std::size_t>(bufferSize), file_));
}

int64_t PassthroughFileIo::seek(int64_t offset, int whence) {
    if (file_ == nullptr) {
        return -1;
    }
    if (whence == AVSEEK_SIZE) {
        const long currentPosition = std::ftell(file_);
        if (currentPosition < 0) {
            return -1;
        }
        if (std::fseek(file_, 0, SEEK_END) != 0) {
            return -1;
        }
        const long fileSize = std::ftell(file_);
        std::fseek(file_, currentPosition, SEEK_SET);
        return fileSize;
    }
    if (std::fseek(file_, static_cast<long>(offset), whence) != 0) {
        return -1;
    }
    return std::ftell(file_);
}

const char* PassthroughFileIo::implementationName() const {
    return "passthrough";
}

bool PassthroughFileIoFactory::supports(const DataSourceSpec& spec) const {
    std::string scheme = resolveScheme(spec.resolvedUrl);
    return spec.localSource
            || scheme.empty()
            || scheme == "file"
            || scheme == "content"
            || scheme == "android.resource";
}

std::unique_ptr<IFileIo> PassthroughFileIoFactory::create() const {
    return std::make_unique<PassthroughFileIo>();
}

} // namespace io
} // namespace flexmusic
