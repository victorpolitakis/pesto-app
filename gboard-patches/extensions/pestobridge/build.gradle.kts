extension {
    // The resource path of the extension dex inside the patches bundle; the patches reference it
    // with extendWith("extensions/pestobridge.mpe").
    name = "extensions/pestobridge.mpe"
}

android {
    namespace = "com.victorpolitakis.pesto.bridge"
}

// The extension is plain Java. AGP 9 adds the Kotlin standard library to every module, and it
// would end up in the dex merged into Gboard, next to Gboard's own kotlin.* classes. Keep it out.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}
