#ifndef FLEXMUSIC_ENCODED_PACKET_H
#define FLEXMUSIC_ENCODED_PACKET_H

struct AVPacket;

namespace flexmusic {
namespace media {

struct EncodedPacket {
    AVPacket* packet = nullptr;
    bool endOfStream = false;
};

} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_ENCODED_PACKET_H
