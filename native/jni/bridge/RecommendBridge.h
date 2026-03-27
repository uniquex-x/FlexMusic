#ifndef FLEXMUSIC_RECOMMEND_BRIDGE_H
#define FLEXMUSIC_RECOMMEND_BRIDGE_H

#include <jni.h>
#include <string>

namespace flexmusic {
namespace jni {

bool register_RecommendBridgeJNI(JNIEnv* env);

class RecommendBridge final {
public:
    static std::string getHomeFeedJson();
    static std::string getLastError();
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_RECOMMEND_BRIDGE_H
