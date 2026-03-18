# 网络模块架构设计

## 1. 背景

当前项目里，所有需要联网的能力都在逐步变多：

- 在线播放
- 电台收听
- 在线搜索
- 后续可能加入的歌词、评论、推荐、下载元数据拉取

但当前运行现象已经说明一个核心问题：

- 只要是联网拉取的音频资源，点击播放后通常要等待约 2 秒甚至更久才开始播放。

这不是单点 bug，而是网络链路、数据解析、流地址解析、播放器预热、失败回退都还没有被统一建模的结果。

当前仓库也已经暴露出结构层面的信号：

- `core_network/` 目前存在，但能力非常薄。
- `app/` 和具体功能包中已经出现临时网络代码。
- 播放层仍以 Java `MediaPlayer` fallback 为主，尚未形成“联网音频启动优化链路”。

因此需要把网络能力从“功能里顺手发请求”升级为“可复用、可观测、可演进的基础设施”。

## 2. 当前问题归因

### 2.1 首播慢的真实原因

联网音频点击后变慢，通常不是单一步骤耗时，而是多个步骤串联：

1. DNS 解析
2. TCP/TLS 建连
3. 302 跳转 / 流地址重定向
4. 流地址首次探测
5. 播放器 `prepareAsync()`
6. 远端服务首包返回慢

如果这些步骤全部在点击后串行发生，用户体感就会明显超过 2 秒。

### 2.2 当前代码组织的问题

1. 搜索、在线播放、电台查询没有共享统一的网络客户端和请求策略。
2. “搜索接口”和“播放流地址”没有经过统一的数据层编排。
3. 没有面向音频流的专门能力：
   - 流健康检查
   - 预连接 / 预探测
   - 候选源回退
   - 启动耗时埋点
4. UI / Controller 层容易直接感知远端来源，耦合过深。

## 3. 设计目标

## 3.1 功能目标

1. 为在线播放、在线搜索、电台收听提供统一网络基础设施。
2. 支持普通 JSON API 请求和音频流地址解析。
3. 支持搜索请求、详情请求、流地址请求、流探测请求的统一接入。
4. 支持后续扩展到歌词、评论、推荐、下载元数据等功能。

## 3.2 性能目标

1. 搜索请求具备防抖、结果缓存、在途去重。
2. 联网音频点击播放后，链路尽量从“点击后全量串行”改成“点击前可复用、点击后最短路径”。
3. 关键链路具备超时、重试、快速失败和候选回退。

## 3.3 架构目标

1. 高内聚：
   - HTTP 传输问题集中在 `core_network`
   - 业务聚合与缓存策略集中在 `core_data`
   - 用例与抽象契约集中在 `core_domain`
2. 低耦合：
   - UI 不直接依赖第三方 API 细节
   - 播放器不直接依赖某个搜索接口
   - feature 模块不自己 new HTTP client
3. 可复用：
   - 任何新功能只要依赖“远端数据拉取”或“远端音频源解析”，都复用同一套能力

## 4. 目标分层

推荐采用分层架构：

### 4.1 `core_network`

职责：

- 统一 HTTP 客户端
- 请求构造与响应解析
- 超时 / 重试 / 失败分类
- 域名镜像切换
- 流探测与可用性验证
- 埋点与耗时采集

这里不放业务“页面语义”，只放通用网络基础设施和具体远端 API client。

### 4.2 `core_data`

职责：

- Repository 聚合
- 本地缓存与远端结果合并
- 搜索排序与去重
- 业务兜底策略
- 音频候选源选择

这里负责把网络结果转成业务模型，并隐藏“请求哪个接口、如何回退、如何缓存”。

### 4.3 `core_domain`

职责：

- 定义业务接口和用例
- 例如：
  - `SearchRadioStationsUseCase`
  - `ResolvePlayableAudioSourceUseCase`
  - `PrepareOnlinePlaybackUseCase`

这一层只讲业务语义，不关心 HTTP。

### 4.4 `app/` 与 feature 模块

职责：

- UI 展示
- 用户输入事件
- 订阅状态
- 调用 domain / data 暴露的能力

约束：

- 不直接拼 URL
- 不直接解析 JSON
- 不直接处理流健康探测

## 5. 推荐模块职责

## 5.1 `core_network` 内建议增加的组件

### `http/NetworkClient`

统一封装：

- GET / POST / HEAD
- 超时
- Header 注入
- User-Agent
- 失败码分类

建议未来以单例共享连接池的客户端为核心，不再由各功能各自创建连接。

### `http/RequestPolicy`

描述请求策略：

- connect timeout
- read timeout
- retry count
- retry backoff
- 是否允许镜像切换
- 是否允许缓存

### `radio/RadioSearchApi`

负责远端电台搜索接口调用。

职责仅限：

- 发送请求
- 解析响应
- DTO 输出

### `stream/AudioStreamProbeApi`

负责流探测：

- 校验流地址是否可达
- 获取最终 resolved URL
- 获取 content type
- 获取首包耗时
- 获取是否为 HLS / MP3 / AAC / Redirect

### `resolver/EndpointResolver`

负责多域名 / 多镜像切换：

- 主域名失败时切换备用节点
- 对电台目录、搜索接口、探测接口做统一策略

### `monitor/NetworkMetricsRecorder`

记录：

- DNS 耗时
- 建连耗时
- 首字节耗时
- 搜索耗时
- 点击播放到开始播放的耗时

这是后续治理“2 秒首播慢”的关键观测点。

## 5.2 `core_data` 内建议增加的组件

### `OnlineAudioRepository`

统一对在线播放、电台、在线歌曲源做聚合。

职责：

- 根据业务 ID 获取候选流
- 调用 `AudioStreamProbeApi`
- 返回可播放源
- 提供失败回退顺序

### `RadioRepository`

职责：

- 搜索电台
- 结果去重
- 本地缓存最近搜索
- 排序与频率匹配增强
- 管理 favorite radio / recent radio 的远端补全策略

### `SearchRepository`

如果未来歌曲搜索、电台搜索、播客搜索都存在，应抽象统一搜索入口，再由不同 provider 实现。

### `NetworkBackedCache`

缓存建议至少覆盖：

- 搜索结果短 TTL 缓存
- 电台详情缓存
- 流探测结果超短 TTL 缓存

## 6. 联网音频播放链路设计

这是本次设计的重点。

### 6.1 错误链路

当前容易演变成：

`UI 点击 -> 搜索结果对象 -> 直接把 URL 丢给播放器 -> prepare -> 等网络`

问题：

- 无法提前校验流
- 无法快速切换备用流
- 无法知道慢在哪一段
- 播放器承担了过多网络初始化责任

### 6.2 目标链路

推荐改成：

`UI 点击`
-> `PrepareOnlinePlaybackUseCase`
-> `OnlineAudioRepository`
-> `AudioStreamProbeApi`
-> `ResolvedPlayableSource`
-> `PlaybackController`
-> `PlayerEngine`

其中：

### `ResolvedPlayableSource`

至少包含：

- sourceId
- originalUrl
- resolvedUrl
- streamType
- contentType
- bitrate
- probeLatencyMs
- expiresAt
- fallbackCandidates

播放器只消费这个统一模型，而不是直接认识远端接口。

## 7. 为什么这样能改善“点击后等待 2 秒”

## 7.1 预探测替代纯被动等待

在播放器 `prepare` 之前先做轻量探测：

- 检查是否存在 302 跳转
- 获取最终 URL
- 识别 HLS / MP3 / AAC
- 发现明显失效流时快速切换候选流

这样可以把一部分失败和等待提前移出播放器。

## 7.2 结果缓存

对以下内容做短时缓存：

- 搜索结果
- 流地址解析结果
- 最近成功可播的 resolved URL

这样用户二次点击同一站点 / 同一首在线音频时，不需要每次从零开始。

## 7.3 共享客户端与连接复用

所有网络请求复用同一个客户端实例，统一连接池和 TLS 会话复用，避免各模块冷启动。

## 7.4 播放前分阶段准备

不要把“搜索”、“解析流地址”、“健康探测”、“播放器 prepare”全部堆在点击后同一瞬间。

建议拆成：

1. 搜索阶段缓存候选站点
2. 点击阶段快速解析与探测
3. 播放器只接已知可播放的 resolved source

## 8. 搜索链路设计

### 8.1 搜索统一入口

推荐：

`SearchQuery`
-> `RadioRepository.search(...)`
-> `RadioSearchApi`
-> `RadioSearchResult`

### 8.2 搜索必须具备的能力

1. 输入防抖
2. 取消过时请求
3. 在途请求去重
4. 结果去重
5. 本地排序增强
6. 空查询兜底推荐

### 8.3 “FM 88.1” 类查询的处理原则

对频率类查询不能只把原始字符串原样透传。

需要做：

- 原始 query 保留
- 频率 token 提取，例如 `88.1`
- `FM 88.1` / `88.1` 双查询候选
- 结果按频率精确命中优先排序

这个能力应放在 `core_data` 的 repository 层，而不是 UI 层。

## 9. 播放器与网络模块的边界

### 播放器负责

- 播放状态机
- 缓冲状态
- 播放控制
- 音频焦点

### 网络模块负责

- 搜索
- 源解析
- 流探测
- 回退策略
- 性能埋点

### 明确禁止

- UI 直接把第三方搜索结果 DTO 喂给播放器
- 播放器里直接写第三方 API 请求
- feature 内自己维护独立 HTTP client

## 10. 目录建议

推荐后续目录演进为：

```text
core_domain/
  src/main/java/com/example/core_domain/network/
    SearchRadioStationsUseCase.java
    ResolvePlayableAudioSourceUseCase.java

core_data/
  src/main/java/com/example/core_data/network/
    OnlineAudioRepository.java
    RadioRepository.java
    cache/
    mapper/

core_network/
  src/main/java/com/example/core_network/http/
    NetworkClient.java
    RequestPolicy.java
    NetworkError.java
  src/main/java/com/example/core_network/radio/
    RadioSearchApi.java
    RadioBrowserService.java
    dto/
  src/main/java/com/example/core_network/stream/
    AudioStreamProbeApi.java
    ResolvedSourceDto.java
  src/main/java/com/example/core_network/monitor/
    NetworkMetricsRecorder.java

app/ or feature_*/
  ui/
  controller/
  viewmodel/
```

## 11. 分阶段落地建议

### P0：先把基础链路收束

1. 所有远端请求统一收口到 `core_network`
2. `sleep`、在线播放、在线搜索不再直接持有临时 HTTP 代码
3. 引入统一 `NetworkClient`
4. 搜索加防抖、超时、镜像切换

#### P0 当前已落地

本轮已按 P0 方向完成第一阶段收口，当前代码落点如下：

- `core_network/src/main/java/com/example/core_network/http/NetworkClient.java`
- `core_network/src/main/java/com/example/core_network/http/RequestPolicy.java`
- `core_network/src/main/java/com/example/core_network/radio/RadioBrowserService.java`
- `core_network/src/main/java/com/example/core_network/radio/RadioBrowserEndpointResolver.java`
- `core_domain/src/main/java/com/example/core_domain/radio/RadioStation.java`
- `core_data/src/main/java/com/example/core_data/radio/RadioRepository.java`

当前含义：

1. `core_network` 已经开始承担统一客户端、请求策略和镜像切换。
2. 电台搜索的频率提取、排序增强、去重、点击上报已经下沉到 `core_data.RadioRepository`。
3. `app` 中的 `SleepPlaybackController` 不再直接依赖远端 HTTP 实现，而只依赖 `core_data` 暴露的仓库能力。
4. UI 搜索已具备防抖与异步结果回调。

但需要明确：

- 这只是 P0 的“收口版”，不是最终性能版。
- `在线播放` 的“点击后 2 秒左右才开始播”的主问题，仍需在 P1 引入 `AudioStreamProbeApi`、`ResolvedPlayableSource`、候选流回退和启动耗时埋点之后才能系统性解决。

### P1：补播放前准备链路

1. 增加 `AudioStreamProbeApi`
2. 播放前返回 `ResolvedPlayableSource`
3. 建立候选流回退
4. 记录点击到开始播放的耗时

#### P1 当前已落地

本轮已经先完成 P1 的第一版接线，当前代码路径如下：

- `core_domain/.../player/`：新增统一播放器抽象
  - `PlaybackRequest`
  - `PlaybackSourceResolver`
  - `ResolvedPlayableSource`
  - `PlayerKernel`
  - `PlayerKernelSnapshot`
- `core_network/.../stream/`
  - `AudioStreamProbeApi`
  - `NetworkPlaybackSourceResolver`
- `feature_player/.../player/`
  - `NativeBackedMediaPlayerKernel`
  - `PlayerJNI`
- `native/jni/bridge/PlayerBridge.cpp`

当前含义：

1. `core_domain` 已经成为播放器内核和播放源解析的统一抽象入口。
2. `core_network` 已经开始承担在线播放 / 电台流的播放前探测与 resolved URL 输出。
3. `feature_player` 已经改为“Java `MediaPlayer` 实际播放 + native 内核同步数据源”的混合形态。
4. native 层目前已接管 `sourceId / originalUrl / resolvedUrl / contentType / userAgent / live / seekable / probeLatency` 等核心数据源上下文，为后续继续下沉到真正的 native 拉流和解码留出稳定入口。

仍需明确：

- 当前 native 侧还只是承接“播放器内核上下文”和“数据源设置”，还没有完全承担解复用、解码和渲染。
- 如果后续继续推进 P1 后半段，应优先把 `stream probe / preconnect / fallback` 继续向 native `NetworkManagerJNI` 形态演进，而不是直接重写所有 JSON API。

### P2：做稳定性与性能优化

1. 搜索结果缓存
2. 流解析缓存
3. 埋点统计
4. 根据数据决定是否将联网音频播放主内核切到 `Media3 ExoPlayer`

## 12. 对当前仓库的直接约束

从现在开始，新增任何联网功能应遵守：

1. 不在 `Fragment` / `Activity` 中直接写 HTTP 请求。
2. 不在播放器控制器中直接拼第三方 API URL。
3. 所有远端接口先进入 `core_network`。
4. 业务聚合、排序、缓存、回退放到 `core_data`。
5. 所有在线音频播放前，必须预留“源解析 / 流探测 / 候选回退”的插入点。

## 13. 关于 `NetworkManagerJNI` / Native 网络内核的思考

可以在 `core_network` 中设计一个 `NetworkManagerJNI`，通过 JNI 调用 native 层 C++ 网络实现，让 native 层承担更底层的网络内核能力，但当前阶段不建议直接把它作为 P0 落地目标。

### 13.1 可以做，但不要直接上

原因不是“JNI 不行”，而是它适合解决的是更靠近“播放前源解析 / 流探测 / 长连接流式拉取”的问题，而不是先拿来替代所有 JSON 搜索请求。

换句话说：

- `NetworkManagerJNI` 更适合 P1 / P2 的播放导向能力
- 不适合一开始就把整个网络栈全部 native 化

### 13.2 适合 native 承担的部分

如果后续进入 native 网络内核阶段，更推荐让它承担：

1. 音频流预探测
   - redirect 跟随
   - content-type 识别
   - 首包耗时采集
   - HLS / MP3 / AAC 快速识别

2. 播放前源解析
   - 解析最终 resolved URL
   - 维护候选流回退顺序

3. 面向播放的网络优化
   - 连接预热
   - DNS / socket / TLS 复用策略
   - 长连接和流式读取

4. 与 native 播放内核的协同
   - 如果未来播放器也下沉到 JNI / C++，则网络和播放处于同一内核，能减少跨层切换和状态同步损耗

### 13.3 不建议一开始就放到 native 的部分

以下能力在现阶段仍建议保留在 Java 层：

1. 通用 JSON 搜索接口
2. 页面级搜索防抖和取消策略
3. 业务排序和去重
4. Repository 编排
5. 业务缓存策略

原因：

- Java 层更容易调试和快速迭代
- UI / Repository / 业务排序本来就不属于 native 强项
- 过早把通用搜索也 JNI 化，会让联调、异常追踪、证书问题、线程问题一起复杂化

### 13.4 推荐的未来接口形态

如果要为 native 网络内核预留位置，推荐在 `core_network` 里先抽象接口，而不是直接让业务层依赖 JNI：

```text
core_network/
  transport/
    NetworkEngine.java
    JavaNetworkEngine.java
    NativeNetworkEngine.java   // 后续接 NetworkManagerJNI
  jni/
    NetworkManagerJNI.java
```

调用关系建议保持为：

`core_data Repository`
-> `core_network.NetworkEngine`
-> `JavaNetworkEngine` 或 `NativeNetworkEngine`

而不是：

`Feature/UI`
-> `NetworkManagerJNI`

### 13.5 这样设计的好处

1. Java 实现和 native 实现可以并存，便于灰度切换。
2. `core_data` 不需要知道底层究竟是 Java 网络栈还是 native 网络栈。
3. 后续如果 native 方案成熟，可以优先替换“流探测”和“播放前准备”链路，而不是一次性推翻全部网络实现。

### 13.6 当前建议

当前阶段结论很明确：

1. P0 先用 Java 层把分层和职责边界收口。
2. `NetworkManagerJNI` 作为 P1 / P2 预留能力写入架构。
3. 真正优先下沉到 native 的，不是“所有网络请求”，而是“播放导向的网络内核”：
   - stream probe
   - source resolve
   - preconnect
   - fallback

## 14. 结论

当前项目的核心矛盾，不是“某一个电台接口不够好”，而是：

- 没有把联网搜索、联网播放、流探测、失败回退、性能治理当成一套统一基础设施来设计。

`core_network` 未来不应该只是“发请求的地方”，而应该是：

- 远端服务接入层
- 流探测层
- 请求策略层
- 性能治理层

只有这样，`sleep`、在线播放、在线搜索等模块才能复用同一套能力，并系统性地解决“点击后要等 2 秒左右才能开始播”的问题。
