# 网络模块架构设计

## 1. 目标

`core_network` 不只是“发 HTTP 请求”，它要承担在线音频的启动治理职责：

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
- seekable 主路径必须保留 Range 语义和后续 warmup 能力

## 4. 分层职责

### 4.1 `core_network`

负责：

- 统一 HTTP client
- 请求策略
- URL probe / redirect / content-type 判断
- seekable 代理
- 网络耗时打点

不负责：

- 播放状态机
- UI 逻辑
- decoder / renderer 逻辑

### 4.2 `core_data`

负责：

- 业务级 source 选择和回退
- 本地缓存与远端结果融合
- 候选流排序

### 4.3 `core_domain`

负责：

- 抽象请求语义和用例
- 隐藏具体远端实现

### 4.4 `feature/app`

只负责：

- 用户点击
- 调用 resolver / kernel
- 展示快照

## 5. Seekable 代理的职责边界

`SeekablePlaybackProxyServer` 现在的职责应该固定为：

1. 向播放器暴露稳定的 localhost URL。
2. 把播放器的 Range 请求翻译成上游 Range。
3. 记录上游 ready 时间、响应码和范围请求信息。
4. 对客户端断连做温和处理，不把正常切歌误记成错误。

代理层不应该做的事：

- 冷启动默认等待 warmup 完成再放行
- 在 UI 层直接暴露原始网络请求细节
- 让播放器自己处理上游 `206/200/redirect`

## 6. 启播治理指标

网络侧必须至少能拆出这些时间点：

1. `resolve_start -> resolved`
2. `proxy_session_open`
3. `upstream_ready`
4. `localhost_open_success`
5. `demux_open`
6. `first_frame_rendered`

这样才能区分：

- 是 resolver 慢
- 是上游 TTFB 慢
- 还是本地播放器链路慢

当前实测已经说明：

- 很多“首播慢”其实主要卡在 `upstream_ready`
- 如果远端首包就要 `5s`，那优化播放器本地只能减少附加损耗，不能消灭上游等待

## 7. 冷启动与预热路径要分开

这是本轮设计最重要的原则之一。

### 7.1 冷启动路径

冷启动只允许：

- resolve
- 建立代理 session
- 透明转发真实请求
- native 直接消费

禁止在冷启动主路径中同步等待：

- 全量 probe
- 自动 warmup 完成
- 多次串行建链

### 7.2 预热路径

预热是单独的 warm path，后续可以做：

- 当前歌曲点击前预连接
- 下一首代理 session 预热
- 同 host 连接复用
- 小块前置缓存
- 候选源健康检查

预热如果失败，不能影响 cold path 正常播放。

## 8. 与播放器内核的协作规则

网络层和播放器内核的边界要严格：

1. 网络层负责“把远端资源变成可播放、可 seek 的输入地址”
2. 播放器内核负责“通过命令队列驱动 AVIO / demux / decode / render”
3. seek 行为由 native 读线程执行，网络层只需正确响应 Range

这意味着：

- `core_network` 不直接控制 `play/pause/seek`
- `PlayerSession` 不直接感知上游 HTTP 细节

## 9. 当前日志规范

网络相关日志至少保留：

- `skip cold-start probe`
- `use seekable proxy`
- `session open`
- `upstream ready`
- `serve`
- `client disconnected`

这些日志要能让人一眼看出：

- 代理有没有参与
- 首包慢在上游还是 localhost
- seek 时 Range 是否真的命中了目标偏移

## 10. 下一步演进建议

如果目标是把 warm start 压到 `<1s`，下一步优先做：

1. 共享连接池和 host 级连接复用
2. 当前歌曲与下一首的显式预热
3. 小块 ring buffer / head cache
4. 预热指标回流到 source 排序策略

如果目标是稳定性，优先做：

1. 代理上游请求的中断和超时治理
2. seek 后重复 Range 的合并
3. 异常断连和正常切歌的日志降噪

## 11. 构建说明

按仓库规则，本轮文档和代码修改后未由 Codex 执行构建或测试，需开发者自行验证。
