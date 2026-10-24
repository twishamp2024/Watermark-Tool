// AGP 9 compiles Kotlin itself ("built-in Kotlin"). Declaring the Kotlin plugin here (not applied)
// pins the Kotlin Gradle Plugin version so it matches the Compose compiler plugin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
