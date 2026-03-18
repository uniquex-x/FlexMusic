#include "PassthroughFileIo.h"

#include <memory>

namespace flexmusic {
namespace io {

bool PassthroughFileIo::open(const DataSourceSpec& spec, std::string* errorMessage) {
    (void) spec;
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    opened_ = true;
    return true;
}

void PassthroughFileIo::close() {
    opened_ = false;
}

bool PassthroughFileIo::isOpen() const {
    return opened_;
}

int64_t PassthroughFileIo::read(uint8_t* buffer, int64_t bufferSize) {
    (void) buffer;
    (void) bufferSize;
    return -1;
}

int64_t PassthroughFileIo::seek(int64_t offset, int whence) {
    (void) offset;
    (void) whence;
    return -1;
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
