#ifndef FLEXMUSIC_NATIVE_DOWNLOADER_H
#define FLEXMUSIC_NATIVE_DOWNLOADER_H

#include <string>

namespace flexmusic {
namespace download {

struct DownloadRequest {
    std::string sourceId;
    std::string sourceUrl;
    std::string userAgent;
    std::string targetPath;
};

class NativeDownloader final {
public:
    static bool download(const DownloadRequest& request, std::string* errorMessage);
};

} // namespace download
} // namespace flexmusic

#endif // FLEXMUSIC_NATIVE_DOWNLOADER_H
