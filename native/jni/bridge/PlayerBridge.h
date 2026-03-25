#ifndef FLEXMUSIC_PLAYER_BRIDGE_H
#define FLEXMUSIC_PLAYER_BRIDGE_H

#include <cstdint>
#include <jni.h>
#include <memory>
#include <string>

#include "PlayerSession.h"

namespace flexmusic {
namespace jni {

bool register_PlayerBridgeJNI(JNIEnv* env);

struct NativePlayerContext {
    std::unique_ptr<flexmusic::player::PlayerSession> playerSession;
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
                              int detachedFd,
                              int64_t fdStartOffset,
                              int64_t fdLength,
                              bool liveStream,
                              bool localSource,
                              bool seekable,
                              int64_t probeLatencyMs);

    static void prepare(NativePlayerContext* context);
    static void play(NativePlayerContext* context);
    static void pause(NativePlayerContext* context);
    static void seekTo(NativePlayerContext* context, int64_t positionMs);
    static void stop(NativePlayerContext* context);
    static void setVolume(NativePlayerContext* context, float volume);
    static void setPlaybackSpeed(NativePlayerContext* context, float playbackSpeed);
    static bool isReady(NativePlayerContext* context);
    static int getState(NativePlayerContext* context);
    static int64_t getCurrentPosition(NativePlayerContext* context);
    static int64_t getDuration(NativePlayerContext* context);
    static std::string getErrorMessage(NativePlayerContext* context);
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_BRIDGE_H
