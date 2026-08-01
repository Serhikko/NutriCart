// Tells Gradle where to download plugins and libraries from, and which modules the project has.
pluginManagement {
    repositories {
        google()            // Android + Jetpack libraries
        mavenCentral()      // everything else (Hilt, JUnit, ...)
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // All dependencies must be declared here, not inside modules — one source of truth.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NutriCart"
include(":app")
