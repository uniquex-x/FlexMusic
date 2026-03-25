#ifndef FLEXMUSIC_ERROR_CODE_H
#define FLEXMUSIC_ERROR_CODE_H

namespace flexmusic {
namespace utils {

// 错误码定义
enum class ErrorCode {
    SUCCESS = 0,
    ERROR_UNKNOWN = -1,
    ERROR_INVALID_ARGUMENT = -2,
    ERROR_NULL_POINTER = -3,
    ERROR_OUT_OF_MEMORY = -4,
    ERROR_IO = -5,
    ERROR_NETWORK = -6,
    ERROR_CODEC = -7,
    ERROR_DEMUX = -8,
    ERROR_MUX = -9,
    ERROR_PLAYER_STATE = -10,
    ERROR_TRANSCODE_FAILED = -11,
    ERROR_CACHE_MISS = -12,
    ERROR_TIMEOUT = -13,
    ERROR_NOT_SUPPORTED = -14,
    ERROR_PERMISSION_DENIED = -15
};

// 错误分类
enum class ErrorCategory {
    NONE,
    IO,
    NETWORK,
    CODEC,
    PLAYER,
    TRANSCODE,
    SYSTEM
};

} // namespace core
} // namespace flexmusic

#endif // FLEXMUSIC_ERROR_CODE_H
