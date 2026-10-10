package com.nutricart.app.screenshots

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nutricart.app.ui.onboarding.OnboardingActions
import com.nutricart.app.ui.onboarding.OnboardingContent
import com.nutricart.app.ui.onboarding.OnboardingUiState
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The seven-step questionnaire a new user sees before the app. */
class OnboardingScreenshots(variant: Variant) : ScreenshotTest(variant) {

    private fun step(name: String, step: Int, state: OnboardingUiState = FakeData.onboarding(step)) =
        shoot(name) { OnboardingContent(state = state, actions = FakeData.noActions<OnboardingActions>()) }

    @Test
    @KeyScreen
    fun sex() = step("onboarding-1-sex", OnboardingUiState.STEP_SEX)

    @Test
    fun sexNothingChosen() = step("onboarding-1-sex-empty", OnboardingUiState.STEP_SEX, OnboardingUiState())

    @Test
    fun birth() = step("onboarding-2-birth", OnboardingUiState.STEP_BIRTH)

    @Test
    fun birthUnderage() = step(
        "onboarding-2-birth-underage",
        OnboardingUiState.STEP_BIRTH,
        FakeData.onboarding(OnboardingUiState.STEP_BIRTH).copy(
            birthDate = LocalDate.now().minusYears(15),
            underageBlocked = true,
        ),
    )

    @Test
    fun birthDatePicker() = shoot(
        "onboarding-2-birth-date-picker",
        interact = {
            val label = LocalDate.of(1993, 4, 17).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            onNodeWithText(label).performClick()
        },
    ) {
        OnboardingContent(
            state = FakeData.onboarding(OnboardingUiState.STEP_BIRTH),
            actions = FakeData.noActions<OnboardingActions>(),
        )
    }

    @Test
    @KeyScreen
    fun body() = step("onboarding-3-body", OnboardingUiState.STEP_BODY)

    @Test
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

    @Test
    @KeyScreen
    fun diet() = step("onboarding-6-diet", OnboardingUiState.STEP_DIET)

    @Test
    @KeyScreen
    fun summary() = step("onboarding-7-summary", OnboardingUiState.STEP_SUMMARY)
}
