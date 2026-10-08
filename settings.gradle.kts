pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // HyperCeiler 的 Miuix（View 版）组件库（fan.miuix:*）发布在 GitHub Packages，
        // 需要 GitHub 凭据。凭据放本机 ~/.gradle/gradle.properties 的 gpr.user / gpr.key，
        // **不要提交进仓库**。没有凭据时这个仓库会被跳过，不影响其它依赖解析。
        val gprUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GIT_ACTOR")
        val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GIT_TOKEN")
        if (!gprUser.isNullOrBlank() && !gprKey.isNullOrBlank()) {
            maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
        maven("https://jitpack.io")
    }
}

rootProject.name = "rom-hub-android"
include(":app")
