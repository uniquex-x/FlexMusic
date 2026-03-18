#include "FileIoRegistry.h"

#include <mutex>
#include <utility>
#include <vector>

#include "FdFileIo.h"
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
    factories().push_back(std::make_unique<FdFileIoFactory>());
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
    std::unique_ptr<IFileIo> fileIo = createForSpec(spec, errorMessage);
    if (fileIo == nullptr) {
        return nullptr;
    }

    std::string openError;
    if (fileIo->open(spec, &openError)) {
        if (errorMessage != nullptr) {
            errorMessage->clear();
        }
        return fileIo;
    }

    if (errorMessage != nullptr) {
        *errorMessage = openError.empty()
                ? "FileIo open failed"
                : openError;
    }
    return nullptr;
}

std::unique_ptr<IFileIo> FileIoRegistry::createForSpec(const DataSourceSpec& spec,
                                                       std::string* errorMessage) {
    std::lock_guard<std::mutex> lock(registryMutex());
    registerDefaultFactoriesIfNeededLocked();

    for (const std::unique_ptr<IFileIoFactory>& factory : factories()) {
        if (!factory->supports(spec)) {
            continue;
        }
        std::unique_ptr<IFileIo> fileIo = factory->create();
        if (fileIo != nullptr) {
            if (errorMessage != nullptr) {
                errorMessage->clear();
            }
            return fileIo;
        }
    }

    if (errorMessage != nullptr) {
        *errorMessage = "No FileIo implementation matches the source scheme";
    }
    return nullptr;
}

} // namespace io
} // namespace flexmusic
