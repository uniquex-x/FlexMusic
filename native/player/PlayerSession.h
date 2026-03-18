#ifndef FLEXMUSIC_PLAYER_SESSION_H
#define FLEXMUSIC_PLAYER_SESSION_H

#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

#include "../audio/OpenSlAudioRenderer.h"
#include "../core/thread/BlockingQueue.h"
#include "../io/DataSourceSpec.h"
#include "../media/codec/FfmpegAudioDecoder.h"
#include "../media/demux/FfmpegDemuxer.h"
#include "../media/packet/EncodedPacket.h"
#include "../media/packet/PcmFrame.h"
#include "../media/source/AvioDataSource.h"
#include "PlayerRuntimeSnapshot.h"

namespace flexmusic {
namespace player {

class PlayerSession final {
public:
    PlayerSession();
    ~PlayerSession();

    bool setDataSource(const flexmusic::io::DataSourceSpec& spec,
                       const std::string& backendName,
                       std::string* errorMessage);
    void prepare();
    void play();
    void pause();
    void stop();
    void seekTo(int64_t positionMs);
    void setVolume(float volume);
    PlayerRuntimeSnapshot snapshot() const;

private:
    void startPipelineLocked(int64_t startPositionMs, bool autoStart);
    void beginStopLocked();
    void detachThreadsLocked(std::unique_ptr<std::thread>* prepareThread,
                             std::unique_ptr<std::thread>* demuxThread,
                             std::unique_ptr<std::thread>* decodeThread,
                             std::unique_ptr<std::thread>* renderThread);
    void finalizeStopLocked(bool clearDataSource);
    static void joinThread(std::unique_ptr<std::thread>* thread);
    void prepareLoop(int64_t startPositionMs, bool autoStart);
    void demuxLoop();
    void decodeLoop();
    void renderLoop(bool autoStart);

    int64_t elapsedSincePipelineStartMs() const;
    void setStateLocked(PlayerState state);
    void setErrorLocked(const std::string& errorMessage);

    mutable std::mutex mutex_;
    flexmusic::io::DataSourceSpec dataSourceSpec_;
    PlayerRuntimeSnapshot snapshot_;
    bool hasDataSource_ = false;
    bool stopRequested_ = false;
    bool firstFrameRendered_ = false;
    bool autoStartOnReady_ = true;
    bool firstPacketLogged_ = false;
    bool firstDecodedFrameLogged_ = false;
    bool firstRendererSubmitLogged_ = false;
    std::chrono::steady_clock::time_point pipelineStartedAt_{};
    std::unique_ptr<flexmusic::core::BlockingQueue<flexmusic::media::EncodedPacket>> packetQueue_;
    std::unique_ptr<flexmusic::core::BlockingQueue<flexmusic::media::PcmFrame>> pcmQueue_;
    std::unique_ptr<std::thread> prepareThread_;
    std::unique_ptr<std::thread> demuxThread_;
    std::unique_ptr<std::thread> decodeThread_;
    std::unique_ptr<std::thread> renderThread_;
    flexmusic::media::source::AvioDataSource avioDataSource_;
    flexmusic::media::demux::FfmpegDemuxer demuxer_;
    flexmusic::media::codec::FfmpegAudioDecoder decoder_;
    flexmusic::audio::OpenSlAudioRenderer renderer_;
};

} // namespace player
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_SESSION_H
