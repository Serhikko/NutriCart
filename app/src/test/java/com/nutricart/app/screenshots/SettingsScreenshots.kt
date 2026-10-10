package com.nutricart.app.screenshots

import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.settings.CloudNotice
import com.nutricart.app.ui.settings.PartnerNotice
import com.nutricart.app.ui.settings.SettingsActions
import com.nutricart.app.ui.settings.SettingsContent
import com.nutricart.app.ui.settings.SettingsUiState
import org.junit.Test

/** Settings: one long form (profile, targets, reminders, AI key, partner, cloud). */
class SettingsScreenshots(variant: Variant) : ScreenshotTest(variant) {

    private fun settings(name: String, state: SettingsUiState = FakeData.settings()) = shoot(name) {
        SettingsContent(state = state, onBack = {}, actions = FakeData.noActions<SettingsActions>())
    }

    @Test
    @KeyScreen
    fun top() = settings("settings-top")

    /** The whole form on one very tall phone. */
    @Test
    @KeyScreen
    @Tall(4300)
    fun full() = settings("settings-full")

    /** Fresh install look: nothing linked, no keys, cloud not configured in this build. */
    @Test
    @Tall(2900)
    fun fullNothingLinked() = settings(
        "settings-full-nothing-linked",
        FakeData.settings().copy(
            recurring = emptyList(),
            aiKeyStored = false,
            botTokenStored = false,
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
    @Tall(4800)
    fun fullManualTargetsAndNotices() = settings(
        "settings-full-manual-targets-notices",
        FakeData.settings().copy(
            manualTargets = true,
            customKcalText = "2100",
            customProteinText = "150",
            customFatText = "70",
            customCarbsText = "",
            aiKeyText = "not-a-key",
            partnerNotice = PartnerNotice.NO_MESSAGE_YET,
            cloudNotice = CloudNotice.OFFLINE,
            cloudLastError = "network",
        ),
    )

    @Test
    fun loading() = settings("settings-loading", SettingsUiState())

    @Test
    fun resetDialog() = settings("settings-reset-dialog", FakeData.settings().copy(showResetDialog = true))

    @Test
    fun recurringDialog() = settings("settings-recurring-dialog", FakeData.settings().copy(showRecurringDialog = true))

    @Test
    fun reminderTimeDialog() = settings(
        "settings-reminder-time-dialog",
        FakeData.settings().copy(editingReminderSlot = MealSlot.BREAKFAST),
    )

    @Test
    fun cloudNameDialog() = settings(
        "settings-cloud-name-dialog",
        FakeData.settings().copy(showCloudNameDialog = true, cloudEnabled = false),
    )
}
