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

这里要额外明确一条边界：

- “下一首是谁”“是否存在推荐结果”“要不要预热”属于 Java / Domain / Data 的业务编排，不属于 native 播放器内核。

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

当前 native 运行时保留 5 类线程：

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
- 接受 `PlaybackRequest`
- 输出 `ResolvedPlayableSource`

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

## 7. 播放器与 warm path 的协作边界

这一轮必须明确：播放器消费 warm path 的结果，但不拥有 warm path 的决策权。

### 7.1 播放器负责什么

- 消费 `ResolvedPlayableSource`
- 进入 `PREPARING -> PLAYING`
- 在 seek 时通过读线程执行 `av_seek_frame()`
- 消费 localhost 或 fd 输入

### 7.2 播放器不负责什么

- 不决定下一首是谁
- 不发起推荐
- 不猜测搜索结果或歌单之外的候选曲目
- 不在 native 里自行预拉下一首媒体资源
- 不因为 warmup 未完成而阻塞当前曲目的冷启动

### 7.3 warm path 的正确接入方式

正确做法应是：

1. Java / Data 层基于业务队列，提前准备 `PreparedPlaybackCandidate`。
2. 用户点击下一首时，优先把这个已准备好的候选转成 `PlaybackRequest`。
3. `PlaybackSourceResolver` 尝试命中 warm path 产物。
4. 如果 warm path 已过期、失败或不存在，则立即回到 cold path。

也就是说：

- warm path 是“加速器”
- 不是“进入播放器的前置门槛”

### 7.4 为什么要这样设计

这和 `ijkplayer/ffplay` 的思路一致：

- 播放器内核只关心当前 source 的状态机和队列切换
- 上层队列与资源选择逻辑放在外层

这也和 VLC 把 preparser 与真实播放输入链路分开的思路一致：

- 预处理可以提前做
- 真正播放时不能要求播放线程等待预处理完成

## 8. “下一首”行为边界

用户在播放界面点击“下一首”前，业务上必须先回答：下一首到底存不存在。

### 8.1 当前列表只有一首歌时

如果播放队列里只有当前这一首，且没有推荐系统，那么播放器层应该接受这个现实：

- 不存在确定的下一首
- 不能让 native 内核自己生成一个“下一首”
- 不能对未知媒体资源做真正的下一首预热

推荐行为：

- UI 层显示“暂无下一首”或触发上层去拉推荐列表
- 播放器仅停止当前曲目结束后的自动续播

### 8.2 Favorites / Recent / Playlist 等已知列表

这类场景队列是已知的，因此：

- `PlaybackController` 可以在当前曲目稳定播放后，对 `queue[index + 1]` 发起预热
- 用户点击下一首时，播放器只接收已经解析好的下一首请求

### 8.3 搜索结果“播放全部”

这类场景从进入播放那一刻开始，也应视为“已知队列”：

- 第一首 cold start
- 第二首 warm path
- 更远的曲目只做低成本预热

## 9. 启播与 seek 的性能边界

需要明确区分两类问题：

### 9.1 内核本地问题

本地链路应该只贡献：

- AVIO open
- demux open
- decoder open
- renderer open
- 几十到几百毫秒级的队列切换

### 9.2 上游网络问题

如果日志里：

- `SeekableProxy upstream ready ... elapsedMs=5000+`

那说明瓶颈在远端首包，不在 native render 链路。

所以：

- `cold start < 1s` 不能只靠调 FFmpeg 参数达成
- 真要稳定冲 `<1s`，需要点击前预热、连接复用、小块预读、下一首预连

这也意味着：

- warm path 的收益主要体现在“下一首切换”与“已知队列”的场景
- 对于不存在下一首的单曲直播，播放器只能做好当前曲目的冷启动与停止语义，不能凭空制造 warm hit

## 10. 关键代码位置

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

## 11. 日志要求

播放器日志保留这些边界：

- `setDataSource`
- `prepared source hit/miss`
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

同时建议补两类 warm path 关联日志：

- `next candidate prepared`
- `next candidate fallback to cold path`

这样才能把“播放器慢”与“预热没有命中”区分开。

## 12. 后续演进方向

下一步如果继续向 `ijkplayer` 靠拢，优先级应是：

1. 给 AVIO / 网络层补中断回调
- 切歌或 stop 时能更快打断阻塞网络读

2. 把 prepare/read 的 abort 语义补完整
- 避免旧 source 的长时间 join 拖慢切歌

3. 引入更明确的 packet / frame flush sentinel
- 进一步贴近 `ffplay` 的 queue serial / flush packet 设计

4. 把 warm path 单独建模
- 当前歌曲冷启动与下一首预热彻底解耦
- 已知队列的下一首预热
- 连接池复用
- 小块 ring buffer / head cache

5. 给队列层补“无下一首”与“推荐待加载”两种明确状态
- 避免播放器层误以为所有场景都可以自动续播

## 13. 构建说明

按仓库规则，本轮文档和代码修改后未由 Codex 执行构建或测试，需开发者自行验证。
