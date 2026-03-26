#include "DownloadBridge.h"

#include <android/log.h>

#include "NativeDownloader.h"

namespace flexmusic {
namespace jni {

namespace {

constexpr char kDownloadBridgeTag[] = "DownloadBridge";
constexpr char kDownloadJniClassName[] = "com/example/feature_download/DownloadBridge";
thread_local std::string g_last_download_error;

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

static jint JNICALL NativeDownload(JNIEnv* env,
                                   jclass clazz,
                                   jstring sourceId,
                                   jstring sourceUrl,
                                   jstring userAgent,
                                   jstring targetPath) {
    (void) clazz;
    const std::string resolvedSourceId = toStdString(env, sourceId);
    const std::string resolvedSourceUrl = toStdString(env, sourceUrl);
    const std::string resolvedUserAgent = toStdString(env, userAgent);
    const std::string resolvedTargetPath = toStdString(env, targetPath);
    __android_log_print(
            ANDROID_LOG_DEBUG,
            kDownloadBridgeTag,
            "nativeDownload sourceId=%s url=%s target=%s",
            resolvedSourceId.c_str(),
            resolvedSourceUrl.c_str(),
            resolvedTargetPath.c_str());
    return static_cast<jint>(DownloadBridge::download(
            resolvedSourceId,
            resolvedSourceUrl,
            resolvedUserAgent,
            resolvedTargetPath));
}

static jstring JNICALL NativeGetLastError(JNIEnv* env, jclass clazz) {
    (void) clazz;
    return env->NewStringUTF(DownloadBridge::getLastError().c_str());
}

const JNINativeMethod kDownloadBridgeMethods[] = {
        {const_cast<char*>("nativeDownload"),
         const_cast<char*>("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I"),
         reinterpret_cast<void*>(NativeDownload)},
        {const_cast<char*>("nativeGetLastError"),
         const_cast<char*>("()Ljava/lang/String;"),
         reinterpret_cast<void*>(NativeGetLastError)},
};

} // namespace

bool register_DownloadBridgeJNI(JNIEnv* env) {
    jclass bridgeClass = env->FindClass(kDownloadJniClassName);
    if (bridgeClass == nullptr) {
        return false;
    }
    const jint methodCount = static_cast<jint>(sizeof(kDownloadBridgeMethods) / sizeof(kDownloadBridgeMethods[0]));
    const jint result = env->RegisterNatives(bridgeClass, kDownloadBridgeMethods, methodCount);
    env->DeleteLocalRef(bridgeClass);
    return result == JNI_OK;
}

int DownloadBridge::download(const std::string& sourceId,
                             const std::string& sourceUrl,
                             const std::string& userAgent,
                             const std::string& targetPath) {
    flexmusic::download::DownloadRequest request;
    request.sourceId = sourceId;
    request.sourceUrl = sourceUrl;
    request.userAgent = userAgent;
    request.targetPath = targetPath;
    g_last_download_error.clear();
    if (!flexmusic::download::NativeDownloader::download(request, &g_last_download_error)) {
        if (g_last_download_error.empty()) {
            g_last_download_error = "Native download failed";
        }
        __android_log_print(
                ANDROID_LOG_ERROR,
                kDownloadBridgeTag,
                "download failed sourceId=%s url=%s target=%s error=%s",
                sourceId.c_str(),
                sourceUrl.c_str(),
                targetPath.c_str(),
                g_last_download_error.c_str());
        return -1;
    }
    return 0;
}

std::string DownloadBridge::getLastError() {
    return g_last_download_error;
}

} // namespace jni
} // namespace flexmusic
