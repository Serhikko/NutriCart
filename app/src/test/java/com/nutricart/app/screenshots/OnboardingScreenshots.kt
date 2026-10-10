package com.nutricart.app.screenshots

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.nutricart.app.domain.model.DailyTargets
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.ui.onboarding.OnboardingActions
import com.nutricart.app.ui.onboarding.OnboardingContent
import com.nutricart.app.ui.onboarding.OnboardingPreview
import com.nutricart.app.ui.onboarding.OnboardingUiState
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The seven-step questionnaire a new user sees before the app. */
class OnboardingScreenshots(private val variant: Variant) : ScreenshotTest(variant) {

    private fun step(name: String, step: Int, state: OnboardingUiState = FakeData.onboarding(step)) =
        shoot(name) { Onboarding(state) }

    @Composable
    private fun Onboarding(state: OnboardingUiState) =
        OnboardingContent(state = state, actions = FakeData.noActions<OnboardingActions>())

    @Test
    @KeyScreen
    fun sex() = step("onboarding-1-sex", OnboardingUiState.STEP_SEX)

    /** Nothing chosen yet: no thumb in the segmented control, Next dimmed. */
    @Test
    fun sexNothingChosen() = step("onboarding-1-sex-empty", OnboardingUiState.STEP_SEX, OnboardingUiState())

    @Test
    fun birth() = step("onboarding-2-birth", OnboardingUiState.STEP_BIRTH)

    @Test
    fun birthEmpty() = step(
        "onboarding-2-birth-empty",
        OnboardingUiState.STEP_BIRTH,
        FakeData.onboarding(OnboardingUiState.STEP_BIRTH).copy(birthDate = null),
    )

    @Test
    fun birthUnderage() = step(
        "onboarding-2-birth-underage",
        OnboardingUiState.STEP_BIRTH,
        FakeData.onboarding(OnboardingUiState.STEP_BIRTH).copy(
            birthDate = LocalDate.now().minusYears(15),
            underageBlocked = true,
        ),
    )

    /** The date row opens the Material date picker in an Ember sheet, OK only; the page recedes. */
    @Test
    @KeyScreen
    fun birthDatePicker() = shoot(
        "onboarding-2-birth-date-picker",
        interact = {
            val label = LocalDate.of(1993, 4, 17).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            onNodeWithText(label).performClick()
        },
    ) { Onboarding(FakeData.onboarding(OnboardingUiState.STEP_BIRTH)) }

    @Test
    @KeyScreen
    fun body() = step("onboarding-3-body", OnboardingUiState.STEP_BODY)

    @Test
    @KeyScreen
    fun bodyInvalid() = step(
        "onboarding-3-body-invalid",
        OnboardingUiState.STEP_BODY,
        FakeData.onboarding(OnboardingUiState.STEP_BODY).copy(heightCmText = "17", weightKgText = "640"),
    )

    @Test
    @KeyScreen
    fun activity() = step("onboarding-4-activity", OnboardingUiState.STEP_ACTIVITY)

    @Test
    @KeyScreen
    fun goal() = step("onboarding-5-goal", OnboardingUiState.STEP_GOAL)

    /** Maintain: no pace to choose. */
    @Test
    fun goalMaintain() = step(
        "onboarding-5-goal-maintain",
        OnboardingUiState.STEP_GOAL,
        FakeData.onboarding(OnboardingUiState.STEP_GOAL).copy(goal = Goal.MAINTAIN, targetKgPerWeek = 0.0),
    )

    @Test
    @KeyScreen
    fun diet() = step("onboarding-6-diet", OnboardingUiState.STEP_DIET)

    @Test
    @KeyScreen
    fun summary() = step("onboarding-7-summary", OnboardingUiState.STEP_SUMMARY)

    /** The target was raised to the safety floor: the note joins the plan card. */
    @Test
    fun summaryFloor() = step(
        "onboarding-7-summary-floor",
        OnboardingUiState.STEP_SUMMARY,
        FakeData.onboarding(OnboardingUiState.STEP_SUMMARY).copy(
            preview = OnboardingPreview(bmr = 1190, targets = DailyTargets(1200, 96, 40, 115), raisedToFloor = true),
        ),
    )

    /** Saving has begun: Start stays off. */
    @Test
    fun summaryFinished() = step(
        "onboarding-7-summary-finished",
        OnboardingUiState.STEP_SUMMARY,
        FakeData.onboarding(OnboardingUiState.STEP_SUMMARY).copy(finished = true),
    )

    // ------------------------------------------------------------------ motion

    /** O2: the plan reveals: the card rises, the ring sweeps, the digits arrive, the bar grows. */
    @Test
    fun planReveal() = shootFrames(
        "onboarding-7-plan-reveal",
        times = listOf(60, 250, 500, 800, 1200, 1800),
    ) { Onboarding(FakeData.onboarding(OnboardingUiState.STEP_SUMMARY)) }

    /** O1: Next slides the new step in from the side while the progress segment fills. */
    @Test
    fun stepChange() {
        val state = mutableStateOf(FakeData.onboarding(OnboardingUiState.STEP_ACTIVITY))
        shootFrames(
            "onboarding-step-change",
            times = listOf(60, 160, 260, 420, 700),
            firstOpen = false,
            interact = { state.value = FakeData.onboarding(OnboardingUiState.STEP_GOAL) },
        ) { Onboarding(state.value) }
    }

    // ------------------------------------------------------------------ Ukrainian at twice the text size

    /**
     * Long Ukrainian labels at font scale 2.0 (the variants give one or the other, not both): shot in
     * the Ukrainian run only, with the text size doubled inside the composition the way the system
     * does it (non-linear: body text about 2×, large numbers less).
     */
    private fun ukAtDoubleSize(name: String, step: Int) {
        assumeTrue("Ukrainian run only", variant == Variant.UK)
        shoot(name) { DoubleTextSize { Onboarding(FakeData.onboarding(step)) } }
    }

    @Test
    @KeyScreen
    fun ukDouble1() = ukAtDoubleSize("onboarding-1-sex-fs200", OnboardingUiState.STEP_SEX)

    @Test
    @KeyScreen
    fun ukDouble3() = ukAtDoubleSize("onboarding-3-body-fs200", OnboardingUiState.STEP_BODY)

    @Test
    @KeyScreen
    fun ukDouble4() = ukAtDoubleSize("onboarding-4-activity-fs200", OnboardingUiState.STEP_ACTIVITY)

    @Test
    @KeyScreen
    fun ukDouble6() = ukAtDoubleSize("onboarding-6-diet-fs200", OnboardingUiState.STEP_DIET)

    @Test
    @KeyScreen
    fun ukDouble7() = ukAtDoubleSize("onboarding-7-summary-fs200", OnboardingUiState.STEP_SUMMARY)

    /**
     * The plan at 1.3 in Ukrainian: still three macro columns (they stack from 1.5), the narrowest
     * width "Вуглеводи" gets. The name shrinks a little rather than break as "Вуглево / ди".
     */
    @Test
    @KeyScreen
    fun ukLarger7() {
        assumeTrue("Ukrainian run only", variant == Variant.UK)
        shoot("onboarding-7-summary-fs130") {
            ScaledTextSize(1.3f) { Onboarding(FakeData.onboarding(OnboardingUiState.STEP_SUMMARY)) }
        }
    }
}

/**
 * [content] at font scale 2.0, with the platform's non-linear scaling (a Density built from a
 * configuration context, as the activity's own would be).
 */
@Composable
internal fun DoubleTextSize(content: @Composable () -> Unit) = ScaledTextSize(2f, content)

/** [content] at [fontScale], scaled the way the platform does it (see [DoubleTextSize]). */
@Composable
internal fun ScaledTextSize(fontScale: Float, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaled = remember(context, configuration, fontScale) {
        context.createConfigurationContext(Configuration(configuration).apply { this.fontScale = fontScale })
    }
    CompositionLocalProvider(
        LocalDensity provides Density(scaled),
        LocalConfiguration provides scaled.resources.configuration,
        content = content,
    )
}
