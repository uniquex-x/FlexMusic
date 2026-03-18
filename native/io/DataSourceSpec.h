#ifndef FLEXMUSIC_DATA_SOURCE_SPEC_H
#define FLEXMUSIC_DATA_SOURCE_SPEC_H

#include <algorithm>
#include <cctype>
#include <string>

namespace flexmusic {
namespace io {

struct DataSourceSpec {
    std::string sourceId;
    std::string originalUrl;
    std::string resolvedUrl;
    std::string contentType;
    std::string userAgent;
    int detachedFd = -1;
    int64_t fdStartOffset = 0;
    int64_t fdLength = -1;
    bool liveStream = false;
    bool localSource = false;
    bool seekable = false;
};

inline std::string resolveScheme(const std::string& url) {
    std::string::size_type separator = url.find(':');
    if (separator == std::string::npos || separator == 0) {
        return "";
    }

    std::string scheme = url.substr(0, separator);
    std::transform(scheme.begin(), scheme.end(), scheme.begin(), [](unsigned char value) {
        return static_cast<char>(std::tolower(value));
    });
    return scheme;
}

} // namespace io
} // namespace flexmusic

#endif // FLEXMUSIC_DATA_SOURCE_SPEC_H
