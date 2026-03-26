# FlexMusic

FlexMusic 是一个以 Android 为主的音频应用工程，当前已经落地的主能力包括：

- 本地与在线统一播放
- 在线搜索、搜索结果播放、加入下一首、加入队列
- 睡眠白噪声 / 氛围音 / 电台
- 音频转码
- Supabase 登录、资料编辑、头像上传

项目仍在持续迭代中，仓库文档以“当前已完成实现”为准，不把规划功能写成已支持。

## 当前模块

```text
app/                应用壳、页面、播放器 UI、睡眠 UI、轻量存储
feature_player/     Java PlayerKernel 与 JNI 桥接
feature_search/     搜索页 UI 与播放协作
feature_transcode/  Java 侧转码编排与 JNI 调用
feature_download/   预留模块壳，当前未完成
core_domain/        领域模型、抽象接口、use case
core_data/          业务编排、仓储、缓存、历史、会话管理
core_network/       搜索 provider、播放解析、在线流与 Supabase 接入
core_database/      预留模块壳，当前未完成
native/             播放与转码 native 实现
doc/                架构与功能说明
```

## 当前主要功能

### 播放

- 迷你播放器 + 全屏播放器
- 播放队列
- 顺序 / 随机 / 单曲循环 / 单曲循环次数
- 倍速
- seek
- 最近播放记录
- 本地源与在线源统一接入 native 播放链路

### 搜索

- 多 scope 搜索
- 搜索建议、历史记录、热搜入口
- 搜索结果分页
- 直接播放、播放全部、加入下一首、加入队列
- 当前默认 provider 为 `Jamendo`

### 睡眠

- 默认白噪声 / 氛围音
- 本地缓存后离线复用
- 电台精选与关键词搜索
- 定时关闭与 30 秒淡出

### 转码

- 单文件转码
- `MP3 / FLAC / OGG / WAV` 输出入口
- 输出自动导入本地音乐库

### 用户系统

- 邮箱注册
- 邮箱或用户名登录
- 会话恢复
- 头像上传
- 资料编辑

## 关键文档

- [播放器说明](doc/player.md)
- [网络模块说明](doc/network.md)
- [搜索模块说明](doc/search.md)
- [睡眠模块说明](doc/sleep.md)
- [转码模块说明](doc/transcode.md)
- [产品范围与路线图](doc/productRequirement.md)

架构图位于：

- `doc/architecture/flexmusic_arch_overview.svg`
- `doc/architecture/flexmusic_native_internals.svg`

## 配置

### 在线搜索

`core_network` 当前会读取：

- `jamendo.clientId`
- `jamendoClientId`
- `JAMENDO_CLIENT_ID`

### Supabase

- `supabase.url`
- `supabase.publishableKey`
- `supabaseUrl`
- `supabasePublishableKey`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

### Release 签名

- `release.storeFile`
- `release.storePassword`
- `release.keyAlias`
- `release.keyPassword`

推荐都放在本地 `local.properties`，不要提交到仓库。

## 当前已知缺口

- `feature_download` 仍未实现
- `core_database` 仍是模块壳
- 搜索仍是单 provider
- 还没有后台播放通知 / 锁屏控制
- 转码还没有任务队列和历史列表
