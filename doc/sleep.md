# 睡眠模块现状说明

## 1. 当前实现范围

睡眠模块已经从 UI 壳发展为可用功能，当前入口：

- `app/src/main/java/com/example/flexmusicplayer/ui/SleepFragment.java`
- `app/src/main/java/com/example/flexmusicplayer/sleep/SleepPlaybackController.java`

当前已实现：

- 默认白噪声 / 氛围音播放
- 氛围音缓存后离线复用
- 睡眠定时
- 30 秒淡出
- 电台精选列表
- 电台关键词搜索
- 电台在线播放
- 与主播放器联动

## 2. 当前播放模型

### 2.1 白噪声 / 氛围音

当前支持的睡眠音：

- `DEFAULT_MIX`
- `RAIN`
- `OCEAN`
- `WIND`
- `FOREST`

实现方式：

- `SleepAudioRepository` 负责缓存和解析播放源
- 首次播放远端下载到本地缓存目录
- 后续优先使用缓存文件
- 最终通过 `PlaybackController` 播放本地音频

当前不是运行时多轨混音，而是“单一睡眠音源播放”。

### 2.2 电台

当前实现包含两部分：

- 本地精选台站：`SleepRadioCatalog`
- 在线搜索：`RadioRepository`

行为：

- 空关键词时展示精选台站
- 非空关键词时走在线搜索
- 在线结果与本地精选结果合并兜底
- 点击站点后接入主播放器 native 播放链路

## 3. 当前状态管理

`SleepPlaybackController` 维护：

- 当前 session 类型
- 当前播放中的氛围音或电台
- loading / playing 状态
- 剩余定时秒数
- fade out 开关
- 当前音量缩放

当前还做了这些控制：

- 启动睡眠音前会暂停主音乐
- 睡眠氛围音会临时切成单曲循环
- 退出睡眠音后恢复原播放模式
- 定时器在最后 30 秒按比例降低音量

## 4. 当前关键代码

- `app/src/main/java/com/example/flexmusicplayer/ui/SleepFragment.java`
- `app/src/main/java/com/example/flexmusicplayer/sleep/SleepPlaybackController.java`
- `app/src/main/java/com/example/flexmusicplayer/sleep/SleepRadioCatalog.java`
- `core_data/src/main/java/com/example/core_data/sleep/SleepAudioRepository.java`
- `core_network/src/main/java/com/example/core_network/sleep/SleepAudioRemoteService.java`
- `core_data/src/main/java/com/example/core_data/radio/RadioRepository.java`

## 5. 当前已知限制

以下能力尚未完成：

- 恢复上次 sleep 会话
- 电台收藏与最近收听的完整闭环
- 官方电台目录聚合
- 电台流健康巡检
- “电台 + 白噪声垫底”双流模式
- 真正多轨混音

因此当前睡眠模块应视为：

- P0 可用版睡眠播放
- 不是完整的睡眠音频平台

## 6. 当前维护原则

后续改动时保持以下边界：

- 睡眠页 UI 放在 `app/ui`
- 睡眠播放控制放在 `app/sleep`
- 白噪声资源缓存和远端获取放在 `core_data/core_network`
- 电台搜索与台站数据继续走 repository，不在 Fragment 里直接发请求
