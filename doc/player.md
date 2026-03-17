# 播放器模块实现说明

## 当前落地范围
本轮实现覆盖了 3 个层面：

1. 播放器 UI
- 新增全屏播放页与歌词页，入口为底部 mini-player 点击后打开。
- 播放页样式对齐 `doc/UI picture/playing歌曲播放.png`。
- 歌词页样式对齐 `doc/UI picture/lyrics歌词界面.png`。

2. 可工作的播放链路
- 当前运行时播放器使用 Java `MediaPlayer` 作为可用内核。
- 支持 HTTP / HTTPS 网络音频流。
- 支持 `content://`、`file://`、绝对路径形式的本地音频源。
- 本地音频由 `Storage Access Framework` 选取后持久化保存 URI，不强制复制文件。

3. 本地音乐存储
- 新增 `LocalMusicStore`，把用户通过“本地 -> Upload Offline Songs”选择的音频 URI 持久化到 `SharedPreferences`。
- 读取时尽量通过 `MediaMetadataRetriever` 提取标题、艺术家、专辑、时长。
- 如果系统中已经有音频文件，优先建议直接保存 `content://` 或 `MediaStore` URI；Android 10+ 不建议依赖裸文件路径。

## 关键代码位置

### UI
- `app/src/main/java/com/example/flexmusicplayer/PlayerActivity.java`
- `app/src/main/res/layout/activity_player.xml`
- `app/src/main/res/layout/item_lyric_line.xml`

### 播放控制
- `app/src/main/java/com/example/flexmusicplayer/player/PlaybackController.java`
- `app/src/main/java/com/example/flexmusicplayer/player/LyricsRepository.java`
- `app/src/main/java/com/example/flexmusicplayer/player/LyricsLine.java`

### 本地音频持久化
- `app/src/main/java/com/example/flexmusicplayer/storage/LocalMusicStore.java`

### 入口接入
- `app/src/main/java/com/example/flexmusicplayer/MainActivity.java`
- `app/src/main/java/com/example/flexmusicplayer/ui/MainPageFragment.java`
- `app/src/main/java/com/example/flexmusicplayer/ui/FavoritesFragment.java`
- `app/src/main/java/com/example/flexmusicplayer/ui/RecentFragment.java`
- `app/src/main/java/com/example/flexmusicplayer/ui/LocalFragment.java`

## 当前播放架构

### 1. UI 层
- 列表点击歌曲后，通过 `MainActivity.onSongPlaybackRequested(...)` 把歌曲和队列交给 `PlaybackController`。
- mini-player 监听播放器状态变化，更新标题、艺人、播放状态和显隐。
- 全屏 `PlayerActivity` 订阅同一份播放状态，实现播放页和歌词页双界面。

### 2. 控制层
- `PlaybackController` 是进程级单例。
- 负责：
  - 维护当前队列与索引
  - 控制 `MediaPlayer` prepare/play/pause/seek/next/previous
  - 维护 `PlayerState`
  - 每 500ms 分发一次进度
  - 处理 buffering / complete / error 状态

### 3. 数据源层
- 网络流：
  - 直接把 `http://` / `https://` URL 交给 `MediaPlayer.setDataSource(String)`。
- 本地流：
  - `content://` 通过 `MediaPlayer.setDataSource(Context, Uri)`。
  - `file://` 或绝对路径优先直接播放。
  - 如果是示例数据里不存在的假本地路径，则自动回退到 demo 网络流，避免演示时点击即失败。

## 本地文件是否可以直接链接
可以，但推荐分 3 种情况处理：

1. 用户从系统文件选择器选择音频
- 直接保存返回的 `content://` URI。
- 调用 `takePersistableUriPermission(...)` 保留读权限。
- 这是当前实现已经采用的方案。

2. 系统媒体库中的音频
- 优先保存 `MediaStore` 查询得到的 `content://media/...` URI。
- 这种方式兼容新版本 Android 分区存储。

3. 应用私有目录或你自己缓存下来的音频
- 可以直接保存绝对路径或 `file://` URI。

结论：
- 手机中已存在的音频文件不必复制一份才能播放。
- 只要你拿到的是稳定可访问的 URI 或路径，就能直接链接。
- Android 10+ 场景优先 `content://`，不要依赖外部存储裸路径。

## 网络拉流说明

当前实现使用系统 `MediaPlayer` 走 HTTP(S) 流播放，可满足：
- 远程 MP3/AAC 等常见音频直链
- 基础缓冲、暂停、续播、拖动

仓库内已经放入：
- FFmpeg 8.0
- FLAC
- mp3lame
- oggVorbis

但这些 native 库目前还没有接入到运行时链路，原因是：
- `native/CMakeLists.txt` 仍为空
- 没有 JNI bridge 的 cpp 实现
- 也没有 native demux / decode / render pipeline

所以当前状态是：
- 资源已就位
- Java fallback 已可工作
- native 播放内核尚未真正启用

## 下一步如何切换到 FFmpeg 播放链路

建议后续按下面的方向推进：

1. 在 `feature_player` 或 `core_player_sdk` 中建立 JNI 播放桥
- Java 层保留统一的 `PlaybackController` 接口
- 底层新增 `NativePlayerBridge`

2. 在 `native/` 中补齐模块
- datasource：本地文件 / HTTP(S)
- demux：FFmpeg `avformat`
- decode：FFmpeg `avcodec`
- output：Android `AudioTrack`

3. 线程模型建议
- UI 线程：界面状态与事件分发
- 控制线程：播放状态机
- 拉流线程：网络读取与 packet buffer
- 解码线程：音频解码
- 渲染线程：PCM -> AudioTrack

4. buffering 建议
- packet buffer：网络层缓冲
- decoded PCM buffer：解码层缓冲
- audio output buffer：渲染层缓冲

5. 保留当前 Java 播放器作为 fallback
- native 初始化失败时回退到 `MediaPlayer`
- 先保证业务可用，再逐步切换高性能链路

## 这轮实现的取舍
- 优先交付“能播、能看、能切歌词、能播本地/网络”的可运行版本。
- 没有在本轮硬上 FFmpeg JNI，是因为仓库当前缺失 native 运行时桥和 CMake 构建内容，直接强接只会得到不可运行的半成品。
- 文档已经把 native 接入的拆分路径写清楚，后续可以在不推翻当前 UI 与控制层的前提下平滑替换底层内核。
