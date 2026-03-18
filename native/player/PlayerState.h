#ifndef FLEXMUSIC_PLAYER_STATE_CAMEL_H
#define FLEXMUSIC_PLAYER_STATE_CAMEL_H

namespace flexmusic {
namespace player {

enum class PlayerState {
    IDLE = 0,
    PREPARING = 1,
    READY = 2,
    PLAYING = 3,
    PAUSED = 4,
    BUFFERING = 5,
    COMPLETED = 6,
    ERROR = 7
};

} // namespace player
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_STATE_CAMEL_H
