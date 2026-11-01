rootProject.name = "gboard-patches"

pluginManagement {
    repositories {
        // The Morphe patches settings plugin and the patcher are built from their public source into
        // the local Maven repository by tools/bootstrap-morphe.sh, so no GitHub token is needed.
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Note: in this cloud container a Gradle init script redirects Maven Central to Google's
        // mirror and drops the plugin portal, which answers 429 here. On a normal machine these
        // repositories are used as declared.
    }
}

plugins {
    id("app.morphe.patches") version "2.0.0"
}

// The patcher resolves its own transitive dependencies (smali from JitPack, the rest from Maven
// Central) through the repositories the settings plugin adds, with mavenLocal first for the patcher.
settings {
    extensions {
        // Namespace shared by every extension module merged into the patched keyboard.
        defaultNamespace = "com.victorpolitakis.pesto.bridge"
    }
}
