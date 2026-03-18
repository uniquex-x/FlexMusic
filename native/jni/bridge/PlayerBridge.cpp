#include "PlayerBridge.h"

#include "../../io/FileIoRegistry.h"

namespace flexmusic {
namespace jni {

namespace {

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

void throwJavaException(JNIEnv* env, const char* class_name, const char* message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class == nullptr) {
        return;
    }
    env->ThrowNew(exception_class, message);
    env->DeleteLocalRef(exception_class);
}

NativePlayerContext* requireContext(JNIEnv* env, jlong handle) {
    NativePlayerContext* context = fromHandle(handle);
    if (context == nullptr) {
        throwJavaException(env, "java/lang/IllegalStateException", "Native player handle is null");
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

static void JNICALL NativeSetDataSource(JNIEnv* env,
                                        jclass clazz,
                                        jlong nativeHandle,
                                        jstring sourceId,
                                        jstring originalUrl,
                                        jstring resolvedUrl,
                                        jstring contentType,
                                        jstring userAgent,
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
            liveStream == JNI_TRUE,
            localSource == JNI_TRUE,
            seekable == JNI_TRUE,
            static_cast<int64_t>(probeLatencyMs));
}

static void JNICALL NativeOnPrepared(JNIEnv* env, jclass clazz, jlong nativeHandle, jlong durationMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::onPrepared(context, static_cast<int64_t>(durationMs));
}

static void JNICALL NativePlay(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::play(context);
}

static void JNICALL NativePause(JNIEnv* env, jclass clazz, jlong nativeHandle, jlong positionMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::pause(context, static_cast<int64_t>(positionMs));
}

static void JNICALL NativeSeekTo(JNIEnv* env, jclass clazz, jlong nativeHandle, jlong positionMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::seekTo(context, static_cast<int64_t>(positionMs));
}

static void JNICALL NativeStop(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::stop(context);
}

static void JNICALL NativeOnCompletion(JNIEnv* env, jclass clazz, jlong nativeHandle, jlong durationMs) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::onCompletion(context, static_cast<int64_t>(durationMs));
}

static void JNICALL NativeRelease(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return;
    }
    PlayerBridge::destroy(context);
}

static jboolean JNICALL NativeIsReady(JNIEnv* env, jclass clazz, jlong nativeHandle) {
    (void) clazz;
    NativePlayerContext* context = requireContext(env, nativeHandle);
    if (context == nullptr) {
        return JNI_FALSE;
    }
    return PlayerBridge::isReady(context) ? JNI_TRUE : JNI_FALSE;
}

const JNINativeMethod kPlayerBridgeMethods[] = {
        {const_cast<char*>("nativeCreate"), const_cast<char*>("()J"), reinterpret_cast<void*>(NativeCreate)},
        {const_cast<char*>("nativeInitialize"),
         const_cast<char*>("(Ljava/lang/String;)V"),
         reinterpret_cast<void*>(NativeInitialize)},
        {const_cast<char*>("nativeSetDataSource"),
         const_cast<char*>("(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZJ)V"),
         reinterpret_cast<void*>(NativeSetDataSource)},
        {const_cast<char*>("nativeOnPrepared"), const_cast<char*>("(JJ)V"), reinterpret_cast<void*>(NativeOnPrepared)},
        {const_cast<char*>("nativePlay"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativePlay)},
        {const_cast<char*>("nativePause"), const_cast<char*>("(JJ)V"), reinterpret_cast<void*>(NativePause)},
        {const_cast<char*>("nativeSeekTo"), const_cast<char*>("(JJ)V"), reinterpret_cast<void*>(NativeSeekTo)},
        {const_cast<char*>("nativeStop"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativeStop)},
        {const_cast<char*>("nativeOnCompletion"), const_cast<char*>("(JJ)V"), reinterpret_cast<void*>(NativeOnCompletion)},
        {const_cast<char*>("nativeRelease"), const_cast<char*>("(J)V"), reinterpret_cast<void*>(NativeRelease)},
        {const_cast<char*>("nativeIsReady"), const_cast<char*>("(J)Z"), reinterpret_cast<void*>(NativeIsReady)},
};

} // namespace

NativePlayerContext* PlayerBridge::create() {
    return new NativePlayerContext();
}

bool register_PlayerBridgeJNI(JNIEnv* env) {
    jclass bridge_class = env->FindClass(kPlayerJniClassName);
    if (bridge_class == nullptr) {
        return false;
    }
    const jint method_count = static_cast<jint>(sizeof(kPlayerBridgeMethods) / sizeof(kPlayerBridgeMethods[0]));
    jint result = env->RegisterNatives(bridge_class, kPlayerBridgeMethods, method_count);
    env->DeleteLocalRef(bridge_class);
    return result == JNI_OK;
}

void PlayerBridge::initializeRuntime(const std::string& appStoragePath) {
    g_app_storage_path = appStoragePath;
    g_runtime_initialized = true;
}

void PlayerBridge::destroy(NativePlayerContext* context) {
    if (context != nullptr && context->fileIo != nullptr) {
        context->fileIo->close();
    }
    delete context;
}

void PlayerBridge::setDataSource(NativePlayerContext* context,
                                 const std::string& sourceId,
                                 const std::string& originalUrl,
                                 const std::string& resolvedUrl,
                                 const std::string& contentType,
                                 const std::string& userAgent,
                                 bool liveStream,
                                 bool localSource,
                                 bool seekable,
                                 int64_t probeLatencyMs) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->sourceId = sourceId;
    context->originalUrl = originalUrl;
    context->resolvedUrl = resolvedUrl;
    context->contentType = contentType;
    context->userAgent = userAgent;
    context->liveStream = liveStream;
    context->localSource = localSource;
    context->seekable = seekable;
    context->probeLatencyMs = probeLatencyMs;
    context->durationMs = 0;
    context->currentPositionMs = 0;
    context->playing = false;
    context->fileIoReady = false;
    context->fileIoBackend.clear();
    context->lastErrorMessage.clear();

    if (context->fileIo != nullptr) {
        context->fileIo->close();
        context->fileIo.reset();
    }

    flexmusic::io::DataSourceSpec spec;
    spec.sourceId = sourceId;
    spec.originalUrl = originalUrl;
    spec.resolvedUrl = resolvedUrl;
    spec.contentType = contentType;
    spec.userAgent = userAgent;
    spec.liveStream = liveStream;
    spec.localSource = localSource;
    spec.seekable = seekable;

    std::string errorMessage;
    std::unique_ptr<flexmusic::io::IFileIo> fileIo =
            flexmusic::io::FileIoRegistry::createAndOpen(spec, &errorMessage);
    if (fileIo != nullptr) {
        context->fileIoBackend = fileIo->implementationName();
        context->fileIoReady = fileIo->isOpen();
        context->fileIo = std::move(fileIo);
    } else {
        context->lastErrorMessage = errorMessage;
    }
}

void PlayerBridge::onPrepared(NativePlayerContext* context, int64_t durationMs) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->durationMs = durationMs;
}

void PlayerBridge::play(NativePlayerContext* context) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->playing = true;
}

void PlayerBridge::pause(NativePlayerContext* context, int64_t positionMs) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->currentPositionMs = positionMs;
    context->playing = false;
}

void PlayerBridge::seekTo(NativePlayerContext* context, int64_t positionMs) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->currentPositionMs = positionMs;
}

void PlayerBridge::stop(NativePlayerContext* context) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->currentPositionMs = 0;
    context->durationMs = 0;
    context->playing = false;
    context->sourceId.clear();
    context->originalUrl.clear();
    context->resolvedUrl.clear();
    context->contentType.clear();
    context->userAgent.clear();
    context->liveStream = false;
    context->localSource = false;
    context->seekable = false;
    context->probeLatencyMs = 0;
    context->fileIoReady = false;
    context->fileIoBackend.clear();
    context->lastErrorMessage.clear();
    if (context->fileIo != nullptr) {
        context->fileIo->close();
        context->fileIo.reset();
    }
}

void PlayerBridge::onCompletion(NativePlayerContext* context, int64_t durationMs) {
    if (context == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    context->durationMs = durationMs;
    context->currentPositionMs = durationMs;
    context->playing = false;
}

bool PlayerBridge::isReady(NativePlayerContext* context) {
    if (context == nullptr) {
        return false;
    }
    std::lock_guard<std::mutex> lock(context->mutex);
    return context->fileIoReady;
}

} // namespace jni
} // namespace flexmusic
