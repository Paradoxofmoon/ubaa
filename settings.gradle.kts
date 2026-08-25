rootProject.name = "UBAA"

// 启用类型安全的项目访问器（如 projects.shared）
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// 注意：pluginManagement / dependencyResolutionManagement 会在 Gradle 早期阶段被单独求值，
// 无法引用 settings 脚本顶层声明的 val，因此下面两个块内各自内联判断。
// GitHub Actions 的 runner（美国）访问阿里云镜像经常返回 502，导致依赖解析直接失败；
// 因此在 CI 环境跳过阿里云镜像、改用官方源（google/mavenCentral），本地（国内网络）保留镜像加速。
pluginManagement {
  repositories {
    val useAliyunMirrors =
        System.getenv("GITHUB_ACTIONS")?.isNotBlank() != true &&
            System.getenv("CI")?.isNotBlank() != true
    if (useAliyunMirrors) {
      maven { url = uri("https://maven.aliyun.com/repository/google") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
      maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositories {
    val useAliyunMirrors =
        System.getenv("GITHUB_ACTIONS")?.isNotBlank() != true &&
            System.getenv("CI")?.isNotBlank() != true
    if (useAliyunMirrors) {
      maven { url = uri("https://maven.aliyun.com/repository/google") }
      maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    // Kamel 及其依赖所在的镜像仓库
    maven("https://s01.oss.sonatype.org/content/repositories/releases/")
    maven("https://maven.pkg.jetbrains.space/public/p/kamel/maven")
  }
}

// 包含所有子模块
include(":androidApp")

include(":composeApp")

include(":shared")
