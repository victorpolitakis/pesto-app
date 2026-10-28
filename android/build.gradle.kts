plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 has Kotlin built in. Declaring the plugin here only pins the Kotlin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
