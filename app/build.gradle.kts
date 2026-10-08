@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.linbaogu.romhub"

    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "org.linbaogu.romhub"
        minSdk = 33
        targetSdk = 37
        // ⚠ versionCode 必须和「版本名自动换算」的规则一致：major*10000 + minor*100 + patch
        //   （见 admin 端 AdminViewModel.kt 的 versionToCode）。2.0.0 → 20000。
        //   不一致的后果：服务端按 2.0.0 算出 20000、客户端写 60，客户端会误报「有新版本」。
        versionCode = 20002
        versionName = "2.0.2"
        resourceConfigurations += listOf("zh", "en")
    }

    /**
     * 独立签名配置。
     *
     * ⚠ 为什么不新建 keystore：已发布的 11 个版本都是用 Android 调试证书签的。
     *    换成别的证书会导致签名校验失败，老用户必须**卸载重装**才能升级。
     *    所以这里沿用调试证书（路径固定为 ~/.android/debug.keystore），
     *    只是把它显式声明成一个叫 `release` 的命名配置，让 release 变体不再
     *    依赖 AGP 的 debug 默认配置 —— 产物本身不带任何 debug 标记。
     *
     *    将来若确实要换正式证书，必须同步告知用户「需卸载旧版」。
     */
    signingConfigs {
        create("release") {
            storeFile = file(
                System.getProperty("user.home") + "/.android/debug.keystore"
            )
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 用上面显式声明的 release 签名配置（内容仍是同一张调试证书，保证可覆盖升级）
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
        // provision 流程里有 IProvisionAnim / IAnimCallback 两个 AIDL，AGP 默认关 AIDL
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=top.yukonga.miuix.kmp.theme.ExperimentalMiuixApi",
        )
    }
}

configurations.all {
    // fan.miuix:appcompat 里**自带了一份 androidx.appcompat 的类**
    // （AppCompatResources / DrawableUtils 等），和真实的 androidx.appcompat:appcompat-resources 撞包。
    // 排除真实那份，用 fan.miuix 自带的（HyperCeiler 就是这么处理的）。
    exclude(group = "androidx.appcompat", module = "appcompat-resources")
    // constraintlayout 会把真实的 androidx.appcompat:appcompat 拖进来，同样与 fan.miuix:appcompat 撞包
    exclude(group = "androidx.appcompat", module = "appcompat")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // KernelSU 同款 UI / 模糊
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.core)

    // HyperCeiler 的布局用了 ConstraintLayout / Flow
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ===== HyperCeiler 的 Miuix（View 版）组件库 =====
    // HyperCeiler 的界面是 Java + 传统 View（Fragment/XML），用的是 fan.miuix:*。
    // 为了「照抄」它的界面（关于页、悬浮底栏开关、卡片、弹窗动画），把整套引进来的。
    // 发布在 GitHub Packages（maven.pkg.github.com/ReChronoRain/HyperCeiler），
    // 需要凭据 —— 已配在 settings.gradle.kts + 本机 ~/.gradle/gradle.properties。
    implementation("fan.miuix:animation:1.0.13.0")
    implementation("fan.miuix:appcompat:1.0.13.0")
    implementation("fan.miuix:basewidget:1.0.13.0")
    implementation("fan.miuix:bottomsheet:1.0.13.0")
    implementation("fan.miuix:cardview:1.0.13.0")
    implementation("fan.miuix:core:1.0.13.0")
    implementation("fan.miuix:folme:1.0.13.0")
    implementation("fan.miuix:navigator:1.0.13.0")
    implementation("fan.miuix:nestedheader:1.0.13.0")
    implementation("fan.miuix:pickerwidget:1.0.13.0")
    implementation("fan.miuix:preference:1.0.13.0")
    implementation("fan.miuix:recyclerview:1.0.13.0")
    implementation("fan.miuix:springback:1.0.13.0")
    implementation("fan.miuix:theme:1.0.13.0")
    implementation("fan.miuix:viewpager:1.0.13.0")
    implementation("fan.miuix:transition:1.0.13.0")
}
