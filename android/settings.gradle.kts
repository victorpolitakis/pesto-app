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
        // The Morphe patcher (app.morphe), built from public source into mavenLocal by
        // gboard-patches/tools/bootstrap-morphe.sh, and its smali fork from JitPack.
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "pesto"
include(":app")
