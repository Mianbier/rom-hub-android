# ROM Hub（Android 客户端）

第三方**非官方**的小米 ROM 索引与社区移植包浏览工具。

- 索引 354 款机型、7.8 万个版本，含官方包（稳定版 / 开发版 / 内测 / Beta）与社区移植包
- 订阅机型后，官方包或移植包有更新时发系统通知，点通知直达该版本
- 动态页本地缓存 + 未读小红点
- 游客可浏览与下载；开发者经站长审核后可上传移植包

界面与动效以 [KernelSU](https://github.com/tiann/KernelSU) 与
[HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) 为蓝本，
并使用二者同源的 UI 库 [Miuix](https://github.com/compose-miuix-ui/miuix)。

---

## 许可

源码仓库：**https://github.com/Mianbier/rom-hub-android**

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
| UI | Jetpack Compose + **Miuix 0.9.4**（`top.yukonga.miuix.kmp`） |
| 模糊 / 液态玻璃 | `miuix-blur` + `miuix-shader`（AGSL `RuntimeShader`） |
| 网络 | OkHttp + kotlinx.serialization |
| 图片 | Coil |
| 后台 | WorkManager（15 分钟轮询，仅按机型订阅发通知） |
| 凭据 | Android Keystore AES-256-GCM |

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

版本矩阵已在 `gradle/libs.versions.toml` 写死，**不要自行升级** ——
Miuix 的版本与 Kotlin / Compose 版本是绑定的，乱升会掉进依赖地狱。

> 注意 `minSdk 33`：底栏的液态玻璃与极光背景依赖 `RuntimeShader`（Android 13+），
> 低版本没有对应的图形能力。

---

## 目录结构

```
app/src/main/java/org/linbaogu/romhub/
  ui/component/FloatingBottomBar.kt   底栏胶囊（移植自 KernelSU）
  ui/liquid/                          Lens 折射 / Vibrancy / 内阴影
  ui/miuix/animation/                 DampedDragAnimation、InteractiveHighlight
  ui/effect/                          HyperCeiler 的 OS3 极光背景（AGSL 着色器）
  ui/screens/                         主页 / 动态 / 包上传 / 关于 / 各详情页
  ui/nav/AppNav.kt                    页面栈 + 返回优先级（照 KernelSU 三条规则）
  data/                              API 客户端、模型、本地缓存
  notify/                            通知渠道、轮询、开机恢复
  core/                              Prefs、Keystore 加密存储
```

---

## 声明

- 本软件与小米公司无任何关联，ROM 与移植包均为第三方资源。
- 刷机有风险，请自行判断并备份数据。
