#ifndef FLEXMUSIC_I_FILE_IO_FACTORY_H
#define FLEXMUSIC_I_FILE_IO_FACTORY_H

#include <memory>

#include "IFileIo.h"

namespace flexmusic {
namespace io {

class IFileIoFactory {
public:
    virtual ~IFileIoFactory() = default;

    virtual bool supports(const DataSourceSpec& spec) const = 0;
    virtual std::unique_ptr<IFileIo> create() const = 0;
};

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_I_FILE_IO_FACTORY_H
