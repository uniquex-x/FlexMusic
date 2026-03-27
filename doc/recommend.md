# 推荐模块落地方案

## 1. 当前问题

当前首页推荐区已经有基础 UI 和 `core_recommend -> native` 的骨架，但还不具备真正可用的推荐能力。

当前缺口主要有：

- 推荐卡数据是静态拼装，不来自真实候选池
- 点击推荐卡和浏览分类后无法进入内容页
- 没有“推荐集合 -> 曲目列表 -> 播放”的闭环
- 没有缓存、降级、去重和刷新策略
- native 目前只是静态返回 payload，还没有承担真正的排序与混排职责

当前入口：

- `app/src/main/java/com/example/flexmusicplayer/ui/MainPageFragment.java`
- `core_recommend/`
- `native/recommend/`

因此当前推荐功能应被视为“UI 骨架 + 接口占位”，不是可交付的发现能力。

## 2. 可用版目标

推荐功能达到“可用”至少需要满足以下标准：

### 2.1 首页

- `Daily Recommend / Trending / Top List` 三个 tab 能拉到真实内容
- `Daily Recommend` 默认展示两张可点击推荐卡
- `Trending / Top List` 切换后展示各自的推荐卡
- `流行 / 摇滚 / 电子 / 全球热歌 / 查看全部` 都可进入列表页

### 2.2 详情页

- 点击推荐卡可进入推荐集合详情页
- 点击浏览分类可进入分类推荐列表页
- 详情页至少支持：
  - 单曲点击播放
  - 播放全部
  - 加入下一首
  - 加入队列

### 2.3 数据体验

- 冷启动无网络时仍能展示最近一次缓存的推荐结果
- provider 请求失败时有明确降级，不出现整块空白
- 同一批推荐中尽量避免重复曲目和重复歌手过多

### 2.4 性能

- 首页推荐接口首屏目标：1.5 秒内返回首屏可渲染数据
- 推荐详情页目标：1 秒内展示缓存或 loading skeleton，3 秒内完成在线刷新

## 3. 推荐范围定义

P0 可用版只做“首页推荐发现”，不做完整平台级个性化系统。

P0 范围：

- 首页三类推荐 tab
- 两张日推卡
- 浏览分类入口
- 推荐集合详情页
- 推荐曲目直接播放
- 基础缓存与降级
- 基础个性化排序

P0 不做：

- 完整服务端画像系统
- 用户登录态跨设备推荐同步
- 大规模协同过滤
- 推荐理由文案生成
- 实时 AB 平台

## 4. 建议分层

推荐功能应继续遵守仓库当前的网络分层规则，不应让 UI 直接拼 provider 请求。

### 4.1 `app`

负责：

- 首页推荐区渲染
- 推荐详情页 / 分类页 UI
- 用户点击事件上报
- 触发播放和队列操作

建议新增：

- `app/ui/RecommendCollectionFragment.java`
- `app/ui/RecommendCategoryFragment.java`
- `app/ui/adapter/RecommendTrackAdapter.java`

### 4.2 `core_recommend`

负责：

- 对外暴露推荐模块统一接口
- 定义推荐领域模型
- 对接 native 排序内核 JNI bridge
- 组织首页 feed 和详情页 use case 的公共入口

建议放在这里的核心抽象：

- `IRecommendRepository`
- `GetHomeRecommendFeedUseCase`
- `GetRecommendCollectionUseCase`
- `GetRecommendCategoryFeedUseCase`
- `RecommendHomeFeed`
- `RecommendCollection`
- `RecommendCollectionEntry`
- `RecommendTrackItem`
- `RecommendRequestContext`
- `RecommendScoreResult`

说明：

- `core_recommend` 是推荐模块对 `app` 的统一出口
- 不应在这里直接写第三方 HTTP 逻辑

### 4.3 `core_data`

负责：

- 推荐业务编排
- 缓存和刷新策略
- 候选池混合
- provider 失败时的降级
- 本地行为数据读取

建议新增包：

- `core_data/src/main/java/com/example/core_data/recommend/`

建议类：

- `RecommendRepositoryImpl`
- `RecommendCacheStore`
- `RecommendSeedStore`
- `RecommendBehaviorStore`
- `RecommendFeedAssembler`
- `RecommendFallbackPolicy`

### 4.4 `core_network`

负责：

- 远端推荐种子获取
- 热门榜单 / 分类列表 / 编辑精选 API 适配
- HTTP policy 与 DTO 解析

建议新增包：

- `core_network/src/main/java/com/example/core_network/recommend/`

建议类：

- `RecommendRemoteService`
- `RecommendPlaylistDto`
- `RecommendTrackDto`
- `RecommendCollectionDto`
- `RecommendEndpointResolver`

如果 P0 不引入新的推荐服务端，也至少要在 `core_network` 中增加以下可复用能力：

- 拉取热门曲目列表
- 拉取热门歌单列表
- 按预设关键词或标签拉取分类候选池

### 4.5 `native/recommend`

负责：

- 本地排序
- 去重
- 多样性控制
- 简单混排
- 冷启动个性化权重计算

不负责：

- HTTP 请求
- provider 认证
- Android 存储
- UI 文案

native 应该是推荐“内核”，不是推荐“数据源”。

## 5. 建议数据流

P0 推荐主链路建议如下：

`MainPageFragment -> core_recommend facade -> core_data/recommend -> core_network + local stores -> native/recommend scoring -> core_recommend models -> UI`

进一步拆开：

1. UI 请求首页推荐 feed
2. `core_recommend` 组装 `RecommendRequestContext`
3. `core_data/recommend` 读取：
   - 最近播放
   - 喜欢歌曲
   - 搜索历史
   - 上次推荐缓存
4. `core_network/recommend` 拉取远端候选池
5. `native/recommend` 对候选池做打分、去重和多样性重排
6. `core_data/recommend` 生成：
   - 首页 tab feed
   - 推荐卡内容
   - 浏览分类入口
7. UI 渲染结果
8. 用户点击某个集合后，再请求集合详情页数据
9. 详情页曲目点击播放时复用现有：
   - `TrackPlaybackRepository`
   - `PlaybackController`

## 6. 推荐内容的具体定义

为了让首页不是空泛的“随机歌单入口”，P0 先把每个区域定义清楚。

### 6.1 Daily Recommend

包含两张卡：

- 卡 1：`Based on Your Recent Plays`
  - 基于最近播放、最近搜索、收藏偏好
  - 候选池从历史偏好相关关键词和热门曲目混合得到
- 卡 2：`Fresh Picks For You`
  - 基于热门池中未近期播放的内容
  - 偏探索，避免和卡 1 完全重叠

点击后进入对应推荐集合详情页。

### 6.2 Trending

包含两张卡：

- `Trending Now`
- `Fast Rising`

候选来源：

- provider 热门曲目
- provider 热门歌单
- 本地短周期热度缓存

核心目标：

- 强调当下热度
- 允许弱个性化，但不要把热门做成“私人日推”

### 6.3 Top List

包含两张卡：

- `Global Chart`
- `Indie Breakout` 或 `Editor Picks`

候选来源：

- 固定榜单种子
- 服务端配置的榜单集合

核心目标：

- 内容稳定
- 可解释
- 每天刷新

### 6.4 Browse Categories

分类：

- `Pop`
- `Rock`
- `Electronic`
- `Global Hits`
- `View All`

点击后进入分类页，分类页至少包含：

- 头部标题
- 推荐曲目列表
- 可选的推荐歌单分区
- 播放全部按钮

## 7. P0 推荐数据来源方案

为了尽快做出“可用版”，P0 不要求新建完整推荐后端，但要避免纯静态假数据。

### 7.1 首选方案

复用当前已存在的搜索/播放基础设施：

- `MusicSearchService`
- `OnlineSearchRepository`
- `TrackPlaybackRepository`

扩展方向：

- 在 `core_network/recommend` 中新增面向推荐的 provider 适配
- 从 provider 拉：
  - 热门曲目
  - 热门歌单
  - 预设标签候选池

### 7.2 P0 候选池构造

建议按以下方式构造：

- `daily_recommend`
  - 最近播放歌手/关键词相关候选 50%
  - 全站热门候选 30%
  - 探索候选 20%
- `trending`
  - 热门候选 70%
  - 快速上升候选 30%
- `top_list`
  - 固定榜单候选 100%
- `browse category`
  - 由类别种子词或标签单独拉取

### 7.3 当前 provider 能力利用

现有搜索链路已经支持：

- 曲目搜索
- 热门关键词加载
- 播放候选解析

因此 P0 可以先利用现有 provider 能力做“关键词种子推荐”，例如：

- `Pop`
  - 流行相关关键词 / 热门结果
- `Rock`
  - 摇滚相关关键词 / 热门结果
- `Electronic`
  - 电子相关关键词 / 热门结果
- `Global Hits`
  - 热门榜单或 `popularity_total` 排序结果

这不是最终形态，但足够支撑一个真正能点进、能播放、能刷新的推荐功能。

## 8. native 内核应承担的具体职责

native 不应该再只返回静态 JSON，而应承担以下真实计算任务。

### 8.1 输入

- 候选曲目列表
- 本地行为摘要
  - 最近播放次数
  - 最近跳过次数
  - 收藏偏好
  - 最近搜索关键词
- 当前场景
  - 首页 tab 类型
  - 时间段
  - 冷启动 / 热启动

### 8.2 输出

- 候选排序分值
- 去重后的曲目顺序
- 每张卡对应的集合选择结果
- 可选的推荐原因码
  - `RECENT_PLAY_BIAS`
  - `TRENDING_BOOST`
  - `DIVERSITY_PROTECTION`
  - `EXPLORE_INJECTION`

### 8.3 核心算法

P0 不需要复杂模型，建议先做规则型排序：

- 最近播放相关度加权
- 最近 7 天已播放过多的曲目衰减
- 同歌手连续出现惩罚
- 同专辑连续出现惩罚
- 探索比例注入
- 长短时偏好混合

这样就能让 native 真正承担“排序内核”角色，而不是继续做静态配置容器。

## 9. 缓存与降级

推荐功能不能只依赖在线成功，否则体验会很脆弱。

### 9.1 缓存层

建议缓存三类数据：

- 首页 feed 缓存
- 推荐集合详情缓存
- 分类页缓存

缓存粒度建议：

- 首页 feed：15 分钟
- Trending：10 分钟
- Top List：6 小时
- Daily Recommend：24 小时
- 分类页：30 分钟

### 9.2 降级顺序

首页请求失败时：

1. 返回新鲜缓存
2. 返回过期缓存并提示刷新失败
3. 返回本地静态降级卡片

详情页请求失败时：

1. 返回缓存曲目
2. 返回空列表 + 错误提示

禁止：

- 网络失败后直接整页空白
- 首页 tab 点击后完全无响应

## 10. 交互落地方案

### 10.1 首页点击

- 点击卡片：进入 `RecommendCollectionFragment`
- 点击分类：进入 `RecommendCategoryFragment`
- 点击 `View All`：进入 `RecommendCategoryFragment` 的总览模式

### 10.2 详情页能力

P0 必须支持：

- `Play All`
- 单曲点击播放
- 加入下一首
- 加入队列

实现时继续复用已有搜索播放链路，不要重新发明播放器入口。

### 10.3 推荐集合页 UI

建议信息结构：

- 顶部标题
- 副标题
- 封面或头图
- 播放全部按钮
- 曲目列表

## 11. 日志要求

推荐功能改动必须保留以下关键日志：

- 首页 feed 请求开始：tab、刷新原因、缓存命中状态
- 候选池装配完成：候选数量、来源占比
- native 排序完成：输入数量、输出数量、耗时
- 详情页加载完成：collectionId、trackCount、数据来源
- 播放点击：collectionId、trackId、providerId
- 降级发生：失败阶段、是否命中缓存

不要记录逐条候选的高频 spam 日志，但要保留阶段边界和首个失败点。

## 12. 分阶段实施计划

### 阶段 1：先打通可点击闭环

目标：

- 首页卡片和分类都能进入列表页
- 列表页曲目可播放
- 首页不再是纯静态不可点击

改动：

- 新增推荐详情页和分类页
- `core_recommend` 输出集合 id
- 详情页先可读取静态或缓存集合

这个阶段完成后，推荐功能从“展示型占位”升级到“可交互功能”。

### 阶段 2：接入真实候选池

目标：

- 推荐卡内容来自真实 provider 候选
- 分类页内容支持在线刷新

改动：

- `core_network/recommend` 新增 provider 适配
- `core_data/recommend` 新增候选池装配
- 首页 feed 改为真实数据 + 缓存

### 阶段 3：native 排序内核上线

目标：

- 日推不再只是热门集合换皮
- 推荐结果有基础个性化

改动：

- native 输入从静态配置切到候选池打分
- 接入最近播放、收藏、搜索历史摘要
- 增加去重和多样性控制

### 阶段 4：质量与运营能力

目标：

- 推荐稳定可维护
- 内容可配置

改动：

- 增加远端种子配置
- 增加实验开关
- 增加命中率和点击率埋点

## 13. 关键结论

要让推荐功能真正可用，重点不是继续扩首页卡片，而是补齐这三件事：

- 让卡片和分类有真实可进入的详情页
- 让详情页里的曲目可以直接播放
- 让推荐结果来自真实候选池，并通过 native 做排序和混排

推荐功能的最终边界应是：

- `core_network` 负责远端候选获取
- `core_data` 负责缓存、降级和装配
- `core_recommend` 负责对外接口和推荐领域抽象
- `native/recommend` 负责排序内核
- `app` 负责渲染和交互

如果只停留在“首页展示几张静态卡片”，这个模块仍然不可用；只有补齐“进入列表页 + 可播放 + 可刷新 + 可降级”的闭环，才算真正拥有推荐功能。
