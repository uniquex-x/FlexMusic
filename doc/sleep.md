# 睡眠模块方案

## 1. 当前状态

当前 `sleep` 模块已经不再是纯静态 UI，入口代码位于：

- `app/src/main/java/com/example/flexmusicplayer/ui/SleepFragment.java`
- `app/src/main/res/layout/fragment_sleep.xml`

当前已落地能力如下：

1. 白噪声 / 氛围音播放
- `SleepPlaybackController` 已接入实际播放链路。
- `Default Mix` 与 `rainfall / ocean waves / night wind / deep forest` 四个默认氛围音都已切到“远端资源首播拉取 + 本地缓存复用”模式。
- 首次播放会把资源下载到 `files/sleep_audio_cache`，后续直接走本地文件。
- 缓存采用 LRU 风格淘汰，当前容量上限为 `80MB`。
- 白噪声播放统一复用主 `PlaybackController`、底部 mini player 与 `PlayerActivity`，不再单独维护 `MediaPlayer` 分支。
- 白噪声会话会临时切到单曲循环，退出睡眠播放后恢复用户原重复模式。

2. 睡眠计时与淡出
- 已支持 `15 / 30 / 60` 分钟定时。
- 已支持最后 `30s` 渐弱淡出。
- 倒计时由 `SleepPlaybackController` 内部 ticker 驱动，并直接作用到当前 sleep 音频音量。

3. 电台推荐、搜索与在线播放
- `FM Results` 在空查询时先直接展示 10 个静态精选且可播的 sample catalog，避免初次进入页面出现空列表。
- sample catalog 已移除不可播站点，只保留当前验证可播的 Qingting 直播源。
- 非空查询会走 `RadioRepository` 在线搜索，并与本地精选结果合并兜底。
- 电台播放已接入主播放器 native 播放链路。

4. 当前已完成的性能修复
- Sleep 页面已对播放状态做去重分发，避免 radio 进度回调触发整页重复刷新。
- 电台列表只在搜索结果变化时重建，播放态切换只更新按钮文案。
- native `FfmpegDemuxer` 已修正对 `.../live/.../*.mp3` 的误判，这类 Qingting 渐进式 MP3 现在会走低时延探测路径，减少首播探测耗时。

5. 目前仍未完成的部分
- 还没有“恢复上次 sleep 会话”的持久化恢复。
- 还没有电台收藏、最近收听、服务端官方目录聚合能力。
- 还没有“电台 + 白噪声混合垫底”的双流模式。

当前代码中的关键落地点：

- `app/src/main/java/com/example/flexmusicplayer/sleep/SleepPlaybackController.java`
- `app/src/main/java/com/example/flexmusicplayer/sleep/SleepRadioCatalog.java`
- `core_data/src/main/java/com/example/core_data/sleep/SleepAudioRepository.java`
- `core_network/src/main/java/com/example/core_network/sleep/SleepAudioRemoteService.java`

## 2. 目标

本方案覆盖两个能力：

1. 默认白噪声播放
- 用户进入睡眠模块后，可以一键播放默认白噪声。
- 默认白噪声在无网环境下也可播放。
- 支持循环、淡入淡出、睡眠定时、音量控制。

2. 电台搜索 + 联网在线播放
- 支持搜索中国国内可免费收听的电台频道。
- 支持频道详情展示、点击即播、最近收听、收藏。
- 支持 HTTP / HTTPS 在线流播放。
- 支持流可用性检查和播放失败自动切换候选流。

这里有一个需要明确的工程事实：

- “支持搜索中国国内免费收听的所有电台频道”不能只依赖单一第三方公开 API。
- 要想把覆盖率、可播放率和合规性都做稳，必须采用“官方名录/官方入口 + 自建电台目录 + 流健康检查”的方案。

## 3. 总体设计

睡眠模块建议拆成两个子系统：

1. 本地睡眠音频子系统
- 负责默认白噪声、氛围音循环、定时关闭、淡出。
- 以本地资源为主，不依赖网络。

2. 在线电台子系统
- 负责国内电台目录聚合、搜索、在线播放、流健康检查。
- 以服务端目录为核心，App 只做查询和播放。

建议的模块边界：

### App/UI 层
- `SleepFragment`
- 后续新增 `SleepViewModel`
- 白噪声列表、定时器 UI、电台搜索 UI

### 领域层
- `SleepSound`
- `SleepPreset`
- `SleepTimerState`
- `RadioStation`
- `RadioStream`
- `SleepPlaybackSession`

### 数据层
- `SleepRepository`
- `RadioCatalogRepository`
- `RecentSleepStore`
- `FavoriteRadioStore`

### 播放层
- `SleepPlaybackController`
- `WhiteNoiseEngine`
- `RadioPlaybackEngine`

### 服务端
- `radio-catalog-service`
- `radio-stream-validator`
- `radio-search-index`

## 4. 默认白噪声播放方案

## 4.1 方案目标

默认白噪声的第一原则不是“可调很多参数”，而是：

- 秒开
- 离线可用
- 无缝循环
- 低功耗
- 睡眠场景下稳定不中断

## 4.2 推荐实现

默认白噪声不要在进入页面后实时混 4 路音频，而是直接提供一个预置好的本地默认混音资源：

- `sleep_default_mix.ogg` 或 `sleep_default_mix.m4a`

推荐默认配比：

- 雨声 45%
- 海浪 25%
- 风声 15%
- 森林 15%

推荐素材要求：

- 单文件长度 10 到 20 分钟
- 首尾可无缝循环
- 采样率 44.1kHz 或 48kHz
- 双声道
- 峰值留足余量，避免夜间刺耳

这样做的好处：

1. 首次播放快
- 不需要运行时做 4 路混音。

2. 功耗低
- 单流循环比多解码器并行更省电。

3. 风险低
- 比实时混音更不容易出现不同设备上的相位、爆音、音量漂移问题。

## 4.3 氛围音卡片的实现策略

对于界面上的雨声/海浪/风声/森林，有两种阶段方案。

### P0：先把默认体验做稳
- 卡片点击即切换到单独的本地循环资源。
- 例如：
  - `rain_loop.ogg`
  - `ocean_loop.ogg`
  - `wind_loop.ogg`
  - `forest_loop.ogg`

### P1：再做多轨混音
- 如果后续要支持同时开启多种氛围音并调整滑杆音量，再引入真正的多轨混音引擎。

推荐顺序：

1. 默认混音单流播放先上线
2. 单卡片单流切换上线
3. 多轨混音最后做

## 4.4 播放内核建议

白噪声与电台虽然都属于音频播放，但职责不同，不建议直接复用当前音乐播放器页面逻辑。

推荐：

1. 默认白噪声/单轨氛围音
- 使用 `Media3 ExoPlayer`
- 原因：
  - 对本地循环资源稳定
  - 对 HLS/HTTP 流也适合
  - 后续和电台在线播放可统一内核

2. 多轨混音
- P1 先可接受使用多个 `ExoPlayer` 实例分别 loop，本地做独立音量控制
- P2 如果功耗和同步表现不够，再切到 native mixer

原因：

- 当前仓库虽然已放入 FFmpeg/FLAC/mp3lame/oggVorbis，但还没有完整 JNI 播放桥。
- 睡眠模块首版目标是稳定上线，不应一开始就把多轨混音完全绑定到 native。

## 4.5 睡眠定时与淡出

建议新增 `SleepTimerStateMachine`：

- `Idle`
- `Running`
- `FadingOut`
- `Stopped`

规则：

1. 用户选择 15m / 30m / 60m / 自定义后启动倒计时
2. 到时前 30 秒进入 `FadingOut`
3. 每秒降低一次主音量
4. 结束后停止播放器并释放焦点

建议将定时信息持久化，避免前后台切换后丢失：

- `endAtEpochMs`
- `fadeOutEnabled`
- `currentSessionType`

## 4.6 默认白噪声的业务行为

推荐默认交互：

1. 进入 `sleep` 页不自动外放
- 避免用户误触后突然出声。

2. 提供一个明显的“开始睡眠”主按钮
- 点击后播放 `sleep_default_mix`

3. 若用户上次离开时仍在听睡眠音
- 下次进入页面恢复上次状态和剩余定时

4. 若用户切换到电台
- 默认暂停白噪声
- 可选配置为“电台主播放 + 白噪声轻音垫底”，但这一能力放到 P2

## 5. 国内免费电台搜索 + 在线播放方案

## 5.1 为什么不能只接一个第三方接口

“全国免费电台可搜可播”有三个难点：

1. 覆盖率
- 中国国内广播体系规模大、地区分散、台名和频率经常存在别名。

2. 可播放率
- 很多公开目录里存在失效流、跳转流、临时维护流。

3. 合规性
- 不能把未授权、付费专享、带 DRM 的私有流当成“免费电台”直接接入。

因此推荐方案不是“客户端直接搜某个公共 API”，而是：

- 自建电台目录
- 以官方目录和官方在线收听入口为主
- 以公共广播目录作为补充发现与健康检查

## 5.2 数据源分层

### A. 权威覆盖源

用途：

- 确定“有哪些合法播出机构和频率”
- 作为电台主数据的基座

建议来源：

1. 国家广播电视总局播出机构名录/频道频率名录
2. 国家广播电视总局行业统计公报

这些数据适合做：

- 台站合法性校验
- 省市区划归属
- 台名标准化

不适合直接做：

- 在线流地址来源

## 5.3 电台在线播放源

在线播放地址建议优先级：

1. 电台官方站点公开直播页
2. 官方聚合入口
   - 例如云听官网已有“电台”“电台直播”入口
3. 省级/地市级台官网公开直播地址
4. 公共广播目录补充源
   - 例如 Radio Browser 仅作为补充发现与可用性参考，不作为唯一真源

关键原则：

- 只接入“可免费收听”的公开流
- 不依赖需要登录、签名、DRM 或私有逆向的封闭接口作为首选方案

## 5.4 服务端总体架构

### 服务一：`radio-catalog-service`

职责：

- 聚合官方台站目录
- 维护标准电台主数据
- 提供搜索接口

核心字段：

- `stationId`
- `stationName`
- `aliasNames`
- `province`
- `city`
- `frequency`
- `category`
- `language`
- `homepage`
- `isOfficial`
- `isFreeToListen`

### 服务二：`radio-stream-validator`

职责：

- 定时检查候选流是否可播
- 解析 `m3u` / `pls` / `xspf` / 跳转链
- 记录 codec、bitrate、hls、时延、最后成功时间

核心字段：

- `streamId`
- `stationId`
- `sourceType`
- `streamUrl`
- `resolvedUrl`
- `format`
- `codec`
- `bitrate`
- `isHls`
- `isAlive`
- `lastCheckedAt`
- `priority`

### 服务三：`radio-search-index`

职责：

- 中文分词搜索
- 拼音搜索
- 台名别名搜索
- 省市筛选、分类筛选

推荐：

- 后端数据库 + FTS 即可起步
- 若数据量和复杂度继续增长，再接 Elasticsearch / OpenSearch

## 5.5 App 侧联网与播放流程

### 搜索流程

1. 用户在 `sleep` 页输入关键词
2. App 调用后端 `/radio/search`
3. 后端返回标准化后的 `RadioStation` 列表
4. 列表按：
- 精确匹配优先
- 官方源优先
- 最近成功流优先
- 热门/最近收听加权

### 播放流程

1. 用户点击某电台
2. App 调用 `/radio/stations/{id}/play`
3. 后端返回：
- 首选流
- 候选流列表
- codec / bitrate / isHls
4. `RadioPlaybackEngine` 先播首选流
5. 若超时或失败，自动切换候选流

### 推荐接口

1. `GET /radio/search?q=&province=&city=&category=&page=`
2. `GET /radio/stations/{stationId}`
3. `GET /radio/stations/{stationId}/play`
4. `GET /radio/recommendations`
5. `POST /radio/stations/{stationId}/recent`

## 5.6 搜索模型设计

建议至少支持以下搜索维度：

1. 台名
- 中国之声
- 音乐之声
- 北京交通广播

2. 别名
- FM、AM、频道简称、历史台名

3. 地区
- 北京、上海、广东、成都

4. 频率
- FM99.3
- AM639

5. 类型
- 新闻
- 交通
- 音乐
- 文艺
- 都市
- 经济
- 少儿

6. 语言
- 普通话
- 粤语
- 藏语
- 维语

为了提升中文体验，建议索引中保存：

- 原始台名
- 拼音全拼
- 拼音首字母
- 频率规范化字段

## 5.7 在线播放内核建议

电台播放推荐直接使用 `Media3 ExoPlayer`，理由：

1. 对 HLS 更稳
2. HTTP/HTTPS 重定向处理比 `MediaPlayer` 更好控
3. 便于监听 buffering / error / metadata
4. 后续可加 cache、retry、track selection

推荐支持的流类型：

- MP3
- AAC / AAC+
- HLS `m3u8`
- 经过解析后的 `m3u` / `pls`

失败重试策略：

1. 首流 6 秒未进入 `READY`，切下一候选
2. 同一流连续失败 3 次，标记短期熔断
3. 候选全部失败时提示用户，并上报失败样本

## 5.8 为什么要服务端代理目录而不是客户端直连所有源

推荐 App 只直连音频流，不直连所有目录源，原因如下：

1. 目录统一
- 各台站命名不一致，服务端更适合做规范化。

2. 搜索质量
- 中文别名、拼音、地区合并更适合在服务端处理。

3. 稳定性
- 流健康检查和失效剔除不适合放在客户端做。

4. 合规控制
- 服务端可以维护白名单，只返回已确认的免费公开流。

5. 便于补源
- 某些台站流地址调整后，只需改后端目录，不必发版。

## 5.9 合规边界

这一块必须写清楚：

1. 只收录免费公开收听流
- 优先接官方网页公开直播地址。

2. 不把付费会员专享内容混入免费目录

3. 不默认依赖抓包/逆向私有 App 接口
- 除非后续拿到明确授权或合作。

4. 对每个台站记录来源类型
- `OFFICIAL_SITE`
- `OFFICIAL_AGGREGATOR`
- `PUBLIC_DIRECTORY`
- `MANUAL_VERIFIED`

5. 对用户展示“来源已验证/官方来源”标签

## 6. 与现有项目的接入建议

## 6.1 建议新增的数据模型

建议放在 `core_domain/`：

```java
public class RadioStation {
    public String stationId;
    public String stationName;
    public List<String> aliasNames;
    public String province;
    public String city;
    public String frequency;
    public String category;
    public boolean official;
    public boolean freeToListen;
}
```

```java
public class RadioStream {
    public String streamId;
    public String stationId;
    public String streamUrl;
    public String resolvedUrl;
    public String codec;
    public int bitrate;
    public boolean hls;
}
```

## 6.2 建议新增的仓库接口

建议放在 `core_data/` 或先临时放 `app/`：

```java
public interface RadioRepository {
    List<RadioStation> search(String query, String province, String category);
    RadioStation getStation(String stationId);
    List<RadioStream> getPlayableStreams(String stationId);
}
```

```java
public interface SleepRepository {
    SleepPreset getDefaultPreset();
    SleepTimerState loadTimerState();
    void saveTimerState(SleepTimerState state);
}
```

## 6.3 建议新增的播放控制器

建议在 `app/src/main/java/com/example/flexmusicplayer/player/` 或未来 `feature_player` 中新增：

- `SleepPlaybackController`
- `RadioPlaybackEngine`
- `WhiteNoiseEngine`

职责拆分：

1. `WhiteNoiseEngine`
- 负责本地睡眠音频 loop、音量、淡出、恢复

2. `RadioPlaybackEngine`
- 负责在线流准备、切流、重试、错误状态

3. `SleepPlaybackController`
- 统一给 UI 暴露：
  - playDefaultMix()
  - playAmbience(soundId)
  - playRadio(stationId)
  - stop()
  - setTimer(...)
  - setVolume(...)

## 6.4 与当前音乐播放器的关系

不建议直接让睡眠白噪声和主播放器共享同一套队列语义。

建议：

1. 控制层复用一套状态分发思路
2. 播放会话分开
- `MusicSession`
- `SleepSession`

3. 音频焦点统一管理
- 从音乐页切到睡眠电台时，明确是否暂停主音乐

默认建议：

- 睡眠模块开始播放时暂停主音乐
- 退出睡眠模块不自动恢复主音乐

## 7. 分阶段落地计划

## P0：先交付可用版本

范围：

- 默认白噪声本地播放
- 雨/海浪/风/森林单轨循环
- 睡眠定时
- 最近播放记录
- 电台搜索先接自建 mock API 或固定样本数据

产出：

- UI 不再是占位
- 睡眠场景离线可用

## P1：接入真实国内免费电台搜索

范围：

- 服务端目录
- 中文搜索
- 播放候选流切换
- 官方/非官方来源标识
- 收藏电台、最近收听

产出：

- 可搜索、可播放、可维护的国内电台目录

## P2：增强体验

范围：

- 白噪声与电台叠播
- 多轨实时混音
- 智能推荐
- 网络缓存和故障诊断面板

## 8. 最终建议

结论很明确：

1. 默认白噪声
- 先用本地预渲染默认混音资源做单流循环，这是最快、最稳、最省电的方案。

2. 国内免费电台搜索
- 必须走“官方目录/官方入口 + 自建目录服务 + 流校验”的架构。
- 不能把某个第三方公共目录当成唯一真源。

3. 在线播放
- App 侧建议统一切到 `Media3 ExoPlayer` 做睡眠音和电台播放。
- 当前项目里的 `MediaPlayer` 可继续保留给现有音乐功能，睡眠模块独立演进。

4. 落地顺序
- 先做本地白噪声和定时
- 再做电台目录服务
- 最后再做混音增强

## 9. 参考信息

以下资料用于支撑本方案中的“覆盖范围、官方入口、公共目录能力”判断：

1. 国家广播电视总局《2024年全国广播电视行业统计公报》
- https://www.nrta.gov.cn/art/2025/5/9/art_113_70729.html

2. 云听官网，页面含“电台”“电台直播”入口
- https://www.radio.cn/pc-portal/home/index.html

3. Radio Browser 官方 API 文档
- https://api.radio-browser.info/

4. Radio Browser API Reference
- https://docs.radio-browser.info/

5. 国家广播电视总局关于播出机构名录按季度更新的公开说明
- https://www.nrta.gov.cn/art/2021/1/30/art_24_54977.html
