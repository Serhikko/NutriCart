package com.nutricart.app.screenshots

import android.content.ComponentName
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.LocalEmberMotion
import com.nutricart.app.ui.ember.resetEmberMotionMemory
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
import java.io.RandomAccessFile
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * One rendering configuration. Every screen is shot in [LIGHT] and [DARK]; key screens also in
 * Ukrainian ([UK]), at font scales 1.3 ([FONT_130]) and 2.0 ([FONT_200]), in Ukrainian at 1.3
 * ([UK_130], the longest words at Android's "Large" text), and with "Remove animations" on
 * ([REDUCED_MOTION]). [UK_200] (the worst case: Ukrainian at 2.0) runs only when asked for by name
 * (`--variants uk200`), to keep the full run short. The device itself (393 x 852 dp, 440 dpi) comes
 * from robolectric.properties.
 */
enum class Variant(
    val dir: String,
    val extraQualifiers: String?,
    val locale: Locale,
    val fontScale: Float,
    val keyScreensOnly: Boolean,
    val reducedMotion: Boolean = false,
    val onRequestOnly: Boolean = false,
) {
    LIGHT("light", null, Locale.US, 1f, keyScreensOnly = false),
    DARK("dark", "+night", Locale.US, 1f, keyScreensOnly = false),
    UK("uk", "+uk-rUA", Locale.forLanguageTag("uk-UA"), 1f, keyScreensOnly = true),
    FONT_130("fs130", null, Locale.US, 1.3f, keyScreensOnly = true),
    FONT_200("fs200", null, Locale.US, 2.0f, keyScreensOnly = true),
    REDUCED_MOTION("rm", null, Locale.US, 1f, keyScreensOnly = true, reducedMotion = true),
    UK_130("uk130", "+uk-rUA", Locale.forLanguageTag("uk-UA"), 1.3f, keyScreensOnly = true),
    UK_200("uk200", "+uk-rUA", Locale.forLanguageTag("uk-UA"), 2.0f, keyScreensOnly = true, onRequestOnly = true),
}

/** Also shoot this test in the key-screen variants (Ukrainian, font scales 1.3 and 2.0, both together, reduced motion). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KeyScreen

/** Render on a taller phone ([heightDp]) to see a long screen in one picture. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tall(val heightDp: Int)

/**
 * Applies a [Variant] BEFORE the compose rule launches its activity, so the
 * activity starts with that night mode / locale / font scale / animator scale
 * exactly like a real device would (nothing is faked inside the composition).
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
                assumeTrue(
                    "${variant.dir} not requested",
                    if (only.isEmpty()) !variant.onRequestOnly else variant.dir in only,
                )
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
                // "Remove animations": the app reads this setting itself (rememberEmberMotion), so the
                // real detection path runs, not a test override.
                val resolver = app.contentResolver
                Settings.Global.putFloat(
                    resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (variant.reducedMotion) 0f else 1f,
                )
                // java.time formatters and month names read the JVM default.
                Locale.setDefault(variant.locale)
                // First opens and remembered values are process-wide; every shot starts fresh.
                resetEmberMotionMemory()
                try {
                    base.evaluate()
                } finally {
                    Locale.setDefault(previous)
                    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
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

    private val root: File
        get() = File(System.getProperty("nutricart.screenshots.dir") ?: "build/outputs/screenshots")

    /**
     * Renders [content] inside the app theme, optionally runs [interact]
     * (clicks that open a dialog, say) and saves the whole screen — dialogs
     * and bottom sheets included — as `<variant>/<name>.png`.
     */
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
        capture(name)
    }

    /**
     * Motion frames: renders [content] with the clock paused and saves `<name>-t<ms>.png` at each of
     * [times] (milliseconds after the first frame, or after [interact] when given, e.g. a click that
     * opens a sheet). [firstOpen] = true plays the first-open build, false the revisit entrance
     * (rememberFirstOpen reads it through LocalEmberMotion; reduced motion still comes from the variant).
     * Frames must never show intermediate numbers or two stacked digits.
     */
    protected fun shootFrames(
        name: String,
        times: List<Int> = listOf(60, 250, 600, 1200),
        firstOpen: Boolean = true,
        interact: (ComposeContentTestRule.() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            NutriCartTheme {
                CompositionLocalProvider(LocalEmberMotion provides Ember.motion.copy(firstOpen = firstOpen)) {
                    content()
                }
            }
        }
        // One frame to compose and lay out; the animations start from here.
        compose.mainClock.advanceTimeByFrame()
        if (interact != null) {
            compose.interact()
            compose.mainClock.advanceTimeByFrame()
        }
        var elapsed = 0L
        for (t in times.sorted()) {
            compose.mainClock.advanceTimeBy(t - elapsed)
            elapsed = t.toLong()
            capture("$name-t$t")
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        val file = File(root, "${variant.dir}/$name.png")
        file.parentFile?.mkdirs()
        // The capture shows the window as it was last drawn, and a layer whose properties changed in
        // the last frame is not always in that picture yet (a digit or a dish name fading in would be
        // missing from a motion frame). Draw the window once more first; the clock does not move.
        redraw()
        for (attempt in 1..CAPTURE_ATTEMPTS) {
            captureScreenRoboImage(
                file = file,
                roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
            )
            if (!isBlank(file)) break
            // Now and then the hardware renderer hands back the window background alone, without
            // the composed content (more often under load, two test JVMs at once). Draw the window
            // again and take the picture again. A frame that really is empty (the first instant of
            // an entrance) stays empty and is kept after the last attempt.
            System.err.println("[shoot] ${variant.dir}/$name: blank capture, attempt $attempt")
            redraw()
        }
        checkTouchTargets(name)
    }

    /** Lays out and draws the window again without advancing the compose clock. */
    private fun redraw() {
        compose.runOnUiThread {
            compose.activity.window.decorView.apply {
                requestLayout()
                invalidate()
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** True when every sampled pixel has the same colour: the background only. */
    private fun isBlank(file: File): Boolean {
        val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return false
        val first = image.getRGB(0, 0)
        for (y in 0 until image.height step 7) {
            for (x in 0 until image.width step 7) {
                if (image.getRGB(x, y) != first) return false
            }
        }
        return true
    }

    /**
     * Every node that can be clicked must be at least 48 x 48 dp to touch (Material's minimum),
     * whatever it draws: the layout grows with minimumInteractiveComponentSize() or padding.
     * Violations are a report, not a failure: one line each in `<screenshots dir>/touch-targets.txt`
     * (shoot.sh copies it next to the PNGs). Zero lines is the goal.
     */
    private fun checkTouchTargets(name: String) {
        val density = compose.activity.resources.displayMetrics.density
        val nodes = runCatching {
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        }.getOrDefault(emptyList())
        val lines = nodes.mapNotNull { node ->
            val shown = node.boundsInRoot
            if (shown.width <= 0f || shown.height <= 0f) return@mapNotNull null // off screen
            // The whole layout node, not touchBoundsInRoot: Compose widens every pointer target to
            // 48 dp when it hit-tests, but neighbours then share that area, so it says nothing. The
            // layout node includes minimumInteractiveComponentSize() (the semantics coordinator
            // alone would not), which is what makes a real 48 dp target.
            val w = max(node.size.width, node.layoutInfo.width) / density
            val h = max(node.size.height, node.layoutInfo.height) / density
            if (w >= 47.5f && h >= 47.5f) return@mapNotNull null
            // The name is user text (it may hold a %), so it is appended, not formatted.
            "${variant.dir}/$name: ${describe(node)} " + "%.0f x %.0f dp at (%.0f, %.0f) dp".format(
                Locale.US, w, h, shown.left / density, shown.top / density,
            )
        }
        appendReport(lines)
    }

    private fun describe(node: SemanticsNode): String {
        val c = node.config
        val label = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            ?: c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: c.getOrNull(SemanticsProperties.TestTag)
            ?: "(unnamed)"
        val role = c.getOrNull(SemanticsProperties.Role)?.toString() ?: "clickable"
        return "[$role] \"${label.take(60)}\""
    }

    // Two test JVMs run at once: a file lock keeps their lines whole. The file always exists after
    // a run, so a clean run never leaves an old report in shoot.sh's output folder.
    private fun appendReport(lines: List<String>) {
        root.mkdirs()
        RandomAccessFile(File(root, "touch-targets.txt"), "rw").use { file ->
            file.channel.lock().use {
                if (lines.isNotEmpty()) {
                    file.seek(file.length())
                    file.write(lines.joinToString("\n", postfix = "\n").toByteArray())
                }
            }
        }
    }

    /** A string in the shot's language (for finding nodes to click). */
    protected fun str(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    /**
     * A top-level tab exactly as AppNavHost frames it: the screen fills the window and the floating
     * tab bar sits over it at the bottom, with [route] selected (the bar takes no layout space).
     */
    @Composable
    protected fun TabFrame(route: String, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            Box(Modifier.align(Alignment.BottomCenter)) {
                AppBottomBar(currentRoute = route, onSelectTab = {}, onQuickAdd = {})
            }
        }
    }

    companion object {
        private const val CAPTURE_ATTEMPTS = 3

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = Variant.entries.map { arrayOf(it) }
    }
}
