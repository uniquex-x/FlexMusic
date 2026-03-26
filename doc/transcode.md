# 转码模块现状说明

## 1. 当前实现范围

转码功能已经具备一条可用主链路，分层如下：

- `app/src/main/java/com/example/flexmusicplayer/ui/TranscodeFragment.java`
  - 文件选择
  - 结果态展示
  - 成功后刷新本地曲库
- `feature_transcode/src/main/java/com/example/feature_transcode/`
  - 输入文件 staging
  - 输出文件路径选择
  - JNI 调用
- `native/transcode/`
  - native decode / encode 实现

## 2. 当前转码流程

当前流程：

1. 用户在 `TranscodeFragment` 选择单个音频文件
2. `TranscodeRepository` 将输入复制到 `cache/transcode_input/`
3. `TranscodeBridge` 调 native 转码
4. 输出写入 app-scoped external music 目录下的 `transcoded/`
5. 成功后通过 `LocalMusicStore` 导入本地曲库

当前 UI 只展示当前任务，不保留历史成功/失败列表。

## 3. 当前支持格式

当前 UI 暴露：

- `MP3`
- `FLAC`
- `OGG`
- `WAV`

说明：

- `OGG` 当前指 Vorbis in Ogg
- `WAV` 直接写 PCM 容器
- `MP3 / FLAC / OGG` 依赖 native 三方库

文档约束：

- 只有目标 ABI 上对应 encoder 可用时，才能把该格式视为“可支持”
- 如果某 ABI 缺少匹配库，不应在发布文档里宣称该格式对所有 ABI 都可用

## 4. 当前存储语义

输入：

- 源文件不会直接在原 URI 上原地转码
- 会先复制到 `cache/transcode_input/`

输出：

- 默认写入 `getExternalFilesDir(Environment.DIRECTORY_MUSIC)/transcoded/`
- 文件命名采用 `<base>_converted.<ext>`
- 如重名会自动追加序号

导入：

- 转码完成后会自动加入 `LocalMusicStore`
- 因此会出现在本地音乐页面

## 5. 当前关键代码

- `app/src/main/java/com/example/flexmusicplayer/ui/TranscodeFragment.java`
- `feature_transcode/src/main/java/com/example/feature_transcode/TranscodeRepository.java`
- `feature_transcode/src/main/java/com/example/feature_transcode/TranscodeBridge.java`
- `feature_transcode/src/main/java/com/example/feature_transcode/TranscodeSettings.java`
- `native/transcode/*`

## 6. 当前限制

- 仅支持单文件转码
- 没有转码队列
- 没有历史任务列表
- 没有进度百分比和阶段细分，只展示处理中/完成
- 没有批量参数模板

## 7. 日志要求

转码相关改动应保留以下日志：

- 转码开始：源 URI、staged path、输出格式、输出路径
- 转码成功：最终输出路径
- 转码失败：首个失败阶段和 native 错误
