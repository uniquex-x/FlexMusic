#ifndef FLEXMUSIC_PLAYER_RUNTIME_SNAPSHOT_H
#define FLEXMUSIC_PLAYER_RUNTIME_SNAPSHOT_H

#include <cstdint>
#include <string>

#include "PlayerState.h"

namespace flexmusic {
namespace player {

struct PlayerRuntimeSnapshot {
    PlayerState state = PlayerState::IDLE;
    int64_t currentPositionMs = 0;
    int64_t durationMs = 0;
    bool seekable = false;
    bool nativeReady = false;
    bool playing = false;
    float volume = 1.0f;
    float playbackSpeed = 1.0f;
    std::string backendName;
    std::string errorMessage;
};

} // namespace player
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_RUNTIME_SNAPSHOT_H
