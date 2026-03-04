#ifndef FLEXMUSIC_PLAYER_BRIDGE_H
#define FLEXMUSIC_PLAYER_BRIDGE_H

#include <jni.h>
#include <memory>

namespace flexmusic {
namespace jni {

class PlayerBridge {
public:
    static PlayerBridge& getInstance();

    // JNI 注册
    void registerNatives(JNIEnv* env);

    // 播放控制
    void play(JNIEnv* env, jobject player);
    void pause(JNIEnv* env, jobject player);
    void stop(JNIEnv* env, jobject player);
    void seek(JNIEnv* env, jobject player, jlong position);
    void release(JNIEnv* env, jobject player);

    // 获取状态
    jlong getCurrentPosition(JNIEnv* env, jobject player);
    jlong getDuration(JNIEnv* env, jobject player);

private:
    PlayerBridge() = default;
    ~PlayerBridge() = default;
    PlayerBridge(const PlayerBridge&) = delete;
    PlayerBridge& operator=(const PlayerBridge&) = delete;
};

} // namespace jni
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_BRIDGE_H
