package com.nutricart.app.partner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sends one message to the partner: a meal that was just logged, or a note
 * that a meal is still missing. Composes the text HERE (with the app's
 * strings) from the diary as it is right now, then hands it to the
 * repository. A network hiccup is retried; a bad token is not — that is the
 * user's to fix in Settings, and Settings says so.
 */
@HiltWorker
class PartnerSendWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: PartnerRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(KEY_SLOT)?.let { MealSlot.valueOf(it) } ?: return Result.success()
        val result = when (inputData.getString(KEY_KIND)) {
            KIND_MEAL -> {
                val epochDay = inputData.getLong(KEY_DAY, -1L)
                if (epochDay < 0) return Result.success()
                repository.sendMealUpdate(slot, epochDay, partnerLabels(applicationContext))
            }
            KIND_MISSED -> repository.sendText(
                applicationContext.getString(
                    R.string.partner_missed_meal,
                    applicationContext.getString(mealSlotLabelRes(slot)),
                )
            )
            else -> return Result.success()
        }
        return when (result) {
            PartnerResult.Offline, PartnerResult.Busy ->
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            else -> Result.success() // sent, or not fixable by retrying
        }
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KEY_SLOT = "slot"
        private const val KEY_DAY = "day"
        private const val KIND_MEAL = "meal"
        private const val KIND_MISSED = "missed"
        private const val MAX_ATTEMPTS = 5

        fun mealInput(slot: MealSlot, epochDay: Long): Data =
            workDataOf(KEY_KIND to KIND_MEAL, KEY_SLOT to slot.name, KEY_DAY to epochDay)

        fun missedInput(slot: MealSlot): Data =
            workDataOf(KEY_KIND to KIND_MISSED, KEY_SLOT to slot.name)
    }
}
