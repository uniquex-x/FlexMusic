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

- `core_network` 轻量探测
  - 优先 `HEAD`
  - 回退 `Range: bytes=0-1`
- `feature_player` JNI 内核
  - `http/https` 由 `FfmpegStreamFileIo` 打开
  - 通过统一 AVIO 进入 FFmpeg demux / decode / render

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
  - `NativeBackedPlayerKernel` 打印 sourceId、url、local/fd 信息
- JNI 层
  - `PlayerBridge` 打印数据源选型和 backend
- Native 内核
  - `PlayerSession` 打印 prepare、状态切换、错误信息

定位问题时，先看：
1. Java 是否拿到了可用 source 和 fd
2. JNI 是否选到了预期 backend
3. native 是否进入 `prepareLoop`
4. 状态是否从 `PREPARING` 进入 `PLAYING`
5. 是否有 demux / decode / render 错误日志

## 已验证的构建状态
本轮已通过：

- `:app:compileDebugJavaWithJavac`
- `:feature_player:externalNativeBuildDebug`
- `:app:assembleDebug`

## 后续建议
下一步建议继续收口 3 件事：

1. 启动耗时指标
- 给 source resolve、native prepare、first pcm、first render 打点

2. buffer 策略
- 继续缩短首播链路，并区分本地/网络的预缓冲阈值

3. `content://` 稳定性
- 补充 SAF URI 权限异常、fd 打开失败、length unknown 场景的专项测试
