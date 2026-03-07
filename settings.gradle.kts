pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 添加OpenCV的仓库
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "apriltag-location"
include(":app")
// 包含AprilTag模块，使用项目内的AprilTag源码
include(":apriltag")