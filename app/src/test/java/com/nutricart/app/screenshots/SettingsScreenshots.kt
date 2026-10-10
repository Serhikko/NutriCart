package com.nutricart.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.ember.LocalSheetPresentation
import com.nutricart.app.ui.ember.SheetPresentation
import com.nutricart.app.ui.ember.SheetStage
import com.nutricart.app.ui.settings.CloudNotice
import com.nutricart.app.ui.settings.PartnerNotice
import com.nutricart.app.ui.settings.SettingsActions
import com.nutricart.app.ui.settings.SettingsContent
import com.nutricart.app.ui.settings.SettingsUiState
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Settings: the Save-gated profile form, Save, then everything that saves at once (regular
 * activities, reminders, the AI key, the partner, the cloud, Health Connect, Reset), its five sheets
 * and the floating "Save changes".
 */
class SettingsScreenshots(private val variant: Variant) : ScreenshotTest(variant) {

    /** A token of the right shape (a made-up one), so the field shows no error. */
    private val BotToken = "1234567890:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"

    /**
     * Settings as the app shows it: a stacked screen behind "‹ Today", on a stage that recedes when one
     * of its sheets presents (the app shell provides the stage; tests provide their own).
     */
    @Composable
    private fun Settings(state: SettingsUiState) {
        val presentation = remember { SheetPresentation() }
        CompositionLocalProvider(LocalSheetPresentation provides presentation) {
            SheetStage {
                SettingsContent(
                    state = state,
                    onBack = {},
                    actions = FakeData.noActions<SettingsActions>(),
                    backLabel = stringResource(R.string.dashboard_title),
                )
            }
        }
    }

    /** The form after the user changed it (one composition after it loaded): it has unsaved edits. */
    @Composable
    private fun EditedSettings(edit: (SettingsUiState) -> SettingsUiState) {
        var state by remember { mutableStateOf(FakeData.settings()) }
        LaunchedEffect(Unit) { state = edit(state) }
        Settings(state)
    }

    private fun settings(name: String, state: SettingsUiState = FakeData.settings()) = shoot(name) { Settings(state) }

    /** Scrolls the list until the section [key] is at the top, then shoots. */
    private fun section(name: String, key: String, state: SettingsUiState = FakeData.settings()) = shoot(
        name,
        interact = { onNode(hasScrollToKeyAction()).performScrollToKey(key) },
    ) { Settings(state) }

    @Test
    @KeyScreen
    fun top() = settings("settings-top")

    /** The whole form on one very tall phone. */
    @Test
    @KeyScreen
    @Tall(4700)
    fun full() = settings("settings-full")

    /** Fresh install look: nothing linked, no keys, cloud not configured in this build. */
    @Test
    @Tall(3600)
    fun fullNothingLinked() = settings(
        "settings-full-nothing-linked",
        FakeData.settings().copy(
            recurring = emptyList(),
            aiKeyStored = false,
            aiKeyText = "",
            botTokenStored = false,
            botTokenText = "",
            botUsername = null,
            partnerName = null,
            cloudConfigured = false,
            cloudEnabled = false,
            cloudPartners = emptyList(),
            cloudAccount = null,
            cloudPairingCode = null,
            hcAvailable = false,
        ),
    )

    @Test
    @Tall(5400)
    fun fullManualTargetsAndNotices() = settings(
        "settings-full-manual-targets-notices",
        FakeData.settings().copy(
            manualTargets = true,
            customKcalText = "2100",
            customProteinText = "150",
            customFatText = "70",
            customCarbsText = "980",
            heightCmText = "18",
            aiKeyText = "ключ-не-ключ",
            aiKeySavedNotice = false,
            partnerNotice = PartnerNotice.NO_MESSAGE_YET,
            cloudNotice = CloudNotice.OFFLINE,
            cloudLastError = "network",
            cloudAccount = null,
            cloudEmailText = "andrii@example",
            cloudPasswordText = "short",
            cloudPairingCode = "K7Q-4MZ" to System.currentTimeMillis() - 60_000,
            cloudPartners = emptyList(),
            underageBlocked = true,
            birthDate = LocalDate.now().minusYears(16),
        ),
    )

    @Test
    fun loading() = settings("settings-loading", SettingsUiState())

    /** The user edited the weight: Save is far below, so the "Save changes" capsule floats in. */
    @Test
    @KeyScreen
    fun saveCapsule() = shoot("settings-save-capsule") {
        EditedSettings { it.copy(weightKgText = "77,9") }
    }

    /** The capsule rises on the sheet spring when the form becomes dirty. */
    @Test
    fun saveCapsuleFrames() {
        val state = mutableStateOf(FakeData.settings())
        shootFrames(
            "settings-save-capsule-in",
            times = listOf(40, 120, 220, 360, 700),
            firstOpen = false,
            interact = { state.value = state.value.copy(heightCmText = "182") },
        ) { Settings(state.value) }
    }

    /** Scrolled: the compact bar with its back chevron over the list. */
    @Test
    fun scrolled() = shoot(
        "settings-scrolled",
        interact = { onNode(hasScrollToKeyAction()).performTouchInput { swipeUp() } },
    ) { Settings(FakeData.settings()) }

    @Test
    @KeyScreen
    fun goalAndTargets() = section("settings-goal-targets", "goal", FakeData.settings().copy(
        manualTargets = true,
        customKcalText = "2240",
        customProteinText = "140",
        customFatText = "72",
        customCarbsText = "980",
    ))

    @Test
    @KeyScreen
    fun remindersAndAi() = section("settings-reminders-ai", "recurring", FakeData.settings().copy(aiKeySavedNotice = true, aiKeyText = "sk-test-0000000000003f9a"))

    @Test
    @KeyScreen
    fun partnerAndCloud() = section(
        "settings-partner-cloud",
        "partner",
        FakeData.settings().copy(partnerNotice = PartnerNotice.TEST_SENT, botTokenText = BotToken),
    )

    /** A Telegram call is running: the partner controls wait and the test row shows the spinner. */
    @Test
    fun partnerBusy() = section(
        "settings-partner-busy",
        "partner",
        FakeData.settings().copy(partnerBusy = true, botTokenText = BotToken),
    )

    /** The bot is saved but nobody wrote /start yet: the connect step. */
    @Test
    fun partnerConnect() = section(
        "settings-partner-connect",
        "partner",
        FakeData.settings().copy(partnerName = null, partnerNotice = PartnerNotice.BOT_SAVED, botTokenText = BotToken),
    )

    /** The cloud with an anonymous account: email, password, Generate and Send confirmation. */
    @Test
    fun cloudEmail() = section(
        "settings-cloud-email",
        "cloud",
        FakeData.settings().copy(
            cloudAccount = null,
            cloudEmailText = "andrii@example.com",
            cloudPasswordText = "plum-river-42-lantern",
            cloudNotice = CloudNotice.CODE_READY,
        ),
    )

    /** The partner list could not be loaded: it says so instead of "Nobody yet", and keeps who was shown. */
    @Test
    fun cloudPartnersFailed() = section(
        "settings-cloud-partners-failed",
        "cloud",
        FakeData.settings().copy(cloudPartnersFailed = true),
    )

    /** Sync off: whoever can still read what was uploaded stays listed, with Remove. */
    @Test
    fun cloudOffPartnersKept() = section(
        "settings-cloud-off-partners",
        "cloud",
        FakeData.settings().copy(cloudEnabled = false),
    )

    /**
     * The pairing code with its 0.12 em tracking and the countdown ring, scrolled into view in every
     * variant (at 2.0 the cloud section is too tall for the other shots to reach it).
     */
    @Test
    @KeyScreen
    fun pairingCode() = shoot(
        "settings-pairing-code",
        interact = {
            val list = onNode(hasScrollToKeyAction())
            list.performScrollToNode(hasContentDescription("K7Q-4MZ"))
            // That leaves the code at the bottom edge: lift it to the middle, with the ring under it.
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, 1000f) }
        },
    ) { Settings(FakeData.settings()) }

    /** A key screen so the larger text sizes are shot too: "Last synced" moves its time under the words there. */
    @Test
    @KeyScreen
    fun healthAndReset() = section("settings-hc-reset", "hc")

    // ------------------------------------------------------------------ the five sheets

    @Test
    @KeyScreen
    fun resetDialog() = settings("settings-reset-dialog", FakeData.settings().copy(showResetDialog = true))

    @Test
    @KeyScreen
    fun recurringDialog() = settings("settings-recurring-dialog", FakeData.settings().copy(showRecurringDialog = true))

    @Test
    @KeyScreen
    fun reminderTimeDialog() = settings(
        "settings-reminder-time-dialog",
        FakeData.settings().copy(editingReminderSlot = MealSlot.BREAKFAST),
    )

    @Test
    @KeyScreen
    fun cloudNameDialog() = settings(
        "settings-cloud-name-dialog",
        FakeData.settings().copy(showCloudNameDialog = true, cloudEnabled = false),
    )

    @Test
    fun datePicker() = shoot(
        "settings-date-picker",
        interact = {
            val label = LocalDate.of(1990, 9, 2).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            onNodeWithText(label).performClick()
        },
    ) { Settings(FakeData.settings()) }

    // ------------------------------------------------------------------ Ukrainian at twice the text size

    /** Long Ukrainian labels at font scale 2.0, shot in the Ukrainian run only (see OnboardingScreenshots). */
    private fun ukAtDoubleSize(name: String, key: String?, state: SettingsUiState = FakeData.settings()) {
        assumeTrue("Ukrainian run only", variant == Variant.UK)
        shoot(
            name,
            interact = key?.let { k -> { onNode(hasScrollToKeyAction()).performScrollToKey(k) } },
        ) { DoubleTextSize { Settings(state) } }
    }

    @Test
    @KeyScreen
    fun ukDoubleTop() = ukAtDoubleSize("settings-top-fs200", null)

    @Test
    @KeyScreen
    fun ukDoubleDiet() = ukAtDoubleSize("settings-diet-fs200", "diet")

    @Test
    @KeyScreen
    fun ukDoubleReminders() = ukAtDoubleSize("settings-reminders-fs200", "reminders")

    @Test
    @KeyScreen
    fun ukDoublePartner() = ukAtDoubleSize("settings-partner-fs200", "partner")

    @Test
    @KeyScreen
    fun ukDoubleCloud() = ukAtDoubleSize("settings-cloud-fs200", "cloud")

    @Test
    @KeyScreen
    fun ukDoubleCapsule() {
        assumeTrue("Ukrainian run only", variant == Variant.UK)
        shoot("settings-save-capsule-fs200") { DoubleTextSize { EditedSettings { it.copy(weightKgText = "77,9") } } }
    }

    // The sheets are their own windows, which take the activity's real text size: the fs200 run
    // shoots them at 2.0 (the four sheets above are key screens), the uk run in Ukrainian.
}
