package com.nutricart.app.ui.shopping

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.domain.model.Aisle
import com.nutricart.app.ui.common.aisleLabel
import com.nutricart.app.ui.ember.Bar
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CheckDisc
import com.nutricart.app.ui.ember.CheckState
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.DisabledAlpha
import com.nutricart.app.ui.ember.Digits
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LocalInCardHead
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.ember.pressScale
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.fridge.FridgeHalfList
import com.nutricart.app.ui.mealplan.GroupHead
import com.nutricart.app.ui.mealplan.capitalized
import com.nutricart.app.ui.mealplan.currentLocale
import com.nutricart.app.ui.mealplan.motionSpec
import com.nutricart.app.ui.mealplan.splitHint
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The "to buy" half of the fridge tab: day chips, aisle groups, checkboxes and
 * the export. It owns no chrome — the fridge screen above it draws the page
 * (including the share action), holds the toast and docks "Bought N" for it.
 */
@Composable
fun ShoppingBody(
    onMessage: (String) -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose { }
    }

    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val amountLabel = amountLabel()
    val copiedMessage = stringResource(R.string.copied_toast)
    val movedMessage = stringResource(R.string.fridge_moved_toast)

    ShoppingContent(
        state = state,
        onToggleDay = viewModel::toggleDay,
        onRegenerate = viewModel::regenerate,
        onSetChecked = viewModel::setChecked,
        onToggleHave = viewModel::toggleAlreadyHave,
        onMoveBought = {
            viewModel.moveBoughtToFridge()
            onMessage(movedMessage)
        },
        onCopy = {
            clipboard.setText(
                AnnotatedString(
                    viewModel.buildShareText(
                        aisleLabel = { aisleLabels.getValue(it) },
                        amountLabel = amountLabel,
                    )
                )
            )
            onMessage(copiedMessage)
        },
    )
}

/**
 * The stateless half of [ShoppingBody]: draws [state], forwards every tap. Its rows go into the
 * fridge page's list ([FridgeHalfList]), one item per aisle.
 *
 * The plan days as wrapping chips, Build / Update the list, how much of it is in the basket, then
 * the list by aisle. Ticking a row fills its disc with Ember from the centre while its check draws
 * and a line strikes through the name; the progress bar advances and "Bought N — put in the fridge"
 * rises in above the tab bar, its number handing off on each tick.
 */
@Composable
fun ShoppingContent(
    state: ShoppingUiState,
    onToggleDay: (epochDay: Long) -> Unit,
    onRegenerate: () -> Unit,
    onSetChecked: (item: ShoppingItemUi, checked: Boolean) -> Unit,
    onToggleHave: (item: ShoppingItemUi) -> Unit,
    onMoveBought: () -> Unit,
    onCopy: () -> Unit,
) {
    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val first = rememberFirstOpen("shopping")
    val entrances = rememberEntranceState()

    // "Bought N" docks above the tab bar, drawn by the fridge page; a stable click keeps the
    // published value equal from one composition to the next, so publishing it changes nothing.
    val dock = LocalShoppingDock.current
    val move by rememberUpdatedState(onMoveBought)
    val click = remember { { move() } }
    val bought = if (!state.loading && state.movableCount > 0) BoughtCta(state.movableCount, state.moving, click) else null
    if (dock != null) {
        SideEffect { dock.bought = bought }
        DisposableEffect(dock) { onDispose { dock.bought = null } }
    }

    val allItems = state.itemsByAisle.values.flatten()
    val toBuy = allItems.count { !it.alreadyHave }
    val inBasket = allItems.count { it.isChecked && !it.alreadyHave }

    // Lazy items in the fridge page's list, the page's 12 dp gap between them: the spacing below is
    // what each needs on top of that gap.
    FridgeHalfList {
        if (state.loading) {
            item(key = "shop/loading", contentType = "shop/loading") { ShoppingSkeleton() }
            return@FridgeHalfList
        }
        item(key = "shop/head", contentType = "shop/head") {
            Column(Modifier.fillMaxWidth()) {
                Column(Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "days")) {
                    GroupHead(stringResource(R.string.shopping_days_label), top = 0.dp, modifier = Modifier.padding(top = 2.dp))
                    DayChips(weekDays = state.weekDays, selected = state.selectedDays, onToggle = onToggleDay)
                }
                // Carrying the shopping into the fridge is an explicit action, never a side effect of
                // ticking a row: a silent write into another table is impossible to notice and to take back.
                EmberButton(
                    text = stringResource(if (state.hasList) R.string.shopping_regenerate else R.string.shopping_generate),
                    onClick = onRegenerate,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .emberEntrance(1, EntranceKind.FadeUp, first, entrances, "build"),
                    variant = ButtonVariant.Fill,
                    size = ButtonSize.Lg,
                    icon = EmberIcons.Refresh,
                    enabled = state.hasPlan,
                    loading = state.generating,
                )
            }
        }
        if (!state.hasPlan) {
            item(key = "shop/no-plan", contentType = "shop/no-plan") {
                val (lead, rest) = splitHint(stringResource(R.string.shopping_no_plan_hint), currentLocale())
                Notice(
                    NoticeTone.Info,
                    title = lead,
                    detail = rest,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .emberEntrance(2, EntranceKind.Rise, first, entrances, "no-plan"),
                )
            }
        }
        if (state.hasList && toBuy > 0) {
            item(key = "shop/progress", contentType = "shop/progress") {
                BasketProgress(
                    inBasket = inBasket,
                    total = toBuy,
                    modifier = Modifier
                        .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
                        .emberEntrance(2, EntranceKind.FadeUp, first, entrances, "progress"),
                )
            }
        }

        var section = 3
        state.itemsByAisle.forEach { (aisle, items) ->
            val index = section++
            // An aisle is one item, its card drawn whole: the list composes only the aisles on screen.
            item(key = "shop/aisle/${aisle.name}", contentType = "shop/aisle") {
                Column(Modifier.emberEntrance(index, EntranceKind.Rise, first, entrances, "aisle-$aisle")) {
                    GroupHead(aisleLabels.getValue(aisle))
                    InsetGroup {
                        items.forEach { item ->
                            // Keyed by the line: a rebuilt list must not hand one row's tick or
                            // strike to the item that takes its place.
                            key(item.id) {
                                row(dividerStart = 54.dp) {
                                    ShoppingRow(
                                        item = item,
                                        onChecked = { checked -> onSetChecked(item, checked) },
                                        onToggleHave = { onToggleHave(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Outside the fridge page (nothing to dock into) the button stays at the end of the list.
        if (dock == null && bought != null) {
            item(key = "shop/bought", contentType = "shop/bought") {
                Box(Modifier.padding(top = 4.dp)) { BoughtButton(bought) }
            }
        }

        if (state.hasList) {
            item(key = "shop/copy", contentType = "shop/copy") {
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    PlainLink(text = stringResource(R.string.copy_action), onClick = onCopy, icon = EmberIcons.Copy)
                }
            }
        }
    }
}

/** What the To-buy half docks above the tab bar: "Bought [count] — put in the fridge". */
@Immutable
internal data class BoughtCta(val count: Int, val moving: Boolean, val onClick: () -> Unit)

/** The To-buy half publishes its docked button here; the fridge page draws it (see FridgeContent). */
@Stable
internal class ShoppingDock {
    var bought: BoughtCta? by mutableStateOf(null)
}

/** Provided by the fridge page around the To-buy half; null elsewhere (the button then sits in the list). */
internal val LocalShoppingDock = staticCompositionLocalOf<ShoppingDock?> { null }

/**
 * The docked "Bought N" for the fridge page: it rises in on the sheet spring when the first item is
 * ticked and drops away when nothing is left to carry over.
 */
@Composable
internal fun ShoppingDockedButton(dock: ShoppingDock) {
    val current = dock.bought
    // Keep the last value while the button leaves, so it does not empty out on its way down.
    val shown = remember { mutableStateOf(current) }
    if (current != null) shown.value = current
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(motionSpec(EmberSprings.sheet())) { it } + fadeIn(motionSpec(tween(220))),
        exit = slideOutVertically(motionSpec(tween(240, easing = EmberEasing.In))) { it / 2 } +
            fadeOut(motionSpec(tween(200, easing = EmberEasing.In))),
    ) {
        shown.value?.let { BoughtButton(it) }
    }
}

/**
 * The ink capsule "Bought 3 — put in the fridge" (Lg, 54 dp), the count drawn with Digits so it
 * hands off on each tick. The label wraps between words at large text sizes. Disabled while moving.
 */
@Composable
internal fun BoughtButton(cta: BoughtCta) {
    val c = Ember.colors
    val t = Ember.type
    val full = stringResource(R.string.fridge_move_bought, cta.count)
    // The sentence around the number, from the string itself, so the number can be drawn on its own.
    val pattern = stringResource(R.string.fridge_move_bought)
    val parts = pattern.split("%1\$d")
    val source = remember { MutableInteractionSource() }
    val style = t.headline.copy(color = c.onInk)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, .96f)
            .heightIn(min = 54.dp)
            .emberShadow(Elevation.Float, EmberShapes.capsule, c.isDark)
            .graphicsLayer { alpha = if (cta.moving) DisabledAlpha else 1f }
            .clip(EmberShapes.capsule)
            .drawBehind { drawRect(c.ink) }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                enabled = !cta.moving,
                role = Role.Button,
                onClick = cta.onClick,
            )
            .clearAndSetSemantics {
                contentDescription = full
                role = Role.Button
            }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmberIcon(EmberIcons.Fridge, null, size = 20.dp, tint = c.onInk)
        if (parts.size != 2) {
            ControlText(full, style = style)
        } else {
            // Word by word, so the sentence wraps like text while its number stays a Digits.
            val space = with(LocalDensity.current) { (style.fontSize.toPx() * .27f).toDp() }
            FlowRow(
                Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(space, Alignment.CenterHorizontally),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                parts[0].split(' ').filter { it.isNotEmpty() }.forEach { ControlText(it, style = style) }
                Digits(cta.count.toLong(), style, color = c.onInk)
                parts[1].split(' ').filter { it.isNotEmpty() }.forEach { ControlText(it, style = style) }
            }
        }
    }
}

/** "3 of 10 in the basket" with an Ember bar: the ticked share of everything that is not at home already. */
@Composable
private fun BasketProgress(inBasket: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val text = stringResource(R.string.shopping_progress, inBasket, total)
    val brush = remember(c) { Brush.horizontalGradient(listOf(c.ember1, c.ember2)) }
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Bar(
            fraction = if (total > 0) inBasket / total.toFloat() else 0f,
            color = c.ember2,
            contentDescription = text,
            modifier = Modifier.weight(1f),
            brush = brush,
        )
        ControlText(
            text,
            Modifier.clearAndSetSemantics {},
            style = Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = c.label2,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The share icon for the fridge screen's app bar. It lives here, next to the
 * list it shares, and builds its own labels — Compose has the language context
 * here, so the exported text always matches what the screen shows.
 */
@Composable
fun ShareListAction(viewModel: ShoppingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val aisleLabels = Aisle.entries.associateWith { aisleLabel(it) }
    val amountLabel = amountLabel()

    ShareListButton(
        enabled = state.hasList,
        onClick = {
            val text = viewModel.buildShareText(
                aisleLabel = { aisleLabels.getValue(it) },
                amountLabel = amountLabel,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, null))
        },
    )
}

/** The stateless share icon of [ShareListAction]: a 44 dp white disc (48 dp to the finger) beside the title. */
@Composable
fun ShareListButton(enabled: Boolean, onClick: () -> Unit) {
    EmberIconButton(
        icon = EmberIcons.Share,
        contentDescription = stringResource(R.string.share_action),
        onClick = onClick,
        style = IconButtonStyle.Surface,
        enabled = enabled,
    )
}

/**
 * "250 g (~2 pcs)" in the phone's language, for both the screen and the export. Read through
 * LocalResources, so a language change recomposes the screen and hands the export new labels.
 */
@Composable
private fun amountLabel(): (Int, Int?) -> String {
    val resources = LocalResources.current
    return { grams, pieces ->
        resources.getString(R.string.grams_value, grams) +
            (pieces?.let { resources.getString(R.string.piece_hint, it) } ?: "")
    }
}

/** The 7 plan days as chips that wrap ("Sat 10"); at least one stays chosen (the ViewModel sees to it). */
@Composable
private fun DayChips(
    weekDays: List<Long>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    val locale = currentLocale()
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val labels = remember(weekDays, formatter) {
        weekDays.map { LocalDate.ofEpochDay(it).format(formatter).replace(".", "").capitalized(locale) }
    }
    ChipGroup(
        options = labels,
        selected = weekDays.indices.filter { weekDays[it] in selected }.toSet(),
        onToggle = { onToggle(weekDays[it]) },
        multiSelect = true,
        groupLabel = stringResource(R.string.shopping_days_label),
    )
}

/**
 * One line of the list. The whole row is the checkbox (named by the item, "Bought" or "Already at
 * home" as its state); "Have it" / "Need it" stays its own button. A ticked or "have it" row is struck
 * through, and a "have it" row cannot be ticked (there is nothing to buy).
 */
@Composable
private fun ShoppingRow(
    item: ShoppingItemUi,
    onChecked: (Boolean) -> Unit,
    onToggleHave: () -> Unit,
) {
    val c = Ember.colors
    val t = Ember.type
    val struck = item.isChecked || item.alreadyHave
    val disc = when {
        item.alreadyHave -> CheckState.Have
        item.isChecked -> CheckState.On
        else -> CheckState.Off
    }
    val haveState = stringResource(R.string.shopping_have_state)
    val tickedState = stringResource(R.string.shopping_ticked_state)
    val source = remember { MutableInteractionSource() }
    val amount = stringResource(R.string.grams_value, item.displayGrams) +
        (item.pieces?.let { stringResource(R.string.piece_hint, it) } ?: "")
    val inFridge = stringResource(R.string.fridge_in_stock_note)
    val sub = buildAnnotatedString {
        append(amount)
        if (item.movedToFridge) {
            append(" · ")
            withStyle(SpanStyle(color = c.good, fontWeight = FontWeight.SemiBold)) { append(inFridge) }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .toggleable(
                value = item.isChecked,
                interactionSource = source,
                indication = EmberIndication(RectangleShape, c.focus, pressed = c.fill),
                enabled = !item.alreadyHave, // nothing to buy -> nothing to tick
                role = Role.Checkbox,
                onValueChange = onChecked,
            )
            .semantics {
                when {
                    item.alreadyHave -> stateDescription = haveState
                    item.isChecked -> stateDescription = tickedState
                }
            }
            // "Have it" / "Need it" ends 14 dp in from the card's edge, like the web's plain button
            // (the link itself has no side padding once its words are wider than 48 dp).
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckDisc(disc)
        // A "have it" row is not dimmed (the web fades it to 62%): what is at home and how much is
        // still worth reading, and label 2 holds 4.5:1 only at full strength. The strike, the fridge
        // disc and "Need it" already say that there is nothing to buy.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            StruckText(item.name, struck, t.callout.copy(color = if (struck) c.label2 else c.label))
            BasicText(sub, style = t.footnote.copy(color = c.label2))
        }
        // The web's smaller plain button inside a row (15 sp).
        CompositionLocalProvider(LocalInCardHead provides true) {
            PlainLink(
                text = stringResource(if (item.alreadyHave) R.string.need_it_action else R.string.already_have_action),
                onClick = onToggleHave,
            )
        }
    }
}

/**
 * The item's name with a strike line that draws through it from the start to the end when it is
 * ticked (320 ms) and goes at once when it is unticked. A name already struck when it appears is
 * drawn struck.
 */
@Composable
private fun StruckText(text: String, struck: Boolean, style: TextStyle) {
    val c = Ember.colors
    val reduced = Ember.motion.reduced
    val p = remember { Animatable(if (struck) 1f else 0f) }
    LaunchedEffect(struck) {
        when {
            !struck -> p.snapTo(0f)
            reduced -> p.snapTo(1f)
            else -> p.animateTo(1f, tween(320, easing = EmberEasing.Out))
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val line = c.label3
    BasicText(
        text,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            val v = p.value
            if (v <= 0f) return@drawWithContent
            val w = 1.5.dp.toPx()
            // Through the middle of the lower-case letters: the baseline less about half an x-height.
            val lift = style.fontSize.toPx() * .3f
            for (i in 0 until l.lineCount) {
                val y = l.getLineBaseline(i) - lift
                val x0 = l.getLineLeft(i)
                val x1 = l.getLineRight(i)
                drawLine(line, Offset(x0, y), Offset(x0 + (x1 - x0) * v, y), w, cap = StrokeCap.Round)
            }
        },
    )
}

/** The list at its final geometry while it loads (decorative; it says "Loading" once). */
@Composable
private fun ShoppingSkeleton() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonLine(80.dp, Modifier.padding(start = 16.dp, top = 4.dp), height = 12.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(7) { Skeleton(Modifier.size(width = 64.dp, height = 36.dp), EmberShapes.capsule) }
        }
        Skeleton(Modifier.fillMaxWidth().heightIn(min = 54.dp), EmberShapes.capsule)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.weight(1f).heightIn(min = 6.dp), EmberShapes.capsule)
            SkeletonLine(110.dp, height = 12.dp)
        }
        EmberCard(padding = PaddingValues(0.dp), modifier = Modifier.padding(top = 18.dp)) { SkeletonRows(4) }
    }
}
