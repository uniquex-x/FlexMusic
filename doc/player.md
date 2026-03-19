# 播放器模块实现说明

## 1. 当前内核原则

当前播放器按更接近 `ffplay` / `ijkplayer` 的思路收敛为两条主线：

1. Java 只负责源解析、fd 获取、快照观察和 UI 控制。
2. native `PlayerSession` 负责消息队列、状态机、demux / decode / render 全链路调度。

核心约束：

- 所有内核控制事件都必须先进消息队列，再由控制线程串行处理。
- `AVFormatContext` 只允许由读线程持有和操作。
- seek 不能再走“控制线程 stop / join / reopen 整条 pipeline”。
- seek 必须走“读线程消费 seek 请求 + serial flush 丢弃旧数据”。

## 2. 运行态链路

### 2.1 Java / Domain

- `PlaybackController`
  - 负责歌单、最近播放、用户交互和 UI 状态分发。
- `NativeBackedPlayerKernel`
  - 实现 `PlayerKernel`
  - 将 `ResolvedPlayableSource`、fd、音量、播放控制交给 JNI
  - 轮询 native 快照
- `PlayerJNI`
  - 只保留动态注册 JNI 桥接，不承载业务逻辑

### 2.2 Native 播放链路

- `IFileIo`
  - `FfmpegStreamFileIo`：`http/https`
  - `FdFileIo`：`content://` / `android.resource://`
  - `PassthroughFileIo`：`file://` 或绝对路径
- `AvioDataSource`
  - 将统一 `IFileIo` 封装成 FFmpeg AVIO
- `FfmpegDemuxer`
  - 负责打开输入、读取编码包、执行 seek
- `FfmpegAudioDecoder`
  - 负责解码和重采样
- `OpenSlAudioRenderer`
  - 负责 OpenSL ES 输出
- `PlayerSession`
  - 负责命令邮箱、状态机、线程生命周期、serial/flush 机制

## 3. 控制模型

`PlayerSession` 不再让外部线程直接碰内核状态，而是引入控制消息队列：

- `SET_DATA_SOURCE`
- `PREPARE`
- `PLAY`
- `PAUSE`
- `STOP`
- `SEEK`
- `SET_VOLUME`
- `RELEASE`

所有 JNI 入口最终都只做一件事：

1. 把命令写入 `PlayerSession` 的消息队列。
2. 由 `controlThread` 串行消费命令。

这样做的原因是：

- 避免 UI 线程、JNI 线程、native worker 线程并发改状态。
- 保证 `play/pause/seek/stop` 的先后顺序可预测。
- 拖动 seek 条时可以合并多次 `SEEK`，不让命令堆积。

当前实现里，`SEEK`、`PLAY/PAUSE`、`SET_VOLUME` 都会在入队时做去重合并。

## 4. 线程模型

当前 native 运行时保留 4 类线程：

1. `controlThread`
- 消费消息队列
- 负责状态机和生命周期切换

2. `prepareThread`
- 打开 `IFileIo`
- 打开 AVIO / demuxer / decoder
- 启动后续 worker

3. `demuxThread`
- 唯一允许调用 `av_read_frame()` 和 `av_seek_frame()` 的线程
- 拥有 demux 上下文的操作权

4. `decodeThread`
- 消费编码包队列
- 调 decoder 输出 PCM

5. `renderThread`
- 消费 PCM 队列
- 打开 renderer 并提交 PCM

注意：

- 控制线程不直接调用 `demuxer_.seekTo()`。
- seek 请求由控制线程写标志，真正 seek 由 `demuxThread` 执行。

## 5. Seek 设计

这次重构最关键的是 seek 模型。

### 5.1 旧模型的问题

旧实现的问题和 `ffplay` / `ijkplayer` 相反：

- 控制线程直接 stop / join / reopen worker
- seek 时重建 pipeline
- 旧 packet / PCM / renderer buffer 没有明确的代际边界

结果就是：

- seek 后长时间无响应
- 旧帧被继续渲染
- UI 提前回到 `PLAYING`
- 用户在 seek 期间再次点击播放，会把状态机搅乱

### 5.2 新模型

当前 seek 采用“请求 + serial + flush”的方式：

1. 控制线程收到 `SEEK`
- 只更新 `pendingSeekPositionMs`
- `queueSerial_++`
- 立即清空 packet / PCM 队列
- 立即 `renderer_.flush()`
- 状态切到 `PREPARING`

2. `demuxThread` 在下一轮循环里消费 seek 请求
- 执行 `demuxer_.seekTo()`
- 执行 `decoder_.reset()`
- 清掉 seek 请求标记

3. `decodeThread` / `renderThread`
- 只消费与当前 `queueSerial_` 一致的数据
- 旧 serial 的 packet / PCM 一律丢弃

这和 `ffplay` / `ijkplayer` 的核心思想一致：

- seek 不是重建播放器
- seek 是让读线程切流点，并让下游队列整体换代

## 6. 当前在线音频路径

为了保留在线音频 seek 能力，当前在线音频主路径为：

1. `PlaybackSourceResolver`
- 解析原始远端 URL

2. `SeekablePlaybackProxyServer`
- 为渐进式在线音频提供本地 `127.0.0.1` seekable 代理
- 对 native 暴露 `Range`
- 对远端负责真实网络请求

3. `FfmpegStreamFileIo`
- FFmpeg 实际连接 localhost

4. `PlayerSession`
- 进入 native demux / decode / render

这个路径的意义是：

- 保留在线音频 `seekable=true`
- 把 Range/重连/后续 warmup 能力收敛到代理层
- 避免 Java UI 层直接处理字节流

## 7. 启播与 seek 的性能边界

需要明确区分两类问题：

### 7.1 内核本地问题

本地链路应该只贡献：

- AVIO open
- demux open
- decoder open
- renderer open
- 几十到几百毫秒级的队列切换

### 7.2 上游网络问题

如果日志里：

- `SeekableProxy upstream ready ... elapsedMs=5000+`

那说明瓶颈在远端首包，不在 native render 链路。

所以：

- `cold start < 1s` 不能只靠调 FFmpeg 参数达成
- 真要稳定冲 `<1s`，需要点击前预热、连接复用、小块预读、下一首预连

## 8. 关键代码位置

### Java

- `app/src/main/java/com/example/flexmusicplayer/player/PlaybackController.java`
- `feature_player/src/main/java/com/example/feature_player/player/NativeBackedPlayerKernel.java`
- `feature_player/src/main/java/com/example/feature_player/coreplayer/PlayerJNI.java`

### Native

- `native/jni/bridge/PlayerBridge.cpp`
- `native/player/PlayerSession.h`
- `native/player/PlayerSession.cpp`
- `native/media/source/AvioDataSource.cpp`
- `native/media/demux/FfmpegDemuxer.cpp`
- `native/media/codec/FfmpegAudioDecoder.cpp`
- `native/audio/OpenSlAudioRenderer.cpp`
- `native/io/FfmpegStreamFileIo.cpp`

## 9. 日志要求

播放器日志保留这些边界：

- `setDataSource`
- backend 选择
- `pipeline start`
- `seek request`
- `seek applied`
- `state change`
- `first demux packet`
- `first decoded pcm`
- `first pcm submitted`
- `first frame rendered`
- `error`

不要补 per-frame 噪声日志，重点看状态边界和第一失败点。

## 10. 后续演进方向

下一步如果继续向 `ijkplayer` 靠拢，优先级应是：

1. 给 AVIO / 网络层补中断回调
- 切歌或 stop 时能更快打断阻塞网络读

2. 把 prepare/read 的 abort 语义补完整
- 避免旧 source 的长时间 join 拖慢切歌

3. 引入更明确的 packet / frame flush sentinel
- 进一步贴近 `ffplay` 的 queue serial / flush packet 设计

4. 把 warm path 单独建模
- 当前歌曲点击前预连
- 下一首预热
- 连接池复用
- 小块 ring buffer

## 11. 构建说明

按仓库规则，本轮文档和代码修改后未由 Codex 执行构建或测试，需开发者自行验证。
