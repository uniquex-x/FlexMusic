#ifndef FLEXMUSIC_FD_FILE_IO_H
#define FLEXMUSIC_FD_FILE_IO_H

#include "IFileIoFactory.h"

namespace flexmusic {
namespace io {

class FdFileIo final : public IFileIo {
public:
    ~FdFileIo() override;

    bool open(const DataSourceSpec& spec, std::string* errorMessage) override;
    void close() override;
    bool isOpen() const override;
    int64_t read(uint8_t* buffer, int64_t bufferSize) override;
    int64_t seek(int64_t offset, int whence) override;
    const char* implementationName() const override;

private:
    int64_t resolveLogicalSize() const;

    int fd_ = -1;
    int64_t startOffset_ = 0;
    int64_t length_ = -1;
    int64_t position_ = 0;
};

class FdFileIoFactory final : public IFileIoFactory {
public:
    bool supports(const DataSourceSpec& spec) const override;
    std::unique_ptr<IFileIo> create() const override;
};

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_FD_FILE_IO_H
