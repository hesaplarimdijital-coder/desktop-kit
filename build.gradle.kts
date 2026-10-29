plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin) and brings Kotlin 2.2.10. Declaring the Kotlin
    // Gradle plugin here, never applied, puts the catalog's Kotlin on the build classpath instead,
    // so AGP and the Compose compiler plugin both use that version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
