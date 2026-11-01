import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The backend URL comes from android/local.properties (not in git). The default
// works with `adb reverse tcp:8000 tcp:8000`, and lets the build pass on CI. It is
// not a secret: without a token it gives nothing. No token is in the build: every
// phone gets its own with `pesto-server pair` (Δ24).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val backendUrl = localProperties.getProperty("pesto.backendUrl", "ws://127.0.0.1:8000/v1/dictate")

// sherpa-onnx (Apache-2.0), the runtime of the offline model, is not on Maven: the AAR of its
// GitHub release, checked by sha256, into app/libs (not in git). Once per checkout, CI too.
val sherpaVersion = "2.0.1"
val sherpaSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
val fetchSherpa = tasks.register("fetchSherpaOnnx") {
    outputs.file(sherpaAar)
    doLast {
        if (sherpaAar.exists() && sha256(sherpaAar) == sherpaSha256) return@doLast
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        URI(url).toURL().openStream().use { input -> sherpaAar.outputStream().use { input.copyTo(it) } }
        check(sha256(sherpaAar) == sherpaSha256) { "sherpa-onnx AAR: wrong sha256" }
    }
}

fun sha256(f: File): String = MessageDigest.getInstance("SHA-256")
    .digest(f.readBytes()).joinToString("") { "%02x".format(it) }

android {
    namespace = "com.victorpolitakis.pesto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.victorpolitakis.pesto"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.3.0"

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")

        // The offline model runs only on arm64 phones; the emulator dictation_api36 is arm64 too.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            // Δ18: signed with the debug key, only for local tests, so that adb install -r needs no uninstall.
            // TODO(later): a release key for the Play Store, after 15/2.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The UI follows the phone's language: Greek in Greek, English in any other. With
    // unqualifiedResLocale (res/resources.properties) the default values/ is en-US, and
    // generateLocaleConfig lists en-US and el, so Android 13+ shows Pesto in System
    // Settings > Apps > Language. No language option of our own.
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Robolectric: resources and assets in the JVM tests (Compose screens, Room).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Robolectric on SDK 36 reaches into jdk.internal.access, closed by default on JDK 21.
            all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
        }
    }

    // The exported schemas, for the MigrationTestHelper of the JVM tests. Robolectric reads
    // the assets of the app, not of the test source set, so they go to the debug build only.
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    // Level 1 of the UI validation: Android Lint with compose-lints, on CI. No baseline:
    // a finding is fixed, or goes to lint-baseline.xml with the reason next to it.
    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
    }
}

// Room schemas in git, so phase 3 can write migrations.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    lintChecks(libs.compose.lint.checks)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(files(sherpaAar) { builtBy(fetchSherpa) })

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric: Room, DataStore and Compose screens on the JVM, without an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.compose.ui.test.manifest)
}
