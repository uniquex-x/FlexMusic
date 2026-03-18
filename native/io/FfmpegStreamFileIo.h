#ifndef FLEXMUSIC_FFMPEG_STREAM_FILE_IO_H
#define FLEXMUSIC_FFMPEG_STREAM_FILE_IO_H

#include "IFileIoFactory.h"

struct AVIOContext;

namespace flexmusic {
namespace io {

class FfmpegStreamFileIo final : public IFileIo {
public:
    FfmpegStreamFileIo();
    ~FfmpegStreamFileIo() override;

    bool open(const DataSourceSpec& spec, std::string* errorMessage) override;
    void close() override;
    bool isOpen() const override;
    int64_t read(uint8_t* buffer, int64_t bufferSize) override;
    int64_t seek(int64_t offset, int whence) override;
    const char* implementationName() const override;

private:
    AVIOContext* ioContext_ = nullptr;
};

class FfmpegStreamFileIoFactory final : public IFileIoFactory {
public:
    bool supports(const DataSourceSpec& spec) const override;
    std::unique_ptr<IFileIo> create() const override;
};

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_FFMPEG_STREAM_FILE_IO_H
