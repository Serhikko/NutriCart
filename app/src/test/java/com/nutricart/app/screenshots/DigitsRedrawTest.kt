package com.nutricart.app.screenshots

import android.content.ComponentName
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.resetEmberMotionMemory
import com.nutricart.app.ui.theme.NutriCartTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.time.Duration
import javax.imageio.ImageIO

/**
 * The digits engine on a device's schedule. The screenshot rule's test dispatcher starts effects
 * before the first draw; on a phone they start after it (AndroidUiDispatcher posts them behind the
 * frame). A number that read its animation clocks only once its effect had started subscribed to
 * nothing in that first draw and, in its own layer (FittedNumber), kept the first picture for good:
 * the old value after a change, nothing at all after an arrival.
 *
 * Here a real activity runs with the paused main looper, so composition, effects and draws happen
 * frame by frame in device order. Each case lets the animation finish and compares the window with
 * the same number composed at rest: they must be the same picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class DigitsRedrawTest {

    private val dir: File = Files.createTempDirectory("digits-redraw").toFile()
    private var controller: ActivityController<ComponentActivity>? = null

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        resetEmberMotionMemory()
    }

    @After
    fun tearDown() {
        close()
        dir.deleteRecursively()
    }

    /** 870 → 709: the same shape, a digit-by-digit hand-off. It must end on 709, not stay on 870. */
    @Test
    fun handOffReachesTheNewValue() {
        val value = mutableLongStateOf(870L)
        show { Number(value.longValue) }
        frames(800)
        change(value, 709L)
        frames(1500)
        val after = picture("handoff")
        close()

        show { Number(709L) }
        frames(800)
        assertSamePicture(picture("handoff-rest"), after)
    }

    /** 709 → 1,000: a new shape, so the number arrives digit by digit. It must not stay blank. */
    @Test
    fun arrivalAfterAShapeChangeShowsTheNumber() {
        val value = mutableLongStateOf(709L)
        show { Number(value.longValue) }
        frames(800)
        change(value, 1_000L)
        frames(1500)
        val after = picture("arrival")
        close()

        show { Number(1_000L) }
        frames(800)
        assertSamePicture(picture("arrival-rest"), after)
    }

    /** The first open of the day: the hero numeral arrives. It must not stay blank. */
    @Test
    fun firstArrivalShowsTheNumber() {
        show { Number(855L, enter = true, delayMillis = 200) }
        frames(2000)
        val after = picture("enter")
        close()

        show { Number(855L) }
        frames(800)
        assertSamePicture(picture("enter-rest"), after)
    }

    /** Back from Add: the number appears handing off from the value shown before (940 → 855). */
    @Test
    fun handOffFromThePreviousValueReachesTheNewValue() {
        show { Number(855L, from = 940L, delayMillis = 240) }
        frames(2000)
        val after = picture("from")
        close()

        show { Number(855L) }
        frames(800)
        assertSamePicture(picture("from-rest"), after)
    }

    @Composable
    private fun Number(value: Long, enter: Boolean = false, delayMillis: Int = 0, from: Long? = null) {
        Box(Modifier.fillMaxSize().background(Ember.colors.bg), contentAlignment = Alignment.Center) {
            FittedNumber(
                value = value,
                numberStyle = Ember.type.heroNumeral,
                enter = enter,
                delayMillis = delayMillis,
                from = from,
            )
        }
    }

    private fun show(content: @Composable () -> Unit) {
        val created = Robolectric.buildActivity(ComponentActivity::class.java).create()
        created.get().setContent { NutriCartTheme { content() } }
        created.start().resume().visible()
        controller = created
    }

    private fun close() {
        controller?.pause()?.stop()?.destroy()
        controller = null
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun change(state: MutableLongState, to: Long) {
        state.longValue = to
        Snapshot.sendApplyNotifications()
    }

    /** Runs [ms] of 16 ms frames on the main looper, as Choreographer would on a phone. */
    private fun frames(ms: Int) {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(ms / 16) { looper.idleFor(Duration.ofMillis(16)) }
    }

    /** The window as it was last drawn (no extra layout or draw pass, so a stale layer stays stale). */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun picture(name: String): BufferedImage {
        val file = File(dir, "$name.png")
        captureScreenRoboImage(file = file, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        return ImageIO.read(file)
    }

    private fun assertSamePicture(expected: BufferedImage, actual: BufferedImage) {
        assertEquals("width", expected.width, actual.width)
        assertEquals("height", expected.height, actual.height)
        var differing = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                if (expected.getRGB(x, y) != actual.getRGB(x, y)) differing++
            }
        }
        assertEquals("pixels that differ from the number at rest", 0, differing)
    }
}
