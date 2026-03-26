#ifndef FLEXMUSIC_DOWNLOAD_BRIDGE_H
#define FLEXMUSIC_DOWNLOAD_BRIDGE_H

#include <jni.h>
#include <string>

namespace flexmusic {
namespace jni {

bool register_DownloadBridgeJNI(JNIEnv* env);

class DownloadBridge final {
public:
    static int download(const std::string& sourceId,
                        const std::string& sourceUrl,
                        const std::string& userAgent,
                        const std::string& targetPath);

    static std::string getLastError();
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_DOWNLOAD_BRIDGE_H
