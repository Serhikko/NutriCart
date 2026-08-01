// Root build file: declares which plugins the project uses, without applying them here.
// Each module (we only have :app) applies the ones it needs.

buildscript {
    dependencies {
        // AGP 9 has Kotlin support built in, but bundles an older Kotlin Gradle plugin.
        // This line upgrades the built-in Kotlin to the version from our catalog,
        // so it matches the Compose compiler plugin below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
