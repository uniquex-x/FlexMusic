## introduction
FlexMusic is a free music player, and support:
- online music play
- online music search、play、download(only support free audio)
- Audio Transcoding
    - Lossy: mp3,vorbis
    - Lossless: flac

## Architecture
Architecture diagrams are under `doc/architecture/`:

- `doc/architecture/flexmusic_arch_overview.svg`
- `doc/architecture/flexmusic_native_internals.svg`

Supporting design notes:

- `doc/network.md`
- `doc/player.md`
- `doc/search.md`
- `doc/sleep.md`

 ### File Directory
 - Android / Java layer：
```
Android modules
├── app/                                   // 当前应用壳与主要页面实现
│   ├── MainActivity.java                  // 底部导航、迷你播放器容器
│   ├── PlayerActivity.java                // 全屏播放器
│   ├── ui/                                // Home / Recent / Favorites / Sleep / Settings 等页面
│   ├── player/                            // app 层播放 facade、歌词仓库
│   ├── sleep/                             // sleep 场景控制、白噪音/电台状态模型
│   ├── storage/                           // 本地歌曲、收藏、最近播放等内存/轻量存储
│   └── model/                             // Song / PlayerState / Playlist 等 UI 侧模型
│
├── feature_player/                        // 播放内核实现模块
│   ├── coreplayer/                        // PlayerJNI，Java ↔ native 播放桥接
│   └── player/                            // SoLibraryLoader、PlayerKernel 实现工厂
│
├── feature_search/                        // 预留搜索 feature 模块壳
├── feature_download/                      // 预留下载 feature 模块壳
├── feature_transcode/                     // 预留转码 feature 模块壳
│
├── core_domain/                           // 核心抽象层
│   ├── player/                            // PlaybackRequest / PlayerKernel / ResolvedPlayableSource
│   └── radio/                             // RadioStation 领域模型
│
├── core_data/                             // 数据编排层
│   └── radio/                             // RadioRepository，负责搜索增强、去重、排序、点击上报
│
├── core_network/                          // 联网基础设施
│   ├── http/                              // NetworkClient / RequestPolicy
│   ├── radio/                             // RadioBrowserService / endpoint resolver
│   └── stream/                            // AudioStreamProbeApi / NetworkPlaybackSourceResolver
│
└── core_database/                         // 预留数据库模块壳
```
- native layer：
```
native/
├── CMakeLists.txt                         // native 构建入口，当前生成 flexmusic_player
├── jni/                                   // JNI 边界层
│   ├── JNILoader.cpp                      // 所有 JNI 动态注册统一入口
│   ├── bridge/
│   │   ├── PlayerBridge.cpp               // PlayerJNI 的方法表与参数转换
│   │   └── PlayerBridge.h                 // NativePlayerContext / bridge 声明
│   └── version/
│       └── native_version.h               // native 版本头
│
├── player/
│   └── state/
│       └── player_state.h                 // native 播放状态枚举与事件定义
│
├── media/
│   ├── codec/
│   │   └── codec_interface.h              // 编解码接口占位
│   └── packet/
│       └── audio_packet.h                 // 音频包结构占位
│
├── io/
│   ├── DataSourceSpec.h                   // 数据源描述，统一 URL / contentType / seekable 等输入
│   ├── IFileIo.h                          // 文件 IO 抽象
│   ├── IFileIoFactory.h                   // IO 实现工厂抽象
│   ├── FileIoRegistry.cpp/.h              // 按协议选择 IO 实现，支持后续扩展
│   ├── FfmpegStreamFileIo.cpp/.h          // http / https 当前走 FFmpeg AVIO
│   └── PassthroughFileIo.cpp/.h           // 本地/占位源的透传实现
│
├── core/
│   ├── error/
│   │   └── error_code.h                   // native 错误码
│   ├── logger/
│   │   └── logger.h                       // 日志接口声明
│   └── thread/
│       └── thread_pool.h                  // 线程池头文件
│
└── third_party/                           // 已 vendor 的三方音频库
    ├── ffmpeg/
    ├── flac/
    ├── mp3lame/
    └── oggvorbis/
```

## Dependency Library
- ffmpeg8.0
- 
