# NOTICE — 开源许可与鸣谢

ROM Hub（包名 `org.linbaogu.romhub`）是一个第三方非官方的小米 ROM 索引与社区移植包工具。
本项目以 **GNU Affero General Public License v3.0（AGPL-3.0）** 发布，完整源码公开。

本文件列出本项目实际使用、移植或作为设计参照的全部开源项目及其许可。
许可证信息均取自各项目的 `LICENSE` 文件或 Maven 元数据，未作推断。

---

## 一、直接依赖的第三方库（Apache-2.0）

| 项目 | 许可 | 作者 | 地址 | 用途 |
|---|---|---|---|---|
| Miuix (compose-miuix-ui) | Apache License 2.0 | compose-miuix-ui | https://github.com/compose-miuix-ui/miuix | 整套 HyperOS 风格组件：主题、卡片、按钮、输入框、图标、模糊（miuix-blur）。底栏胶囊与极光背景特效的**原始实现**也来自其官方示例（IosLiquidGlassNavigationBar 与 OS3 背景特效） |
| AndroidLiquidGlass | Apache License 2.0 | Kyant0 | https://github.com/Kyant0/AndroidLiquidGlass | 液态玻璃折射的 SDF 位移算法（Lens 着色器）、薄玻璃高光、内阴影、vibrancy |
| AndroidX / Jetpack Compose | Apache License 2.0 | The Android Open Source Project | https://cs.android.com/androidx/platform/frameworks/support | Compose UI / Foundation / Material3、Activity、Lifecycle、WorkManager、WebKit、Core |
| Kotlin & kotlinx | Apache License 2.0 | JetBrains | https://github.com/JetBrains/kotlin | Kotlin 语言、kotlinx.coroutines、kotlinx.serialization |
| OkHttp | Apache License 2.0 | Square, Inc. | https://github.com/square/okhttp | HTTP 客户端 |
| Coil | Apache License 2.0 | Coil Contributors | https://github.com/coil-kt/coil | 机型图片加载 |
| Android Open Source Project | Apache License 2.0 | The Android Open Source Project | https://source.android.com/ | Android 平台本身，含 RuntimeShader / AGSL 图形着色语言 |

---

## 二、移植源码 / 直接来源（GPL 系）

| 项目 | 许可 | 作者 | 地址 | 用途 |
|---|---|---|---|---|
| KernelSU | GNU General Public License v3.0 | weishu (tiann) 及 KernelSU 贡献者 | https://github.com/tiann/KernelSU | 以下文件由 KernelSU 的实现移植而来：`FloatingBottomBar.kt`、`DampedDragAnimation.kt`、`InteractiveHighlight.kt`、`DragGestureInspector.kt`（`ui/miuix/`）、`ui/liquid/`（Lens / Vibrancy / InnerShadow / CombinedBackdrop）、`ui/effect/`（OS3 极光背景） |
| HyperCeiler | GNU Affero General Public License v3.0 | ReChronoRain 及 HyperCeiler 贡献者 | https://github.com/ReChronoRain/HyperCeiler | 「关于」页面的整体布局与背景特效（`bg_frag.glsl` 及其全套参数 `BgEffectDataManager`）以 HyperCeiler 的关于页为蓝本 |

> **为什么本项目选 AGPL-3.0：** 上表中 HyperCeiler 使用 AGPL-3.0。
> 由于本项目在其基础上做了移植与改写，采用 AGPL-3.0 可以同时满足
> KernelSU（GPL-3.0）与 HyperCeiler（AGPL-3.0）的许可义务。

---

## 三、数据来源

| 来源 | 地址 | 用途 |
|---|---|---|
| hyperos.fans | https://hyperos.fans/ | 机型库与机型图片 |
| xiaomirom.com | https://xiaomirom.com/ | ROM 版本索引 |
| MiROMs HUB / HyperOS 更新 / Mi Firmware | — | 版本号、直链与发布时间交叉校验 |
| 小米官方 OTA、小米社区 | https://www.mi.com/ | 官方固件直链与官方公告 |

以上数据版权归各权利人所有，本项目仅作索引与聚合展示。

---

## 四、免责声明

- 本项目是第三方非官方工具，与小米公司及其关联公司**没有隶属关系**，未获其授权或背书。
- ROM、移植包及其名称、商标版权归各自权利人所有。
- 刷机有风险，可能导致数据丢失或设备损坏。请自行判断并承担后果。

---

## 五、源码

本项目源码地址：<https://github.com/请替换为你的仓库地址/rom-hub-app>

依据 AGPL-3.0，任何通过网络与本应用交互的用户，均有权获取其完整源码。
