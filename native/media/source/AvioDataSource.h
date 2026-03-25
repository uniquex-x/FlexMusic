#ifndef FLEXMUSIC_AVIO_DATA_SOURCE_H
#define FLEXMUSIC_AVIO_DATA_SOURCE_H

#include <memory>
#include <string>

#include "DataSourceSpec.h"
#include "IFileIo.h"

struct AVIOContext;

namespace flexmusic {
namespace media {
namespace source {

class AvioDataSource final {
public:
    AvioDataSource();
    ~AvioDataSource();

    bool open(std::unique_ptr<flexmusic::io::IFileIo> fileIo,
              const flexmusic::io::DataSourceSpec& spec,
              std::string* errorMessage);
    void close();

    AVIOContext* context() const;
    const flexmusic::io::DataSourceSpec& spec() const;

private:
    static int readPacket(void* opaque, uint8_t* buffer, int bufferSize);
    static int64_t seek(void* opaque, int64_t offset, int whence);

    std::unique_ptr<flexmusic::io::IFileIo> fileIo_;
    flexmusic::io::DataSourceSpec spec_;
    AVIOContext* context_ = nullptr;
    uint8_t* buffer_ = nullptr;
};

} // namespace source
} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_AVIO_DATA_SOURCE_H
