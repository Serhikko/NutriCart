package com.nutricart.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.nutricart.app.R
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CompositionBar
import com.nutricart.app.ui.ember.DateField
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberEasing
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSpace
import com.nutricart.app.ui.ember.EmberSprings
import com.nutricart.app.ui.ember.EntranceKind
import com.nutricart.app.ui.ember.EntranceState
import com.nutricart.app.ui.ember.FittedNumber
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.SectionHeader
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.ember.SwitchRow
import com.nutricart.app.ui.ember.WholeWordsText
import com.nutricart.app.ui.ember.emberEntrance
import com.nutricart.app.ui.ember.emberSpec
import com.nutricart.app.ui.ember.of
import com.nutricart.app.ui.ember.rememberEntranceState
import com.nutricart.app.ui.ember.rememberFirstOpen
import com.nutricart.app.ui.ember.rememberGutter
import com.nutricart.app.ui.ember.ringEntrance
import java.time.LocalDate

/**
 * Seven-step questionnaire. One Composable per step below; the ViewModel holds
 * all state, this file only draws it and forwards clicks. When finish() saves
 * the profile, AppRoot notices the "onboarding completed" flag and switches to
 * the main app — this screen never navigates by itself.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    OnboardingContent(state = state, actions = viewModel)
}

/** Everything the questionnaire can ask for; [OnboardingViewModel] implements it. */
interface OnboardingActions {
    fun selectSex(sex: Sex)
    fun selectBirthDate(date: LocalDate)
    fun setHeightText(text: String)
    fun setWeightText(text: String)
    fun selectActivityLevel(level: ActivityLevel)
    fun selectGoal(goal: Goal)
    fun selectRate(rate: Double)
    fun selectSnacksPerDay(count: Int)
    fun selectCookingSessions(sessions: Int)
    fun toggleVegetarian(enabled: Boolean)
    fun toggleNoPork(enabled: Boolean)
    fun toggleAllergen(allergen: Allergen)
    fun next()
    fun back()
    fun finish()
}

/** How far a step's content slides in from (or out to) the side when the step changes. */
private val StepShift = 24.dp

/**
 * The stateless half of [OnboardingScreen]: draws the current step of [state].
 *
 * The frame: the seven-segment progress row and "6 of 7" at the top, the step's large title and its
 * questions in the middle (they scroll), and Back / Next (Start on the last step) docked at the
 * bottom over a fade of the page colour. The docked buttons ride above the keyboard, so typing a
 * height never hides Next. A step change slides the content 24 dp in the direction of travel.
 */
@Composable
fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions) {
    val c = Ember.colors
    val density = LocalDensity.current
    val gutter = rememberGutter()
    val reduced = Ember.motion.reduced
    val first = rememberFirstOpen("onboarding")
    val entrances = rememberEntranceState()
    val statusTop = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    // The docked buttons' own height: the questions keep clear of them at the bottom of the scroll.
    var dockHeight by remember { mutableIntStateOf(0) }
    val dockDp = with(density) { dockHeight.toDp() }

    // The plan on the last step reveals once per arrival: going back and forward again replays it
    // (the numbers may have changed), a rotation on the plan does not.
    var planShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.step) {
        if (state.step != OnboardingUiState.STEP_SUMMARY) planShown = false
    }

    // Onboarding runs before the app shell, which is what makes the page recede behind a sheet: the
    // date picker's sheet gets the same receding page from a stage of its own here.
    OwnStage {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = statusTop + EmberSpace.LargeTitleTop),
            ) {
                StepProgress(
                    step = state.step,
                    count = OnboardingUiState.STEP_COUNT,
                    modifier = Modifier
                        .padding(horizontal = gutter)
                        .emberEntrance(0, EntranceKind.FadeUp, first, entrances, "progress"),
                )
                val shift = with(density) { StepShift.roundToPx() }
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                AnimatedContent(
                    targetState = state.step,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // The page ends above the keyboard, so a focused field can always scroll into view.
                        .imePadding(),
                    transitionSpec = {
                        if (reduced) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            // Forward: the new step comes in from the end side and the old one leaves
                            // towards the start; back is the mirror image. The old step is nearly gone
                            // before the new one shows, so the two titles never print over each other.
                            val dir = (if (targetState > initialState) 1 else -1) * (if (rtl) -1 else 1)
                            (
                                fadeIn(tween(360, 120, EmberEasing.Out)) +
                                    slideInHorizontally(tween(360, 120, EmberEasing.Smooth)) { dir * shift }
                                ) togetherWith (
                                fadeOut(tween(140, easing = EmberEasing.In)) +
                                    slideOutHorizontally(tween(140, easing = EmberEasing.In)) { -dir * shift }
                                ) using SizeTransform(clip = false)
                        }
                    },
                    label = "onboardingStep",
                ) { step ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = gutter)
                            .padding(
                                top = EmberSpace.s4,
                                // Clear of the docked buttons; with the keyboard open the page already
                                // ends above it (and the navigation bar is under the keyboard).
                                bottom = dockDp + EmberSpace.s4 + max(navBottom - imeBottom, 0.dp),
                            ),
                    ) {
                        StepTitle(step, first, entrances)
                        when (step) {
                            OnboardingUiState.STEP_SEX -> SexStep(state, actions::selectSex)
                            OnboardingUiState.STEP_BIRTH -> BirthDateStep(state, actions::selectBirthDate)
                            OnboardingUiState.STEP_BODY -> BodyStep(state, actions::setHeightText, actions::setWeightText)
                            OnboardingUiState.STEP_ACTIVITY -> ActivityStep(state, actions::selectActivityLevel)
                            OnboardingUiState.STEP_GOAL -> GoalStep(state, actions::selectGoal, actions::selectRate)
                            OnboardingUiState.STEP_DIET -> DietStep(
                                state,
                                actions::toggleVegetarian,
                                actions::toggleNoPork,
                                actions::toggleAllergen,
                                actions::selectSnacksPerDay,
                                actions::selectCookingSessions,
                            )
                            OnboardingUiState.STEP_SUMMARY -> SummaryStep(
                                state = state,
                                reveal = remember { !planShown && !reduced },
                                onShown = { planShown = true },
                            )
                        }
                    }
                }
            }

            DockedButtons(
                state = state,
                actions = actions,
                onButtonsHeight = { dockHeight = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * A [SheetStage] with its own [SheetPresentation] when nothing above provides one (the app shell
 * does for the main app, not for onboarding); otherwise the content as it is.
 */
@Composable
private fun OwnStage(content: @Composable () -> Unit) {
    if (LocalSheetPresentation.current != null) {
        content()
    } else {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            SheetStage(content = content)
        }
    }
}

// ---------- The frame ----------

/**
 * Seven 4 dp capsules and "6 of 7": done steps are filled amber to vermilion, the current one
 * vermilion to raspberry, the rest are the quiet track. A segment fills from its start on the snappy
 * spring when its step is reached (and empties when the user goes back). One TalkBack node with the
 * progress.
 */
@Composable
private fun StepProgress(step: Int, count: Int, modifier: Modifier = Modifier) {
    val c = Ember.colors
    val label = stringResource(R.string.onboarding_step, step + 1, count)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 0f..count.toFloat(), count - 1)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i ->
                val fill by animateFloatAsState(
                    targetValue = if (i <= step) 1f else 0f,
                    animationSpec = emberSpec(EmberSprings.snappy()),
                    label = "segment",
                )
                val current = i == step
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .drawWithCache {
                            val r = CornerRadius(size.height / 2f)
                            val brush = if (current) {
                                Brush.horizontalGradient(listOf(c.ember2, c.ember3))
                            } else {
                                Brush.horizontalGradient(listOf(c.ember1, c.ember2))
                            }
                            onDrawBehind {
                                drawRoundRect(c.fill3, cornerRadius = r)
                                val w = size.width * fill.coerceIn(0f, 1f)
                                if (w > 0f) {
                                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                                    drawRoundRect(brush, Offset(x, 0f), Size(w, size.height), r)
                                }
                            }
                        },
                )
            }
        }
        BasicText(
            label,
            style = Ember.type.subhead.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            color = { c.label2 },
            maxLines = 1,
        )
    }
}

/**
 * The step's large title (a heading). It rises out of its own line on the first composition of the
 * questionnaire only; later steps arrive with the step slide instead.
 */
@Composable
private fun StepTitle(step: Int, first: Boolean, entrances: EntranceState) {
    val c = Ember.colors
    val title = stringResource(
        when (step) {
            OnboardingUiState.STEP_SEX -> R.string.onboarding_title_sex
            OnboardingUiState.STEP_BIRTH -> R.string.onboarding_title_birth
            OnboardingUiState.STEP_BODY -> R.string.onboarding_title_body
            OnboardingUiState.STEP_ACTIVITY -> R.string.onboarding_title_activity
            OnboardingUiState.STEP_GOAL -> R.string.onboarding_title_goal
            OnboardingUiState.STEP_DIET -> R.string.onboarding_title_diet
            else -> R.string.onboarding_title_summary
        },
    )
    BasicText(
        title,
        style = Ember.type.largeTitle,
        color = { c.label },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            // As the scaffold's title: the clip is 4 dp taller than the line on both sides so accents
            // and descenders are never cut while it rises, without moving the layout.
            .layout { measurable, constraints ->
                val pad = 4.dp.roundToPx()
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - 2 * pad).coerceAtLeast(0)) { placeable.place(0, -pad) }
            }
            .emberEntrance(0, EntranceKind.Title, first, entrances, "title-$step")
            .padding(vertical = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Back (from the second step) and Next, or Start on the plan: 54 dp capsules, Back a third and Next
 * two thirds of the width, docked over a fade of the page colour, above the navigation bar and the
 * keyboard. Next waits (dimmed) until the step is answered; Start stays off once saving began.
 */
@Composable
private fun DockedButtons(
    state: OnboardingUiState,
    actions: OnboardingActions,
    onButtonsHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val fade = remember(c.bg) {
        Brush.verticalGradient(0f to c.bg.copy(alpha = 0f), .35f to c.bg.copy(alpha = .9f), .6f to c.bg)
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(fade)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom))
            .padding(start = EmberSpace.TabBarSide, end = EmberSpace.TabBarSide, top = EmberSpace.s6, bottom = EmberSpace.s3),
    ) {
        Row(
            Modifier
                .widthIn(max = EmberSpace.ContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.Center)
                .onSizeChanged { onButtonsHeight(it.height) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.step > OnboardingUiState.STEP_SEX) {
                EmberButton(
                    stringResource(R.string.back),
                    onClick = actions::back,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Fill,
                    size = ButtonSize.Lg,
                )
            }
            if (state.step < OnboardingUiState.STEP_SUMMARY) {
                EmberButton(
                    stringResource(R.string.next),
                    onClick = actions::next,
                    modifier = Modifier.weight(2f),
                    size = ButtonSize.Lg,
                    enabled = state.canGoNext,
                )
            } else {
                EmberButton(
                    stringResource(R.string.start),
                    onClick = actions::finish,
                    modifier = Modifier.weight(2f),
                    size = ButtonSize.Lg,
                    enabled = !state.finished,
                )
            }
        }
    }
}

// ---------- Steps ----------

@Composable
private fun SexStep(state: OnboardingUiState, onSelect: (Sex) -> Unit) {
    SexControl(state.sex, onSelect, groupLabel = stringResource(R.string.onboarding_title_sex))
}

@Composable
private fun BirthDateStep(state: OnboardingUiState, onDatePicked: (LocalDate) -> Unit) {
    StepLead(stringResource(R.string.birth_hint))
    InsetGroup {
        row {
            DateField(
                label = stringResource(R.string.onboarding_title_birth),
                date = state.birthDate,
                onPick = onDatePicked,
                placeholder = stringResource(R.string.choose_date),
            )
        }
    }
    if (state.underageBlocked) {
        Notice(NoticeTone.Warning, stringResource(R.string.underage_error), Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun BodyStep(
    state: OnboardingUiState,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
) {
    InsetGroup {
        row {
            MeasurementFields(
                heightText = state.heightCmText,
                weightText = state.weightKgText,
                heightInvalid = state.heightCmText.isNotEmpty() && state.heightCm == null,
                weightInvalid = state.weightKgText.isNotEmpty() && state.weightKg == null,
                onHeightChange = onHeightChange,
                onWeightChange = onWeightChange,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun ActivityStep(state: OnboardingUiState, onSelect: (ActivityLevel) -> Unit) {
    InsetGroup { ActivityRows(state.activityLevel, onSelect) }
}

@Composable
private fun GoalStep(
    state: OnboardingUiState,
    onSelectGoal: (Goal) -> Unit,
    onSelectRate: (Double) -> Unit,
) {
    InsetGroup {
        GoalRows(state.goal, onSelectGoal)
        // The pace only makes sense when the goal is to lose or gain.
        if (state.goal != null && state.goal != Goal.MAINTAIN) {
            row {
                RateControl(
                    state.targetKgPerWeek,
                    onSelectRate,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DietStep(
    state: OnboardingUiState,
    onVegetarian: (Boolean) -> Unit,
    onNoPork: (Boolean) -> Unit,
    onToggleAllergen: (Allergen) -> Unit,
    onSnacks: (Int) -> Unit,
    onCookingSessions: (Int) -> Unit,
) {
    InsetGroup {
        row { SwitchRow(stringResource(R.string.vegetarian_label), state.isVegetarian, onVegetarian) }
        row { SwitchRow(stringResource(R.string.no_pork_label), state.noPork, onNoPork) }
    }
    SectionHeader(stringResource(R.string.allergies_label))
    InsetGroup {
        row {
            AllergyChips(state.allergies, onToggleAllergen, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
    }
    SectionHeader(stringResource(R.string.snacks_label))
    SnacksControl(state.snacksPerDay, onSnacks)
    SectionHeader(stringResource(R.string.cooking_label))
    CookingControl(state.cookingSessionsPerWeek, onCookingSessions)
}

/**
 * The plan: a decorative Ember ring with a flame, the daily target in large Ember digits, the macro
 * split as a composition bar and three columns, then the BMR, the safety-floor note when the target
 * was raised, and the disclaimer. On arrival ([reveal]) the card rises, the ring sweeps, the target's
 * digits arrive one by one, the bar grows and the macro numbers follow.
 */
@Composable
private fun SummaryStep(state: OnboardingUiState, reveal: Boolean, onShown: () -> Unit) {
    val preview = state.preview ?: return
    LaunchedEffect(Unit) { onShown() }
    val c = Ember.colors
    val t = Ember.type
    val targets = preview.targets
    val kcal = stringResource(R.string.kcal_unit)
    val gram = stringResource(R.string.form_unit_g)
    val stacked = LocalDensity.current.fontScale >= StackFontScale

    EmberCard(
        Modifier.emberEntrance(0, EntranceKind.Rise, first = reveal),
        mergeDescendants = true,
    ) {
        val ring: @Composable () -> Unit = {
            Ring(
                // Decorative: the logo's open ring (83%), never a measurement.
                value = 83f,
                target = 100f,
                size = RingSizes.Onboarding.size,
                stroke = RingSizes.Onboarding.stroke,
                modifier = Modifier.ringEntrance(play = reveal),
                sweep = reveal,
                delayMillis = 200,
            ) {
                // The 44 dp flame scales the 24-unit glyph up, so its stroke is thinned in glyph units
                // to stay about 2.6 dp on screen.
                EmberIcon(
                    EmberIcons.Flame, null,
                    size = FlameSize,
                    brush = EmberBrushes.emberIcon(c),
                    strokeWidth = 2.6f * 24f / FlameSize.value,
                )
            }
        }
        val target: @Composable ColumnScope.() -> Unit = {
            BasicText(stringResource(R.string.summary_kcal_target), style = t.subhead, color = { c.label2 })
            FittedNumber(
                value = targets.kcal.toLong(),
                numberStyle = t.display.copy(fontSize = 52.sp),
                modifier = Modifier.padding(vertical = 4.dp),
                minFontSize = 30.sp,
                enter = reveal,
                delayMillis = 420,
                gradient = true,
            )
            BasicText(stringResource(R.string.per_day), style = t.subhead, color = { c.label2 })
        }
        if (stacked) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ring() }
            Column(Modifier.padding(top = 16.dp), content = target)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ring()
                Column(Modifier.weight(1f), content = target)
            }
        }

        CompositionBar(
            proteinKcal = targets.proteinG * 4f,
            fatKcal = targets.fatG * 9f,
            carbsKcal = targets.carbsG * 4f,
            modifier = Modifier.padding(top = 20.dp),
            growDelayMillis = if (reveal) 600 else null,
        )
        val macros = listOf(
            Triple(R.string.summary_protein, targets.proteinG, Metric.Protein),
            Triple(R.string.summary_fat, targets.fatG, Metric.Fat),
            Triple(R.string.summary_carbs, targets.carbsG, Metric.Carbs),
        )
        val column: @Composable (Int, Triple<Int, Int, Metric>, TextStyle, Modifier) -> Unit =
            { i, (labelRes, grams, metric), labelStyle, m ->
                MacroColumn(
                    label = stringResource(labelRes),
                    grams = grams,
                    unit = gram,
                    metric = metric,
                    inline = stacked,
                    enter = reveal,
                    delayMillis = 680 + 60 * i,
                    labelStyle = labelStyle,
                    modifier = m,
                )
            }
        if (stacked) {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                macros.forEachIndexed { i, m -> column(i, m, t.subhead, Modifier.fillMaxWidth()) }
            }
        } else {
            BoxWithConstraints(Modifier.padding(top = 16.dp)) {
                // A third of the card is narrow for a long name ("Вуглеводи" at 1.3): when one does
                // not fit, all three names shrink together, so they stay one size and the grams level.
                val measurer = rememberTextMeasurer()
                val names = macros.map { stringResource(it.first) }
                val room = with(LocalDensity.current) { (maxWidth / 3 - MacroDotWidth).toPx() }
                val widest = names.maxOf { measurer.measure(it, t.subhead, maxLines = 1).size.width }
                val labelStyle = if (widest <= room) {
                    t.subhead
                } else {
                    t.subhead.copy(fontSize = t.subhead.fontSize * (room / widest))
                }
                Row {
                    macros.forEachIndexed { i, m -> column(i, m, labelStyle, Modifier.weight(1f)) }
                }
            }
        }

        Hairline(Modifier.padding(top = 20.dp, bottom = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicText(
                stringResource(R.string.summary_bmr),
                style = t.footnote.copy(fontSize = 15.sp),
                color = { c.label2 },
                modifier = Modifier.weight(1f),
            )
            NumberWithUnit(preview.bmr.toLong(), kcal, t.rowNumber.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.label)
        }
        if (preview.raisedToFloor) {
            Notice(NoticeTone.Info, stringResource(R.string.safety_floor_note), Modifier.padding(top = 16.dp), nested = true)
        }
        BasicText(
            stringResource(R.string.summary_note),
            style = t.footnote.copy(fontSize = 15.sp),
            color = { c.label2 },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** The plan ring's flame: 44 dp drawn from the 24 dp glyph. */
private val FlameSize = 44.dp

/** A macro name's dot and the gap after it. */
private val MacroDotWidth = 16.dp

/** One macro of the plan: a dot in its colour and its name, then the grams; a single line when [inline]. */
@Composable
private fun MacroColumn(
    label: String,
    grams: Int,
    unit: String,
    metric: Metric,
    inline: Boolean,
    enter: Boolean,
    delayMillis: Int,
    labelStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val c = Ember.colors
    val t = Ember.type
    val name: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MacroDotWidth - 8.dp)) {
            Box(Modifier.size(8.dp).background(c.of(metric), EmberShapes.circle))
            // Never split inside a word, whatever the measuring above missed (rounding, kerning).
            WholeWordsText(label, labelStyle.copy(color = c.label2))
        }
    }
    val value: @Composable () -> Unit = {
        NumberWithUnit(grams.toLong(), unit, t.statSmall, enter = enter, delayMillis = delayMillis, color = c.label)
    }
    if (inline) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { name() }
            value()
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            name()
            value()
        }
    }
}

// ---------- Small local pieces ----------

/** A footnote under a step's title (the date step's "We use your age…"). */
@Composable
private fun StepLead(text: String) {
    val c = Ember.colors
    BasicText(
        text,
        style = Ember.type.footnote.copy(fontSize = 15.sp),
        color = { c.label2 },
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
    )
}

/** A 0.5 dp `sep` line across the card. */
@Composable
private fun Hairline(modifier: Modifier = Modifier, thickness: Dp = .5.dp) {
    val c = Ember.colors
    Spacer(modifier.fillMaxWidth().height(thickness).background(c.sep))
}
