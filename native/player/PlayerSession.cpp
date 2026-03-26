#include "PlayerSession.h"

#include "logger.h"

#include <algorithm>
#include <chrono>
#include <utility>
#include <vector>

#include "FileIoRegistry.h"

extern "C" {
#include <libavcodec/avcodec.h>
}

namespace flexmusic {
namespace player {

namespace {

constexpr std::size_t kPacketQueueSize = 96;
constexpr std::size_t kPcmQueueSize = 48;
constexpr int kReadFailureRetryLimit = 3;
constexpr int kInvalidPacketRecoveryThreshold = 3;
constexpr char kPlayerSessionTag[] = "PlayerSession";

void releasePacket(AVPacket* packet) {
    if (packet != nullptr) {
        av_packet_free(&packet);
    }
}

void releaseEncodedPacket(flexmusic::media::EncodedPacket& packet) {
    releasePacket(packet.packet);
    packet.packet = nullptr;
}

} // namespace

PlayerSession::PlayerSession() {
    snapshot_.state = PlayerState::IDLE;
    controlThread_ = std::thread(&PlayerSession::controlLoop, this);
}

PlayerSession::~PlayerSession() {
    Command releaseCommand;
    releaseCommand.type = CommandType::RELEASE;
    enqueueCommand(std::move(releaseCommand));
    if (controlThread_.joinable()) {
        controlThread_.join();
    }
}

bool PlayerSession::setDataSource(const flexmusic::io::DataSourceSpec& spec,
                                  const std::string& backendName,
                                  std::string* errorMessage) {
    Command command;
    command.type = CommandType::SET_DATA_SOURCE;
    command.spec = spec;
    command.backendName = backendName;
    enqueueCommand(std::move(command));
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void PlayerSession::prepare() {
    Command command;
    command.type = CommandType::PREPARE;
    enqueueCommand(std::move(command));
}

void PlayerSession::play() {
    Command command;
    command.type = CommandType::PLAY;
    enqueueCommand(std::move(command));
}

void PlayerSession::pause() {
    Command command;
    command.type = CommandType::PAUSE;
    enqueueCommand(std::move(command));
}

void PlayerSession::stop() {
    Command command;
    command.type = CommandType::STOP;
    enqueueCommand(std::move(command));
}

void PlayerSession::seekTo(int64_t positionMs) {
    Command command;
    command.type = CommandType::SEEK;
    command.positionMs = positionMs;
    enqueueCommand(std::move(command));
}

void PlayerSession::setVolume(float volume) {
    Command command;
    command.type = CommandType::SET_VOLUME;
    command.volume = volume;
    enqueueCommand(std::move(command));
}

void PlayerSession::setPlaybackSpeed(float playbackSpeed) {
    Command command;
    command.type = CommandType::SET_PLAYBACK_SPEED;
    command.playbackSpeed = playbackSpeed;
    enqueueCommand(std::move(command));
}

PlayerRuntimeSnapshot PlayerSession::snapshot() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return snapshot_;
}

void PlayerSession::enqueueCommand(Command command) {
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        if (controlThreadExitRequested_) {
            return;
        }
        auto removePendingCommands = [this](CommandType type) {
            commandQueue_.erase(
                    std::remove_if(commandQueue_.begin(), commandQueue_.end(), [type](const Command& pending) {
                        return pending.type == type;
                    }),
                    commandQueue_.end());
        };
        switch (command.type) {
            case CommandType::SET_DATA_SOURCE:
                commandQueue_.clear();
                break;
            case CommandType::SEEK:
                removePendingCommands(CommandType::SEEK);
                break;
            case CommandType::PLAY:
            case CommandType::PAUSE:
                removePendingCommands(CommandType::PLAY);
                removePendingCommands(CommandType::PAUSE);
                break;
            case CommandType::SET_VOLUME:
                removePendingCommands(CommandType::SET_VOLUME);
                break;
            case CommandType::SET_PLAYBACK_SPEED:
                removePendingCommands(CommandType::SET_PLAYBACK_SPEED);
                break;
            case CommandType::RECOVER:
                removePendingCommands(CommandType::RECOVER);
                break;
            case CommandType::STOP:
                commandQueue_.clear();
                break;
            case CommandType::PREPARE:
            case CommandType::RELEASE:
                break;
        }
        commandQueue_.push_back(std::move(command));
    }
    commandCondition_.notify_one();
}

void PlayerSession::controlLoop() {
    while (true) {
        Command command;
        {
            std::unique_lock<std::mutex> lock(commandMutex_);
            commandCondition_.wait(lock, [this]() {
                return controlThreadExitRequested_ || !commandQueue_.empty();
            });
            if (controlThreadExitRequested_ && commandQueue_.empty()) {
                return;
            }
            command = std::move(commandQueue_.front());
            commandQueue_.pop_front();
        }

        switch (command.type) {
            case CommandType::SET_DATA_SOURCE:
                handleSetDataSourceCommand(command.spec, command.backendName);
                break;
            case CommandType::PREPARE:
                handlePrepareCommand();
                break;
            case CommandType::PLAY:
                handlePlayCommand();
                break;
            case CommandType::PAUSE:
                handlePauseCommand();
                break;
            case CommandType::STOP:
                handleStopCommand(true);
                break;
            case CommandType::SEEK:
                handleSeekCommand(command.positionMs);
                break;
            case CommandType::SET_VOLUME:
                handleSetVolumeCommand(command.volume);
                break;
            case CommandType::SET_PLAYBACK_SPEED:
                handleSetPlaybackSpeedCommand(command.playbackSpeed);
                break;
            case CommandType::RECOVER:
                handleRecoverCommand(command.positionMs, command.autoStart, command.reason);
                break;
            case CommandType::RELEASE:
                handleStopCommand(true);
                {
                    std::lock_guard<std::mutex> lock(commandMutex_);
                    controlThreadExitRequested_ = true;
                    commandQueue_.clear();
                }
                commandCondition_.notify_all();
                return;
        }
    }
}

void PlayerSession::handleSetDataSourceCommand(const flexmusic::io::DataSourceSpec& spec,
                                               const std::string& backendName) {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        beginStopLocked();
        detachThreadsLocked(&prepareThread, &demuxThread, &decodeThread, &renderThread);
    }
    joinThread(&prepareThread);
    joinThread(&demuxThread);
    joinThread(&decodeThread);
    joinThread(&renderThread);
    clearTempoProcessor();
    {
        std::lock_guard<std::mutex> lock(mutex_);
        finalizeStopLocked(false);
        dataSourceSpec_ = spec;
        snapshot_ = PlayerRuntimeSnapshot();
        snapshot_.state = PlayerState::IDLE;
        snapshot_.seekable = spec.seekable;
        snapshot_.backendName = backendName;
        snapshot_.nativeReady = !backendName.empty();
        hasDataSource_ = true;
    }
}

void PlayerSession::handlePrepareCommand() {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!hasDataSource_) {
            setErrorLocked("Data source is not configured");
            return;
        }
        beginStopLocked();
        detachThreadsLocked(&prepareThread, &demuxThread, &decodeThread, &renderThread);
    }
    joinThread(&prepareThread);
    joinThread(&demuxThread);
    joinThread(&decodeThread);
    joinThread(&renderThread);
    {
        std::lock_guard<std::mutex> lock(mutex_);
        finalizeStopLocked(false);
        startPipelineLocked(0, true);
    }
}

void PlayerSession::handlePlayCommand() {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    bool restartFromZero = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (snapshot_.state == PlayerState::PREPARING) {
            autoStartOnReady_ = true;
            snapshot_.playing = true;
            return;
        }
        if (snapshot_.state == PlayerState::READY) {
            autoStartOnReady_ = true;
            renderer_.play();
            snapshot_.playing = true;
            setStateLocked(PlayerState::PLAYING);
            return;
        }
        if (snapshot_.state == PlayerState::PAUSED) {
            autoStartOnReady_ = true;
            renderer_.play();
            snapshot_.playing = true;
            setStateLocked(PlayerState::PLAYING);
            return;
        }
        restartFromZero = snapshot_.state == PlayerState::COMPLETED && hasDataSource_;
        if (!restartFromZero) {
            return;
        }
        beginStopLocked();
        detachThreadsLocked(&prepareThread, &demuxThread, &decodeThread, &renderThread);
    }
    joinThread(&prepareThread);
    joinThread(&demuxThread);
    joinThread(&decodeThread);
    joinThread(&renderThread);
    {
        std::lock_guard<std::mutex> lock(mutex_);
        finalizeStopLocked(false);
        startPipelineLocked(0, true);
    }
}

void PlayerSession::handlePauseCommand() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (snapshot_.state == PlayerState::PREPARING || snapshot_.state == PlayerState::READY) {
        autoStartOnReady_ = false;
        snapshot_.playing = false;
        return;
    }
    if (snapshot_.state != PlayerState::PLAYING && snapshot_.state != PlayerState::BUFFERING) {
        return;
    }
    autoStartOnReady_ = false;
    renderer_.pause();
    snapshot_.playing = false;
    setStateLocked(PlayerState::PAUSED);
}

void PlayerSession::handleStopCommand(bool clearDataSource) {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        beginStopLocked();
        detachThreadsLocked(&prepareThread, &demuxThread, &decodeThread, &renderThread);
    }
    joinThread(&prepareThread);
    joinThread(&demuxThread);
    joinThread(&decodeThread);
    joinThread(&renderThread);
    {
        std::lock_guard<std::mutex> lock(mutex_);
        finalizeStopLocked(clearDataSource);
    }
}

void PlayerSession::handleSeekCommand(int64_t positionMs) {
    flexmusic::utils::BlockingQueue<flexmusic::media::EncodedPacket>* packetQueue = nullptr;
    flexmusic::utils::BlockingQueue<flexmusic::media::PcmFrame>* pcmQueue = nullptr;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!hasDataSource_ || !snapshot_.seekable) {
            return;
        }
        const int64_t safePositionMs = std::max<int64_t>(0, positionMs);
        const bool shouldResume = snapshot_.playing
                || autoStartOnReady_
                || snapshot_.state == PlayerState::PLAYING
                || snapshot_.state == PlayerState::BUFFERING;
        pendingSeekPositionMs_ = safePositionMs;
        seekRequested_ = true;
        autoStartOnReady_ = shouldResume;
        queueSerial_++;
        firstFrameRendered_ = false;
        firstPacketLogged_ = false;
        firstDecodedFrameLogged_ = false;
        firstRendererSubmitLogged_ = false;
        pendingPositionRebase_ = true;
        pendingPositionRebaseSerial_ = queueSerial_;
        pendingPositionRebaseTargetMs_ = safePositionMs;
        pipelineStartedAt_ = std::chrono::steady_clock::now();
        snapshot_.currentPositionMs = safePositionMs;
        snapshot_.errorMessage.clear();
        snapshot_.playing = shouldResume;
        setStateLocked(PlayerState::PREPARING);
        packetQueue = packetQueue_.get();
        pcmQueue = pcmQueue_.get();
        flexmusic::utils::levelLog(kPlayerSessionTag).i(
                "seek request sourceId=%s positionMs=%lld resume=%d serial=%d state=%d",
                dataSourceSpec_.sourceId.c_str(),
                static_cast<long long>(safePositionMs),
                shouldResume ? 1 : 0,
                queueSerial_,
                static_cast<int>(snapshot_.state));
    }
    clearPacketQueue(packetQueue);
    clearPcmQueue(pcmQueue);
    renderer_.flush();
    clearTempoProcessor();
}

void PlayerSession::handleSetVolumeCommand(float volume) {
    std::lock_guard<std::mutex> lock(mutex_);
    snapshot_.volume = std::max(0.0f, std::min(1.0f, volume));
    renderer_.setVolume(snapshot_.volume);
}

void PlayerSession::handleSetPlaybackSpeedCommand(float playbackSpeed) {
    float safePlaybackSpeed = 1.0f;
    std::string sourceId;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        snapshot_.playbackSpeed = std::max(0.5f, std::min(2.0f, playbackSpeed));
        safePlaybackSpeed = snapshot_.playbackSpeed;
        sourceId = dataSourceSpec_.sourceId;
    }
    {
        std::lock_guard<std::mutex> tempoLock(tempoProcessorMutex_);
        tempoProcessor_.setPlaybackSpeed(safePlaybackSpeed);
    }
    flexmusic::utils::levelLog(kPlayerSessionTag).i(
            "set playback speed sourceId=%s speed=%.2f",
            sourceId.c_str(),
            safePlaybackSpeed);
}

void PlayerSession::handleRecoverCommand(int64_t positionMs, bool autoStart, const std::string& reason) {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    std::string sourceId;
    std::string backendName;
    std::string resolvedUrl;
    bool liveStream = false;
    bool seekable = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        recoveryPending_ = false;
        if (!hasDataSource_) {
            return;
        }
        sourceId = dataSourceSpec_.sourceId;
        backendName = snapshot_.backendName;
        resolvedUrl = dataSourceSpec_.resolvedUrl;
        liveStream = dataSourceSpec_.liveStream;
        seekable = snapshot_.seekable;
        flexmusic::utils::levelLog(kPlayerSessionTag).w(
                "recover start sourceId=%s url=%s backend=%s live=%d seekable=%d positionMs=%lld autoStart=%d reason=%s",
                sourceId.c_str(),
                resolvedUrl.c_str(),
                backendName.c_str(),
                liveStream ? 1 : 0,
                seekable ? 1 : 0,
                static_cast<long long>(positionMs),
                autoStart ? 1 : 0,
                reason.c_str());
        beginStopLocked();
        detachThreadsLocked(&prepareThread, &demuxThread, &decodeThread, &renderThread);
    }
    joinThread(&prepareThread);
    joinThread(&demuxThread);
    joinThread(&decodeThread);
    joinThread(&renderThread);
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!hasDataSource_) {
            return;
        }
        finalizeStopLocked(false);
        startPipelineLocked(positionMs, autoStart);
    }
    flexmusic::utils::levelLog(kPlayerSessionTag).w(
            "recover scheduled sourceId=%s url=%s backend=%s live=%d seekable=%d positionMs=%lld autoStart=%d reason=%s",
            sourceId.c_str(),
            resolvedUrl.c_str(),
            backendName.c_str(),
            liveStream ? 1 : 0,
            seekable ? 1 : 0,
            static_cast<long long>(positionMs),
            autoStart ? 1 : 0,
            reason.c_str());
}

void PlayerSession::startPipelineLocked(int64_t startPositionMs, bool autoStart) {
    packetQueue_ = std::make_unique<flexmusic::utils::BlockingQueue<flexmusic::media::EncodedPacket>>(kPacketQueueSize);
    pcmQueue_ = std::make_unique<flexmusic::utils::BlockingQueue<flexmusic::media::PcmFrame>>(kPcmQueueSize);
    stopRequested_ = false;
    seekRequested_ = false;
    firstFrameRendered_ = false;
    firstPacketLogged_ = false;
    firstDecodedFrameLogged_ = false;
    firstRendererSubmitLogged_ = false;
    consecutiveReadFailureCount_ = 0;
    consecutiveInvalidPacketCount_ = 0;
    recoveryPending_ = false;
    autoStartOnReady_ = autoStart;
    queueSerial_++;
    pendingPositionRebase_ = false;
    activePositionSerial_ = queueSerial_;
    activePositionOffsetMs_ = 0;
    pendingPositionRebaseSerial_ = 0;
    pendingPositionRebaseTargetMs_ = 0;
    pendingSeekPositionMs_ = startPositionMs;
    pipelineStartedAt_ = std::chrono::steady_clock::now();
    snapshot_.currentPositionMs = startPositionMs;
    snapshot_.durationMs = 0;
    snapshot_.errorMessage.clear();
    snapshot_.playing = autoStart;
    snapshot_.nativeReady = true;
    setStateLocked(PlayerState::PREPARING);
    flexmusic::utils::levelLog(kPlayerSessionTag).i(
            "pipeline start sourceId=%s backend=%s startPositionMs=%lld autoStart=%d",
            dataSourceSpec_.sourceId.c_str(),
            snapshot_.backendName.c_str(),
            static_cast<long long>(startPositionMs),
            autoStart ? 1 : 0);

    prepareThread_ = std::make_unique<std::thread>(&PlayerSession::prepareLoop, this, startPositionMs);
}

void PlayerSession::requestStreamRecovery(int64_t positionMs, bool autoStart, const std::string& reason) {
    bool shouldEnqueue = false;
    std::string sourceId;
    std::string backendName;
    std::string resolvedUrl;
    bool liveStream = false;
    bool seekable = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_ || !hasDataSource_ || recoveryPending_) {
            return;
        }
        recoveryPending_ = true;
        consecutiveReadFailureCount_ = 0;
        consecutiveInvalidPacketCount_ = 0;
        sourceId = dataSourceSpec_.sourceId;
        backendName = snapshot_.backendName;
        resolvedUrl = dataSourceSpec_.resolvedUrl;
        liveStream = dataSourceSpec_.liveStream;
        seekable = snapshot_.seekable;
        shouldEnqueue = true;
    }
    if (!shouldEnqueue) {
        return;
    }
    flexmusic::utils::levelLog(kPlayerSessionTag).w(
            "recover requested sourceId=%s url=%s backend=%s live=%d seekable=%d positionMs=%lld autoStart=%d reason=%s",
            sourceId.c_str(),
            resolvedUrl.c_str(),
            backendName.c_str(),
            liveStream ? 1 : 0,
            seekable ? 1 : 0,
            static_cast<long long>(positionMs),
            autoStart ? 1 : 0,
            reason.c_str());
    Command command;
    command.type = CommandType::RECOVER;
    command.positionMs = positionMs;
    command.autoStart = autoStart;
    command.reason = reason;
    enqueueCommand(std::move(command));
}

void PlayerSession::beginStopLocked() {
    stopRequested_ = true;
    seekRequested_ = false;
    if (packetQueue_ != nullptr) {
        clearPacketQueue(packetQueue_.get());
        packetQueue_->close();
    }
    if (pcmQueue_ != nullptr) {
        clearPcmQueue(pcmQueue_.get());
        pcmQueue_->close();
    }
    renderer_.stop();
}

void PlayerSession::detachThreadsLocked(std::unique_ptr<std::thread>* prepareThread,
                                        std::unique_ptr<std::thread>* demuxThread,
                                        std::unique_ptr<std::thread>* decodeThread,
                                        std::unique_ptr<std::thread>* renderThread) {
    if (prepareThread != nullptr) {
        *prepareThread = std::move(prepareThread_);
    }
    if (demuxThread != nullptr) {
        *demuxThread = std::move(demuxThread_);
    }
    if (decodeThread != nullptr) {
        *decodeThread = std::move(decodeThread_);
    }
    if (renderThread != nullptr) {
        *renderThread = std::move(renderThread_);
    }
}

void PlayerSession::finalizeStopLocked(bool clearDataSource) {
    decoder_.close();
    demuxer_.close();
    avioDataSource_.close();
    clearTempoProcessor();
    packetQueue_.reset();
    pcmQueue_.reset();
    firstFrameRendered_ = false;
    pendingPositionRebase_ = false;
    recoveryPending_ = false;
    consecutiveReadFailureCount_ = 0;
    consecutiveInvalidPacketCount_ = 0;
    activePositionSerial_ = 0;
    activePositionOffsetMs_ = 0;
    pendingPositionRebaseSerial_ = 0;
    pendingPositionRebaseTargetMs_ = 0;
    snapshot_.playing = false;
    snapshot_.nativeReady = hasDataSource_ && !snapshot_.backendName.empty();
    if (clearDataSource) {
        hasDataSource_ = false;
        dataSourceSpec_ = flexmusic::io::DataSourceSpec();
        snapshot_ = PlayerRuntimeSnapshot();
        snapshot_.state = PlayerState::IDLE;
    }
}

void PlayerSession::joinThread(std::unique_ptr<std::thread>* thread) {
    if (thread != nullptr && *thread != nullptr && (*thread)->joinable()) {
        (*thread)->join();
    }
    if (thread != nullptr) {
        thread->reset();
    }
}

void PlayerSession::clearPacketQueue(flexmusic::utils::BlockingQueue<flexmusic::media::EncodedPacket>* queue) {
    if (queue == nullptr) {
        return;
    }
    queue->clearWith([](flexmusic::media::EncodedPacket& packet) {
        releaseEncodedPacket(packet);
    });
}

void PlayerSession::clearPcmQueue(flexmusic::utils::BlockingQueue<flexmusic::media::PcmFrame>* queue) {
    if (queue == nullptr) {
        return;
    }
    queue->clearWith([](flexmusic::media::PcmFrame&) {
    });
}

bool PlayerSession::applyPendingSeekIfNeeded() {
    int64_t targetPositionMs = 0;
    int activeSerial = 0;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!seekRequested_) {
            return true;
        }
        if (stopRequested_) {
            return false;
        }
        targetPositionMs = pendingSeekPositionMs_;
        activeSerial = queueSerial_;
    }

    std::string errorMessage;
    if (!demuxer_.seekTo(targetPositionMs, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!stopRequested_) {
            setErrorLocked(errorMessage.empty() ? "Seek failed" : errorMessage);
        }
        return false;
    }
    {
        std::lock_guard<std::mutex> decoderLock(decoderMutex_);
        if (!decoder_.reset(&errorMessage)) {
            std::lock_guard<std::mutex> lock(mutex_);
            if (!stopRequested_) {
                setErrorLocked(errorMessage.empty() ? "Reset decoder failed" : errorMessage);
            }
            return false;
        }
    }
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return false;
        }
        if (queueSerial_ != activeSerial || pendingSeekPositionMs_ != targetPositionMs) {
            flexmusic::utils::levelLog(kPlayerSessionTag).i(
                    "seek superseded sourceId=%s positionMs=%lld serial=%d latestSerial=%d latestPositionMs=%lld",
                    dataSourceSpec_.sourceId.c_str(),
                    static_cast<long long>(targetPositionMs),
                    activeSerial,
                    queueSerial_,
                    static_cast<long long>(pendingSeekPositionMs_));
            return true;
        }
        seekRequested_ = false;
        snapshot_.currentPositionMs = targetPositionMs;
        snapshot_.playing = autoStartOnReady_;
    }

    flexmusic::utils::levelLog(kPlayerSessionTag).i(
            "seek applied sourceId=%s positionMs=%lld serial=%d autoStart=%d",
            dataSourceSpec_.sourceId.c_str(),
            static_cast<long long>(targetPositionMs),
            activeSerial,
            autoStartOnReady_ ? 1 : 0);
    return true;
}

void PlayerSession::clearTempoProcessor() {
    std::lock_guard<std::mutex> lock(tempoProcessorMutex_);
    tempoProcessor_.clear();
}

bool PlayerSession::processDecodedFrameWithTempo(const flexmusic::media::PcmFrame& frame,
                                                 std::vector<flexmusic::media::PcmFrame>* outputFrames,
                                                 std::string* errorMessage) {
    std::lock_guard<std::mutex> lock(tempoProcessorMutex_);
    return tempoProcessor_.processFrame(frame, outputFrames, errorMessage);
}

bool PlayerSession::flushTempoProcessor(std::vector<flexmusic::media::PcmFrame>* outputFrames,
                                        std::string* errorMessage) {
    std::lock_guard<std::mutex> lock(tempoProcessorMutex_);
    return tempoProcessor_.flush(outputFrames, errorMessage);
}

void PlayerSession::prepareLoop(int64_t startPositionMs) {
    const auto log = flexmusic::utils::levelLog(kPlayerSessionTag);
    flexmusic::io::DataSourceSpec dataSourceSpec;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        dataSourceSpec = dataSourceSpec_;
    }

    std::string errorMessage;
    std::unique_ptr<flexmusic::io::IFileIo> fileIo = flexmusic::io::FileIoRegistry::createForSpec(dataSourceSpec, &errorMessage);
    if (fileIo == nullptr) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        setErrorLocked(errorMessage.empty() ? "Create FileIo failed" : errorMessage);
        return;
    }
    log.i("prepareLoop sourceId=%s url=%s contentType=%s local=%d live=%d seekable=%d fd=%d backend=%s",
          dataSourceSpec.sourceId.c_str(),
          dataSourceSpec.resolvedUrl.c_str(),
          dataSourceSpec.contentType.c_str(),
          dataSourceSpec.localSource ? 1 : 0,
          dataSourceSpec.liveStream ? 1 : 0,
          dataSourceSpec.seekable ? 1 : 0,
          dataSourceSpec.detachedFd,
          fileIo->implementationName());

    if (!avioDataSource_.open(std::move(fileIo), dataSourceSpec, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        setErrorLocked(errorMessage.empty() ? "Open data source failed" : errorMessage);
        return;
    }
    log.i("prepare stage=avio-open sourceId=%s live=%d seekable=%d elapsedMs=%lld",
          dataSourceSpec.sourceId.c_str(),
          dataSourceSpec.liveStream ? 1 : 0,
          dataSourceSpec.seekable ? 1 : 0,
          static_cast<long long>(elapsedSincePipelineStartMs()));
    if (!demuxer_.open(&avioDataSource_, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        setErrorLocked(errorMessage.empty() ? "Open demuxer failed" : errorMessage);
        return;
    }
    log.i("prepare stage=demux-open sourceId=%s live=%d elapsedMs=%lld durationMs=%lld seekable=%d",
          dataSourceSpec.sourceId.c_str(),
          dataSourceSpec.liveStream ? 1 : 0,
          static_cast<long long>(elapsedSincePipelineStartMs()),
          static_cast<long long>(demuxer_.durationMs()),
          demuxer_.isSeekable() ? 1 : 0);
    if (startPositionMs > 0 && demuxer_.isSeekable() && !demuxer_.seekTo(startPositionMs, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        setErrorLocked(errorMessage.empty() ? "Seek before start failed" : errorMessage);
        return;
    }
    {
        std::lock_guard<std::mutex> decoderLock(decoderMutex_);
        if (!decoder_.open(demuxer_.audioStreamInfo(), &errorMessage)) {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            setErrorLocked(errorMessage.empty() ? "Open decoder failed" : errorMessage);
            return;
        }
    }
    log.i("prepare stage=decoder-open sourceId=%s elapsedMs=%lld",
          dataSourceSpec.sourceId.c_str(),
          static_cast<long long>(elapsedSincePipelineStartMs()));

    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stopRequested_) {
            return;
        }
        snapshot_.durationMs = demuxer_.durationMs();
        snapshot_.seekable = snapshot_.seekable && demuxer_.isSeekable();
        snapshot_.currentPositionMs = startPositionMs;
    }

    demuxThread_ = std::make_unique<std::thread>(&PlayerSession::demuxLoop, this);
    decodeThread_ = std::make_unique<std::thread>(&PlayerSession::decodeLoop, this);
    renderThread_ = std::make_unique<std::thread>(&PlayerSession::renderLoop, this);
    log.i("prepare complete sourceId=%s totalElapsedMs=%lld",
          dataSourceSpec.sourceId.c_str(),
          static_cast<long long>(elapsedSincePipelineStartMs()));
}

void PlayerSession::demuxLoop() {
    const auto log = flexmusic::utils::levelLog(kPlayerSessionTag);
    while (true) {
        if (!applyPendingSeekIfNeeded()) {
            return;
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_ || packetQueue_ == nullptr) {
                return;
            }
        }

        flexmusic::media::EncodedPacket encodedPacket;
        std::string errorMessage;
        const flexmusic::media::demux::ReadPacketStatus readStatus =
                demuxer_.readPacket(&encodedPacket, &errorMessage);
        if (readStatus == flexmusic::media::demux::ReadPacketStatus::RETRYABLE_ERROR) {
            int failureCount = 0;
            int64_t recoverPositionMs = 0;
            bool autoStart = false;
            std::string backendName;
            {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                consecutiveReadFailureCount_++;
                failureCount = consecutiveReadFailureCount_;
                recoverPositionMs = snapshot_.seekable ? snapshot_.currentPositionMs : 0;
                autoStart = autoStartOnReady_;
                backendName = snapshot_.backendName;
            }
            log.w("retryable demux read failure sourceId=%s url=%s backend=%s live=%d seekable=%d failure=%d/%d recoverPositionMs=%lld error=%s",
                  dataSourceSpec_.sourceId.c_str(),
                  dataSourceSpec_.resolvedUrl.c_str(),
                  backendName.c_str(),
                  dataSourceSpec_.liveStream ? 1 : 0,
                  dataSourceSpec_.seekable ? 1 : 0,
                  failureCount,
                  kReadFailureRetryLimit,
                  static_cast<long long>(recoverPositionMs),
                  errorMessage.c_str());
            if (failureCount < kReadFailureRetryLimit) {
                continue;
            }
            requestStreamRecovery(
                    recoverPositionMs,
                    autoStart,
                    "retryable demux read failure threshold reached");
            return;
        }
        if (readStatus == flexmusic::media::demux::ReadPacketStatus::FATAL_ERROR) {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            setErrorLocked(errorMessage.empty() ? "Read packet failed" : errorMessage);
            if (packetQueue_ != nullptr) {
                packetQueue_->close();
            }
            return;
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            consecutiveReadFailureCount_ = 0;
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                releasePacket(encodedPacket.packet);
                return;
            }
            if (seekRequested_) {
                releasePacket(encodedPacket.packet);
                continue;
            }
            encodedPacket.serial = queueSerial_;
        }
        const AVPacket* packetForLog = encodedPacket.packet;
        if (!packetQueue_->push(std::move(encodedPacket))) {
            releasePacket(encodedPacket.packet);
            return;
        }
        if (!firstPacketLogged_ && packetForLog != nullptr) {
            firstPacketLogged_ = true;
            log.i("first demux packet sourceId=%s elapsedMs=%lld size=%d pts=%lld",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  packetForLog->size,
                  static_cast<long long>(packetForLog->pts));
        }
        if (readStatus == flexmusic::media::demux::ReadPacketStatus::END_OF_STREAM) {
            return;
        }
    }
}

void PlayerSession::decodeLoop() {
    const auto log = flexmusic::utils::levelLog(kPlayerSessionTag);
    while (true) {
        flexmusic::media::EncodedPacket encodedPacket;
        if (packetQueue_ == nullptr || !packetQueue_->pop(&encodedPacket)) {
            return;
        }

        int currentSerial = 0;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                releasePacket(encodedPacket.packet);
                return;
            }
            currentSerial = queueSerial_;
        }
        if (encodedPacket.serial != currentSerial) {
            releasePacket(encodedPacket.packet);
            continue;
        }

        std::vector<flexmusic::media::PcmFrame> decodedFrames;
        std::string errorMessage;
        if (encodedPacket.endOfStream) {
            bool flushOk = false;
            {
                std::lock_guard<std::mutex> decoderLock(decoderMutex_);
                flushOk = decoder_.flush(&decodedFrames, &errorMessage);
            }
            if (!flushOk) {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                setErrorLocked(errorMessage.empty() ? "Flush decoder failed" : errorMessage);
                if (pcmQueue_ != nullptr) {
                    pcmQueue_->close();
                }
                return;
            }
            for (flexmusic::media::PcmFrame& frame : decodedFrames) {
                std::vector<flexmusic::media::PcmFrame> processedFrames;
                if (!processDecodedFrameWithTempo(frame, &processedFrames, &errorMessage)) {
                    std::lock_guard<std::mutex> lock(mutex_);
                    if (stopRequested_) {
                        return;
                    }
                    setErrorLocked(errorMessage.empty() ? "Process SoundTouch frame failed" : errorMessage);
                    if (pcmQueue_ != nullptr) {
                        pcmQueue_->close();
                    }
                    return;
                }
                for (flexmusic::media::PcmFrame& processedFrame : processedFrames) {
                    processedFrame.serial = encodedPacket.serial;
                    if (pcmQueue_ == nullptr || !pcmQueue_->push(std::move(processedFrame))) {
                        return;
                    }
                }
            }
            std::vector<flexmusic::media::PcmFrame> trailingFrames;
            if (!flushTempoProcessor(&trailingFrames, &errorMessage)) {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                setErrorLocked(errorMessage.empty() ? "Flush SoundTouch failed" : errorMessage);
                if (pcmQueue_ != nullptr) {
                    pcmQueue_->close();
                }
                return;
            }
            for (flexmusic::media::PcmFrame& trailingFrame : trailingFrames) {
                trailingFrame.serial = encodedPacket.serial;
                if (pcmQueue_ == nullptr || !pcmQueue_->push(std::move(trailingFrame))) {
                    return;
                }
            }
            flexmusic::media::PcmFrame eosFrame;
            eosFrame.endOfStream = true;
            eosFrame.serial = encodedPacket.serial;
            if (pcmQueue_ != nullptr) {
                pcmQueue_->push(std::move(eosFrame));
            }
            return;
        }

        flexmusic::media::codec::DecodePacketStatus decodeStatus =
                flexmusic::media::codec::DecodePacketStatus::FATAL_ERROR;
        {
            std::lock_guard<std::mutex> decoderLock(decoderMutex_);
            decodeStatus = decoder_.decodePacket(encodedPacket, &decodedFrames, &errorMessage);
        }
        if (decodeStatus == flexmusic::media::codec::DecodePacketStatus::INVALID_PACKET) {
            if (!dataSourceSpec_.liveStream) {
                releasePacket(encodedPacket.packet);
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                setErrorLocked(errorMessage.empty() ? "Decode packet failed" : errorMessage);
                if (pcmQueue_ != nullptr) {
                    pcmQueue_->close();
                }
                return;
            }
            int invalidPacketCount = 0;
            int64_t recoverPositionMs = 0;
            bool autoStart = false;
            std::string backendName;
            {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    releasePacket(encodedPacket.packet);
                    return;
                }
                backendName = snapshot_.backendName;
            }
            log.w("invalid packet dropped sourceId=%s url=%s backend=%s live=%d size=%d pts=%lld dts=%lld serial=%d error=%s",
                  dataSourceSpec_.sourceId.c_str(),
                  dataSourceSpec_.resolvedUrl.c_str(),
                  backendName.c_str(),
                  dataSourceSpec_.liveStream ? 1 : 0,
                  encodedPacket.packet != nullptr ? encodedPacket.packet->size : 0,
                  static_cast<long long>(encodedPacket.packet != nullptr ? encodedPacket.packet->pts : AV_NOPTS_VALUE),
                  static_cast<long long>(encodedPacket.packet != nullptr ? encodedPacket.packet->dts : AV_NOPTS_VALUE),
                  encodedPacket.serial,
                  errorMessage.c_str());
            releasePacket(encodedPacket.packet);
            {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                consecutiveInvalidPacketCount_++;
                invalidPacketCount = consecutiveInvalidPacketCount_;
                recoverPositionMs = snapshot_.seekable ? snapshot_.currentPositionMs : 0;
                autoStart = autoStartOnReady_;
                backendName = snapshot_.backendName;
            }
            log.w("invalid packet streak sourceId=%s backend=%s live=%d failure=%d/%d recoverPositionMs=%lld",
                  dataSourceSpec_.sourceId.c_str(),
                  backendName.c_str(),
                  dataSourceSpec_.liveStream ? 1 : 0,
                  invalidPacketCount,
                  kInvalidPacketRecoveryThreshold,
                  static_cast<long long>(recoverPositionMs));
            if (invalidPacketCount >= kInvalidPacketRecoveryThreshold) {
                requestStreamRecovery(
                        recoverPositionMs,
                        autoStart,
                        "invalid packet threshold reached");
                return;
            }
            continue;
        }
        if (decodeStatus != flexmusic::media::codec::DecodePacketStatus::OK) {
            releasePacket(encodedPacket.packet);
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            setErrorLocked(errorMessage.empty() ? "Decode packet failed" : errorMessage);
            if (pcmQueue_ != nullptr) {
                pcmQueue_->close();
            }
            return;
        }
        releasePacket(encodedPacket.packet);
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            consecutiveInvalidPacketCount_ = 0;
            if (encodedPacket.serial != queueSerial_) {
                continue;
            }
        }

        if (!firstDecodedFrameLogged_ && !decodedFrames.empty()) {
            firstDecodedFrameLogged_ = true;
            const flexmusic::media::PcmFrame& firstFrame = decodedFrames.front();
            log.i("first decoded pcm sourceId=%s elapsedMs=%lld positionMs=%lld durationMs=%lld bytes=%zu",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  static_cast<long long>(firstFrame.positionMs),
                  static_cast<long long>(firstFrame.durationMs),
                  firstFrame.data.size());
        }
        for (flexmusic::media::PcmFrame& frame : decodedFrames) {
            frame.serial = encodedPacket.serial;
            {
                std::lock_guard<std::mutex> lock(mutex_);
                if (pendingPositionRebase_
                        && pendingPositionRebaseSerial_ == frame.serial
                        && pendingPositionRebaseTargetMs_ > 0) {
                    activePositionSerial_ = frame.serial;
                    activePositionOffsetMs_ = pendingPositionRebaseTargetMs_ - frame.positionMs;
                    pendingPositionRebase_ = false;
                    log.i("position rebase sourceId=%s serial=%d rawPositionMs=%lld targetMs=%lld offsetMs=%lld",
                          dataSourceSpec_.sourceId.c_str(),
                          frame.serial,
                          static_cast<long long>(frame.positionMs),
                          static_cast<long long>(pendingPositionRebaseTargetMs_),
                          static_cast<long long>(activePositionOffsetMs_));
                }
                if (activePositionSerial_ == frame.serial) {
                    frame.positionMs = std::max<int64_t>(0, frame.positionMs + activePositionOffsetMs_);
                }
            }
            std::vector<flexmusic::media::PcmFrame> processedFrames;
            if (!processDecodedFrameWithTempo(frame, &processedFrames, &errorMessage)) {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                setErrorLocked(errorMessage.empty() ? "Process SoundTouch frame failed" : errorMessage);
                if (pcmQueue_ != nullptr) {
                    pcmQueue_->close();
                }
                return;
            }
            for (flexmusic::media::PcmFrame& processedFrame : processedFrames) {
                processedFrame.serial = encodedPacket.serial;
                if (pcmQueue_ == nullptr || !pcmQueue_->push(std::move(processedFrame))) {
                    return;
                }
            }
        }
    }
}

void PlayerSession::renderLoop() {
    const auto log = flexmusic::utils::levelLog(kPlayerSessionTag);
    bool bufferingAnnounced = false;
    while (true) {
        flexmusic::media::PcmFrame pcmFrame;
        if (pcmQueue_ == nullptr) {
            return;
        }
        if (!pcmQueue_->popFor(&pcmFrame, std::chrono::milliseconds(200))) {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            if (firstFrameRendered_ && snapshot_.state == PlayerState::PLAYING) {
                setStateLocked(PlayerState::BUFFERING);
                bufferingAnnounced = true;
            }
            continue;
        }

        int currentSerial = 0;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_) {
                return;
            }
            currentSerial = queueSerial_;
        }
        if (pcmFrame.serial != currentSerial) {
            continue;
        }

        if (pcmFrame.endOfStream) {
            renderer_.waitForDrain();
            std::lock_guard<std::mutex> lock(mutex_);
            if (!stopRequested_) {
                snapshot_.playing = false;
                snapshot_.currentPositionMs = snapshot_.durationMs;
                setStateLocked(PlayerState::COMPLETED);
            }
            return;
        }

        if (!renderer_.isOpen()) {
            std::string errorMessage;
            if (!renderer_.open(pcmFrame.sampleRate, pcmFrame.channelCount, &errorMessage)) {
                std::lock_guard<std::mutex> lock(mutex_);
                if (stopRequested_) {
                    return;
                }
                setErrorLocked(errorMessage.empty() ? "Open renderer failed" : errorMessage);
                return;
            }
            log.i("renderer open sourceId=%s elapsedMs=%lld sampleRate=%d channels=%d",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  pcmFrame.sampleRate,
                  pcmFrame.channelCount);
            bool shouldAutoStart = false;
            {
                std::lock_guard<std::mutex> lock(mutex_);
                renderer_.setVolume(snapshot_.volume);
                shouldAutoStart = autoStartOnReady_;
            }
            if (shouldAutoStart) {
                renderer_.play();
            } else {
                renderer_.pause();
            }
        }

        std::string errorMessage;
        if (!renderer_.enqueueFrame(std::move(pcmFrame), &errorMessage)) {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_ || errorMessage == "Audio renderer is closed") {
                return;
            }
            setErrorLocked(errorMessage.empty() ? "Render PCM failed" : errorMessage);
            return;
        }
        bool staleFrameRendered = false;
        bool stopAfterEnqueue = false;
        {
            std::lock_guard<std::mutex> lock(mutex_);
            stopAfterEnqueue = stopRequested_;
            staleFrameRendered = pcmFrame.serial != queueSerial_;
        }
        if (staleFrameRendered) {
            renderer_.flush();
            if (stopAfterEnqueue) {
                return;
            }
            continue;
        }
        if (stopAfterEnqueue) {
            return;
        }
        if (!firstRendererSubmitLogged_) {
            firstRendererSubmitLogged_ = true;
            log.i("first pcm submitted sourceId=%s elapsedMs=%lld",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()));
        }

        std::lock_guard<std::mutex> lock(mutex_);
        snapshot_.currentPositionMs = pcmFrame.positionMs + pcmFrame.durationMs;
        if (!firstFrameRendered_) {
            firstFrameRendered_ = true;
            snapshot_.playing = autoStartOnReady_;
            setStateLocked(autoStartOnReady_ ? PlayerState::PLAYING : PlayerState::READY);
            log.i("first frame rendered sourceId=%s elapsedMs=%lld autoStart=%d",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  autoStartOnReady_ ? 1 : 0);
            continue;
        }
        if (bufferingAnnounced && autoStartOnReady_) {
            bufferingAnnounced = false;
            snapshot_.playing = true;
            setStateLocked(PlayerState::PLAYING);
        }
    }
}

int64_t PlayerSession::elapsedSincePipelineStartMs() const {
    if (pipelineStartedAt_ == std::chrono::steady_clock::time_point{}) {
        return 0;
    }
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - pipelineStartedAt_).count();
}

void PlayerSession::setStateLocked(PlayerState state) {
    if (snapshot_.state != state) {
        flexmusic::utils::levelLog(kPlayerSessionTag).i(
                "state change %d -> %d sourceId=%s elapsedMs=%lld",
                static_cast<int>(snapshot_.state),
                static_cast<int>(state),
                dataSourceSpec_.sourceId.c_str(),
                static_cast<long long>(elapsedSincePipelineStartMs()));
    }
    snapshot_.state = state;
}

void PlayerSession::setErrorLocked(const std::string& errorMessage) {
    flexmusic::utils::levelLog(kPlayerSessionTag).e(
            "error sourceId=%s elapsedMs=%lld message=%s",
            dataSourceSpec_.sourceId.c_str(),
            static_cast<long long>(elapsedSincePipelineStartMs()),
            errorMessage.c_str());
    stopRequested_ = true;
    snapshot_.playing = false;
    snapshot_.errorMessage = errorMessage;
    setStateLocked(PlayerState::ERROR);
    if (packetQueue_ != nullptr) {
        packetQueue_->close();
    }
    if (pcmQueue_ != nullptr) {
        pcmQueue_->close();
    }
    renderer_.stop();
}

} // namespace player
} // namespace flexmusic
