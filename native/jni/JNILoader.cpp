#include <jni.h>

#include <android/log.h>
#include <cstdlib>

#include "PlayerBridge.h"
#include "TranscodeBridge.h"

namespace {

constexpr jint kFlexMusicJniVersion = JNI_VERSION_1_6;
constexpr char kJniLoaderTag[] = "JNILoader";

void configureOpenSslArmCapabilities() {
    const char* configuredValue = std::getenv("OPENSSL_armcap");
    if (configuredValue != nullptr && configuredValue[0] != '\0') {
        __android_log_print(
                ANDROID_LOG_INFO,
                kJniLoaderTag,
                "keep existing OPENSSL_armcap=%s",
                configuredValue);
        return;
    }

    // Some Android-targeted OpenSSL builds still enter ARM/SVE capability probes
    // that fault on devices lacking those instructions. Force the generic path.
    if (setenv("OPENSSL_armcap", "0", 1) == 0) {
        __android_log_print(
                ANDROID_LOG_INFO,
                kJniLoaderTag,
                "set OPENSSL_armcap=0 to avoid unsupported CPU feature probes");
    } else {
        __android_log_print(
                ANDROID_LOG_WARN,
                kJniLoaderTag,
                "set OPENSSL_armcap=0 failed");
    }
}

bool registerAllJni(JNIEnv* env) {
    if (!flexmusic::jni::register_PlayerBridgeJNI(env)) {
        return false;
    }
    if (!flexmusic::jni::register_TranscodeBridgeJNI(env)) {
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

    configureOpenSslArmCapabilities();

    if (!registerAllJni(env)) {
        return JNI_ERR;
    }

    return kFlexMusicJniVersion;
}
