#include "PlayerSession.h"

#include "../core/logger/logger.h"

#include <algorithm>
#include <chrono>
#include <utility>
#include <vector>

#include "../io/FileIoRegistry.h"

extern "C" {
#include <libavcodec/avcodec.h>
}

namespace flexmusic {
namespace player {

namespace {

constexpr std::size_t kPacketQueueSize = 96;
constexpr std::size_t kPcmQueueSize = 48;
constexpr char kPlayerSessionTag[] = "PlayerSession";

void releasePacket(AVPacket* packet) {
    if (packet != nullptr) {
        av_packet_free(&packet);
    }
}

} // namespace

PlayerSession::PlayerSession() {
    snapshot_.state = PlayerState::IDLE;
}

PlayerSession::~PlayerSession() {
    stop();
}

bool PlayerSession::setDataSource(const flexmusic::io::DataSourceSpec& spec,
                                  const std::string& backendName,
                                  std::string* errorMessage) {
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
        finalizeStopLocked(false);
        dataSourceSpec_ = spec;
        snapshot_ = PlayerRuntimeSnapshot();
        snapshot_.state = PlayerState::IDLE;
        snapshot_.seekable = spec.seekable;
        snapshot_.backendName = backendName;
        snapshot_.nativeReady = !backendName.empty();
        hasDataSource_ = true;
    }
    if (errorMessage != nullptr) {
        errorMessage->clear();
    }
    return true;
}

void PlayerSession::prepare() {
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

void PlayerSession::play() {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    bool restartFromZero = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (snapshot_.state == PlayerState::PAUSED) {
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

void PlayerSession::pause() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (snapshot_.state != PlayerState::PLAYING && snapshot_.state != PlayerState::BUFFERING) {
        return;
    }
    renderer_.pause();
    snapshot_.playing = false;
    setStateLocked(PlayerState::PAUSED);
}

void PlayerSession::stop() {
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
        finalizeStopLocked(true);
    }
}

void PlayerSession::seekTo(int64_t positionMs) {
    std::unique_ptr<std::thread> prepareThread;
    std::unique_ptr<std::thread> demuxThread;
    std::unique_ptr<std::thread> decodeThread;
    std::unique_ptr<std::thread> renderThread;
    bool shouldResume = false;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!hasDataSource_ || !snapshot_.seekable) {
            return;
        }
        shouldResume = snapshot_.state == PlayerState::PLAYING || snapshot_.state == PlayerState::BUFFERING;
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
        startPipelineLocked(std::max<int64_t>(0, positionMs), shouldResume);
    }
}

void PlayerSession::setVolume(float volume) {
    std::lock_guard<std::mutex> lock(mutex_);
    snapshot_.volume = std::max(0.0f, std::min(1.0f, volume));
    renderer_.setVolume(snapshot_.volume);
}

PlayerRuntimeSnapshot PlayerSession::snapshot() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return snapshot_;
}

void PlayerSession::startPipelineLocked(int64_t startPositionMs, bool autoStart) {
    packetQueue_ = std::make_unique<flexmusic::core::BlockingQueue<flexmusic::media::EncodedPacket>>(kPacketQueueSize);
    pcmQueue_ = std::make_unique<flexmusic::core::BlockingQueue<flexmusic::media::PcmFrame>>(kPcmQueueSize);
    stopRequested_ = false;
    firstFrameRendered_ = false;
    firstPacketLogged_ = false;
    firstDecodedFrameLogged_ = false;
    firstRendererSubmitLogged_ = false;
    autoStartOnReady_ = autoStart;
    pipelineStartedAt_ = std::chrono::steady_clock::now();
    snapshot_.currentPositionMs = startPositionMs;
    snapshot_.durationMs = 0;
    snapshot_.errorMessage.clear();
    snapshot_.playing = false;
    snapshot_.nativeReady = true;
    setStateLocked(PlayerState::PREPARING);
    flexmusic::core::levelLog(kPlayerSessionTag).i(
            "pipeline start sourceId=%s backend=%s startPositionMs=%lld autoStart=%d",
            dataSourceSpec_.sourceId.c_str(),
            snapshot_.backendName.c_str(),
            static_cast<long long>(startPositionMs),
            autoStart ? 1 : 0);

    prepareThread_ = std::make_unique<std::thread>(&PlayerSession::prepareLoop, this, startPositionMs, autoStart);
}

void PlayerSession::beginStopLocked() {
    stopRequested_ = true;
    if (packetQueue_ != nullptr) {
        packetQueue_->close();
    }
    if (pcmQueue_ != nullptr) {
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
    packetQueue_.reset();
    pcmQueue_.reset();
    firstFrameRendered_ = false;
    snapshot_.playing = false;
    snapshot_.nativeReady = hasDataSource_ && !snapshot_.backendName.empty();
    if (clearDataSource) {
        hasDataSource_ = false;
        dataSourceSpec_ = flexmusic::io::DataSourceSpec();
        snapshot_ = PlayerRuntimeSnapshot();
        snapshot_.state = PlayerState::IDLE;
    } else {
        setStateLocked(PlayerState::IDLE);
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

void PlayerSession::prepareLoop(int64_t startPositionMs, bool autoStart) {
    const auto log = flexmusic::core::levelLog(kPlayerSessionTag);
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
        setErrorLocked(errorMessage.empty() ? "Create FileIo failed" : errorMessage);
        return;
    }
    log.i("prepareLoop sourceId=%s url=%s local=%d fd=%d backend=%s",
          dataSourceSpec.sourceId.c_str(),
          dataSourceSpec.resolvedUrl.c_str(),
          dataSourceSpec.localSource ? 1 : 0,
          dataSourceSpec.detachedFd,
          fileIo->implementationName());

    if (!avioDataSource_.open(std::move(fileIo), dataSourceSpec, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        setErrorLocked(errorMessage.empty() ? "Open data source failed" : errorMessage);
        return;
    }
    log.i("prepare stage=avio-open sourceId=%s elapsedMs=%lld",
          dataSourceSpec.sourceId.c_str(),
          static_cast<long long>(elapsedSincePipelineStartMs()));
    if (!demuxer_.open(&avioDataSource_, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        setErrorLocked(errorMessage.empty() ? "Open demuxer failed" : errorMessage);
        return;
    }
    log.i("prepare stage=demux-open sourceId=%s elapsedMs=%lld durationMs=%lld seekable=%d",
          dataSourceSpec.sourceId.c_str(),
          static_cast<long long>(elapsedSincePipelineStartMs()),
          static_cast<long long>(demuxer_.durationMs()),
          demuxer_.isSeekable() ? 1 : 0);
    if (startPositionMs > 0 && demuxer_.isSeekable() && !demuxer_.seekTo(startPositionMs, &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        setErrorLocked(errorMessage.empty() ? "Seek before start failed" : errorMessage);
        return;
    }
    if (!decoder_.open(demuxer_.audioStreamInfo(), &errorMessage)) {
        std::lock_guard<std::mutex> lock(mutex_);
        setErrorLocked(errorMessage.empty() ? "Open decoder failed" : errorMessage);
        return;
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
    renderThread_ = std::make_unique<std::thread>(&PlayerSession::renderLoop, this, autoStart);
    log.i("prepare complete sourceId=%s totalElapsedMs=%lld",
          dataSourceSpec.sourceId.c_str(),
          static_cast<long long>(elapsedSincePipelineStartMs()));
}

void PlayerSession::demuxLoop() {
    const auto log = flexmusic::core::levelLog(kPlayerSessionTag);
    while (true) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (stopRequested_ || packetQueue_ == nullptr) {
                return;
            }
        }

        flexmusic::media::EncodedPacket encodedPacket;
        std::string errorMessage;
        if (!demuxer_.readPacket(&encodedPacket, &errorMessage)) {
            std::lock_guard<std::mutex> lock(mutex_);
            setErrorLocked(errorMessage.empty() ? "Read packet failed" : errorMessage);
            if (packetQueue_ != nullptr) {
                packetQueue_->close();
            }
            return;
        }
        if (!packetQueue_->push(std::move(encodedPacket))) {
            releasePacket(encodedPacket.packet);
            return;
        }
        if (!firstPacketLogged_ && encodedPacket.packet != nullptr) {
            firstPacketLogged_ = true;
            log.i("first demux packet sourceId=%s elapsedMs=%lld size=%d pts=%lld",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  encodedPacket.packet->size,
                  static_cast<long long>(encodedPacket.packet->pts));
        }
        if (encodedPacket.endOfStream) {
            return;
        }
    }
}

void PlayerSession::decodeLoop() {
    const auto log = flexmusic::core::levelLog(kPlayerSessionTag);
    while (true) {
        flexmusic::media::EncodedPacket encodedPacket;
        if (packetQueue_ == nullptr || !packetQueue_->pop(&encodedPacket)) {
            return;
        }

        std::vector<flexmusic::media::PcmFrame> decodedFrames;
        std::string errorMessage;
        if (encodedPacket.endOfStream) {
            if (!decoder_.flush(&decodedFrames, &errorMessage)) {
                std::lock_guard<std::mutex> lock(mutex_);
                setErrorLocked(errorMessage.empty() ? "Flush decoder failed" : errorMessage);
                if (pcmQueue_ != nullptr) {
                    pcmQueue_->close();
                }
                return;
            }
            for (flexmusic::media::PcmFrame& frame : decodedFrames) {
                if (pcmQueue_ == nullptr || !pcmQueue_->push(std::move(frame))) {
                    return;
                }
            }
            flexmusic::media::PcmFrame eosFrame;
            eosFrame.endOfStream = true;
            if (pcmQueue_ != nullptr) {
                pcmQueue_->push(std::move(eosFrame));
            }
            return;
        }

        if (!decoder_.decodePacket(encodedPacket, &decodedFrames, &errorMessage)) {
            releasePacket(encodedPacket.packet);
            std::lock_guard<std::mutex> lock(mutex_);
            setErrorLocked(errorMessage.empty() ? "Decode packet failed" : errorMessage);
            if (pcmQueue_ != nullptr) {
                pcmQueue_->close();
            }
            return;
        }
        releasePacket(encodedPacket.packet);

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
            if (pcmQueue_ == nullptr || !pcmQueue_->push(std::move(frame))) {
                return;
            }
        }
    }
}

void PlayerSession::renderLoop(bool autoStart) {
    const auto log = flexmusic::core::levelLog(kPlayerSessionTag);
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
                setErrorLocked(errorMessage.empty() ? "Open renderer failed" : errorMessage);
                return;
            }
            log.i("renderer open sourceId=%s elapsedMs=%lld sampleRate=%d channels=%d",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  pcmFrame.sampleRate,
                  pcmFrame.channelCount);
            renderer_.setVolume(snapshot_.volume);
            if (autoStart) {
                renderer_.play();
            } else {
                renderer_.pause();
            }
        }

        std::string errorMessage;
        if (!renderer_.enqueueFrame(std::move(pcmFrame), &errorMessage)) {
            std::lock_guard<std::mutex> lock(mutex_);
            setErrorLocked(errorMessage.empty() ? "Render PCM failed" : errorMessage);
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
            snapshot_.playing = autoStart;
            setStateLocked(autoStart ? PlayerState::PLAYING : PlayerState::READY);
            log.i("first frame rendered sourceId=%s elapsedMs=%lld autoStart=%d",
                  dataSourceSpec_.sourceId.c_str(),
                  static_cast<long long>(elapsedSincePipelineStartMs()),
                  autoStart ? 1 : 0);
            continue;
        }
        if (bufferingAnnounced && autoStart) {
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
        flexmusic::core::levelLog(kPlayerSessionTag).i(
                "state change %d -> %d sourceId=%s elapsedMs=%lld",
                static_cast<int>(snapshot_.state),
                static_cast<int>(state),
                dataSourceSpec_.sourceId.c_str(),
                static_cast<long long>(elapsedSincePipelineStartMs()));
    }
    snapshot_.state = state;
}

void PlayerSession::setErrorLocked(const std::string& errorMessage) {
    flexmusic::core::levelLog(kPlayerSessionTag).e(
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
