# 网络模块现状说明

## 1. 目标

`core_network` 是 FlexMusic 的在线能力接入层，负责把外部搜索、播放地址解析、流探测、代理和认证配置收敛到统一边界。

UI 和 feature 模块不应该直接请求第三方接口，也不应该自己处理：

- URL 重定向
- `Accept-Ranges` / seekable 判断
- localhost 代理
- provider 认证参数
- 在线流 warmup

## 2. 当前已实现能力

### 2.1 在线曲库搜索

当前在线搜索的默认 provider 是 `Jamendo`，实现链路如下：

- `feature_search`
  - `SearchFragment`
  - `SearchViewModel`
- `core_domain`
  - `SearchUseCase`
  - `GetSearchSuggestionsUseCase`
  - `PlayTrackFromSearchUseCase`
- `core_data`
  - `OnlineSearchRepository`
  - `SearchSuggestionRepository`
  - `TrackPlaybackRepository`
  - `SearchHistoryStore`
  - `SearchCacheStore`
  - `SearchResultRanker`
  - `SearchResultDeduplicator`
- `core_network/search`
  - `MusicSearchService`
  - `TrackPlaybackResolveService`
  - `SearchEndpointResolver`
  - `JamendoApiConfig`

当前行为：

- 支持关键词搜索
- 支持 `ALL / TRACKS / ALBUMS / ARTISTS / PLAYLISTS` scope
- 支持分页加载
- 支持搜索建议、历史记录、热搜词入口
- 支持把搜索结果解析为 `PlaybackRequest` 后接入主播放器

### 2.2 在线播放解析

主播放解析入口是：

- `core_domain/player/PlaybackSourceResolver`
- `core_network/stream/NetworkPlaybackSourceResolver`

当前职责：

- 将 `PlaybackRequest` 转成 `ResolvedPlayableSource`
- 对在线流做 URL 解析和能力判断
- 为 seekable 在线音频准备 localhost 代理入口
- 维护播放 warmup 的命中、取消和回收

### 2.3 电台搜索

睡眠页电台搜索使用：

- `core_data/radio/RadioRepository`
- `core_network/radio/RadioBrowserService`

当前行为：

- 非空关键词走在线电台搜索
- 空查询优先展示本地精选台站
- 在线搜索结果会和本地精选结果做合并兜底

### 2.4 配置注入

`core_network/build.gradle` 当前会把以下配置注入 `BuildConfig`：

- `JAMENDO_CLIENT_ID`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

来源优先级：

1. `local.properties`
2. Gradle Property
3. 环境变量

对应键名：

- `jamendo.clientId`
- `jamendoClientId`
- `JAMENDO_CLIENT_ID`
- `supabase.url`
- `supabaseUrl`
- `SUPABASE_URL`
- `supabase.publishableKey`
- `supabasePublishableKey`
- `SUPABASE_PUBLISHABLE_KEY`

## 3. 当前分层约束

### `core_network`

负责：

- HTTP client 与请求策略
- provider endpoint 和认证参数
- 搜索 DTO / 播放解析 DTO
- 代理、probe、warmup 等在线播放网络控制面

不负责：

- UI 状态
- 播放队列业务
- 推荐逻辑
- native 解码和渲染

### `core_data`

负责：

- 搜索结果缓存、排序、去重
- 搜索点击后的播放候选编排
- 电台搜索结果整理
- warmup 计划编排

### `feature` / `app`

负责：

- 用户交互
- 调用 use case / repository
- 渲染结果和错误态

禁止事项：

- 直接拼第三方搜索接口 URL
- 在 Fragment 里写 provider header / token
- 在 UI 层直接做流探测和代理逻辑

## 4. 与播放器的边界

当前播放主链路仍是：

`PlaybackRequest -> PlaybackSourceResolver -> ResolvedPlayableSource -> PlayerKernel -> native PlayerSession`

网络层的责任是把“远端资源”整理成“播放器可直接消费的输入”，而不是参与播放状态机本身。

播放器侧只关心：

- 当前 source 是什么
- 是否可 seek
- 什么时候 `PREPARING / PLAYING / PAUSED / ERROR`

## 5. 当前缺口

截至目前，`core_network` 已能支撑单 provider 在线搜索、在线播放、睡眠电台和 warmup，但仍有明显缺口：

- 在线曲库仍是单 provider，未做多源聚合
- 搜索热词仍以本地/轻量策略为主，未接服务端推荐
- 电台目录仍未引入官方名录聚合和健康巡检体系
- provider 失败后的多候选回退策略还不完整

## 6. 更新文档时的原则

后续如果增加新的联网功能，文档应继续遵守以下规则：

- 新的远端接入必须先落在 `core_network`
- 业务编排放 `core_data`
- UI 只消费抽象 use case / repository
- 任何新的在线播放能力都要说明冷启动路径、失败回退路径和日志边界
