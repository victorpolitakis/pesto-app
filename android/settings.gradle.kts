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
        // gboard-patches/tools/bootstrap-morphe.sh, and its smali/ARSCLib forks from JitPack. Both
        // are filtered to the Morphe groups, so no other dependency can come from them.
        mavenLocal {
            content { includeGroup("app.morphe") }
        }
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github\\.MorpheApp.*") }
        }
    }
}

rootProject.name = "pesto"
include(":app")
