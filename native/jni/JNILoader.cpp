#include <jni.h>

#include "bridge/PlayerBridge.h"

namespace {

constexpr jint kFlexMusicJniVersion = JNI_VERSION_1_6;

bool registerAllJni(JNIEnv* env) {
    if (!flexmusic::jni::register_PlayerBridgeJNI(env)) {
        return false;
    }
    return true;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void) reserved;

    JNIEnv* env = nullptr;
    if (vm == nullptr || vm->GetEnv(reinterpret_cast<void**>(&env), kFlexMusicJniVersion) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }

    if (!registerAllJni(env)) {
        return JNI_ERR;
    }

    return kFlexMusicJniVersion;
}
