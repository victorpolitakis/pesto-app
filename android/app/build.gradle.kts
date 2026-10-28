import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The backend URL comes from android/local.properties (not in git). The default
// works with `adb reverse tcp:8000 tcp:8000`, and lets the build pass on CI.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val backendUrl = localProperties.getProperty("pesto.backendUrl", "ws://127.0.0.1:8000/v1/dictate")

// The app token lives only in backend/.env. Empty when the file is missing (CI).
val appToken = rootProject.file("../backend/.env").takeIf { it.exists() }
    ?.readLines()
    ?.firstOrNull { it.startsWith("PESTO_APP_TOKEN=") }
    ?.substringAfter("=")
    ?.trim()
    .orEmpty()

android {
    namespace = "com.victorpolitakis.pesto"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.victorpolitakis.pesto"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "APP_TOKEN", "\"$appToken\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
