![Next Player banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# 小蜗牛播放器 (LittleSnail Player)

> 基于 [Next Player](https://github.com/anilbeesetti/nextplayer) 修改的 Android 视频播放器，面向「家长希望孩子看视频时不能随便快进」的使用场景。

本项目是 [Next Player](https://github.com/anilbeesetti/nextplayer) 的衍生作品（fork / modification），在保留其全部基础播放能力的同时，加入了**防快进**机制，并提供断点续播、去除画中画等适配。

---

## 项目简介

- **一句话定位**：给孩子用的「不能快进」视频播放器。
- **许可**：与原项目一致，遵循 **GNU GPL-3.0**（详见 `LICENSE`）。
- **当前版本**：v1.0.0（`applicationId` 沿用 `dev.anilbeesetti.nextplayer`，可直接覆盖安装原版）。

---

## 与原版 Next Player 的核心差异

| # | 改动 | 说明 |
|---|------|------|
| 1 | **禁止频繁快进（核心）** | 每次快进需间隔 **2 分钟**；冷却期内拖动进度条会被拦截——播放位置不变、进度条自动回弹，并弹 Toast 提示「距离下次可快进还需等待 X 秒」。 |
| 2 | **跨重进持久冷却** | 冷却计数保存在应用级静态变量，退出视频再进入、甚至切换到其他视频，2 分钟内仍不可快进。 |
| 3 | **断点续播不受冷却影响** | 重新打开看过的视频会自动从断点继续；续播跳转直接作用于底层播放器，绕开节流逻辑（不刷新、不触发冷却）。 |
| 4 | **去除画中画（PiP）悬浮窗** | 移除 PiP 按钮及 `AndroidManifest` 中的 `supportsPictureInPicture` 配置。 |
| 5 | **应用更名** | 应用名称改为「小蜗牛播放器」。 |

实现核心见 `feature/player/.../service/ThrottledSeekPlayer.kt`：通过 `ForwardingSimpleBasePlayer` 包裹真实 `ExoPlayer`，覆写 `handleSeek` 实现节流与回弹提示。

---

## 继承 Next Player 的功能

- 本地视频与 **WebDAV** 网络视频播放
- 播放列表 / 历史记录
- 字幕、音轨切换、手势调节
- 断点续播（按视频 URI 记忆播放位置，含网络流）

---

## 开源许可与致谢

- 本项目为 Next Player 的衍生作品，遵循 **GNU GPL-3.0**。
- 原项目版权 © [Anil Beesetti](https://github.com/anilbeesetti) 及贡献者；本项目的修改部分同样以 GPL-3.0 发布。
- 特别感谢原项目 [anilbeesetti/nextplayer](https://github.com/anilbeesetti/nextplayer) 及所有贡献者。
- 修改声明见 `NOTICE`。

---

## 构建与签名

**环境要求**

- Android SDK（已通过 `local.properties` 的 `sdk.dir` 指定）
- JDK 17 及以上
- 项目自带 Gradle Wrapper（无需单独安装 Gradle）

**调试构建**

```bash
./gradlew assembleDebug
```

**正式签名构建**

签名配置已从源码中移除，改由本地 `local.properties` 提供（该文件已被 `.gitignore` 忽略，不会进入仓库）。请在 `local.properties` 末尾追加：

```properties
tk.storeFile=../keystore/release.keystore
tk.storePassword=你的密码
tk.keyAlias=tongkan
tk.keyPassword=你的密码
```

> 未配置上述项时，`assembleRelease` 仍会生成**未签名**的 Release APK，方便开源协作；只有配置了签名信息才会产出正式签名包。

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`，包含按 ABI 拆分的包（armeabi-v7a / arm64-v8a / x86 / x86_64）及 `universal` 通用包。

---

## 下载

- 最新正式版见 [Releases](releases/latest)。
- v1.0.0 提供 **arm64-v8a**（约 19MB，平板推荐）与 **universal**（约 54MB）两个签名安装包。

---

## 免责声明

本应用仅供家长在受控设备上使用，用于限制未成年人随意快进视频。作者不对任何使用方式及后果承担责任。
