package com.nutricart.app.screenshots

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.nutricart.app.ui.navigation.AppBottomBar
import com.nutricart.app.ui.theme.NutriCartTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * One rendering configuration. Every screen is shot in [LIGHT] and [DARK];
 * key screens also in Ukrainian ([UK]) and at font scale 1.3 ([FONT_130]).
 * The device itself (393 x 852 dp, 440 dpi) comes from robolectric.properties.
 */
enum class Variant(
    val dir: String,
    val extraQualifiers: String?,
    val locale: Locale,
    val fontScale: Float,
    val keyScreensOnly: Boolean,
) {
    LIGHT("light", null, Locale.US, 1f, keyScreensOnly = false),
    DARK("dark", "+night", Locale.US, 1f, keyScreensOnly = false),
    UK("uk", "+uk-rUA", Locale.forLanguageTag("uk-UA"), 1f, keyScreensOnly = true),
    FONT_130("fs130", null, Locale.US, 1.3f, keyScreensOnly = true),
}

/** Also shoot this test in the key-screen variants (Ukrainian, font scale 1.3). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KeyScreen

/** Render on a taller phone ([heightDp]) to see a long screen in one picture. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tall(val heightDp: Int)

/**
 * Applies a [Variant] BEFORE the compose rule launches its activity, so the
 * activity starts with that night mode / locale / font scale exactly like a
 * real device would (nothing is faked inside the composition).
 *
 * It also registers the host ComponentActivity with Robolectric's package
 * manager: an app module's unit tests do not merge test-only manifests, so
 * ui-test-manifest would have to ship in the debug APK otherwise.
 */
class VariantRule(private val variant: Variant) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                // Skip BEFORE the activity exists: a skipped variant costs nothing.
                val only = System.getProperty("nutricart.screenshots.variants")
                    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                assumeTrue("${variant.dir} not requested", only.isEmpty() || variant.dir in only)
                assumeTrue(
                    "${variant.dir} is for @KeyScreen tests only",
                    !variant.keyScreensOnly || description.getAnnotation(KeyScreen::class.java) != null,
                )
                description.getAnnotation(Tall::class.java)?.let {
                    RuntimeEnvironment.setQualifiers("+h${it.heightDp}dp")
                }
                val app = RuntimeEnvironment.getApplication()
                shadowOf(app.packageManager)
                    .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
                val previous = Locale.getDefault()
                variant.extraQualifiers?.let { RuntimeEnvironment.setQualifiers(it) }
                if (variant.fontScale != 1f) RuntimeEnvironment.setFontScale(variant.fontScale)
                // java.time formatters and month names read the JVM default.
                Locale.setDefault(variant.locale)
                try {
                    base.evaluate()
                } finally {
                    Locale.setDefault(previous)
                }
            }
        }
}

/**
 * Base class: each test method renders ONE screen state and saves one PNG per
 * variant to `<nutricart.screenshots.dir>/<variant>/<name>.png`.
 *
 * Runs only with `./gradlew :app:testDebugUnitTest -Pscreenshots` (see app/build.gradle.kts).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotTest(private val variant: Variant) {

    @get:Rule(order = 0)
    val variantRule = VariantRule(variant)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Renders [content] inside the app theme, optionally runs [interact]
     * (clicks that open a dialog, say) and saves the whole screen — dialogs
     * and bottom sheets included — as `<variant>/<name>.png`.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    protected fun shoot(
        name: String,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { NutriCartTheme(content = content) }
        compose.waitForIdle()
        if (interact != null) {
            compose.interact()
            compose.waitForIdle()
        }
        val root = System.getProperty("nutricart.screenshots.dir") ?: "build/outputs/screenshots"
        val file = File(root, "${variant.dir}/$name.png")
        file.parentFile?.mkdirs()
        captureScreenRoboImage(
            file = file,
            roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
    }

    /** A string in the shot's language (for finding nodes to click). */
    protected fun str(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    /**
     * A top-level tab exactly as AppNavHost frames it: the screen above the
     * bottom bar, with [route] selected.
     */
    @Composable
    protected fun TabFrame(route: String, content: @Composable () -> Unit) {
        Scaffold(bottomBar = { AppBottomBar(currentRoute = route, onSelectTab = {}, onQuickAdd = {}) }) { padding ->
            Box(modifier = Modifier.padding(padding)) { content() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = Variant.entries.map { arrayOf(it) }
    }
}
