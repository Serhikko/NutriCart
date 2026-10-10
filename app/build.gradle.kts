import java.util.Properties

plugins {
    alias(libs.plugins.android.application) // includes built-in Kotlin since AGP 9
    alias(libs.plugins.kotlin.compose)      // Compose compiler ships with Kotlin since 2.0
    alias(libs.plugins.kotlin.serialization) // @Serializable DTOs for the food API
    alias(libs.plugins.ksp)                 // annotation processing for Room + Hilt
    alias(libs.plugins.hilt)
}

// Cloud keys come from local.properties (never committed) or, on CI, from the
// environment. An empty value builds fine: the app then hides the sync section
// and says so, instead of failing at runtime with a broken URL.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun configValue(name: String): String =
    localProperties.getProperty(name) ?: System.getenv(name) ?: ""

// Screenshot tests (Roborazzi on Robolectric) live in src/test/.../screenshots and run
// ONLY with -Pscreenshots. Without it (CI, `./gradlew :app:testDebugUnitTest`) they are
// excluded and the unit tests run exactly as before. See README "Screenshots".
val screenshots = providers.gradleProperty("screenshots").isPresent

android {
    namespace = "com.nutricart.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nutricart.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 100
        versionName = "1.0"

        // Supabase project URL and anon key (safe in a client: row-level
        // security decides what it may do). See supabase/README.md.
        buildConfigField("String", "SUPABASE_URL", "\"${configValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${configValue("SUPABASE_ANON_KEY")}\"")
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
        buildConfig = true // for the Supabase keys above
    }

    testOptions {
        // Robolectric needs the merged resources and manifest — only the screenshot run does.
        unitTests.isIncludeAndroidResources = screenshots
    }
}

tasks.withType<Test>().configureEach {
    if (screenshots) {
        filter.includeTestsMatching("com.nutricart.app.screenshots.*")
        // Where the PNGs go; shoot.sh collects them from here.
        val shotsDir = layout.buildDirectory.dir("outputs/screenshots").get().asFile
        systemProperty("nutricart.screenshots.dir", shotsDir.absolutePath)
        // -Pscreenshots.variants=light,dark renders only those variants (light, dark, uk, fs130, fs200, rm, uk130;
        // uk200 only when named).
        providers.gradleProperty("screenshots.variants").orNull
            ?.let { systemProperty("nutricart.screenshots.variants", it) }
        // Real-GPU-like rendering in Robolectric: elevation shadows, dialogs, sheets.
        systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        // Robolectric 4.17 on JDK 17+ reaches into FileDescriptor internals for SDK 37.
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
        maxHeapSize = "3g"
        maxParallelForks = 2
        // Screenshots are an output, not a cacheable test result: always re-render.
        outputs.upToDateWhen { false }
    } else {
        exclude("com/nutricart/app/screenshots/**")
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

    // Open Food Facts API (food search); OkHttp comes with Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Barcode scanning (system-provided scanner UI, no camera permission)
    implementation(libs.play.services.code.scanner)

    // Home-screen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Unit tests (pure JVM)
    testImplementation(libs.junit)

    // Screenshot tests (-Pscreenshots only; test classpath only, nothing ships in the APK)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.test.espresso.core)
}
