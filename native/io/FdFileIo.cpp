#include "FdFileIo.h"

#include <cerrno>
#include <cstring>
#include <memory>

#include <sys/stat.h>
#include <unistd.h>

extern "C" {
#include <libavformat/avio.h>
}

namespace flexmusic {
namespace io {

FdFileIo::~FdFileIo() {
    close();
}

bool FdFileIo::open(const DataSourceSpec& spec, std::string* errorMessage) {
    close();
    if (spec.detachedFd < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = "Detached file descriptor is invalid";
        }
        return false;
    }

    fd_ = spec.detachedFd;
    startOffset_ = spec.fdStartOffset;
    length_ = spec.fdLength;
    position_ = 0;

    if (lseek(fd_, startOffset_, SEEK_SET) < 0) {
        if (errorMessage != nullptr) {
            *errorMessage = std::string("Seek detached fd failed: ") + std::strerror(errno);
        }
        close();
        return false;
    }

    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void FdFileIo::close() {
    if (fd_ >= 0) {
        ::close(fd_);
        fd_ = -1;
    }
    startOffset_ = 0;
    length_ = -1;
    position_ = 0;
}

bool FdFileIo::isOpen() const {
    return fd_ >= 0;
}

int64_t FdFileIo::read(uint8_t* buffer, int64_t bufferSize) {
    if (fd_ < 0 || buffer == nullptr || bufferSize <= 0) {
        return -1;
    }
    if (length_ >= 0 && position_ >= length_) {
        return 0;
    }

    int64_t allowedSize = bufferSize;
    if (length_ >= 0) {
        allowedSize = std::min<int64_t>(allowedSize, length_ - position_);
    }
    const ssize_t readSize = ::read(fd_, buffer, static_cast<size_t>(allowedSize));
    if (readSize > 0) {
        position_ += readSize;
    }
    return readSize;
}

int64_t FdFileIo::seek(int64_t offset, int whence) {
    if (fd_ < 0) {
        return -1;
    }
    if (whence == AVSEEK_SIZE) {
        return resolveLogicalSize();
    }

    int64_t targetPosition = 0;
    if (whence == SEEK_SET) {
        targetPosition = offset;
    } else if (whence == SEEK_CUR) {
        targetPosition = position_ + offset;
    } else if (whence == SEEK_END) {
        const int64_t logicalSize = resolveLogicalSize();
        if (logicalSize < 0) {
            return -1;
        }
        targetPosition = logicalSize + offset;
    } else {
        return -1;
    }

    if (targetPosition < 0) {
        return -1;
    }
    if (length_ >= 0) {
        targetPosition = std::min(targetPosition, length_);
    }

    const off_t absolutePosition = static_cast<off_t>(startOffset_ + targetPosition);
    if (lseek(fd_, absolutePosition, SEEK_SET) < 0) {
        return -1;
    }
    position_ = targetPosition;
    return position_;
}

const char* FdFileIo::implementationName() const {
    return "fd";
}

int64_t FdFileIo::resolveLogicalSize() const {
    if (length_ >= 0) {
        return length_;
    }
    struct stat fileStat {};
    if (fstat(fd_, &fileStat) != 0) {
        return -1;
    }
    return static_cast<int64_t>(fileStat.st_size) - startOffset_;
}

bool FdFileIoFactory::supports(const DataSourceSpec& spec) const {
    return spec.detachedFd >= 0;
}

std::unique_ptr<IFileIo> FdFileIoFactory::create() const {
    return std::make_unique<FdFileIo>();
}

} // namespace io
} // namespace flexmusic
