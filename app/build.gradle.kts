plugins {
    alias(libs.plugins.android.application) // includes built-in Kotlin since AGP 9
    alias(libs.plugins.kotlin.compose)      // Compose compiler ships with Kotlin since 2.0
    alias(libs.plugins.ksp)                 // annotation processing for Room + Hilt
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.nutricart.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nutricart.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}
// Kotlin's jvmTarget automatically follows compileOptions.targetCompatibility (17).

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose: the BOM pins compatible versions of all Compose artifacts
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core) // Settings/ArrowBack icons
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // LifecycleResumeEffect
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room (local database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (small key-value settings)
    implementation(libs.androidx.datastore.preferences)

    // Hilt (dependency injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose) // new home of hiltViewModel()

    // Health Connect (watch/phone activity data) + hourly background sync
    implementation(libs.androidx.health.connect.client)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work) // @HiltWorker support
    ksp(libs.androidx.hilt.compiler)

    // Unit tests (pure JVM)
    testImplementation(libs.junit)
}
