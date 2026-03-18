#ifndef FLEXMUSIC_FILE_IO_REGISTRY_H
#define FLEXMUSIC_FILE_IO_REGISTRY_H

#include <memory>
#include <string>

#include "IFileIoFactory.h"

namespace flexmusic {
namespace io {

class FileIoRegistry final {
public:
    static void registerFactory(std::unique_ptr<IFileIoFactory> factory);

    static std::unique_ptr<IFileIo> createForSpec(const DataSourceSpec& spec,
                                                  std::string* errorMessage);

    static std::unique_ptr<IFileIo> createAndOpen(const DataSourceSpec& spec,
                                                  std::string* errorMessage);
};

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_FILE_IO_REGISTRY_H
