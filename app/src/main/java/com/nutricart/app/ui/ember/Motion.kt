package com.nutricart.app.ui.ember

import android.annotation.SuppressLint
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// Ember motion: the web's springs and curves, the first-open / revisit rule, and reduced motion
// ("Remove animations"). Physical but restrained: input is never blocked by animation, entrances
// end on the element's real state (so cancelling is always safe), exits are shorter than entrances.

// ---------------------------------------------------------------------------------------------
// Springs and curves
// ---------------------------------------------------------------------------------------------

/**
 * The web's springs (damped step responses stretched to a duration) as Compose springs with the same
 * curve: stiffness = (settle time / duration)², mass 1. Use them for anything interruptible.
 */
object EmberSprings {
    /** ζ 1.00, 680 ms: entrances, card rise, page content, restore after a sheet. */
    fun <T> smooth(threshold: T? = null): SpringSpec<T> = spring(1.00f, 185f, threshold)
    /** ζ 0.82, 480 ms: buttons, thumbs, tab pill edges, chips, digits, toggles. */
    fun <T> snappy(threshold: T? = null): SpringSpec<T> = spring(0.82f, 323f, threshold)
    /** ζ 0.82, 1150 ms: the ring sweep. */
    fun <T> ring(threshold: T? = null): SpringSpec<T> = spring(0.82f, 56f, threshold)
    /** ζ 0.82, 900 ms: macro bars fill. */
    fun <T> bars(threshold: T? = null): SpringSpec<T> = spring(0.82f, 92f, threshold)
    /** ζ 0.82, 760 ms: chart bars rise. */
    fun <T> rise(threshold: T? = null): SpringSpec<T> = spring(0.82f, 129f, threshold)
    /** ζ 0.76, 600 ms: sheets up, the page recede, shared-ring bounds. */
    fun <T> sheet(threshold: T? = null): SpringSpec<T> = spring(0.76f, 182f, threshold)
    /** ζ 0.66, 620 ms, 6.3% overshoot: pops only (zone badge, toast, day markers, water glass, + release). */
    fun <T> bouncy(threshold: T? = null): SpringSpec<T> = spring(0.66f, 280f, threshold)
}

/** Piecewise-linear easing through evenly spaced samples (CSS linear()); the web's spring curves exactly. */
class SampledEasing(private val y: FloatArray) : Easing {
    override fun transform(fraction: Float): Float {
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val x = fraction * (y.size - 1)
        val i = x.toInt()
        return y[i] + (y[i + 1] - y[i]) * (x - i)
    }
}

/** Curves for choreographed moments (a delay or an exact duration): tweens on the spring curves. */
object EmberEasing {
    /** Fades, rises, count changes. */
    val Out = CubicBezierEasing(.16f, 1f, .3f, 1f)
    /** Exits. */
    val In = CubicBezierEasing(.4f, 0f, 1f, 1f)
    /** Draws, wipes, the light pass. */
    val InOut = CubicBezierEasing(.65f, 0f, .35f, 1f)
    val Smooth = SampledEasing(floatArrayOf(0f, .034f, .115f, .215f, .321f, .423f, .517f, .6f, .671f, .732f, .783f, .826f, .86f, .889f, .911f, .93f, .945f, .956f, .966f, .973f, .979f, .984f, .987f, .99f, .992f, .994f, .995f, .996f, .997f, .998f, .998f, .999f, 1f))
    val Snappy = SampledEasing(floatArrayOf(0f, .025f, .088f, .174f, .271f, .371f, .468f, .559f, .641f, .713f, .776f, .828f, .872f, .908f, .936f, .958f, .975f, .988f, .997f, 1.003f, 1.007f, 1.01f, 1.011f, 1.011f, 1.011f, 1.01f, 1.009f, 1.008f, 1.007f, 1.006f, 1.005f, 1.004f, 1.003f, 1.002f, 1.002f, 1.001f, 1f))
    val Sheet = SampledEasing(floatArrayOf(0f, .018f, .067f, .135f, .215f, .302f, .391f, .477f, .559f, .635f, .703f, .764f, .817f, .862f, .901f, .932f, .958f, .978f, .994f, 1.005f, 1.014f, 1.02f, 1.023f, 1.025f, 1.025f, 1.025f, 1.023f, 1.022f, 1.02f, 1.017f, 1.015f, 1.013f, 1.011f, 1.009f, 1.007f, 1.006f, 1.004f, 1.003f, 1.002f, 1.002f, 1f))
    val Bouncy = SampledEasing(floatArrayOf(0f, .03f, .106f, .211f, .33f, .453f, .571f, .679f, .774f, .854f, .919f, .97f, 1.008f, 1.035f, 1.051f, 1.06f, 1.063f, 1.062f, 1.057f, 1.051f, 1.043f, 1.036f, 1.028f, 1.021f, 1.015f, 1.01f, 1.006f, 1.002f, 1f, .998f, .997f, .996f, .996f, .996f, .996f, .997f, .997f, .998f, .998f, .999f, 1f))
}

object EmberDurations {
    const val Press = 160
    const val State = 220
    const val Revisit = 300
    const val Rise = 680
    const val Ring = 1150
    const val Digit = 520
    const val DigitStagger = 40
    const val Toast = 2600
    const val ToastAction = 5000
}

// ---------------------------------------------------------------------------------------------
// Reduced motion: Android's "Remove animations" (ANIMATOR_DURATION_SCALE == 0)
// ---------------------------------------------------------------------------------------------

@Immutable
data class EmberMotion(
    /** ANIMATOR_DURATION_SCALE == 0: decorative motion (light pass, halos, recede…) is not drawn at all. */
    val reduced: Boolean = false,
    /** The animator scale itself (0.5, 1, 1.5…), informational. */
    val scale: Float = 1f,
    /** Test override for [rememberFirstOpen]: true = always the full build, false = always a revisit. */
    val firstOpen: Boolean? = null,
)

/** Reads "Remove animations" and follows it while the app runs (the switch can change at any time). */
@Composable
fun rememberEmberMotion(): EmberMotion {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return remember(scale) { EmberMotion(reduced = scale == 0f, scale = scale) }
}

/**
 * delay() that respects the animator scale: Compose scales its animations by itself, but not
 * delay(). Use it (or a tween's delayMillis) for every choreographed wait; at scale 0 it returns at once.
 */
suspend fun motionDelay(millis: Long) {
    val s = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
    if (s > 0f) delay((millis * s).toLong())
}

/**
 * [spec], or an instant jump under "Remove animations". On a device Compose already scales its own
 * animations to nothing there; the controls also gate explicitly, so the result never depends on
 * which clock drives them (the thumb, the knob and the chips simply jump).
 */
@Composable
@ReadOnlyComposable
fun <T> emberSpec(spec: AnimationSpec<T>): AnimationSpec<T> = if (Ember.motion.reduced) snap() else spec

/**
 * The same for the transitions that need a finite spec (AnimatedVisibility, AnimatedContent,
 * animateContentSize).
 */
@Composable
@ReadOnlyComposable
fun <T> emberSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (Ember.motion.reduced) snap() else spec

/** The same outside composition (a transition lambda), with [reduced] read from Ember.motion beforehand. */
fun <T> emberSpec(reduced: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (reduced) snap() else spec

// ---------------------------------------------------------------------------------------------
// First open of the day, revisits, remembered values
// ---------------------------------------------------------------------------------------------

// Process-wide memory. Both survive leaving a screen (Today leaves composition while Food search is
// open), which is the point: "back from Add" sweeps from the value the ring showed before.
private val openedToday: MutableSet<String> = ConcurrentHashMap.newKeySet()
private val lastShown = ConcurrentHashMap<String, Any>()

/**
 * True for the first composition of [key] in this process today, false afterwards. It stays true for
 * the whole first visit (so data that arrives late still builds); every later visit gets the calm
 * revisit entrance. Keys: today, stats-week, stats-month, stats-90, diary, plan, recipe, fridge,
 * shopping, settings, onboarding.
 */
@Composable
fun rememberFirstOpen(key: String): Boolean {
    val override = LocalEmberMotion.current.firstOpen
    return remember(key, override) {
        override ?: openedToday.add("$key/${LocalDate.now().toEpochDay()}")
    }
}

/**
 * The value [key] last showed anywhere in the process, or null the first time. Rings and digits take
 * it as their `from`. Written on every change. Keys: ring/<epochDay>, remaining/<epochDay>,
 * eaten/<epochDay>, water/<epochDay>, slot/<epochDay>/<slot>.
 */
@Composable
fun rememberLastShown(key: String, value: Float): Float? = rememberLastShownValue(key, value)

/** [rememberLastShown] for any value: a Long total, or a set of entry ids (the Diary's new-row glow). */
@Composable
fun <T : Any> rememberLastShownValue(key: String, value: T): T? {
    @Suppress("UNCHECKED_CAST")
    val previous = remember(key) { lastShown[key] as? T }
    SideEffect { lastShown[key] = value }
    return previous
}

/** Tests and previews: pretend [key] last showed [value] (a back-from-Add frame needs an old value). */
fun seedLastShown(key: String, value: Any) {
    lastShown[key] = value
}

/** Tests and previews: forget every first open and every remembered value. */
fun resetEmberMotionMemory() {
    openedToday.clear()
    lastShown.clear()
}

// ---------------------------------------------------------------------------------------------
// Entrances (G1–G4)
// ---------------------------------------------------------------------------------------------

enum class EntranceKind {
    /** Alpha, 18 dp rise and a .985 scale: cards. */
    Rise,
    /** Alpha and an 8 dp rise: lines of text, KPIs. */
    FadeUp,
    /** The large title rising out of its own clipped line. */
    Title,
}

/**
 * Which elements of one screen already played their entrance. Elements composed later by scrolling
 * a lazy list appear without one: entrances only start in the first moments of the screen (the
 * window closes by itself), and a key plays once, also across rotation (it is saved).
 */
@Stable
class EntranceState internal constructor(played: Collection<String>, open: Boolean) {
    private val played: MutableSet<String> = played.toMutableSet()
    internal var open: Boolean = open
        private set

    /** True when [key] may play now; it then counts as played. */
    fun claim(key: Any): Boolean = open && played.add(key.toString())

    internal fun close() {
        open = false
    }

    companion object {
        /** A first-open build is done by about 1.25 s; anything composed after that just appears. */
        internal const val WINDOW_MILLIS = 1500L

        val Saver: Saver<EntranceState, ArrayList<String>> = Saver(
            save = { ArrayList(it.played) },
            // A restored screen (rotation, back from a stacked screen) never replays.
            restore = { EntranceState(it, open = false) },
        )
    }
}

@Composable
fun rememberEntranceState(): EntranceState {
    val state = rememberSaveable(saver = EntranceState.Saver) { EntranceState(emptyList(), open = true) }
    LaunchedEffect(state) {
        motionDelay(EntranceState.WINDOW_MILLIS)
        state.close()
    }
    return state
}

/**
 * The G1–G4 entrances on first composition. [first] (from [rememberFirstOpen]) picks the full build:
 * Rise = alpha, 18 dp and .985 → 1 on tween(680, 300 + 60 × index, Smooth); FadeUp = 8 dp on
 * tween(560, …, Out); Title = translationY 105% → 0 on tween(760, 30, Smooth). Otherwise every kind
 * is the revisit entrance, one 300 ms fade-up of 8 dp with no stagger. Reduced motion: nothing, the
 * element is simply there. With [state] and [key], an element plays at most once per screen.
 */
fun Modifier.emberEntrance(
    index: Int,
    kind: EntranceKind = EntranceKind.Rise,
    first: Boolean,
    state: EntranceState? = null,
    key: Any? = null,
): Modifier = (if (kind == EntranceKind.Title) clipToBounds() else this)
    .then(EntranceElement(index.coerceIn(0, 8), kind, first, state, key))

private data class EntranceElement(
    val index: Int,
    val kind: EntranceKind,
    val first: Boolean,
    val state: EntranceState?,
    val key: Any?,
) : ModifierNodeElement<EntranceNode>() {
    override fun create() = EntranceNode(index, kind, first, state, key)

    // An entrance is decided once, when the element first appears; later changes do not restart it.
    override fun update(node: EntranceNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "emberEntrance"
        properties["index"] = index
        properties["kind"] = kind
        properties["first"] = first
    }
}

private class EntranceNode(
    private val index: Int,
    private val kind: EntranceKind,
    private val first: Boolean,
    private val state: EntranceState?,
    private val key: Any?,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    // null = no entrance: drawn at rest.
    private var progress: Animatable<Float, AnimationVector1D>? = null

    // The fading element is drawn through this layer while its entrance runs, then directly again.
    private var layer: GraphicsLayer? = null

    // The motion setting is read once, on attach, on purpose: an entrance is decided when the element
    // first appears, and a later change of the setting must neither restart nor hide it.
    @SuppressLint("SuspiciousCompositionLocalModifierRead")
    override fun onAttach() {
        if (progress != null) return
        if (currentValueOf(LocalEmberMotion).reduced) return
        if (state != null && key != null && !state.claim(key)) return
        val p = Animatable(0f)
        progress = p
        val spec: AnimationSpec<Float> = when {
            !first -> tween(EmberDurations.Revisit, 0, EmberEasing.Out)
            kind == EntranceKind.Title -> tween(760, 30, EmberEasing.Smooth)
            kind == EntranceKind.FadeUp -> tween(560, 300 + 60 * index, EmberEasing.Out)
            else -> tween(EmberDurations.Rise, 300 + 60 * index, EmberEasing.Smooth)
        }
        coroutineScope.launch {
            p.animateTo(1f, spec)
            releaseLayer()
        }
    }

    override fun onDetach() = releaseLayer()

    private fun releaseLayer() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun ContentDrawScope.draw() {
        val p = progress?.value
        if (p == null || p >= 1f) {
            drawContent()
            return
        }
        val rest = 1f - p
        if (first && kind == EntranceKind.Title) {
            // Clipped to its own line by the modifier in front of this one; no fade, so no layer.
            translate(top = size.height * 1.05f * rest) { this@draw.drawContent() }
            return
        }
        val rises = first && kind == EntranceKind.Rise
        // A fade of a whole element needs an offscreen buffer, and a buffer the element's own size would
        // cut off its shadow (a card's shadow reaches past its bounds) and then let it pop in when the
        // fade ends. So the element is recorded with room round it, and the shadow fades in with it.
        val room = ShadowRoom.roundToPx()
        val l = layer ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
        l.record(size = IntSize(size.width.roundToInt() + 2 * room, size.height.roundToInt() + 2 * room)) {
            translate(room.toFloat(), room.toFloat()) { this@draw.drawContent() }
        }
        l.topLeft = IntOffset(-room, -room)
        l.pivotOffset = Offset(room + size.width / 2f, room + size.height / 2f)
        l.alpha = p
        l.translationY = (if (rises) 18.dp else 8.dp).toPx() * rest
        val scale = if (rises) 1f - .015f * rest else 1f
        l.scaleX = scale
        l.scaleY = scale
        drawLayer(l)
    }
}

// Room round an entering element for its shadow: the floating shadow (32 dp blur, 12 dp down) fits.
private val ShadowRoom = 48.dp

// ---------------------------------------------------------------------------------------------
// Press feedback
// ---------------------------------------------------------------------------------------------

/**
 * Shrinks the element while [interaction] is pressed: in over 160 ms, back on [release] (snappy).
 * Buttons .96, chips .95, tabs .94; the + button .90 with `release = EmberSprings.bouncy()`.
 */
fun Modifier.pressScale(
    interaction: InteractionSource,
    scale: Float = .96f,
    release: AnimationSpec<Float> = EmberSprings.snappy(),
): Modifier = this.then(PressScaleElement(interaction, scale, release))

private data class PressScaleElement(
    val interaction: InteractionSource,
    val scale: Float,
    val release: AnimationSpec<Float>,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interaction, scale, release)

    override fun update(node: PressScaleNode) {
        node.scale = scale
        node.release = release
        node.updateInteraction(interaction)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["scale"] = scale
    }
}

private class PressScaleNode(
    private var interaction: InteractionSource,
    var scale: Float,
    var release: AnimationSpec<Float>,
) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {

    private val s = Animatable(1f)
    private var job: Job? = null

    override fun onAttach() = collect()

    fun updateInteraction(source: InteractionSource) {
        if (source == interaction) return
        interaction = source
        job?.cancel()
        if (isAttached) collect()
    }

    private fun collect() {
        job = coroutineScope.launch {
            interaction.interactions.collect { i ->
                // "Remove animations": the press still shows, but as a jump. Compose would finish the
                // animation at once on a device anyway; reading the setting keeps it so wherever the
                // animator scale is not applied to Compose (the screenshot harness).
                val reduced = currentValueOf(LocalEmberMotion).reduced
                when (i) {
                    is PressInteraction.Press -> launch {
                        if (reduced) s.snapTo(scale)
                        else s.animateTo(scale, tween(EmberDurations.Press, easing = EmberEasing.Out))
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                        if (reduced) s.snapTo(1f) else s.animateTo(1f, release)
                    }
                }
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val v = s.value
                scaleX = v
                scaleY = v
            }
        }
    }
}
