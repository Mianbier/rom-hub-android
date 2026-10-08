# ROM Hub（Android 客户端）

第三方**非官方**的小米 ROM 索引与社区移植包浏览工具。

- 索引 354 款机型、7.8 万个版本，含官方包（稳定版 / 开发版 / 内测 / Beta）与社区移植包
- 订阅机型后，官方包或移植包有更新时发系统通知，点通知直达该版本
- 动态页本地缓存 + 未读小红点
- 内置**网盘下载器**：夸克 / UC / 百度 / 115 / 123 / 移动云盘 / 迅雷，支持登录、浏览、搜索、转存、取直链
- 多线程分片下载 + 断点续传，通知栏实时进度
- 游客可浏览与下载；**开发者账号申请**经站长审核后可上传移植包

当前版本 **v2.0.2**（versionCode 20002）。安装包见
[Releases](https://github.com/Mianbier/rom-hub-android/releases)。

界面与动效以 [KernelSU](https://github.com/tiann/KernelSU) 与
[HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) 为蓝本，
并使用二者同源的 UI 库 [Miuix](https://github.com/compose-miuix-ui/miuix)。

---

## 许可

**GNU Affero General Public License v3.0**，完整正文见 [`LICENSE`](LICENSE)。

选择 AGPL-3.0 的原因：本项目的界面实现移植自 KernelSU（GPL-3.0）与 HyperCeiler（AGPL-3.0），
采用 AGPL-3.0 可同时满足两者的许可义务（AGPL 的义务是 GPL 的超集）。

**完整的使用、移植与鸣谢清单见 [`NOTICE.md`](NOTICE.md)** —— 若你分发本软件，
请一并保留 `LICENSE` 与 `NOTICE.md`。

### 关于服务端

本仓库**只含 Android 客户端**。它默认连接 `https://rom.linbaogu.dpdns.org`
（ROM Hub 后端），两者仅通过 HTTPS 接口通信，属于互相独立的程序，
后端不在本仓库范围内，也不受本许可约束。

---

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 | Kotlin 2.4.20 |
| UI（主界面） | Jetpack Compose（BOM 2026.09.00）+ **Miuix 0.9.4**（`top.yukonga.miuix.kmp`） |
| UI（HyperCeiler 移植层） | **fan.miuix 1.0.13.0** —— Miuix 的 **View** 版，随 HyperCeiler 经 GitHub Packages 分发 |
| 模糊 / 液态玻璃 | `miuix-blur` + `miuix-shader`（AGSL `RuntimeShader`） |
| 网络 | OkHttp 5.5.0 + kotlinx.serialization 1.9.0 |
| 图片 | Coil 3.4.0 |
| 后台 | WorkManager（15 分钟轮询，仅按机型订阅发通知） |
| 凭据 | Android Keystore AES-256-GCM |
| 构建 | AGP 9.4.1 + Gradle 9.8 + JDK 21 |

---

## 构建

要求 **JDK 21** + **Android SDK（platform 37.2、build-tools 37）** + **Gradle 9.8**。
`AGP 9` 必须用 JDK 21，用 17 会失败。

```bash
# 1) 告诉 Gradle SDK 在哪（本机的路径不要提交）
echo 'sdk.dir=<你的 Android SDK 路径>' > local.properties

# 2) 出包（本仓库未附带 gradle wrapper，请用本机安装的 Gradle 9.8.0）
export JAVA_HOME=<JDK 21 路径>
export ANDROID_HOME=<Android SDK 路径>
gradle assembleRelease           # 或 assembleDebug
# 产物：app/build/outputs/apk/release/app-release.apk
```

> 仓库里没有 `gradlew`：生成 wrapper 需要联网下载 Gradle 分发包，开发机当时下载不通。
> 如果你本地网络正常，跑一次 `gradle wrapper --gradle-version 9.8.0` 补上即可。

### ⚠️ 需要自备 GitHub 凭据

`fan.miuix:*` 发布在 **GitHub Packages**（`maven.pkg.github.com/ReChronoRain/HyperCeiler`），
拉取需要 GitHub 账号凭据。在 **`GRADLE_USER_HOME/gradle.properties`**（不是项目里的那份）写入：

```properties
gpr.user=<你的 GitHub 用户名>
gpr.key=<一个带 read:packages 权限的 PAT>
```

没有凭据时这个仓库会被跳过，其余依赖仍能正常解析，但**编译会缺 `fan.miuix` 相关类**。

版本矩阵已在 `gradle/libs.versions.toml` 写死，**不要自行升级** ——
Miuix 的版本与 Kotlin / Compose 版本是绑定的，乱升会掉进依赖地狱。

> 注意 `minSdk 33`：底栏的液态玻璃与极光背景依赖 `RuntimeShader`（Android 13+），
> 低版本没有对应的图形能力。

---

## 目录结构

```
app/src/main/java/
  fan/provision/                     引导流程基础设施（移植自 HyperCeiler）
  org/linbaogu/romhub/
    ui/
      AppRoot.kt                     根界面：底栏三 tab（主页 / 设置 / 关于）+ 导航壳
      AppViewModel.kt
      screens/                       各功能页与详情页
      component/ effect/ liquid/     底栏胶囊、液态玻璃折射、极光背景（移植自 KernelSU）
      miuix/ nav/ theme/ welcome/
    hc/                              ★ HyperCeiler 界面移植层
      HcBottomBar.kt                 底栏（照 HyperCeiler 重做）
      HcAboutBg.kt / HcAboutCards.kt 「关于」页外壳
      HcHomeTip.kt                   主页提示文案（300 条自写）
      about/                         关于页：VersionCard / DeviceInfoCard / 流动光效
      provision/                     引导流程：activity / fragment / state / widget / utils
      common/                        替身：日志 / Prefs / 权限 / 语言
      util/ widget/                  工具与自定义 View
    data/                            API 客户端、数据模型、本地缓存
    cloud/                           云端同步（用户协议 / 隐私政策）
    pan/                             网盘下载器（移植自云析，7 家平台）
    download/                        多线程分片下载、HLS、断点续传
    notify/                          通知渠道、轮询、开机恢复
    network/                         链接常量
    core/                            Prefs、Keystore 加密存储、存储权限
```

---

## 界面结构（v2.0.0 起）

- **底栏三 tab**：主页 / 设置 / 关于
- **主页** = 一条提示（照 HyperCeiler 的 tips 样式，文案自写）+ 功能卡片，点进去在当前 tab 内联展开
- **设置** = 通知 / 外观 / 存储 / 数据（备份·恢复·重置）/ 其他
- **关于** = 版本卡片 + 设备信息卡 + 流动光效背景（AGSL 着色器）
- **引导流程**（首次启动）：欢迎 → 权限 → 协议与声明 → 基础设置 → 登录 → 开始使用
  - 「登录」可选：开发者登录（提交账号密码）或游客进入
  - 登录页含「**申请开发者账号**」入口，需填 账号（邮箱）/ 名称 / 酷安名 / 密码

---

## 声明

- 本软件与小米公司无任何关联，ROM 与移植包均为第三方资源。
- 刷机有风险，请自行判断并备份数据。
