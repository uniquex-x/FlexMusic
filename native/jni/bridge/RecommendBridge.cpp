#include "RecommendBridge.h"

#include <android/log.h>

#include "RecommendKernel.h"

namespace flexmusic {
namespace jni {

namespace {

constexpr char kRecommendBridgeTag[] = "RecommendBridge";
constexpr char kRecommendJniClassName[] = "com/example/core_recommend/RecommendNativeBridge";

static jstring JNICALL NativeGetHomeFeedJson(JNIEnv* env, jclass clazz) {
    (void) clazz;
    __android_log_print(ANDROID_LOG_DEBUG, kRecommendBridgeTag, "nativeGetHomeFeedJson");

    const std::string payload = RecommendBridge::getHomeFeedJson();
    if (payload.empty()) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kRecommendBridgeTag,
                "nativeGetHomeFeedJson empty payload error=%s",
                RecommendBridge::getLastError().c_str());
    }
    return env->NewStringUTF(payload.c_str());
}

static jstring JNICALL NativeGetLastError(JNIEnv* env, jclass clazz) {
    (void) clazz;
    const std::string error = RecommendBridge::getLastError();
    return env->NewStringUTF(error.c_str());
}

const JNINativeMethod kRecommendBridgeMethods[] = {
        {const_cast<char*>("nativeGetHomeFeedJson"),
         const_cast<char*>("()Ljava/lang/String;"),
         reinterpret_cast<void*>(NativeGetHomeFeedJson)},
        {const_cast<char*>("nativeGetLastError"),
         const_cast<char*>("()Ljava/lang/String;"),
         reinterpret_cast<void*>(NativeGetLastError)},
};

} // namespace

bool register_RecommendBridgeJNI(JNIEnv* env) {
    jclass bridgeClass = env->FindClass(kRecommendJniClassName);
    if (bridgeClass == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kRecommendBridgeTag, "FindClass failed");
        return false;
    }
    const jint methodCount = static_cast<jint>(
            sizeof(kRecommendBridgeMethods) / sizeof(kRecommendBridgeMethods[0]));
    const jint result = env->RegisterNatives(bridgeClass, kRecommendBridgeMethods, methodCount);
    env->DeleteLocalRef(bridgeClass);
    if (result != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kRecommendBridgeTag, "RegisterNatives failed result=%d", result);
    }
    return result == JNI_OK;
}

std::string RecommendBridge::getHomeFeedJson() {
    return flexmusic::recommend::RecommendKernel::buildHomeFeedJson();
}

std::string RecommendBridge::getLastError() {
    return flexmusic::recommend::RecommendKernel::getLastError();
}

} // namespace jni
} // namespace flexmusic
