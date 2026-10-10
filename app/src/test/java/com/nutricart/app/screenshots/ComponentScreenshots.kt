package com.nutricart.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.domain.logic.AdherenceCalculator.DayState
import com.nutricart.app.domain.logic.WeightTrendCalculator
import com.nutricart.app.ui.common.AddWeightDialog
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.common.AnimatedNumber
import com.nutricart.app.ui.common.BarChart
import com.nutricart.app.ui.common.BarPoint
import com.nutricart.app.ui.common.CalorieRing
import com.nutricart.app.ui.common.CookedPortionsDialog
import com.nutricart.app.ui.common.DatePickerField
import com.nutricart.app.ui.common.ErrorCard
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.common.MacroBar
import com.nutricart.app.ui.common.RadioOptionRow
import com.nutricart.app.ui.common.SwitchRow
import com.nutricart.app.ui.common.WeightChart
import com.nutricart.app.ui.dashboard.HcBannerState
import com.nutricart.app.ui.dashboard.cards.ActivityCard
import com.nutricart.app.ui.dashboard.cards.HcBannerCard
import com.nutricart.app.ui.dashboard.cards.HeroRing
import com.nutricart.app.ui.dashboard.cards.MacroCard
import com.nutricart.app.ui.dashboard.cards.TodayMenuCard
import com.nutricart.app.ui.dashboard.cards.WaterCard
import com.nutricart.app.ui.dashboard.cards.WeightCard
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeAiBlock
import org.junit.Test
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The shared building blocks (ui/common and the dashboard cards), each in its
 * interesting states, laid out as gallery pages.
 */
class ComponentScreenshots(variant: Variant) : ScreenshotTest(variant) {

    /** A labelled gallery page on the app background. */
    @Composable
    private fun Gallery(content: @Composable ColumnScope.() -> Unit) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }

    @Composable
    private fun Caption(text: String) = Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
    )

    @Test
    @KeyScreen
    @Tall(1500)
    fun chartsAndRings() = shoot("components-charts-rings") {
        Gallery {
            Caption("CalorieRing 45% · 100% · 118% (over)")
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf(0.45f, 1f, 1.18f).forEach { p ->
                    CalorieRing(progress = p, modifier = Modifier.size(100.dp), ringWidth = 10.dp) {
                        Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Caption("HeroRing (dashboard)")
            HeroRing(FakeData.dashboard())
            Caption("BarChart · 14 days, target line")
            BarChart(
                points = (0 until 14).map { i ->
                    val v = if (i == 5) 0f else 1700f + ((i * 53) % 9) * 110f
                    BarPoint(v, if (v == 0f) DayState.EMPTY else if (v > 2400f) DayState.OVER else DayState.GOOD)
                },
                targetLine = 2240f,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Caption("WeightChart · 30 days with trend")
            val points = FakeData.dashboard().weightPoints
            WeightChart(points, WeightTrendCalculator.calculate(points), Modifier.fillMaxWidth().height(140.dp))
            Caption("MacroBar · under / near / over")
            MacroBar(label = "Protein", eatenG = 64, targetG = 140)
            MacroBar(label = "Fat", eatenG = 70, targetG = 72, valueColor = MaterialTheme.colorScheme.primary)
            MacroBar(label = "Carbs", eatenG = 301, targetG = 252, valueColor = MaterialTheme.colorScheme.error)
            Caption("AnimatedNumber · LoadingBox")
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedNumber(value = 1385, style = MaterialTheme.typography.displaySmall)
                Box(Modifier.size(80.dp)) { LoadingBox() }
            }
        }
    }

    @Test
    @KeyScreen
    @Tall(2600)
    fun dashboardCards() = shoot("components-dashboard-cards") {
        Gallery {
            HcBannerState.entries.filter { it != HcBannerState.NONE }.forEach { banner ->
                Caption("HcBannerCard · $banner")
                HcBannerCard(banner = banner, onInstallOrUpdate = {}, onGrant = {})
            }
            Caption("TodayMenuCard")
            TodayMenuCard(menu = FakeData.todayMenu, onOpenRecipe = { _, _ -> })
            Caption("MacroCard")
            MacroCard(FakeData.dashboard())
            Caption("WaterCard · empty and 1250 ml")
            WaterCard(waterMl = 0, onAdd = {}, onUndo = {})
            WaterCard(waterMl = 1250, onAdd = {}, onUndo = {})
            Caption("ActivityCard · watch + manual workouts")
            ActivityCard(FakeData.dashboard(), onAddWorkout = {}, onDeleteWorkout = {})
        }
    }

    @Test
    @Tall(1500)
    fun dashboardCardsSparse() = shoot("components-dashboard-cards-sparse") {
        Gallery {
            Caption("ActivityCard · no watch data")
            ActivityCard(FakeData.dashboardNewUser(), onAddWorkout = {}, onDeleteWorkout = {})
            Caption("WeightCard · one point")
            WeightCard(FakeData.dashboardNewUser())
            Caption("WeightCard · 30 days")
            WeightCard(FakeData.dashboard())
            Caption("MacroCard · over the limits")
            MacroCard(FakeData.dashboardOver())
        }
    }

    @Test
    fun formControls() = shoot("components-form-controls") {
        Gallery {
            Caption("SwitchRow on / off")
            SwitchRow(R.string.vegetarian_label, checked = true, onChecked = {})
            SwitchRow(R.string.no_pork_label, checked = false, onChecked = {})
            Caption("RadioOptionRow selected / with description")
            RadioOptionRow(R.string.activity_moderate, R.string.activity_moderate_desc, selected = true, onClick = {})
            RadioOptionRow(R.string.activity_active, R.string.activity_active_desc, selected = false, onClick = {})
            RadioOptionRow(R.string.goal_maintain, null, selected = false, onClick = {})
            Caption("ErrorCard")
            ErrorCard("Something went wrong. Check the connection and try again.")
            Caption("DatePickerField · empty / chosen")
            DatePickerField(date = null, onDatePicked = {})
            DatePickerField(date = LocalDate.of(1993, 4, 17), onDatePicked = {})
        }
    }

    @Test
    @Tall(2400)
    fun fridgeAiStates() = shoot("components-fridge-ai-states") {
        Gallery {
            Caption("FridgeAiBlock · no key")
            FridgeAiBlock(FakeData.aiNoKey(), canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {})
            Caption("FridgeAiBlock · asking")
            FridgeAiBlock(AiUiState(hasKey = true, loading = true), canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {})
            Caption("FridgeAiBlock · answer, truncated")
            val ok = FakeData.aiAnswer().result as AiResult.Ok
            FridgeAiBlock(
                AiUiState(hasKey = true, result = ok.copy(truncated = true)),
                canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {},
            )
            listOf(
                AiResult.BadKey, AiResult.Offline, AiResult.TooSlow, AiResult.Busy,
                AiResult.Refused, AiResult.Empty, AiResult.Failed,
            ).forEach { result ->
                Caption("FridgeAiBlock · $result")
                FridgeAiBlock(
                    AiUiState(hasKey = true, result = result),
                    canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {},
                )
            }
        }
    }

    @Test
    @KeyScreen
    fun addWeightDialog() = shoot("dialog-add-weight") {
        Gallery { }
        AddWeightDialog(currentWeightKg = 78.4, onConfirm = {}, onDismiss = {})
    }

    @Test
    @KeyScreen
    fun addWorkoutDialog() = shoot("dialog-add-workout") {
        Gallery { }
        AddWorkoutDialog(weightKg = 78.4, onConfirm = { _, _ -> }, onDismiss = {})
    }

    @Test
    fun cookedPortionsDialog() = shoot("dialog-cooked-portions") {
        Gallery { }
        CookedPortionsDialog(onConfirm = {}, onDismiss = {})
    }
}
