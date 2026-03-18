#include "FileIoRegistry.h"

#include <mutex>
#include <utility>
#include <vector>

#include "FfmpegStreamFileIo.h"
#include "PassthroughFileIo.h"

namespace flexmusic {
namespace io {

namespace {

std::vector<std::unique_ptr<IFileIoFactory>>& factories() {
    static std::vector<std::unique_ptr<IFileIoFactory>> registry;
    return registry;
}

std::mutex& registryMutex() {
    static std::mutex mutex;
    return mutex;
}

void registerDefaultFactoriesIfNeededLocked() {
    if (!factories().empty()) {
        return;
    }
    factories().push_back(std::make_unique<FfmpegStreamFileIoFactory>());
    factories().push_back(std::make_unique<PassthroughFileIoFactory>());
}

} // namespace

void FileIoRegistry::registerFactory(std::unique_ptr<IFileIoFactory> factory) {
    if (factory == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(registryMutex());
    registerDefaultFactoriesIfNeededLocked();
    factories().insert(factories().begin(), std::move(factory));
}

std::unique_ptr<IFileIo> FileIoRegistry::createAndOpen(const DataSourceSpec& spec,
                                                       std::string* errorMessage) {
    std::lock_guard<std::mutex> lock(registryMutex());
    registerDefaultFactoriesIfNeededLocked();

    std::string lastError;
    for (const std::unique_ptr<IFileIoFactory>& factory : factories()) {
        if (!factory->supports(spec)) {
            continue;
        }
        std::unique_ptr<IFileIo> fileIo = factory->create();
        if (fileIo != nullptr && fileIo->open(spec, &lastError)) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return fileIo;
        }
    }

    if (errorMessage != nullptr) {
        if (!lastError.empty()) {
            *errorMessage = lastError;
        } else {
            *errorMessage = "No FileIo implementation matches the source scheme";
        }
    }
    return nullptr;
}

} // namespace io
} // namespace flexmusic
