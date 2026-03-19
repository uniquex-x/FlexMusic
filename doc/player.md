# 播放器模块实现说明

## 当前运行态
播放器运行时已经从 Java `MediaPlayer` 切到 native 播放链路，当前主路径为：

1. `PlaybackController`
- 负责队列、播放请求、进度同步和最近播放记录。

2. `feature_player`
- `NativeBackedPlayerKernel` 作为 `core_domain` 的播放器内核实现。
- Java 层只负责：
  - 解析 `ResolvedPlayableSource`
  - 对 `content://` / `android.resource://` 打开 `AssetFileDescriptor`
  - 将 `fd + offset + length` 传给 JNI
  - 轮询 native 播放快照并分发给 UI

3. JNI Bridge
- `PlayerJNI` -> `PlayerBridge`
- 全部采用动态注册
- 仅负责参数转换、句柄管理和 native session 调度

4. Native 播放链路
- `IFileIo`
  - `FfmpegStreamFileIo`：`http/https`
  - `FdFileIo`：`content://` / `android.resource://` 的 fd 输入
  - `PassthroughFileIo`：`file://` 或绝对路径
- `AvioDataSource`
  - 把统一的 `IFileIo` 适配成 FFmpeg 自定义 AVIO
- `FfmpegDemuxer`
  - 打开输入、找到音频流、输出编码包
- `FfmpegAudioDecoder`
  - 解码并重采样成 `S16 PCM`
- `OpenSlAudioRenderer`
  - 负责 OpenSL ES 音频输出
- `PlayerSession`
  - 负责状态机、线程编排、缓冲队列、播放控制

## 本地音频支持
当前本地音频有 3 条可用路径：

1. `content://`
- 来自 SAF / MediaStore 的本地音频 URI
- Java 层通过 `ContentResolver.openAssetFileDescriptor(...)` 打开
- 不复制文件，不落本地缓存
- native 通过 `FdFileIo` 直接读取 fd

2. `android.resource://`
- 应用内资源音频
- 同样通过 fd 路径进入 native

3. `file://` / 绝对路径
- native 通过 `PassthroughFileIo` 直接读取文件

结论：
- 当前离线音频不依赖 `MediaPlayer`
- `SAF content://` 已能直接进 native 播放链路
- 不需要把本地音频额外复制到应用私有目录

## 在线音频支持
在线音频当前走：

- `core_network`
  - 冷启动不再做阻塞式 `HEAD` / `GET` probe
  - 对非 live 的渐进式 `http/https`，优先创建本地 loopback seekable 代理 URL
  - 代理层使用 Java 网络栈拉取远端数据，并对播放器暴露 `Range` 能力
  - 冷启动首播不再等待代理 warmup 完成；warmup 只留给未来显式预热
- `feature_player` JNI 内核
  - `http/https` 默认仍走 native `FfmpegStreamFileIo`
  - 当 `resolvedUrl` 是本地代理 URL 时，FFmpeg 实际连接的是 `127.0.0.1`，远端网络由代理层处理
  - 仅在 `https` native prepare 失败时，才回退到 `StreamingPipeSource`
  - 通过统一 AVIO 进入 FFmpeg demux / decode / render

这样做的原因不是“少一层 Java 代码”，而是首播时延主要受两件事影响：

1. 远端首字节时间（TTFB）
2. demux / stream-info 阶段是否又触发了额外探测与 seek

本轮实测日志已经说明：

- 旧链路：
  - Java probe + native 建链串行，首播常见 `> 2s`
- 第一轮 pipe 默认链路：
  - 阻塞 probe 被拿掉
  - 但 `prepare complete -> first demux packet` 仍多等约 `800ms`
  - 说明“Java 建连 + pipe”并没有减少总首包路径，反而让 FFmpeg 在 prepare 后继续等更多字节
- 第二轮链路：
  - 回到 native 直接持有网络 transport
  - 但这要求把冷启动网络源改成 `seekable=false`
  - 不满足在线音频必须可 seek 的需求
- 第三轮链路：
  - `core_network` 为渐进式在线音频创建本地 seekable 代理 URL
  - 代理使用 Java 网络栈处理远端 HTTP / Range
  - FFmpeg 对 localhost 播放，seek 能力由代理层保留
- 第四轮链路：
  - 保留 seekable 代理
  - 去掉“冷启动自动 warmup 等待”
  - 首播改成代理透明转发，避免为了读满首块而把 `prepare` 卡死

结论：

- 对远端 MP3 / AAC 直链，主路径应优先走“seekable loopback proxy + native ffmpeg”
- Java pipe 更适合作为 HTTPS 失败兜底，而不是默认主路径
- 如果远端服务本身 TTFB 就已经超过 `1s`，那么“点击后严格小于 `1s` 出声”在物理上不可保证，除非引入点击前预热

当前仓库只 vendored：
- `arm64-v8a`
- `armeabi-v7a`

因此 `app` 和 `feature_player` 已显式限制 ABI，避免构建阶段错误尝试 `x86`。

## 关键代码位置

### Java / Domain
- `app/src/main/java/com/example/flexmusicplayer/player/PlaybackController.java`
- `feature_player/src/main/java/com/example/feature_player/player/NativeBackedPlayerKernel.java`
- `feature_player/src/main/java/com/example/feature_player/coreplayer/PlayerJNI.java`
- `core_domain/src/main/java/com/example/core_domain/player/PlayerKernel.java`

### Native
- `native/jni/JNILoader.cpp`
- `native/jni/bridge/PlayerBridge.cpp`
- `native/player/PlayerSession.cpp`
- `native/media/source/AvioDataSource.cpp`
- `native/media/demux/FfmpegDemuxer.cpp`
- `native/media/codec/FfmpegAudioDecoder.cpp`
- `native/audio/OpenSlAudioRenderer.cpp`
- `native/io/FfmpegStreamFileIo.cpp`
- `native/io/FdFileIo.cpp`
- `native/io/PassthroughFileIo.cpp`

## 当前线程模型
- Java 主线程：
  - 控制层事件分发
  - 快照轮询
- native prepare 线程：
  - 打开数据源、demuxer、decoder
- native demux 线程：
  - 拉流 / 读文件并输出编码包
- native decode 线程：
  - 解码并输出 PCM
- native render 线程：
  - 将 PCM 送入 OpenSL ES

## 当前日志策略
播放器关键环节已经补日志，便于排查“点了没声”类问题：

- Java 层
  - `NativeBackedPlayerKernel` 打印 sourceId、url、transport、local/fd 信息
  - `NetworkPlaybackSourceResolver` 打印是否跳过冷启动 probe
  - `SeekablePlaybackProxyServer` 打印 session、warmup、range serving 信息
- JNI 层
  - `PlayerBridge` 打印数据源选型和 backend
- Native 内核
  - `PlayerSession` 打印 prepare、状态切换、错误信息
  - `FfmpegStreamIo` 打印网络 open 耗时和 seekable 选择
  - `FfmpegDemuxer` 打印低时延 tuning 是否启用
  - `AvioDataSource` 打印 AVIO buffer 大小
  - `StreamingPipeSource` 打印 fallback 连接耗时和首块到达耗时

定位问题时，先看：
1. Java 是否拿到了可用 source 和 fd
2. JNI 是否选到了预期 backend
3. native 是否进入 `prepareLoop`
4. 状态是否从 `PREPARING` 进入 `PLAYING`
5. 是否有 demux / decode / render 错误日志

排查首播慢时，再额外看 4 个时间点：

1. `PlaybackSourceResolver skip cold-start probe`
2. `SeekableProxy serve`、`FfmpegStreamIo open success` 或 `StreamingPipeSource connected`
3. `FfmpegDemuxer opened`
4. `PlayerSession first frame rendered`

如果第 2 步本身已经 `> 1000ms`，说明瓶颈主要在远端 TTFB，而不是本地解码链路。

## 构建验证说明
按仓库规则，本轮代码修改后未由 Codex 执行构建或测试。

建议开发者至少回归：

- `:app:compileDebugJavaWithJavac`
- `:feature_player:externalNativeBuildDebug`
- `:app:assembleDebug`

## 启播优化原则
本项目后续要沿着更接近 `ijkplayer` / `VLC` 的思路继续收口：

1. 冷启动优先最短路径
- 点击后不要再串行做 URL probe、二次建连、额外 seek 探测
- 渐进式网络音频优先 `seekable proxy + native ffmpeg`

2. 把 `<1s` 目标分成两类
- `warm start`：
  - 允许通过预连接、预读、连接池复用、候选源预热去冲击 `<1s`
- `cold start`：
  - 如果上游首字节本身 `> 1s`，目标应是“尽量接近 TTFB + 100~300ms”，而不是写死 `<1s`

3. 继续补齐预热能力
- 当前还缺：
  - 当前歌曲点击前预连接
  - 下一首候选源预热
  - 同 host 连接复用
  - 小块预读 / ring buffer

4. 保留兜底路径
- HTTPS native 失败时，仍允许 `StreamingPipeSource` 兜底
- 但不要把 fallback 变成默认主路径
