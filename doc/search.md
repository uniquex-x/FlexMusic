# 搜索模块实现方案

## 1. 目标

在线搜索模块需要解决的不是“把一个关键词发给第三方接口”，而是把“发现内容”稳定转换成“可加入播放队列、可继续解析、可治理启动时延”的标准能力。

本模块目标：

1. 支持歌曲、歌手、专辑、歌单等在线曲库搜索。
2. 支持搜索建议、历史、热搜、分页和空态兜底。
3. 搜索结果可直接触发播放、加入队列、收藏、查看详情。
4. 搜索链路与播放链路解耦，避免搜索模块直接处理 Range、代理、seekable 与播放器内核细节。
5. 为后续多源聚合、缓存、推荐和预热留出扩展位。

明确边界：

- `feature_search` 负责搜索页 UI 与交互。
- `core_domain` 负责搜索抽象、用例和播放意图。
- `core_data` 负责聚合、缓存、排序、去重、回退和“搜索结果 -> 播放候选”的业务编排。
- `core_network` 负责远端搜索 API、详情 API、播放地址解析 API、流可用性探测。
- native 播放器不参与搜索，也不感知搜索来源。

## 2. 设计原则

参考 `ijkplayer/ffplay` 与 VLC 的成熟做法，搜索模块应遵循三个原则：

1. 发现面和执行面分离
- 搜索负责找“内容身份”，播放负责处理“可播放输入”。
- 搜索结果不应直接等同于最终播放 URL，因为在线资源通常有过期、重定向、鉴权和多音质候选问题。

2. 预解析和真实播放分离
- 类似 VLC 将 preparser 与实际 input thread 分离，搜索页可以做轻量元数据准备，但不能把真实播放网络链路硬塞进列表滚动或输入联想。

3. 播放内核复用既有链路
- 类似 `ijkplayer` 基于 `ffplay` 的思路，真正播放仍然走当前工程已经建立的
  `PlaybackSourceResolver -> SeekablePlaybackProxyServer -> PlayerSession`
  链路。
- 搜索模块不重新发明播放器，也不在 UI 层临时拼接字节流。

## 3. 当前现状与缺口

当前仓库里已经有：

- `feature_search/` 模块壳
- `core_network` 中偏电台场景的 `RadioBrowserService`
- `core_data` 中偏睡眠页场景的 `RadioRepository`
- 在线播放主链路：`PlaybackSourceResolver -> SeekablePlaybackProxyServer -> native PlayerSession`
- 当前已落地的 P0 在线曲库 provider：`Jamendo`

但在线曲库搜索仍缺少以下关键能力：

1. 多源聚合与服务端统一编排仍未落地，当前仍是单 provider。
2. 歌手 / 专辑 / 歌单详情页与详情内“播放全部”仍未落地。
3. 播放地址过期后的跨 provider 多候选回退仍未落地。
4. 搜索结果、播放队列和预热策略之间的 warm path 协同仍未落地。

因此建议把“电台搜索”保留为垂直能力，而“在线曲库搜索”单独建模，不和 `sleep` 场景混在一起。

## 4. 推荐架构

### 4.1 模块分层

#### `feature_search`

负责：

- 搜索页输入框、联想词、历史记录、结果列表、筛选器
- “播放”“加入下一首”“加入歌单”“查看专辑/歌手详情”等交互
- 展示加载中、空结果、网络错误、资源不可播等状态

不负责：

- 直接请求第三方曲库接口
- 直接解析播放 URL
- 直接决定代理、probe、Range 和 warm path

#### `core_domain`

建议新增的抽象：

- `ISearchRepository`
- `ISearchSuggestionRepository`
- `ITrackPlaybackResolver`
- `SearchQuery`
- `SearchFilter`
- `SearchScope`
- `SearchResultPage`
- `SearchTrack`
- `SearchAlbum`
- `SearchArtist`
- `SearchPlaylist`
- `TrackPlaybackIntent`
- `TrackPlaybackCandidate`

职责：

- 定义搜索输入/输出模型
- 定义搜索、建议、详情、播放意图解析等用例
- 隐藏底层具体远端站点和服务端实现

#### `core_data`

建议新增：

- `OnlineSearchRepository`
- `SearchSuggestionRepository`
- `TrackPlaybackRepository`
- `SearchResultRanker`
- `SearchResultDeduplicator`
- `SearchHistoryStore`
- `SearchCacheStore`
- `PlaybackWarmupCoordinator`

职责：

- 聚合多个 provider 或服务端结果
- 统一标准化字段
- 做本地缓存、历史、热搜回流
- 搜索结果点击后，把内容身份转换成播放候选列表
- 和现有播放器队列联动，决定何时可对“已知下一首”做预热

#### `core_network`

建议新增或扩展：

- `MusicCatalogService`
- `MusicSearchService`
- `TrackDetailService`
- `TrackPlaybackResolveService`
- `StreamProbeService`
- `SearchEndpointResolver`

职责：

- 统一 HTTP client、header、超时、重试、熔断与埋点
- 请求远端搜索 API、详情 API、播放地址解析 API
- 返回规范化 DTO，不承载业务排序
- 执行播放前的 URL probe、redirect 处理、可用性检查

当前实现：

- `MusicSearchService` 已接入 `Jamendo /v3.0/tracks`
- 搜索使用 `search + order=relevance + fullcount=true`
- 播放解析使用二次 `id` 查询拿最新 `audio` 字段
- `SearchEndpointResolver` 目前指向 `https://api.jamendo.com/v3.0`

#### `native/player`

保持现状：

- 只消费 `ResolvedPlayableSource`
- 只做 AVIO / demux / decode / render
- 不知道搜索结果页、不知道推荐逻辑、不知道曲库 provider

### 4.2 推荐的高层调用链

#### 搜索链路

`SearchFragment/SearchViewModel`
-> `SearchUseCase`
-> `ISearchRepository`
-> `OnlineSearchRepository`
-> `MusicSearchService`
-> 远端搜索服务或 provider adapter

#### 播放链路

`SearchResult item click`
-> `PlayTrackFromSearchUseCase`
-> `ITrackPlaybackResolver`
-> `TrackPlaybackRepository`
-> `TrackPlaybackResolveService`
-> `PlaybackRequest`
-> `PlaybackSourceResolver`
-> `SeekablePlaybackProxyServer`
-> `PlayerSession`

这条链路要点是：

- 搜索结果点击时先解析“播放意图”，再进入统一播放器链路。
- 搜索模块只负责把“这首歌是谁”交出去，不负责决定最后用哪个 `IFileIo`。

## 5. 核心领域模型

### 5.1 搜索结果模型

建议搜索结果不要只有一个平铺的 `Song`，而是分层：

- `SearchTrack`
  - `trackId`
  - `providerId`
  - `title`
  - `subtitle`
  - `artistNames`
  - `albumName`
  - `durationMs`
  - `coverUrl`
  - `availability`
  - `qualitySummary`
  - `playbackHint`

- `SearchAlbum`
  - `albumId`
  - `providerId`
  - `title`
  - `artistNames`
  - `coverUrl`
  - `trackCount`

- `SearchArtist`
  - `artistId`
  - `providerId`
  - `name`
  - `coverUrl`

- `SearchPlaylist`
  - `playlistId`
  - `providerId`
  - `title`
  - `creatorName`
  - `coverUrl`
  - `trackCount`

### 5.2 播放意图模型

搜索结果里的 `playbackHint` 不应该直接等于最终 URL，建议定义为：

- `TrackPlaybackIntent`
  - `trackId`
  - `providerId`
  - `albumId`
  - `preferredQuality`
  - `requiresResolve`
  - `candidateToken`

解析后输出：

- `TrackPlaybackCandidate`
  - `sourceId`
  - `originalUrl`
  - `headers`
  - `qualityLabel`
  - `expiresAtMs`
  - `isLive`
  - `confidence`

这样做的原因：

1. 很多曲库返回的是“歌曲身份”，不是最终流地址。
2. 播放地址可能短时过期，不能在搜索结果页长时间持有。
3. 同一首歌可能存在多个候选源，需要 `core_data` 排序和回退。

## 6. 搜索结果到播放的协作规则

### 6.1 点击单曲播放

1. 用户在搜索结果页点击一首歌。
2. `feature_search` 只提交 `TrackPlaybackIntent`。
3. `core_data` 解析出 `TrackPlaybackCandidate` 列表。
4. 选择当前最优候选，生成 `PlaybackRequest`。
5. `PlaybackSourceResolver` 决定是否进入 `SeekablePlaybackProxyServer`。
6. native 播放器开始 prepare/play。

### 6.2 点击“播放全部”

如果用户播放的是搜索结果列表、专辑页或歌单页，则这批内容已经是“已知队列”，可以额外做两件事：

1. 当前曲目按 cold path 立即启动。
2. 当前曲目稳定进入 `PLAYING` 后，对队列中的下一首执行异步播放意图解析和轻量预热。

### 6.3 加入下一首 / 加入队列

搜索模块不直接操作 native 队列，统一走 `PlaybackController`：

- `addNext(track)`
- `addToQueue(track)`
- `replaceQueue(tracks, startIndex)`

这样后续的下一首预热才能基于稳定的“业务队列视图”运行，而不是由播放器猜测。

## 7. 数据源策略

在线曲库搜索不建议一开始就让客户端直接耦合多个第三方站点。推荐优先级：

### 7.1 首选：服务端聚合

建议服务端至少拆成三个能力：

1. `catalog-search-service`
- 统一关键词搜索、建议词、热搜、分页。

2. `catalog-normalize-service`
- 统一字段映射、专辑/歌手去重、别名归并、封面与时长清洗。

3. `catalog-playback-resolve-service`
- 负责把 `trackId/providerId` 解析成客户端可继续处理的播放候选。

服务端聚合的好处：

- 客户端不暴露第三方 provider 细节
- 更方便做限流、签名、容灾、排序和推荐
- 更方便控制合规与资源可用性

### 7.2 次选：客户端多 adapter

如果当前阶段没有自建服务端，也建议在 `core_network` 内做 provider adapter，而不是让 `feature_search` 直接请求外站。

建议形式：

- `MusicSearchService` 只面向统一 DTO
- 具体 provider 以 `ProviderAService`、`ProviderBService` 形式隔离
- `core_data` 负责聚合和回退

### 7.3 当前已接入方案：Jamendo

当前代码已经按客户端单 provider 方式接入 `Jamendo`，原因是：

- 官方 API 同时支持曲目搜索和在线播放地址返回
- 返回字段里已经包含 `audio`、封面、时长、艺术家、专辑等基础信息
- 现阶段可以先满足 P0 的“搜单曲 -> 点击播放”主链路

当前接法：

1. `MusicSearchService`
- 调 `GET /v3.0/tracks`
- 使用 `client_id`
- 使用 `search` 作为自由文本搜索参数
- 使用 `order=relevance`
- 使用 `audioformat=mp32`
- 使用 `fullcount=true` 支持总量和分页

2. `TrackPlaybackResolveService`
- 不长期信任搜索页返回的流地址
- 点击播放时按 `trackId` 再查一次 `/v3.0/tracks`
- 重新读取最新 `audio` 字段，转换为 `TrackPlaybackCandidate`

3. 配置方式
- `core_network` 优先从忽略文件 `local.properties` 读取 `jamendo.clientId`
- 若本地文件未提供，则回退到 Gradle 属性 `jamendoClientId`
- 再回退到环境变量 `JAMENDO_CLIENT_ID`
- 未配置时搜索会明确报错，不再静默退回 demo 数据

限制说明：

- 当前接入遵循 Jamendo 官方免费 API 约束，默认仅适合非商业使用场景
- 当前已接 `tracks / albums / artists / playlists / autocomplete`，但歌手 / 专辑 / 歌单详情页尚未落地
- 当前歌单结果仅支持发现与二次 refine，不直接进入 playlist detail 播放链路
- 当前仍是单源实现，后续可继续在 `core_network/search/` 下增加其他 provider adapter

## 8. 排序、缓存与去重

### 8.1 排序

建议排序综合考虑：

- 文本相关性
- 标题命中权重
- 歌手/专辑精确匹配
- 可播性
- 音质等级
- 来源可信度
- 近期点击/播放反馈

### 8.2 去重

同一首歌可能多来源重复出现，推荐以如下维度做去重：

- 标准化标题
- 标准化主歌手
- 时长近似
- 专辑名或发行信息

输出层保留“主结果 + 备选来源”，不要在 UI 上直接平铺多个重复条目。

### 8.3 缓存

缓存拆成三类：

1. 查询缓存
- `query + filter + page -> SearchResultPage`

2. 详情缓存
- `trackId/albumId/artistId -> detail`

3. 播放意图缓存
- `trackId/providerId -> TrackPlaybackCandidate`
- 必须带 `expiresAtMs`

注意：

- 搜索缓存可以相对长一些。
- 播放候选缓存必须短 TTL，因为 URL 可能过期。

## 9. 与预热的关系

搜索模块可以帮助播放器做“已知队列预热”，但不能反过来把媒体预热塞进搜索主流程。

建议规则：

1. 用户只是在输入和浏览列表时
- 只做搜索接口和图片资源加载
- 不做音频资源预请求

2. 用户明确点击“播放”
- 才触发 `TrackPlaybackIntent` 解析

3. 用户点击“播放全部”或进入已知歌单
- 当前曲目 cold start
- 下一首才进入异步 warm path

4. 搜索页的联想词、热搜、结果分页
- 归类为“发现层预取”
- 不属于播放器 warmup

## 10. 推荐目录结构

```text
feature_search/
  src/main/java/com/example/feature_search/
    ui/
      SearchFragment.java
      SearchViewModel.java
      SearchResultAdapter.java
    action/
      SearchPlaybackCoordinator.java

core_domain/
  src/main/java/com/example/core_domain/search/
    ISearchRepository.java
    ISearchSuggestionRepository.java
    ITrackPlaybackResolver.java
    SearchQuery.java
    SearchFilter.java
    SearchTrack.java
    SearchAlbum.java
    SearchArtist.java
    SearchPlaylist.java
    SearchResultPage.java
    TrackPlaybackIntent.java
    TrackPlaybackCandidate.java

core_data/
  src/main/java/com/example/core_data/search/
    OnlineSearchRepository.java
    SearchSuggestionRepository.java
    TrackPlaybackRepository.java
    SearchResultRanker.java
    SearchResultDeduplicator.java
    SearchCacheStore.java
    SearchHistoryStore.java

core_network/
  src/main/java/com/example/core_network/search/
    MusicSearchService.java
    TrackDetailService.java
    TrackPlaybackResolveService.java
    SearchEndpointResolver.java
    dto/
```

## 11. 分阶段落地建议

### P0

- 单关键词搜单曲
- 返回歌曲卡片
- 支持点击播放
- 支持搜索历史
- `TrackPlaybackIntent -> PlaybackRequest` 主链路打通

当前状态：

- 已完成
- 真实 provider 为 `Jamendo`
- 首页搜索入口已接到 `feature_search`
- 点击搜索结果后已接到现有 `PlaybackController -> PlaybackSourceResolver -> SeekablePlaybackProxyServer -> PlayerSession`

### P1

- 补歌手、专辑、歌单搜索
- 补建议词、热搜、分页
- 补结果去重、排序、缓存
- 补“播放全部”与队列联动

当前状态：

- 已完成客户端单 provider 版 P1 主能力
- `Jamendo` 已支持 `tracks / albums / artists / playlists` 搜索
- 已支持 `autocomplete` 建议词与热门搜索词回退
- 已支持 scope 切换、分页加载、分组展示、结果去重/排序/缓存
- 已支持单曲 `Play Next / Add To Queue`，以及搜索结果 `Play All -> replaceQueue`
- 歌单点击当前走 refine 到单曲搜索，不是完整 playlist detail 页

### P2

- 多源聚合与故障回退
- 点击/播放反馈回流排序
- 已知队列的下一首预热
- 推荐、猜你喜欢、相似歌曲等发现能力

## 12. 结论

搜索模块的本质不是“拿到 URL 就播”，而是：

1. 先统一内容身份。
2. 再把内容身份解析成播放意图。
3. 再复用现有网络治理与 native 播放内核。

这样才能让搜索、预热、seekable 代理、播放器状态机彼此解耦，同时保留后续多曲库、多来源和推荐系统的扩展空间。

## 13. 构建说明

按仓库规则，本轮已完成代码与文档修改，但未执行构建或测试，需开发者自行验证。

## 14. 配置说明

为避免提交机器私有配置，`Jamendo client_id` / `client_secret` 不写入仓库，建议优先通过本地忽略文件提供：

```properties
# local.properties
jamendo.clientId=your_jamendo_client_id
jamendo.clientSecret=your_jamendo_client_secret
```

当前实现只实际使用 `client_id`；`client_secret` 仅本地保留，未进入源码常量、`BuildConfig` 或文档示例。

如果你更希望走用户目录配置，也可以使用：

```properties
# ~/.gradle/gradle.properties
jamendoClientId=your_jamendo_client_id
```

或：

```bash
export JAMENDO_CLIENT_ID=your_jamendo_client_id
```

如果没有配置，搜索页会返回明确错误，提示补齐 `client_id`。
