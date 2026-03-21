#include "TranscodeBridge.h"

#include <android/log.h>

#include "transcode/AudioTranscoder.h"

namespace flexmusic {
namespace jni {

namespace {

constexpr char kTranscodeBridgeTag[] = "TranscodeBridge";
constexpr char kTranscodeJniClassName[] = "com/example/feature_transcode/TranscodeBridge";
thread_local std::string g_last_transcode_error;

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return "";
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

static jint JNICALL NativeTranscode(JNIEnv* env,
                                    jclass clazz,
                                    jstring sourcePath,
                                    jstring targetPath,
                                    jint outputFormat,
                                    jint bitrateKbps,
                                    jint sampleRate) {
    (void) clazz;
    const std::string resolvedSourcePath = toStdString(env, sourcePath);
    const std::string resolvedTargetPath = toStdString(env, targetPath);
    __android_log_print(
            ANDROID_LOG_DEBUG,
            kTranscodeBridgeTag,
            "nativeTranscode source=%s target=%s format=%d bitrate=%d sampleRate=%d",
            resolvedSourcePath.c_str(),
            resolvedTargetPath.c_str(),
            static_cast<int>(outputFormat),
            static_cast<int>(bitrateKbps),
            static_cast<int>(sampleRate));
    return static_cast<jint>(TranscodeBridge::transcode(
            resolvedSourcePath,
            resolvedTargetPath,
            static_cast<int>(outputFormat),
            static_cast<int>(bitrateKbps),
            static_cast<int>(sampleRate)));
}

static jstring JNICALL NativeGetLastError(JNIEnv* env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF(TranscodeBridge::getLastError().c_str());
}

const JNINativeMethod kTranscodeBridgeMethods[] = {
        {const_cast<char*>("nativeTranscode"),
         const_cast<char*>("(Ljava/lang/String;Ljava/lang/String;III)I"),
         reinterpret_cast<void*>(NativeTranscode)},
        {const_cast<char*>("nativeGetLastError"),
         const_cast<char*>("()Ljava/lang/String;"),
         reinterpret_cast<void*>(NativeGetLastError)},
};

} // namespace

bool register_TranscodeBridgeJNI(JNIEnv* env) {
    jclass bridgeClass = env->FindClass(kTranscodeJniClassName);
    if (bridgeClass == nullptr) {
        return false;
    }
    const jint methodCount = static_cast<jint>(sizeof(kTranscodeBridgeMethods) / sizeof(kTranscodeBridgeMethods[0]));
    const jint result = env->RegisterNatives(bridgeClass, kTranscodeBridgeMethods, methodCount);
    env->DeleteLocalRef(bridgeClass);
    return result == JNI_OK;
}

int TranscodeBridge::transcode(const std::string& sourcePath,
                               const std::string& targetPath,
                               int outputFormat,
                               int bitrateKbps,
                               int sampleRate) {
    flexmusic::transcode::TranscodeRequest request;
    request.sourcePath = sourcePath;
    request.targetPath = targetPath;
    request.outputFormat = static_cast<flexmusic::transcode::OutputFormat>(outputFormat);
    request.bitrateKbps = bitrateKbps;
    request.sampleRate = sampleRate;
    g_last_transcode_error.clear();
    if (!flexmusic::transcode::AudioTranscoder::transcode(request, &g_last_transcode_error)) {
        if (g_last_transcode_error.empty()) {
            g_last_transcode_error = "Native transcode failed";
        }
        __android_log_print(
                ANDROID_LOG_ERROR,
                kTranscodeBridgeTag,
                "transcode failed source=%s target=%s error=%s",
                sourcePath.c_str(),
                targetPath.c_str(),
                g_last_transcode_error.c_str());
        return -1;
    }
    return 0;
}

std::string TranscodeBridge::getLastError() {
    return g_last_transcode_error;
}

} // namespace jni
} // namespace flexmusic
