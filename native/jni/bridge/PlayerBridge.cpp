#include "PlayerBridge.h"

#include <android/log.h>
#include <unistd.h>

#include "../../io/FileIoRegistry.h"

namespace flexmusic {
namespace jni {

namespace {

constexpr char kPlayerBridgeTag[] = "PlayerBridge";
constexpr char kPlayerJniClassName[] = "com/example/feature_player/coreplayer/PlayerJNI";
std::string g_app_storage_path;
bool g_runtime_initialized = false;

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

NativePlayerContext* fromHandle(jlong handle) {
    return reinterpret_cast<NativePlayerContext*>(handle);
}

void throwJavaException(JNIEnv* env, const char* className, const char* message) {
    jclass exceptionClass = env->FindClass(className);
    if (exceptionClass == nullptr) {
        return;
    }
    env->ThrowNew(exceptionClass, message);
    env->DeleteLocalRef(exceptionClass);
}

NativePlayerContext* requireContext(JNIEnv* env, jlong handle) {
    NativePlayerContext* context = fromHandle(handle);
    if (context == nullptr || context->playerSession == nullptr) {
        throwJavaException(env, "java/lang/IllegalStateException", "Native player handle is null");
        return nullptr;
    }
    return context;
}

static jlong JNICALL NativeCreate(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    return reinterpret_cast<jlong>(PlayerBridge::create());
}

static void JNICALL NativeInitialize(JNIEnv* env, jclass clazz, jstring appStoragePath) {
    (void) clazz;
    PlayerBridge::initializeRuntime(toStdString(env, appStoragePath));
}

static void JNICALL NativePrepare(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::prepare(context);
    }
}

static void JNICALL NativeSetDataSource(JNIEnv* env,
                                        jclass clazz,
                                        jlong nativeHandle,
                                        jstring sourceId,
                                        jstring originalUrl,
                                        jstring resolvedUrl,
                                        jstring contentType,
                                        jstring userAgent,
                                        jint detachedFd,
                                        jlong fdStartOffset,
                                        jlong fdLength,
                                        jboolean liveStream,
                                        jboolean localSource,
                                        jboolean seekable,
                                        jlong probeLatencyMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::setDataSource(
            context,
            toStdString(env, sourceId),
            toStdString(env, originalUrl),
            toStdString(env, resolvedUrl),
            toStdString(env, contentType),
            toStdString(env, userAgent),
            static_cast<int>(detachedFd),
            static_cast<int64_t>(fdStartOffset),
            static_cast<int64_t>(fdLength),
            liveStream == JNI_TRUE,
            localSource == JNI_TRUE,
            seekable == JNI_TRUE,
            static_cast<int64_t>(probeLatencyMs));
}

static void JNICALL NativePlay(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::play(context);
    }
}

static void JNICALL NativePause(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::pause(context);
    }
}

static void JNICALL NativeSeekTo(JNIEnv* env, jclass clazz, jlong nativeHandle, jlong positionMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::seekTo(context, static_cast<int64_t>(positionMs));
    }
}

static void JNICALL NativeStop(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::stop(context);
    }
}

static void JNICALL NativeSetVolume(JNIEnv* env, jclass clazz, jlong nativeHandle, jfloat volume) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::setVolume(context, static_cast<float>(volume));
    }
}

static void JNICALL NativeSetPlaybackSpeed(JNIEnv* env, jclass clazz, jlong nativeHandle, jfloat playbackSpeed) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::setPlaybackSpeed(context, static_cast<float>(playbackSpeed));
    }
}

static void JNICALL NativeRelease(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context != nullptr) {
        PlayerBridge::destroy(context);
    }
}

static jboolean JNICALL NativeIsReady(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    return context != nullptr && PlayerBridge::isReady(context) ? JNI_TRUE : JNI_FALSE;
}

static jint JNICALL NativeGetState(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    return context == nullptr ? 0 : static_cast<jint>(PlayerBridge::getState(context));
}

static jlong JNICALL NativeGetCurrentPosition(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    return context == nullptr ? 0L : static_cast<jlong>(PlayerBridge::getCurrentPosition(context));
}

static jlong JNICALL NativeGetDuration(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    return context == nullptr ? 0L : static_cast<jlong>(PlayerBridge::getDuration(context));
}

static jstring JNICALL NativeGetErrorMessage(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    const std::string errorMessage = context == nullptr ? "" : PlayerBridge::getErrorMessage(context);
    return env->NewStringUTF(errorMessage.c_str());
}

const JNINativeMethod kPlayerBridgeMethods[] = {
        {const_cast<char*>("nativeCreate"), const_cast<char*>("()J"), reinterpret_cast<void*>(NativeCreate)},
        {const_cast<char*>("nativeInitialize"),
         const_cast<char*>("(Ljava/lang/String;)V"),
         reinterpret_cast<void*>(NativeInitialize)},
        {const_cast<char*>("nativePrepare"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativePrepare)},
        {const_cast<char*>("nativeSetDataSource"),
         const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJJZZZJ)V"),
         reinterpret_cast<void*>(NativeSetDataSource)},
        {const_cast<char*>("nativePlay"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativePlay)},
        {const_cast<char*>("nativePause"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativePause)},
        {const_cast<char*>("nativeSeekTo"), const_cast<char*>("(JJ)V"), reinterpret_cast<void*>(NativeSeekTo)},
        {const_cast<char*>("nativeStop"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativeStop)},
        {const_cast<char*>("nativeSetVolume"), const_cast<char*>("(JF)V"), reinterpret_cast<void*>(NativeSetVolume)},
        {const_cast<char*>("nativeSetPlaybackSpeed"),
         const_cast<char*>("(JF)V"),
         reinterpret_cast<void*>(NativeSetPlaybackSpeed)},
        {const_cast<char*>("nativeRelease"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativeRelease)},
        {const_cast<char*>("nativeIsReady"), const_cast<char*>("(J)Z"), reinterpret_cast<void*>(NativeIsReady)},
        {const_cast<char*>("nativeGetState"), const_cast<char*>("(J)I"), reinterpret_cast<void*>(NativeGetState)},
        {const_cast<char*>("nativeGetCurrentPosition"),
         const_cast<char*>("(J)J"),
         reinterpret_cast<void*>(NativeGetCurrentPosition)},
        {const_cast<char*>("nativeGetDuration"),
         const_cast<char*>("(J)J"),
         reinterpret_cast<void*>(NativeGetDuration)},
        {const_cast<char*>("nativeGetErrorMessage"),
         const_cast<char*>("(J)Ljava/lang/String;"),
         reinterpret_cast<void*>(NativeGetErrorMessage)},
};

} // namespace

NativePlayerContext* PlayerBridge::create() {
    NativePlayerContext* context = new NativePlayerContext();
    context->playerSession = std::make_unique<flexmusic::player::PlayerSession>();
    return context;
}

bool register_PlayerBridgeJNI(JNIEnv* env) {
    jclass bridgeClass = env->FindClass(kPlayerJniClassName);
    if (bridgeClass == nullptr) {
        return false;
    }
    const jint methodCount = static_cast<jint>(sizeof(kPlayerBridgeMethods) / sizeof(kPlayerBridgeMethods[0]));
    const jint result = env->RegisterNatives(bridgeClass, kPlayerBridgeMethods, methodCount);
    env->DeleteLocalRef(bridgeClass);
    return result == JNI_OK;
}

void PlayerBridge::initializeRuntime(const std::string& appStoragePath) {
    g_app_storage_path = appStoragePath;
    g_runtime_initialized = true;
}

void PlayerBridge::destroy(NativePlayerContext* context) {
    if (context == nullptr) {
        return;
    }
    context->playerSession.reset();
    delete context;
}

void PlayerBridge::setDataSource(NativePlayerContext* context,
                                 const std::string& sourceId,
                                 const std::string& originalUrl,
                                 const std::string& resolvedUrl,
                                 const std::string& contentType,
                                 const std::string& userAgent,
                                 int detachedFd,
                                 int64_t fdStartOffset,
                                 int64_t fdLength,
                                 bool liveStream,
                                 bool localSource,
                                 bool seekable,
                                 int64_t probeLatencyMs) {
    (void) probeLatencyMs;
    if (context == nullptr || context->playerSession == nullptr) {
        return;
    }

    flexmusic::io::DataSourceSpec spec;
    spec.sourceId = sourceId;
    spec.originalUrl = originalUrl;
    spec.resolvedUrl = resolvedUrl;
    spec.contentType = contentType;
    spec.userAgent = userAgent;
    spec.detachedFd = detachedFd;
    spec.fdStartOffset = fdStartOffset;
    spec.fdLength = fdLength;
    spec.liveStream = liveStream;
    spec.localSource = localSource;
    spec.seekable = seekable;

    std::string errorMessage;
    std::unique_ptr<flexmusic::io::IFileIo> fileIo = flexmusic::io::FileIoRegistry::createForSpec(spec, &errorMessage);
    std::string backendName;
    if (fileIo != nullptr) {
        backendName = fileIo->implementationName();
    }
    __android_log_print(
            ANDROID_LOG_INFO,
            kPlayerBridgeTag,
            "setDataSource sourceId=%s url=%s backend=%s fd=%d",
            sourceId.c_str(),
            resolvedUrl.c_str(),
            backendName.c_str(),
            detachedFd);
    if (fileIo == nullptr) {
        if (detachedFd >= 0) {
            ::close(detachedFd);
        }
        __android_log_print(
                ANDROID_LOG_ERROR,
                kPlayerBridgeTag,
                "setDataSource failed sourceId=%s url=%s error=%s",
                sourceId.c_str(),
                resolvedUrl.c_str(),
                errorMessage.c_str());
        context->playerSession->setDataSource(flexmusic::io::DataSourceSpec(), "", &errorMessage);
        return;
    }
    context->playerSession->setDataSource(spec, backendName, &errorMessage);
}

void PlayerBridge::prepare(NativePlayerContext* context) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->prepare();
    }
}

void PlayerBridge::play(NativePlayerContext* context) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->play();
    }
}

void PlayerBridge::pause(NativePlayerContext* context) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->pause();
    }
}

void PlayerBridge::seekTo(NativePlayerContext* context, int64_t positionMs) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->seekTo(positionMs);
    }
}

void PlayerBridge::stop(NativePlayerContext* context) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->stop();
    }
}

void PlayerBridge::setVolume(NativePlayerContext* context, float volume) {
    if (context != nullptr && context->playerSession != nullptr) {
        context->playerSession->setVolume(volume);
    }
}

void PlayerBridge::setPlaybackSpeed(NativePlayerContext* context, float playbackSpeed) {
    if (context != nullptr && context->playerSession != nullptr) {
        __android_log_print(
                ANDROID_LOG_DEBUG,
                kPlayerBridgeTag,
                "setPlaybackSpeed speed=%.2f",
                playbackSpeed);
        context->playerSession->setPlaybackSpeed(playbackSpeed);
    }
}

bool PlayerBridge::isReady(NativePlayerContext* context) {
    return context != nullptr
            && context->playerSession != nullptr
            && context->playerSession->snapshot().nativeReady;
}

int PlayerBridge::getState(NativePlayerContext* context) {
    if (context == nullptr || context->playerSession == nullptr) {
        return 0;
    }
    return static_cast<int>(context->playerSession->snapshot().state);
}

int64_t PlayerBridge::getCurrentPosition(NativePlayerContext* context) {
    if (context == nullptr || context->playerSession == nullptr) {
        return 0;
    }
    return context->playerSession->snapshot().currentPositionMs;
}

int64_t PlayerBridge::getDuration(NativePlayerContext* context) {
    if (context == nullptr || context->playerSession == nullptr) {
        return 0;
    }
    return context->playerSession->snapshot().durationMs;
}

std::string PlayerBridge::getErrorMessage(NativePlayerContext* context) {
    if (context == nullptr || context->playerSession == nullptr) {
        return "";
    }
    return context->playerSession->snapshot().errorMessage;
}

} // namespace jni
} // namespace flexmusic
