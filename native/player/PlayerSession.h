#ifndef FLEXMUSIC_PLAYER_SESSION_H
#define FLEXMUSIC_PLAYER_SESSION_H

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
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
    enum class CommandType {
        SET_DATA_SOURCE,
        PREPARE,
        PLAY,
        PAUSE,
        STOP,
        SEEK,
        SET_VOLUME,
        RELEASE
    };

    struct Command {
        CommandType type = CommandType::STOP;
        flexmusic::io::DataSourceSpec spec;
        std::string backendName;
        int64_t positionMs = 0;
        float volume = 1.0f;
    };

    void enqueueCommand(Command command);
    void controlLoop();
    void handleSetDataSourceCommand(const flexmusic::io::DataSourceSpec& spec,
                                    const std::string& backendName);
    void handlePrepareCommand();
    void handlePlayCommand();
    void handlePauseCommand();
    void handleStopCommand(bool clearDataSource);
    void handleSeekCommand(int64_t positionMs);
    void handleSetVolumeCommand(float volume);
    void startPipelineLocked(int64_t startPositionMs, bool autoStart);
    void beginStopLocked();
    void detachThreadsLocked(std::unique_ptr<std::thread>* prepareThread,
                             std::unique_ptr<std::thread>* demuxThread,
                             std::unique_ptr<std::thread>* decodeThread,
                             std::unique_ptr<std::thread>* renderThread);
    void finalizeStopLocked(bool clearDataSource);
    static void joinThread(std::unique_ptr<std::thread>* thread);
    void prepareLoop(int64_t startPositionMs);
    void demuxLoop();
    void decodeLoop();
    void renderLoop();
    bool applyPendingSeekIfNeeded();

    int64_t elapsedSincePipelineStartMs() const;
    void setStateLocked(PlayerState state);
    void setErrorLocked(const std::string& errorMessage);
    static void clearPacketQueue(flexmusic::core::BlockingQueue<flexmusic::media::EncodedPacket>* queue);
    static void clearPcmQueue(flexmusic::core::BlockingQueue<flexmusic::media::PcmFrame>* queue);

    mutable std::mutex mutex_;
    mutable std::mutex decoderMutex_;
    std::mutex commandMutex_;
    std::condition_variable commandCondition_;
    std::deque<Command> commandQueue_;
    flexmusic::io::DataSourceSpec dataSourceSpec_;
    PlayerRuntimeSnapshot snapshot_;
    bool hasDataSource_ = false;
    bool stopRequested_ = false;
    bool seekRequested_ = false;
    bool controlThreadExitRequested_ = false;
    bool firstFrameRendered_ = false;
    bool autoStartOnReady_ = true;
    bool firstPacketLogged_ = false;
    bool firstDecodedFrameLogged_ = false;
    bool firstRendererSubmitLogged_ = false;
    bool pendingPositionRebase_ = false;
    int queueSerial_ = 1;
    int activePositionSerial_ = 0;
    int pendingPositionRebaseSerial_ = 0;
    int64_t pendingSeekPositionMs_ = 0;
    int64_t activePositionOffsetMs_ = 0;
    int64_t pendingPositionRebaseTargetMs_ = 0;
    std::chrono::steady_clock::time_point pipelineStartedAt_{};
    std::unique_ptr<flexmusic::core::BlockingQueue<flexmusic::media::EncodedPacket>> packetQueue_;
    std::unique_ptr<flexmusic::core::BlockingQueue<flexmusic::media::PcmFrame>> pcmQueue_;
    std::unique_ptr<std::thread> prepareThread_;
    std::unique_ptr<std::thread> demuxThread_;
    std::unique_ptr<std::thread> decodeThread_;
    std::unique_ptr<std::thread> renderThread_;
    std::thread controlThread_;
    flexmusic::media::source::AvioDataSource avioDataSource_;
    flexmusic::media::demux::FfmpegDemuxer demuxer_;
    flexmusic::media::codec::FfmpegAudioDecoder decoder_;
    flexmusic::audio::OpenSlAudioRenderer renderer_;
};

} // namespace player
} // namespace flexmusic

#endif // FLEXMUSIC_PLAYER_SESSION_H
