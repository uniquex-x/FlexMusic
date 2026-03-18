#ifndef FLEXMUSIC_I_FILE_IO_H
#define FLEXMUSIC_I_FILE_IO_H

#include <cstdint>
#include <string>

#include "DataSourceSpec.h"

namespace flexmusic {
namespace io {

class IFileIo {
public:
    virtual ~IFileIo() = default;

    virtual bool open(const DataSourceSpec& spec, std::string* errorMessage) = 0;
    virtual void close() = 0;
    virtual bool isOpen() const = 0;
    virtual int64_t read(uint8_t* buffer, int64_t bufferSize) = 0;
    virtual int64_t seek(int64_t offset, int whence) = 0;
    virtual const char* implementationName() const = 0;
};

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_I_FILE_IO_H
