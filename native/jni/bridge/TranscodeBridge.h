#ifndef FLEXMUSIC_TRANSCODE_BRIDGE_H
#define FLEXMUSIC_TRANSCODE_BRIDGE_H

#include <jni.h>
#include <string>

namespace flexmusic {
namespace jni {

bool register_TranscodeBridgeJNI(JNIEnv* env);

class TranscodeBridge final {
public:
    static int transcode(const std::string& sourcePath,
                         const std::string& targetPath,
                         int outputFormat,
                         int bitrateKbps,
                         int sampleRate);

    static std::string getLastError();
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_TRANSCODE_BRIDGE_H
