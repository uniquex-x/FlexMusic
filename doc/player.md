# 播放器模块现状说明

## 1. 当前整体结构

FlexMusic 当前播放器分为三层：

### Java / App 层

- `PlaybackController`
- `PlayerActivity`
- `MainActivity` 迷你播放器

职责：

- 管理播放队列
- 分发 `PlayerState`
- 响应播放/暂停/切歌/seek/倍速/播放模式操作
- 维护最近播放和搜索队列扩展

### Java / Feature 层

- `feature_player/player/NativeBackedPlayerKernel`
- `feature_player/coreplayer/PlayerJNI`

职责：

- 实现 `PlayerKernel`
- 桥接 Java 与 native 播放核心
- 轮询 native 快照并回传上层

### Native 层

- `native/player/PlayerSession`
- `native/io/*`
- `native/media/*`
- `native/jni/bridge/PlayerBridge.*`

职责：

- 控制线程与命令队列
- demux / decode / render
- 本地 fd、文件路径、localhost 流输入

## 2. 当前已实现能力

### 2.1 基础播放能力

当前已落地：

- 播放 / 暂停
- 切歌
- seek
- 播放队列
- 顺序 / 随机 / 单曲循环 / 单曲循环次数
- 倍速
- 最近播放记录
- 全屏播放器和迷你播放器联动

### 2.2 在线与本地统一播放

当前播放器统一消费 `ResolvedPlayableSource`，支持：

- `content://`
- `android.resource://`
- `file://` 和绝对路径
- `http://` / `https://`

其中：

- 本地源走 fd 或本地文件路径
- 在线源先经过 `PlaybackSourceResolver`
- seekable 在线音频优先走 localhost 代理

### 2.3 搜索结果直接接入播放

`PlaybackController` 当前已经支持：

- 播放单首搜索结果
- 播放搜索结果页
- 把搜索结果加入下一首
- 把搜索结果加入队列
- 搜索队列动态扩容

### 2.4 warmup 接入

当前已接入：

- `PlaybackWarmupCoordinator`
- `IPlaybackWarmupEngine`
- `NetworkPlaybackSourceResolver` warmup 命中

当前行为：

- 仅在当前曲目进入稳定 `PLAYING` 后才异步触发预热
- 队列变化或切歌后会取消过期 warmup
- warmup 是加速器，不阻塞当前冷启动

### 2.5 最近一次交互改动

当前播放按钮行为已经调整为：

- 用户点击播放后，按钮立即切到非“播放”态
- 若资源仍在加载，再次点击会直接停止当前加载链路
- 同类连续点击会做短时间消抖

这一逻辑当前体现在：

- `PlayerState.playWhenReadyRequested`
- `PlaybackController.togglePlayPause()`

## 3. 当前状态机边界

`PlayerState` 暴露给 UI 的主要状态：

- `IDLE`
- `LOADING`
- `PLAYING`
- `PAUSED`
- `STOPPED`
- `ERROR`

native `PlayerSession` 内部通过命令队列处理：

- `SET_DATA_SOURCE`
- `PREPARE`
- `PLAY`
- `PAUSE`
- `STOP`
- `SEEK`
- `SET_VOLUME`
- `SET_PLAYBACK_SPEED`

关键原则：

- 所有控制先入队，再由控制线程串行处理
- seek 不重建整条播放 pipeline
- 旧 packet / PCM 依赖 serial 机制淘汰

## 4. 关键代码位置

Java 侧：

- `app/src/main/java/com/example/flexmusicplayer/player/PlaybackController.java`
- `app/src/main/java/com/example/flexmusicplayer/PlayerActivity.java`
- `app/src/main/java/com/example/flexmusicplayer/MainActivity.java`
- `app/src/main/java/com/example/flexmusicplayer/model/PlayerState.java`

Feature / JNI：

- `feature_player/src/main/java/com/example/feature_player/player/NativeBackedPlayerKernel.java`
- `feature_player/src/main/java/com/example/feature_player/coreplayer/PlayerJNI.java`

Native：

- `native/player/PlayerSession.cpp`
- `native/jni/bridge/PlayerBridge.cpp`
- `native/io/*`
- `native/media/*`

## 5. 当前已知限制

- 还没有媒体通知、锁屏控制、蓝牙按键整合
- 还没有后台 Service 化播放管理
- 还没有真正的歌词同步滚动来源治理，当前歌词仓库仍偏在线轻量实现
- 播放器 UI 仍主要集中在 `app/`，尚未进一步模块化

## 6. 日志要求

播放器相关改动必须保留以下日志边界：

- prepare 时记录 source identity 和 source kind
- backend / transport 选择日志
- native 状态切换日志
- prepare 失败的首个失败点日志

不要添加逐帧级别的高频噪声日志。
