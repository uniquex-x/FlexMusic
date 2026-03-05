## introduction
FlexMusic is a free music player, and support:
- online music play
- online music search、play、download(only support free audio)
- Audio Transcoding
    - Lossy: mp3,vorbis
    - Lossless: flac

## Architecture
```
                    app (rely on feature module)
                        |
         +-------------+-------------+-------------+
         |             |             |             |
  feature-player  feature-transcode  feature-search  feature-download
         |             |             |             |
         +-------------+-------------+-------------+
                        |
              core-player-sdk (rely on core-domain)
                        |
           +------------+------------+-----------+
           |            |            |           |
      core-domain   core-data    core-network  core-database
           |            |
           +------------+
                        |
                  native (.so lib)
 ```

 ### file dirctroy
 - java层：
 ```
  Android 模块：
├── app/                           // 应用壳（仅组装）
├── feature-player/                // 播放 UI 功能
├── feature-transcode/              // 转码 UI 功能
├── feature-search/                // 搜索 UI 功能
├── feature-download/               // 下载 UI 功能
├── core-player-sdk/               // Native SDK 封装（纯 JNI 调用）
├── core-domain/                   // 领域层（UseCase + Entity + Repository 接口）
├── core-data/                     // 数据层（Repository 实现 + 本地存储）
├── core-network/                  // 网络层（API + 网络请求）
└── core-database/                 // 数据库层（Room）
```
- native层：
```
native/
 ├── core/                          // 基础设施层
 │   ├── error/                     // 错误处理
 │   ├── logger/                    // 日志系统
 │   ├── thread/                    // 线程池与任务队列
 │   ├── memory/                    // 内存管理
 │   └── utils/                     // 工具类（时间、字符串等）
 ├── media/                         // 媒体抽象层
 │   ├── demux/                     // 解封装（MP3/FLAC/OGG）
 │   ├── format/                    // 容器格式识别
 │   ├── codec/                     // 编解码器接口
 │   └── packet/                    // 音频帧数据结构
 ├── player/                        // 播放引擎
 │   ├── state/                     // 状态管理
 │   ├── event/                     // 事件总线
 │   ├── audio_output/              // 音频输出（AAudio/OpenSL ES）
 │   ├── sync/                      // 同步控制
 │   └── playlist/                  // 播放列表
 ├── transcoder/                    // 转码引擎
 │   ├── pipeline/                  // 转码流水线
 │   ├── format/                    // 输出格式封装
 │   └── progress/                  // 转码进度
 ├── io/                            // 输入输出抽象
 │   ├── file/                      // 文件输入
 │   ├── network/                   // 网络流（HTTP/HLS）
 │   └── cache/                     // 缓存机制
 ├── audio/                         // 音频处理
 │   ├── resample/                  // 重采样
 │   ├── filter/                    // 音频滤镜（EQ/增益）
 │   └── mixer/                     // 混音（预留）
 ├── jni/                           // JNI 桥接层
 │   ├── bridge/                    // Java 接口实现
 │   ├── manager/                   // 实例生命周期管理
 │   ├── callback/                  // 回调机制
 │   ├── jni_utils/                 // JNI 工具
 │   ├── adapter/                   // 对象适配
 │   └── version/                   // 版本管理
 └── third_party/                   // 第三方库
     ├── ffmpeg/                    // FFmpeg
     └── soxr/                      // 高质量重采样
```

