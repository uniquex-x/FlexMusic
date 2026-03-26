# 搜索模块现状说明

## 1. 当前实现范围

搜索功能已经从占位模块发展为可用链路，当前覆盖：

- 搜索页 UI
- 搜索建议
- 搜索历史
- 热搜入口
- 多 scope 搜索
- 分页加载
- 搜索结果直接播放
- 加入下一首 / 加入队列
- 搜索结果页“播放全部”

当前入口：

- `feature_search/src/main/java/com/example/feature_search/ui/SearchFragment.java`

## 2. 当前模块分层

### `feature_search`

负责：

- 搜索页交互
- 输入框、建议词、历史词、热搜词展示
- scope 切换
- 结果列表和 item 操作菜单

当前主要类：

- `SearchFragment`
- `SearchViewModel`
- `SearchResultAdapter`
- `SearchPlaybackCoordinator`
- `ISearchHost`

### `core_domain`

负责抽象和 use case：

- `SearchUseCase`
- `GetSearchSuggestionsUseCase`
- `PlayTrackFromSearchUseCase`
- `SearchQuery`
- `SearchFilter`
- `SearchScope`
- `SearchResultPage`
- `SearchTrack / SearchAlbum / SearchArtist / SearchPlaylist`

### `core_data`

负责业务编排：

- `OnlineSearchRepository`
- `SearchSuggestionRepository`
- `TrackPlaybackRepository`
- `SearchHistoryStore`
- `SearchCacheStore`
- `SearchResultRanker`
- `SearchResultDeduplicator`

### `core_network`

负责 provider 接入：

- `MusicSearchService`
- `TrackPlaybackResolveService`
- `SearchEndpointResolver`
- `JamendoApiConfig`

## 3. 当前用户可见行为

### 3.1 搜索交互

当前支持：

- 输入关键字后延迟拉取建议
- 回车或按钮触发搜索
- 空输入时展示 landing 内容
- 清空历史记录
- 切换 `ALL / TRACKS / ALBUMS / ARTISTS / PLAYLISTS`
- 点击 `Load more` 继续翻页

### 3.2 播放交互

当前搜索结果支持：

- 点击单曲直接播放
- 长按/菜单加入下一首
- 长按/菜单加入队列
- 结果页播放全部

播放并不是直接把搜索结果里的 URL 交给播放器，而是：

`SearchTrack -> PlayTrackFromSearchUseCase -> PlaybackRequest -> PlaybackController`

### 3.3 provider

当前默认 provider 是 `Jamendo`。

现状说明：

- 搜索和播放地址解析都已接通
- provider 凭据由 `core_network` 统一注入
- `feature_search` 和 `app` 不持有 `client_id`

## 4. 当前未实现部分

以下内容仍未落地或只完成了基础骨架：

- 歌手详情页
- 专辑详情页
- 歌单详情页
- 多 provider 聚合
- 搜索结果跨 provider 回退
- 服务端统一热搜与推荐

因此当前搜索文档应被理解为“单 provider 在线搜索已可用”，而不是“完整音乐平台发现体系已完成”。

## 5. 关键代码位置

- `feature_search/src/main/java/com/example/feature_search/ui/SearchFragment.java`
- `feature_search/src/main/java/com/example/feature_search/ui/SearchViewModel.java`
- `feature_search/src/main/java/com/example/feature_search/action/SearchPlaybackCoordinator.java`
- `core_data/src/main/java/com/example/core_data/search/OnlineSearchRepository.java`
- `core_data/src/main/java/com/example/core_data/search/TrackPlaybackRepository.java`
- `core_network/src/main/java/com/example/core_network/search/MusicSearchService.java`
- `core_network/src/main/java/com/example/core_network/search/TrackPlaybackResolveService.java`

## 6. 当前维护原则

后续扩展搜索功能时，继续遵守以下约束：

- UI 不直接请求第三方 provider
- 搜索结果和最终播放 URL 必须解耦
- 任何新的 provider 先接到 `core_network`
- 播放仍统一复用主播放器链路
