# 网络模块架构设计

## 1. 目标

`core_network` 不只是“发 HTTP 请求”，它要承担在线音频的网络控制面职责：

- URL 解析
- 流能力探测
- seekable 代理
- Range / 重连
- 启播耗时埋点
- warm path 预热能力

播放器侧不应该直接面向远端站点细节，也不应该在 UI 层里临时做 probe、redirect、Range 处理。

## 2. 当前问题复盘

在线音频过去出现过三类典型问题：

1. 点击后串行做两次网络首开
- Java probe 一次
- native 真正 open 一次

2. 为了保留 seekability，引入代理后把 warmup 错放进冷启动关键路径
- 导致 localhost open 仍然要等远端 warmup 完成

3. seek 语义和网络层没有明确边界
- seek 期间重复建链
- 旧连接和旧数据没有稳定淘汰

结论：

- `core_network` 必须承担在线音频的“网络控制面”
- native 播放器只负责“媒体数据面”

## 3. 当前推荐链路

### 3.1 冷启动主路径

1. `PlaybackSourceResolver`
- 接受 `PlaybackRequest`
- 输出 `ResolvedPlayableSource`

2. `NetworkPlaybackSourceResolver`
- 冷启动默认跳过阻塞 probe
- 对渐进式在线音频优先产出 seekable localhost URL

3. `SeekablePlaybackProxyServer`
- 对播放器暴露 `http://127.0.0.1/...`
- 对远端发真实请求
- 负责 `Range`、`206`、重连和 seek 对应的偏移请求

4. `FfmpegStreamFileIo`
- 只连 localhost

5. native `PlayerSession`
- 进入 AVIO / demux / decode / render

### 3.2 兜底路径

- 仅在 HTTPS native prepare 失败时，允许 `StreamingPipeSource` 作为单次 fallback
- pipe 不是默认主路径

原因很简单：

- pipe 适合作兜底，不适合作 seekable 主路径
- VLC 的 chained/FIFO demux 思路也说明了“可持续送数据的 pipe”不等价于“可 seek 的稳定输入”
- seekable 主路径必须保留 Range 语义和后续 warmup 能力

## 4. 分层职责

### 4.1 `core_network`

负责：

- 统一 HTTP client
- 请求策略
- URL probe / redirect / content-type 判断
- seekable 代理
- 连接复用与 warmup
- 网络耗时打点
- 在线曲库 provider adapter 与认证参数管理

不负责：

- 播放状态机
- UI 逻辑
- decoder / renderer 逻辑
- 推荐算法和队列推断

### 4.2 搜索曲库的网络实现约束

搜索场景同样属于 `core_network` 的职责边界，必须遵守与在线播放一致的分层规则：

1. `feature_search`
- 不允许直接请求第三方曲库接口
- 不允许自己拼 `client_id`、header 或 query 参数

2. `core_network/search`
- 负责 provider endpoint、认证参数、DTO 解析、超时和重试
- 当前已接入 `Jamendo /v3.0/tracks`

3. `core_data/search`
- 负责把 provider DTO 变成领域模型
- 负责缓存、排序、去重和播放候选编排

4. `app/feature`
- 只消费 `SearchUseCase` / `PlayTrackFromSearchUseCase`
- 不知道 Jamendo 参数细节

### 4.3 当前 Jamendo 配置

当前仓库通过 `core_network` 的 `BuildConfig.JAMENDO_CLIENT_ID` 读取认证参数，来源按优先级为：

1. 本地忽略文件 `local.properties` 中的 `jamendo.clientId`
2. Gradle 属性 `jamendoClientId`
3. 环境变量 `JAMENDO_CLIENT_ID`

这样做的目的：

- 避免把 `client_id` 写死在源码里
- 允许保存在忽略的 `local.properties`，但不得提交到仓库
- 保证 `feature_search` 和 `app` 不持有 provider 凭据

补充说明：

- `client_secret` 当前搜索和公开流地址读取链路并不需要，不应编入源码或 `BuildConfig`
- 如需本地保存，只允许放在忽略文件中，例如 `local.properties`

未配置时，`MusicSearchService` 会直接返回明确错误，避免误以为网络或解析链路异常。

### 4.4 `core_data`

负责：

- 业务级 source 选择和回退
- 本地缓存与远端结果融合
- 候选流排序
- 基于业务队列决定是否存在“下一首”
- 触发和取消预热计划

### 4.5 `core_domain`

负责：

- 抽象请求语义和用例
- 隐藏具体远端实现
- 定义 warmup 的输入输出语义

### 4.6 `feature/app`

只负责：

- 用户点击
- 调用 use case / resolver / kernel
- 展示快照

### 4.7 当前预热实现状态

截至当前代码，预热能力已经按分层接入，但仍保留了后续继续演进的空间。

1. `core_domain`
- 已新增 `IPlaybackWarmupEngine`
- 已新增 `PlaybackWarmupLevel`
- 已新增 `PlaybackWarmupRequest`
- 已新增 `PlaybackWarmupSnapshot`

2. `core_data`
- 已新增 `PlaybackWarmupCoordinator`
- 当前负责基于业务队列决定：
  - 是否需要预热
  - 预热当前 host 还是下一首
  - 预热到 `HOST / URL_METADATA / PLAYBACK_CANDIDATE` 哪一层

3. `core_network`
- `NetworkPlaybackSourceResolver` 当前同时承担：
  - 冷路径 `resolve`
  - 热路径 `warmup` 命中与回收
- 已新增 `HostWarmupClient`
  - 做 DNS / TCP / TLS 级轻量 host warmup
- 已新增 `NetworkWarmupEngine`
  - 按层执行 host、URL 元数据、代理 session、head cache 预热
- `AudioStreamProbeApi` 当前会产出：
  - `resolvedUrl`
  - `contentType`
  - `contentLength`
  - `Accept-Ranges`
- `SeekablePlaybackProxyServer` 当前已支持：
  - `openSession()`
  - `prepareSession()`
  - `primeHead()`
  - `releasePreparedSession()`
  - `promotePreparedSession()`

4. `app`
- `PlaybackController` 当前在“当前曲目进入稳定 `PLAYING` 后”异步触发预热
- 预热目标来自当前业务队列，不由网络层猜测
- 队列变化、切歌、随机/循环模式变化时会取消过期预热

## 5. 预热到底预热什么

这是本轮设计最需要说清楚的地方。

“预热”不是单一动作，而是分层能力。网络连接实际至少拆成：

1. DNS 解析
2. TCP 握手
3. TLS 握手
4. HTTP 请求与重定向
5. 首字节返回
6. 资源头部或前若干字节可用

因此“预热的是域名解析还是音频资源 URL”这个问题，正确答案不是二选一，而是：

- 域名级预热是低成本、低收益的基础层
- 资源 URL 级预热是高收益、但必须依赖“已知播放候选”的高阶层

推荐把预热分成五层：

### 5.1 Host 级预热

目标：

- 提前建立 DNS 缓存
- 尝试建立同 host 的连接池或 TLS 会话复用条件

适用：

- 已知下一首和未知下一首都可以做

局限：

- 只知道 host，不知道最终资源是否还有效
- 如果实际播放走的是另一个域名、另一个 CDN、另一个 token URL，收益会明显下降

### 5.2 URL 元数据预热

目标：

- 提前完成 redirect 解析
- 拿到 content-type、content-length、Accept-Ranges、最终 URL

适用：

- 已经拿到具体播放候选 URL，但还不想真正拉流

局限：

- 只能确认“这个 URL 看起来像可播资源”
- 不能保证点击时仍然有效

### 5.3 代理 Session 预热

目标：

- 预先创建 `SeekablePlaybackProxyServer` 的 session
- 提前准备好 localhost 播放入口

适用：

- 渐进式音频，且下一首已知

价值：

- 播放器点击下一首后可以更快进入 localhost open

### 5.4 首包 / Head Cache 预热

目标：

- 对已知下一首拉一个很小的字节区间
- 提前拿到前 32KB 到 128KB 数据

适用：

- 渐进式文件流
- 上游支持 Range
- 下一首大概率确定

价值：

- 降低 `upstream_ready` 和 demux 首开抖动

注意：

- 不建议在用户未明确形成队列时就直接拉整首下一首资源
- 不建议对 live/HLS 套用同样策略

### 5.5 播放候选级预热

目标：

- 围绕“下一首具体播放候选”做完整 warm path

这是最有价值的一层，因为真正决定体验的不是抽象域名，而是：

- 这首歌是不是已经选定
- 播放 URL 是否已解析
- 代理 session 是否已存在
- 首包是否已经在本地可读

### 5.6 当前代码对应关系

当前实现里，这五层已经和代码一一对应：

1. Host 级预热
- `HostWarmupClient`

2. URL 元数据预热
- `AudioStreamProbeApi`
- 输出最终 URL、`content-type`、`content-length`、`Accept-Ranges`

3. 代理 Session 预热
- `SeekablePlaybackProxyServer.prepareSession()`

4. 首包 / Head Cache 预热
- `SeekablePlaybackProxyServer.primeHead()`
- 当前默认 head cache 大小为 `64KB`

5. 播放候选级预热
- `PlaybackWarmupCoordinator` 选出当前业务队列中的下一首
- `NetworkWarmupEngine` 执行完整 warm path
- `NetworkPlaybackSourceResolver.resolve()` 在真正切歌时命中 warmup 产物

补充一个关键实现细节：

- prepared session 命中后，代理层必须把已缓存的 head bytes 与上游真实响应做严格字节对齐
- 不能把 head cache 和 `206` 上游返回内容重复拼接
- 当前代理已增加 `head cache skip` 对齐逻辑，避免 FFmpeg 读到错位数据

## 6. 预热的基本原则

### 6.1 冷路径和热路径必须分离

冷启动只允许：

- resolve
- 建立代理 session
- 透明转发真实请求
- native 直接消费

禁止在冷启动主路径中同步等待：

- 全量 probe
- 自动 warmup 完成
- 多次串行建链

### 6.2 预热失败不能拖垮冷启动

warm path 必须满足：

- 异步
- 可取消
- 可过期
- 失败后自动回退到 cold path

### 6.3 预热目标必须由业务队列驱动

网络层不能自己猜“下一首是谁”，它只能执行来自 `core_data` 的 warmup 计划。

## 7. 你提出的两个问题的结论

### 7.1 当播放列表只有当前一首歌时怎么办

如果当前播放列表只有这一首，且项目当前没有曲库推荐算法或推荐服务，那么结论很直接：

- 不能对“下一首歌曲资源”做真实媒体预热
- 只能做当前 host 的通用连接复用优化，或在 UI 层显示“暂无下一首”
- 如需提升“下一首”体验，应该补的是推荐/电台/相似歌曲服务，而不是盲目拉未知媒体资源

更具体地说：

- 可以做：DNS/TCP/TLS 级别的轻量 host warmup
- 可以做：在队列耗尽前异步请求推荐列表元数据
- 不应该做：提前拉一个根本尚未选定的下一首媒体 URL

### 7.2 当 Favorites 等页面是已知播放列表时怎么办

这时可以做真正有收益的预热：

1. 当前曲目进入稳定 `PLAYING` 后
2. 由 `PlaybackWarmupCoordinator` 读取业务队列中的下一首
3. 产出下一首 `PlaybackWarmupRequest`
4. 创建代理 session
5. 如适合，再做小块 head cache

也就是说：

- 已知列表场景可以做下一首预热
- 未知列表场景只做 host 级或元数据级预热

## 8. 场景化策略

### 8.1 单曲直播，队列中只有当前一首

允许：

- 当前曲目冷启动
- 当前 host 的轻量 warmup

不允许：

- 伪造下一首
- 预拉未知媒体资源

### 8.2 Favorites / Recent / Playlist / Album

允许：

- 当前曲目冷启动
- 下一首完整 warm path
- 必要时对下下一首只做 host 级 warmup

推荐策略：

- 只保留一个“完整预热的下一首”
- 更远的曲目只做低成本 warmup，避免浪费流量和连接

### 8.3 搜索结果“播放全部”

这类场景从业务上已经形成了“已知队列”，因此可以：

- 第一首 cold start
- 第二首 warm path
- 第三首只做 host 级 warmup

### 8.4 Live / HLS / 电台流

建议只做：

- DNS / TCP / TLS 级 warmup
- manifest/head 轻量探测

不建议做：

- seekable proxy head cache
- Range 首包缓存

因为 live 流的收益点不是随机 seek，而是更快完成 manifest / 首段请求。

## 9. 推荐的 warmup 架构

建议新增两层抽象：

### 9.1 `core_domain`

- `IPlaybackWarmupEngine`
- `PlaybackWarmupRequest`
- `PlaybackWarmupSnapshot`

当前说明：

- 本轮已落地 `IPlaybackWarmupEngine`
- `PreparedPlaybackCandidate` 还没有单独抽象成独立领域对象
- 热路径命中产物当前由 `NetworkPlaybackSourceResolver` 内部缓存管理

### 9.2 `core_data`

- `PlaybackWarmupCoordinator`
  - 输入：当前业务队列、当前位置、队列版本
  - 输出：要不要 warm、warm 哪一首、warm 到哪一层

### 9.3 `core_network`

- `NetworkWarmupEngine`
  - 执行 host warmup
  - 执行 URL 元数据 warmup
  - 执行代理 session 预创建
  - 执行 head cache

### 9.4 `SeekablePlaybackProxyServer`

建议演进能力：

1. `openSession()`
- 当前已有，用于冷路径

2. `prepareSession()`
- 为已知下一首建立 session，但不阻塞当前播放

3. `primeHead(range)`
- 拉小块前置缓存

4. `releasePreparedSession()`
- 在队列变化、切歌、超时后释放

5. `promotePreparedSession()`
- 在真正切歌命中 warm path 后，把 prepared session 提升成当前播放会话

## 10. 连接复用的实现注意点

如果后续要把 warm start 稳定压低，网络实现上有一个关键现实：

- 只有使用同一套 client、同一套连接池、同一套 TLS/ALPN 语义时，host 级预热才有稳定收益

这意味着：

- 预热动作不应分散在 UI 层、随机工具类和代理内部多套 client 里
- `core_network` 最终应收敛为单一可复用的 HTTP 栈

当前基于 `HttpURLConnection` 的实现能工作，但对显式连接池治理、预连接控制和精细埋点并不理想。后续若继续强化 warm path，建议统一到可控的复用型 client。

当前实现说明：

- host warmup 目前使用 `Socket / SSLSocket`
- URL 元数据和代理上游请求目前仍基于 `HttpURLConnection`
- 因此当前“host 预热收益”受多 client 现实约束，不能等价视为稳定连接池复用

## 11. Seekable 代理的职责边界

`SeekablePlaybackProxyServer` 的职责应该固定为：

1. 向播放器暴露稳定的 localhost URL。
2. 把播放器的 Range 请求翻译成上游 Range。
3. 记录上游 ready 时间、响应码和范围请求信息。
4. 对客户端断连做温和处理，不把正常切歌误记成错误。
5. 在 warm path 中可选择性持有 head cache，但不反向控制播放器状态机。

代理层不应该做的事：

- 冷启动默认等待 warmup 完成再放行
- 在 UI 层直接暴露原始网络请求细节
- 让播放器自己处理上游 `206/200/redirect`
- 擅自推断下一首是谁

## 12. 启播治理指标

网络侧必须至少能拆出这些时间点：

1. `resolve_start -> resolved`
2. `warmup_start -> warmup_ready`
3. `proxy_session_open`
4. `upstream_ready`
5. `localhost_open_success`
6. `demux_open`
7. `first_frame_rendered`

这样才能区分：

- 是 resolver 慢
- 是 warmup 没命中
- 是上游 TTFB 慢
- 还是本地播放器链路慢

当前实测已经说明：

- 很多“首播慢”其实主要卡在 `upstream_ready`
- 如果远端首包就要 `5s`，那优化播放器本地只能减少附加损耗，不能消灭上游等待

## 13. 当前日志规范

网络相关日志至少保留：

- `skip cold-start probe`
- `use seekable proxy`
- `warmup start`
- `warmup hit/miss`
- `session open`
- `prepared session ready`
- `prepared session promoted`
- `upstream ready`
- `head cache primed`
- `head cache skip`
- `serve`
- `client disconnected`
- `prepared session released`

这些日志要能让人一眼看出：

- 代理有没有参与
- 首包慢在上游还是 localhost
- warmup 命中了 host、session 还是 head cache
- seek 时 Range 是否真的命中了目标偏移

## 14. 下一步演进建议

如果目标是把 warm start 压到 `<1s`，下一步优先做：

1. 共享连接池和 host 级连接复用
2. 让 warmup 覆盖搜索“播放全部”等更多已知队列场景
3. 小块 ring buffer / head cache
4. 预热指标回流到 source 排序策略

如果目标是稳定性，优先做：

1. 代理上游请求的中断和超时治理
2. seek 后重复 Range 的合并
3. 预热资源的 TTL、取消和释放机制
4. 异常断连和正常切歌的日志降噪

当前已完成：

1. 已知队列的下一首显式预热
2. prepared session 的命中、提升和释放
3. head cache 的预取与回放字节对齐修正

## 15. 构建说明

按仓库规则，本轮文档修改后未由 Codex 执行构建或测试，需开发者自行验证。
