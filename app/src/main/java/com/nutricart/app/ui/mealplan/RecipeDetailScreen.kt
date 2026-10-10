package com.nutricart.app.ui.mealplan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.data.repository.RecipeDetails
import com.nutricart.app.ui.common.CookedPortionsDialog
import com.nutricart.app.ui.ember.BackLink
import com.nutricart.app.ui.ember.BottomClearance
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CompositionBar
import com.nutricart.app.ui.ember.ControlText
import com.nutricart.app.ui.ember.Elevation
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberIndication
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LargeTitleScaffold
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.Skeleton
import com.nutricart.app.ui.ember.SkeletonLine
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.WholeWordsText
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberShadow
import com.nutricart.app.ui.ember.pressScale
import com.nutricart.app.ui.ember.rememberDecimalFormat
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberIntegerFormat
import com.nutricart.app.ui.navigation.LocalBackLabel
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Ingredients (scaled to the chosen portion) and cooking steps of a recipe. */
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
    backLabel: String = LocalBackLabel.current ?: stringResource(R.string.back),
) {
    val state by viewModel.uiState.collectAsState()
    RecipeDetailContent(state = state, onBack = onBack, onCook = viewModel::cook, backLabel = backLabel)
}

/**
 * The stateless half of [RecipeDetailScreen]; it owns only the "cooked" sheet.
 *
 * An Ember page under "‹ Meal plan": the dish as the large title, its kcal for this portion in big
 * gradient numerals over a soft glow, the macros, the ingredients and the steps as inset lists, and
 * "I cooked this" docked at the bottom; once cooked it turns into the green "Taken out of the
 * fridge" with its check drawing on. A recipe id the book no longer has says so and offers the way
 * back, instead of loading forever.
 */
@Composable
fun RecipeDetailContent(
    state: RecipeDetailUiState,
    onBack: () -> Unit,
    onCook: (portions: Int) -> Unit,
    backLabel: String = LocalBackLabel.current ?: stringResource(R.string.back),
) {
    var showCooked by rememberSaveable { mutableStateOf(false) }

    if (showCooked) {
        CookedPortionsDialog(
            onConfirm = { portions ->
                onCook(portions)
                showCooked = false
            },
            onDismiss = { showCooked = false },
        )
    }

    val details = state.details
    // The ViewModel answers once: no details after loading means the id is not in the recipe book.
    val missing = !state.loading && details == null
    val first = rememberFirstOpen("recipe")
    val entrances = rememberEntranceState()
    val title = when {
        details != null -> details.nutrition.name
        missing -> stringResource(R.string.recipe_missing_title)
        else -> ""
    }

    LargeTitleScaffold(
        title = title,
        back = BackLink(backLabel, onBack),
        bottom = if (details != null) BottomClearance.DockedCta else BottomClearance.Stacked,
        dockedCta = if (details != null) {
            { CookButton(cooked = state.cooked, onClick = { showCooked = true }) }
        } else {
            null
        },
    ) {
        when {
            details != null -> recipeBody(details, state.portionFactor, first, entrances)
            missing -> item(key = "missing", contentType = "missing") {
                MissingRecipe(backLabel, onBack, Modifier.emberEntrance(0, EntranceKind.Rise, first, entrances, "missing"))
            }
            else -> item(key = "loading", contentType = "loading") { RecipeSkeleton() }
        }
    }
}

private fun LazyListScope.recipeBody(
    details: RecipeDetails,
    factor: Double,
    first: Boolean,
    entrances: EntranceState,
) {
    val n = details.nutrition
    item(key = "hero", contentType = "hero") {
        RecipeHero(
            kcal = (n.kcal * factor).roundToInt(),
            cookMinutes = n.cookTimeMin,
            factor = factor,
            first = first,
            modifier = Modifier.emberEntrance(0, EntranceKind.FadeUp, first, entrances, "hero"),
        )
    }
    item(key = "macros", contentType = "macros") {
        MacrosCard(
            proteinG = (n.proteinG * factor).roundToInt(),
            fatG = (n.fatG * factor).roundToInt(),
            carbsG = (n.carbsG * factor).roundToInt(),
            first = first,
            modifier = Modifier
                .padding(top = 6.dp)
                .emberEntrance(1, EntranceKind.Rise, first, entrances, "macros"),
        )
    }
    item(key = "ingredients", contentType = "list") {
        Column(Modifier.emberEntrance(2, EntranceKind.Rise, first, entrances, "ingredients")) {
            GroupHead(
                "${stringResource(R.string.ingredients_title)} · ${rememberIntegerFormat().format(details.ingredients.size.toLong())}",
                Modifier.padding(top = 6.dp),
            )
            InsetGroup {
                details.ingredients.forEach { ingredient ->
                    // Scale to the portion, then round: big amounts to 5 g ("87.3 g" is silly), small
                    // ones to whole grams — 2 g of salt must never display as "0 g".
                    val scaled = ingredient.grams * factor
                    val grams = if (scaled < 10.0) scaled.roundToInt().coerceAtLeast(1) else roundTo5(scaled)
                    val pieces = ingredient.gramsPerPiece?.let { ceil(ingredient.grams * factor / it).toInt() }
                    row { IngredientRow(ingredient.name, grams, pieces) }
                }
            }
        }
    }
    item(key = "steps", contentType = "list") {
        Column(Modifier.emberEntrance(3, EntranceKind.Rise, first, entrances, "steps")) {
            GroupHead(stringResource(R.string.steps_title), Modifier.padding(top = 6.dp))
            InsetGroup {
                details.steps.forEachIndexed { index, step ->
                    row(dividerStart = 58.dp) { StepRow(index + 1, step) }
                }
            }
        }
    }
}

/** "For your portion (×1.20)", the kcal in 64 sp gradient numerals and the meta chips, over a soft glow. */
@Composable
private fun RecipeHero(kcal: Int, cookMinutes: Int, factor: Double, first: Boolean, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val t = Ember.type
    Column(
        modifier
            .fillMaxWidth()
            // The glow sits behind the number, leaning to the start like the web's stage light.
            .drawBehind {
                val r = size.width * .62f
                drawCircle(
                    Brush.radialGradient(
                        0f to c.emberGlow,
                        .55f to c.emberGlow.copy(alpha = c.emberGlow.alpha * .45f),
                        1f to Color.Transparent,
                        center = Offset(size.width * .26f, size.height * .42f),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(size.width * .26f, size.height * .42f),
                )
            }
            .padding(top = 2.dp, bottom = 6.dp),
    ) {
        if (factor != 1.0) {
            ControlText(stringResource(R.string.per_portion_note, factor), style = t.subhead, color = c.label2)
        }
        FittedNumber(
            value = kcal.toLong(),
            numberStyle = t.display.copy(fontSize = 64.sp),
            unit = stringResource(R.string.kcal_unit),
            minFontSize = 40.sp,
            gradient = true,
            enter = first,
            delayMillis = 340,
            unitSize = 20.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(
            Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetaChip(EmberIcons.Clock, stringResource(R.string.cook_time_value, cookMinutes))
            if (factor != 1.0) {
                MetaChip(
                    EmberIcons.Utensils,
                    stringResource(R.string.recipe_portion_chip, rememberDecimalFormat(2).format(factor)),
                    ember = true,
                )
            }
        }
    }
}

/** A 32 dp `surface` capsule with a small glyph: "35 min to cook", "×1.20 portion". */
@Composable
private fun MetaChip(icon: EmberIcons, text: String, ember: Boolean = false) {
    val c = Ember.colors
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .emberShadow(Elevation.Card, EmberShapes.capsule, c.isDark)
            .clip(EmberShapes.capsule)
            .background(c.surface)
            .border(.5.dp, c.sep, EmberShapes.capsule)
            .padding(start = 9.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (ember) {
            EmberIcon(icon, null, size = 16.dp, brush = EmberBrushes.emberIcon(c))
        } else {
            EmberIcon(icon, null, size = 16.dp, tint = c.label2)
        }
        ControlText(text, style = t13(), color = c.label)
    }
}

@Composable
private fun t13() = Ember.type.subhead.copy(fontSize = 13.5.sp, lineHeight = 1.2.em)

/**
 * Protein, fat and carbs of this portion: the composition bar and three columns. One TalkBack stop
 * that reads the recipe's macro line ("Protein 50 g · Fat 14 g · Carbs 82 g").
 */
@Composable
private fun MacrosCard(proteinG: Int, fatG: Int, carbsG: Int, first: Boolean, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val line = stringResource(R.string.recipe_macros_line, proteinG, fatG, carbsG)
    EmberCard(modifier.clearAndSetSemantics { contentDescription = line }) {
        CompositionBar(
            proteinKcal = proteinG * 4f,
            fatKcal = fatG * 9f,
            carbsKcal = carbsG * 4f,
            growDelayMillis = if (first) 520 else null,
        )
        val protein = stringResource(R.string.summary_protein)
        val fat = stringResource(R.string.summary_fat)
        val carbs = stringResource(R.string.summary_carbs)
        if (LocalDensity.current.fontScale >= 1.5f) {
            // Twice the text size cannot share a row three ways: one macro per line, its grams at the end.
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroLine(protein, proteinG, c.protein)
                MacroLine(fat, fatG, c.fat)
                MacroLine(carbs, carbsG, c.carbs)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroColumn(protein, proteinG, c.protein, Modifier.weight(1f))
                MacroColumn(fat, fatG, c.fat, Modifier.weight(1f))
                MacroColumn(carbs, carbsG, c.carbs, Modifier.weight(1f))
            }
        }
    }
}

/** The macro's name beside its dot; a long one ("Вуглеводи" at 1.3) steps its size down instead of losing letters. */
@Composable
private fun MacroLabel(label: String, color: Color, modifier: Modifier = Modifier) {
    val c = Ember.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, EmberShapes.circle))
        WholeWordsText(label, Ember.type.footnote.copy(fontWeight = FontWeight.SemiBold, color = c.label2))
    }
}

@Composable
private fun MacroGrams(grams: Int) {
    val t = Ember.type
    NumberWithUnit(
        value = grams.toLong(),
        unit = stringResource(R.string.plan_unit_g),
        numberStyle = t.statSmall.copy(fontSize = 22.sp),
        unitSize = 13.sp,
    )
}

@Composable
private fun MacroColumn(label: String, grams: Int, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        MacroLabel(label, color)
        MacroGrams(grams)
    }
}

/** The stacked form for large text: "• Protein … 50 g" on one line. */
@Composable
private fun MacroLine(label: String, grams: Int, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MacroLabel(label, color, Modifier.weight(1f))
        MacroGrams(grams)
    }
}

/** "Морква … 70 g (~1 pcs)": the name, then the amount and the piece hint at the end. */
@Composable
private fun IngredientRow(name: String, grams: Int, pieces: Int?) {
    val c = Ember.colors
    val t = Ember.type
    ListRow(
        title = name,
        minHeight = 50.dp,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NumberWithUnit(
                    value = grams.toLong(),
                    unit = stringResource(R.string.plan_unit_g),
                    numberStyle = t.rowNumber.copy(fontSize = 15.sp),
                    unitSize = 12.sp,
                )
                if (pieces != null) {
                    ControlText(
                        stringResource(R.string.piece_hint, pieces).trim(),
                        style = t.footnote.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        color = c.label2,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        },
    )
}

/** A numbered step: a 28 dp disc with the number, then the text (16 sp, 1.4 line height). */
@Composable
private fun StepRow(number: Int, text: String) {
    val c = Ember.colors
    val t = Ember.type
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(28.dp).background(c.fill, EmberShapes.circle), contentAlignment = Alignment.Center) {
            ControlText(
                rememberIntegerFormat().format(number.toLong()),
                style = t.rowNumber.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = c.label,
            )
        }
        ControlText(
            text,
            Modifier.weight(1f).padding(top = 3.dp),
            style = t.callout.copy(lineHeight = 1.4.em),
            color = c.label,
        )
    }
}

/** The check of EmberIcons.Check on the 24 grid, drawn on progressively by the done button. */
private val CheckPath: Path = PathParser().parsePathString("M5 12.8 9.4 17 19 7.2").toPath()

/**
 * The docked "I cooked this" (Pot, ink). Once cooked it becomes the green "Taken out of the fridge"
 * capsule: the colour moves on the snappy spring, the label cross-fades and the check draws itself.
 * It stays disabled then (cooking counts once per visit).
 */
@Composable
private fun CookButton(cooked: Boolean, onClick: () -> Unit) {
    val c = Ember.colors
    val t = Ember.type
    val reduced = Ember.motion.reduced
    // goodSoft is a tint; laid over the page colour it stays readable over the list scrolling under it.
    val doneBg = c.goodSoft.compositeOver(c.bg)
    val bg by animateColorAsState(if (cooked) doneBg else c.ink, motionSpec(EmberSprings.snappy()), label = "cookBg")
    val fg by animateColorAsState(if (cooked) c.good else c.onInk, motionSpec(EmberSprings.snappy()), label = "cookFg")
    val draw = remember { Animatable(if (cooked) 1f else 0f) }
    LaunchedEffect(cooked) {
        if (cooked && draw.value < 1f) {
            if (reduced) draw.snapTo(1f) else draw.animateTo(1f, tween(420, 220, EmberEasing.Out))
        }
    }
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, .96f)
            .heightIn(min = 54.dp)
            .emberShadow(Elevation.Float, EmberShapes.capsule, c.isDark)
            .clip(EmberShapes.capsule)
            .drawBehind { drawRect(bg) }
            .clickable(
                interactionSource = source,
                indication = EmberIndication(EmberShapes.capsule, c.focus),
                enabled = !cooked,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = cooked,
            // The width moves on the snappy spring without clipping: the longer label is never cut
            // off while the content grows into it.
            transitionSpec = {
                (fadeIn(motionSpecFor(reduced, tween(220, 90))) togetherWith fadeOut(motionSpecFor(reduced, tween(120))))
                    .using(SizeTransform(clip = false) { _, _ -> motionSpecFor(reduced, EmberSprings.snappy(IntSize.VisibilityThreshold)) })
            },
            label = "cookLabel",
        ) { done ->
            // Each side keeps its own words: the leaving "I cooked this" must not turn into the new label.
            val label = stringResource(if (done) R.string.fridge_cooked_toast else R.string.fridge_cooked_action)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Canvas(Modifier.size(20.dp)) {
                        val part = Path()
                        val measure = PathMeasure().apply { setPath(CheckPath, false) }
                        measure.getSegment(0f, measure.length * draw.value, part, true)
                        val s = size.width / 24f
                        scale(s, s, pivot = Offset.Zero) {
                            drawPath(part, fg, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                } else {
                    EmberIcon(EmberIcons.Pot, null, size = 20.dp, tint = fg)
                }
                WholeWordsText(label, t.headline.copy(color = fg, textAlign = TextAlign.Center))
            }
        }
    }
}

/** A finite spec, or a jump when animations are removed (for transition lambdas, outside composition). */
private fun <T> motionSpecFor(reduced: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (reduced) snap() else spec

/** The recipe book has no dish with this id: say so calmly and offer the way back. */
@Composable
private fun MissingRecipe(backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    EmberCard(modifier, padding = PaddingValues(horizontal = 6.dp)) {
        EmptyState(
            title = stringResource(R.string.recipe_missing_lead),
            icon = EmberIcons.Bowl,
            body = stringResource(R.string.recipe_missing_body),
            action = {
                EmberButton(
                    text = backLabel,
                    onClick = onBack,
                    variant = ButtonVariant.Fill,
                    size = ButtonSize.Md,
                    icon = EmberIcons.Left,
                )
            },
        )
    }
}

/**
 * The recipe at its final geometry while it loads: the title's two lines (drawn up into the title's
 * place, which is blank until the name arrives), the kcal, the macros card and a list.
 */
@Composable
private fun RecipeSkeleton() {
    val loading = stringResource(R.string.loading)
    Column(
        Modifier
            .semantics(mergeDescendants = true) { contentDescription = loading }
            // Pull the first lines up into the blank large-title line above this item.
            .layout { measurable, constraints ->
                val pull = TitlePull.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) { placeable.place(0, -pull) }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonLine(280.dp, height = 30.dp)
        SkeletonLine(180.dp, height = 30.dp)
        SkeletonLine(150.dp, Modifier.padding(top = 18.dp), height = 14.dp)
        SkeletonLine(190.dp, height = 56.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.size(width = 130.dp, height = 32.dp), EmberShapes.capsule)
            Skeleton(Modifier.size(width = 110.dp, height = 32.dp), EmberShapes.capsule)
        }
        Skeleton(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(top = 6.dp))
        EmberCard(padding = PaddingValues(0.dp)) { SkeletonRows(4) }
    }
}

/** How far the loading lines reach up into the large title's blank line (its row and the gaps). */
private val TitlePull = 58.dp

private fun roundTo5(value: Double): Int = ((value / 5.0).roundToInt() * 5)
