#ifndef FLEXMUSIC_CODEC_INTERFACE_H
#define FLEXMUSIC_CODEC_INTERFACE_H

#include <cstdint>
#include <memory>
#include "../packet/audio_packet.h"

namespace flexmusic {
namespace media {

// 音频采样格式
enum class SampleFormat {
    UNKNOWN = 0,
    U8,
    S16,
    S32,
    FLOAT,
    DOUBLE,
    U8P,
    S16P,
    S32P,
    FLOATP,
    DOUBLEP
};

// 编解码器类型
enum class CodecType {
    UNKNOWN = 0,
    DECODER,
    ENCODER
};

// 编解码器信息
struct CodecInfo {
    const char* name;
    CodecType type;
    SampleFormat sampleFormat;
    uint32_t sampleRate;
    uint8_t channels;
};

// 解码器接口
class IDecoder {
public:
    virtual ~IDecoder() = default;

    virtual int open(const CodecInfo& info) = 0;
    virtual int decode(const uint8_t* data, size_t size) = 0;
    virtual void close() = 0;
};

// 编码器接口
class IEncoder {
public:
    virtual ~IEncoder() = default;

    virtual int open(const CodecInfo& info) = 0;
    virtual int encode(const AudioPacket& packet) = 0;
    virtual void close() = 0;
};

} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_CODEC_INTERFACE_H
