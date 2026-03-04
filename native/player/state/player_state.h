#ifndef FLEXMUSIC_PLAYER_STATE_H
#define FLEXMUSIC_PLAYER_STATE_H

#include <string>

namespace flexmusic {
namespace player {

// 播放器状态
enum class PlayerState {
    IDLE = 0,           // 空闲
    PREPARING,          // 准备中
    PREPARED,           // 已准备
    PLAYING,            // 播放中
    PAUSED,             // 已暂停
    STOPPING,           // 停止中
    STOPPED,            // 已停止
    COMPLETED,          // 播放完成
    ERROR               // 错误
};

// 播放器事件类型
enum class PlayerEventType {
    STATE_CHANGED = 0,
    PLAYBACK_PROGRESS,
    BUFFERING_START,
    BUFFERING_END,
    ERROR_OCCURRED,
    COMPLETION
};

// 播放器事件
struct PlayerEvent {
    PlayerEventType type;
    int64_t timestamp;
    int errorCode;
    std::string message;

    PlayerEvent(PlayerEventType t, int64_t ts = 0, int err = 0, const std::string& msg = "")
        : type(t), timestamp(ts), errorCode(err), message(msg) {}
};

// 状态转换基类
class IPlayerState {
public:
    virtual ~IPlayerState() = default;

    virtual PlayerState getState() const = 0;
    virtual bool canPlay() const = 0;
    virtual bool canPause() const = 0;
    virtual bool canStop() const = 0;
    virtual bool canSeek() const = 0;
};

} // namespace player
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_STATE_H
