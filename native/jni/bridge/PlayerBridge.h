#ifndef FLEXMUSIC_PLAYER_BRIDGE_H
#define FLEXMUSIC_PLAYER_BRIDGE_H

#include <cstdint>
#include <jni.h>
#include <memory>
#include <mutex>
#include <string>

#include "../../io/IFileIo.h"

namespace flexmusic {
namespace jni {

bool register_PlayerBridgeJNI(JNIEnv* env);

struct NativePlayerContext {
    std::mutex mutex;
    std::string sourceId;
    std::string originalUrl;
    std::string resolvedUrl;
    std::string contentType;
    std::string userAgent;
    bool liveStream = false;
    bool localSource = false;
    bool seekable = false;
    int64_t probeLatencyMs = 0;
    int64_t durationMs = 0;
    int64_t currentPositionMs = 0;
    bool playing = false;
    bool fileIoReady = false;
    std::string fileIoBackend;
    std::string lastErrorMessage;
    std::unique_ptr<flexmusic::io::IFileIo> fileIo;
};

class PlayerBridge final {
public:
    static void initializeRuntime(const std::string& appStoragePath);

    static NativePlayerContext* create();
    static void destroy(NativePlayerContext* context);

    static void setDataSource(NativePlayerContext* context,
                              const std::string& sourceId,
                              const std::string& originalUrl,
                              const std::string& resolvedUrl,
                              const std::string& contentType,
                              const std::string& userAgent,
                              bool liveStream,
                              bool localSource,
                              bool seekable,
                              int64_t probeLatencyMs);

    static void onPrepared(NativePlayerContext* context, int64_t durationMs);
    static void play(NativePlayerContext* context);
    static void pause(NativePlayerContext* context, int64_t positionMs);
    static void seekTo(NativePlayerContext* context, int64_t positionMs);
    static void stop(NativePlayerContext* context);
    static void onCompletion(NativePlayerContext* context, int64_t durationMs);
    static bool isReady(NativePlayerContext* context);
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_BRIDGE_H
