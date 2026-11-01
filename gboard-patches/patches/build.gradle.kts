group = "com.victorpolitakis.pesto"

patches {
    about {
        name = "Gboard with Pesto"
        description = "Routes Gboard's microphone key to Pesto dictation, seeds the keyboard's " +
            "languages, and hides the patched keyboard from the launcher."
        source = "git@github.com:victorpolitakis/pesto-app.git"
        author = "Victor Politakis"
        contact = "na"
        website = "na"
        license = "GPLv3"
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    // The patcher exposes coroutines only at runtime; the integration test drives its flow, so the
    // core artifact is needed on the test compile classpath too.
    testImplementation(libs.kotlinx.coroutines.core)
    // Gradle 9 requires the JUnit Platform launcher on the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
    // The integration test reads Gboard APK paths from local.properties and skips without them.
    // Never cache a pass, so adding the APKs locally re-runs it without a clean.
    outputs.upToDateWhen { false }
}
