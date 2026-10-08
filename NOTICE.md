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
| fan.miuix（Miuix 的 View 版重打包） | Apache License 2.0（上游 Miuix 为 Apache-2.0） | ReChronoRain | https://github.com/ReChronoRain/Miuix | 引导流程与「关于」页用到的 Miuix **View** 组件（`fan.miuix:animation`、`fan.miuix:appcompat` 等 1.0.13.0），随 HyperCeiler 通过 GitHub Packages（`maven.pkg.github.com/ReChronoRain/HyperCeiler`）分发，拉取需要 GitHub 凭据 |
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
| HyperCeiler | GNU Affero General Public License v3.0 | ReChronoRain 及 HyperCeiler 贡献者 | https://github.com/ReChronoRain/HyperCeiler | **引导流程**（欢迎 → 权限 → 协议 → 基础设置 → 登录 → 开始使用）与**「关于」页**的整体布局、状态机、背景特效均以 HyperCeiler 为蓝本移植，见下方明细 |
| YunX（云析） | GNU Affero General Public License v3.0 | CYQawa | https://github.com/CYQawa/YunX | 底栏「网盘下载器」的全部能力由云析移植而来，见下方明细 |

### HyperCeiler 移植明细

2.0.0 的界面层以 HyperCeiler 为蓝本重做。**只移植看得见的界面层**，
HyperCeiler 内部约 1.9 万行 Xposed hook 代码（`core` / `libhook` / `common` 的大部分）
没有搬入，改为等价替身实现。

| HyperCeiler 原路径 | 本项目落点 | 内容 |
|---|---|---|
| `fan/provision/*` | `fan/provision/*`（同名） | 引导流程基础设施：`OobeUtils`（含协议页验证码）、`ProvisionBaseActivity`、`ProvisionAnimHelper`、`CustomDispatchFrameLayout` |
| `provision/activity/*` `provision/fragment/*` `provision/state/*` | `hc/provision/` 同名结构 | 引导流程各步骤的 Activity / Fragment / 状态机（`StateMachine`、`StartupState`、`PermissionState`、`TermsAndStatementState`、`BasicState`、`CongratulationState`） |
| `provision/renderengine/*` | `hc/provision/renderengine/` | `GlowController` / `GlowPainter` / `RenderViewLayout` —— 引导页的光晕渲染 |
| `provision/utils/*` `provision/widget/*` `provision/text/style/*` | `hc/provision/` 同名 | `AnimHelper`、`BlurUtils`、`ProvisionManager`、`PageIntercepHelper`、`MarkdownView`（支持 `asset://` 与 `legal://`）、`TermsAndStatementBottomSheet`、`ClickSpan`、`TermsTitleSpan` 等 |
| `about/controller/*` `about/widget/*` `about/util/*` | `hc/about/` 同名 | 「关于」页：`VersionCard` / `DeviceNameCard` / `DeviceInfoCard` / `VersionNameCard`，以及流动光效 `BgEffectController` / `BgEffectPainter` / `BgEffectDataManager` / `AboutAnimationController` |
| `res/raw/bg_frag.glsl` + 全套参数 | `res/raw/bg_frag.glsl` | AGSL 着色器（`View.setRenderEffect`）实现的关于页背景光效 |
| `libhook.utils.api.PropUtils` 等 | `hc/util/` | `PropUtils`、`DeviceHelper`、`LargeFontUtils`、`SettingsFeatures`、`EggHelper` |
| `common/*`（日志 / Prefs / 权限 / 语言） | `hc/common/` | **等价替身**：`AndroidLog`、`PrefsBridge`、`PrefsConfigurator`、`AppSettingsStore`、`PermissionUtils`、`AppLanguageHelper`。原版依赖 Xposed 框架的 Prefs 体系，这里改用普通 SharedPreferences + AndroidX 实现 |

**有意偏离（改写而非照搬）**：
1. `hc/Hc*.kt`（`HcBottomBar` / `HcAboutBg` / `HcAboutCards` / `HcHomeTip` / `HcNavState`）
   是本项目自己写的**胶水层**，把移植来的 View 组件接进 Compose 界面。
2. 主页 tips 的 300 条文案（`HcHomeTip.kt`）为本项目自写，未使用 HyperCeiler 的文案与卡通插图。
3. 引导流程新增了「登录」步骤（`hc/provision/activity/LoginActivity.kt`、`fragment/LoginFragment.kt`、
   `ProvisionLogin.kt`），HyperCeiler 原版没有这一步。
4. `BgEffectPainter` 原版调用 `fan.animation.Folme` 做颜色补间；本项目所用的
   `fan.miuix` 里不含 Folme，改为手写 ease-out 插值（`CubicEaseOutInterpolater`）。

### 云析（YunX）移植明细

`pan/`、`download/` 与相关 UI 层为云析源码移植，**只改包名 `com.yunx.app` → `org.linbaogu.romhub`**，
并把 Room 数据库换成 JSON 文件存储（`pan/store/AccountStore.kt`、`BookmarkStore.kt`）。

| 云析原路径 | 本项目落点 | 内容 |
|---|---|---|
| `data/network/*` | `pan/*` | 夸克 / UC / 百度 / 115 / 123 / 移动云盘 / 迅雷 的 API 封装、ShareLinkParser、GitHub API |
| `data/network/model/*` | `pan/model/*` | ShareModels、ShareExpire |
| `data/repository/*` | `pan/repo/*` | 7 家的账号仓库 + 分享解析仓库（建会话 → 列文件 → 转存 → 取直链） |
| `data/security/CredentialCipher.kt` | `pan/security/CredentialCipher.kt` | 凭证落盘加密 |
| `data/db/*Account*Entity/Dao.kt` | `pan/store/AccountStore.kt` | 账号存储（Room → JSON） |
| `data/db/Bookmark*` | `pan/store/BookmarkStore.kt` | 收藏存储（Room → JSON） |
| `data/prefs/SettingsRepository.kt` | `core/Prefs.kt` | 下载设置项并入本项目已有 Prefs |
| `data/download/ChunkDownloader.kt` | `download/ChunkDownloader.kt` | 多线程分片 + 断点续传 |
| `data/download/HlsDownloader.kt` `HlsRequestPolicy.kt` `HttpRangePolicy.kt` `DownloadPlatform.kt` | `download/` 同名文件 | HLS 下载与请求策略 |
| `data/download/DownloadManager.kt` `DownloadService.kt` | `download/` 同名文件 | 任务队列与前台服务（并入本项目已有下载体系） |
| `data/update/UpdateChecker.kt` | `ui/update/` + `data/AppUpdateInfo` | 更新检查（并入本项目已有更新体系） |
| `data/backup/AuthBackupManager.kt` | `pan/store/AccountStore.kt` | 账号备份导入导出能力 |
| `ui/login/*` | `ui/login/WebPanLoginScreen.kt` 等 | 6 家 WebView 登录 + 迅雷账密短信登录（收拢成 `WebPanLoginSpec` 单一泛化页面） |
| `ui/screens/BookmarkScreen.kt` | `ui/screens/BookmarkScreen.kt` | 收藏页 |
| `ui/screens/DownloadScreen.kt` | `ui/screens/DownloadScreen.kt` | 下载页（与原有下载体系合并） |
| `ui/screens/SettingsScreen.kt` | `ui/screens/DownloadSettingsScreen.kt` | 下载设置页 |
| `ui/screens/BookmarkScreen.kt` | `ui/screens/BookmarkScreen.kt` | 收藏页 |
| `ui/screens/CloudFileSheets.kt` `SaveToCloudSheet.kt` | `ui/screens/CloudFileSheets.kt` | 云盘文件操作弹窗（下载直链 / 新建文件夹 / 重命名 / 批量分享 / 移动 / 删除确认） |
| `ui/screens/*CloudScreen.kt`（7 家） | `ui/screens/CloudBrowserScreen.kt` + `cloud/CloudApi.kt` + `cloud/CloudBrowserViewModel.kt` | **云盘文件浏览**：面包屑、列表、搜索、多选、下载 / 重命名 / 移动 / 删除 / 新建文件夹 / 分享。7 家的差异收拢进 `CloudApi` 适配器，UI 与 ViewModel 只有一份 |
| `ui/viewmodel/*` | `ui/login/PanAccounts.kt` | 7 个 AccountViewModel 收拢为单例门面 |
| `ui/SnackbarController.kt` | `ui/common/Snackbar.kt` | 全局轻提示 |
| `ui/components/ExpressiveLoading.kt` 等 | `ui/login/WebLoadingBar.kt` 等 | 加载指示器等小组件 |
| `util/*` | `pan/XunleiDeviceFingerprint.kt`、`ui/screens/SimpleMarkdown.kt` 等 | 设备指纹、轻量 Markdown 渲染 |

**未移植**：云析自身的 Onboarding / 主题 / 关于 / 更新弹窗等「App 自身设置体系」——
本项目已有对应实现（关于页、更新弹窗、主题跟随系统），故未重复搬入。

**有意偏离（改写而非照搬）**：
1. `data/db/` 的 Room 数据库 → JSON 文件存储（`pan/store/`），少一个 KSP/Room 依赖。
2. 7 个 `*AccountViewModel` → 单例门面 `PanAccounts`。
3. 7 个 `*CloudViewModel`（各约 680 行、逻辑高度重复）+ 7 个 `*CloudScreen`
   → 1 个 `CloudApi` 接口 + 7 个轻量适配器 + 1 个共用 `CloudBrowserViewModel` / `CloudBrowserScreen`。
4. 「保存到本机」的 MediaStore 链路未搬：本项目下载改落公共 `Download/rom-hub/`，
   走 `MANAGE_EXTERNAL_STORAGE`（所有文件访问）一次性授权；未授权时自动退回 App 私有目录，
   功能不中断（见 `core/StoragePermission.kt`、`download/DownloadStore.kt`）。
5. 下载体验在本项目侧有增强：通知栏进度 + 暂停/继续/取消（`download/DownloadService.kt`）、
   任务卡进度/速度/剩余时间/打开文件（`ui/screens/DownloadScreen.kt`）、
   各下载入口统一提示并跳转到「网盘下载器 → 下载」段。

> **为什么本项目选 AGPL-3.0：** 上表中 HyperCeiler 与云析均使用 AGPL-3.0。
> 由于本项目在其基础上做了移植与改写，采用 AGPL-3.0 可以同时满足
> KernelSU（GPL-3.0）、HyperCeiler（AGPL-3.0）与云析（AGPL-3.0）的许可义务。

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

本项目源码地址：<https://github.com/Mianbier/rom-hub-android>

依据 AGPL-3.0 第 13 条，任何通过网络与本应用交互的用户，均有权获取其完整源码。
本仓库公开的就是**构建本应用所用的全部源码**（不含签名密钥与运行时二进制），
任何人可自行编译、修改、再分发，条件是一并保留 `LICENSE` 与本文件、
并以同样的 AGPL-3.0 许可发布其衍生作品。
