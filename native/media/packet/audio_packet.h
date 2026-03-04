#ifndef FLEXMUSIC_AUDIO_PACKET_H
#define FLEXMUSIC_AUDIO_PACKET_H

#include <cstdint>
#include <memory>

namespace flexmusic {
namespace media {

class AudioPacket {
public:
    AudioPacket();
    ~AudioPacket();

    // 分配数据
    bool allocate(size_t size);

    // 获取数据指针
    uint8_t* data() const { return data_; }
    size_t size() const { return size_; }
    size_t capacity() const { return capacity_; }

    // 设置数据
    void setData(const uint8_t* data, size_t size);

    // 音频参数
    int sampleRate() const { return sampleRate_; }
    int channels() const { return channels_; }
    void setSampleRate(int rate) { sampleRate_ = rate; }
    void setChannels(int channels) { channels_ = channels; }

    // 时间戳
    int64_t timestamp() const { return timestamp_; }
    void setTimestamp(int64_t ts) { timestamp_ = ts; }

    // 时长（毫秒）
    int64_t duration() const { return duration_; }
    void setDuration(int64_t duration) { duration_ = duration; }

private:
    uint8_t* data_{nullptr};
    size_t size_{0};
    size_t capacity_{0};

    int sampleRate_{0};
    int channels_{0};
    int64_t timestamp_{0};
    int64_t duration_{0};
};

} // namespace media
} // namespace flexmusic

#endif // FLEXMUSIC_AUDIO_PACKET_H
