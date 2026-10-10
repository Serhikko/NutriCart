package com.nutricart.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.data.repository.AiResult
import com.nutricart.app.domain.model.ActivityLevel
import com.nutricart.app.domain.model.Allergen
import com.nutricart.app.domain.model.Goal
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.domain.model.Sex
import com.nutricart.app.ui.common.AddWeightDialog
import com.nutricart.app.ui.common.AddWorkoutDialog
import com.nutricart.app.ui.common.CookedPortionsDialog
import com.nutricart.app.ui.dashboard.HcBannerState
import com.nutricart.app.ui.dashboard.cards.ActivityCard
import com.nutricart.app.ui.dashboard.cards.HcBannerCard
import com.nutricart.app.ui.dashboard.cards.HeroRing
import com.nutricart.app.ui.dashboard.cards.MacroCard
import com.nutricart.app.ui.dashboard.cards.NoDataHint
import com.nutricart.app.ui.dashboard.cards.TodayMenuCard
import com.nutricart.app.ui.dashboard.cards.WaterCard
import com.nutricart.app.ui.dashboard.cards.WeightCard
import com.nutricart.app.ui.ember.Bar
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.CompositionBar
import com.nutricart.app.ui.ember.DateField
import com.nutricart.app.ui.ember.DayMarker
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSpinner
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.MarkerState
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.OptionRow
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.RingSizes
import com.nutricart.app.ui.ember.SectionFooter
import com.nutricart.app.ui.ember.SectionHeader
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl
import com.nutricart.app.ui.ember.SkeletonRows
import com.nutricart.app.ui.ember.SwitchRow
import com.nutricart.app.ui.ember.TimePill
import com.nutricart.app.ui.ember.ToolButton
import com.nutricart.app.ui.ember.inkOf
import com.nutricart.app.ui.ember.of
import com.nutricart.app.ui.fridge.AiUiState
import com.nutricart.app.ui.fridge.FridgeAiBlock
import com.nutricart.app.ui.onboarding.ActivityRows
import com.nutricart.app.ui.onboarding.AllergyChips
import com.nutricart.app.ui.onboarding.GoalRows
import com.nutricart.app.ui.onboarding.MeasurementFields
import com.nutricart.app.ui.onboarding.RateControl
import com.nutricart.app.ui.onboarding.SexControl
import org.junit.Test
import java.time.LocalDate

/**
 * The Ember gallery: the shared building blocks as the screens use them, on the app's stage. One
 * overview page of the kit (ring, digits, bars, markers, buttons, choices, rows, states), the Today
 * cards in their states, the profile form that onboarding and Settings share, the fridge assistant
 * in every state, and the three sheets that several screens open. The primitives in depth (every
 * size, variant and motion frame) are in the Ember*Screenshots classes.
 */
class ComponentScreenshots(variant: Variant) : ScreenshotTest(variant) {

    /** A scrolling gallery page on the stage, with a 393 dp phone's gutter. */
    @Composable
    private fun Gallery(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ember.colors.bg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    /** A small grey note naming what follows (not an app string: the gallery's own label). */
    @Composable
    private fun Caption(text: String) = BasicText(
        text,
        Modifier.padding(start = 4.dp, top = 8.dp),
        style = Ember.type.caption.copy(color = Ember.colors.label2),
    )

    // ------------------------------------------------------------------ the kit at a glance

    @Test
    @KeyScreen
    @Tall(2300)
    fun kit() = shoot("components-kit") {
        Gallery {
            val c = Ember.colors
            val t = Ember.type
            SectionHeader(stringResource(R.string.dashboard_title))
            HeroRing(FakeData.dashboard())

            Caption("Ring · plan, sheet with this food, summary, day card, over target")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ring(1385f, 2240f, RingSizes.Sheet.size, RingSizes.Sheet.stroke, add = 161f) {
                    NumberWithUnit(66L, "%", t.rowNumber, Modifier.align(Alignment.Center))
                }
                Ring(2190f, 2240f, RingSizes.Summary.size, RingSizes.Summary.stroke)
                Ring(1700f, 2240f, RingSizes.DayCard.size, RingSizes.DayCard.stroke)
                Ring(2530f, 2240f, RingSizes.Summary.size, RingSizes.Summary.stroke)
                Ring(640f, 2240f, RingSizes.Mini.size, RingSizes.Mini.stroke, muted = true)
            }

            Caption("Bars · macros and the composition bar")
            EmberCard {
                CardHead(
                    stringResource(R.string.macros_title),
                    meta = stringResource(R.string.macros_meta),
                )
                listOf(
                    Triple(Metric.Protein, 72, 140),
                    Triple(Metric.Fat, 66, 72),
                    Triple(Metric.Carbs, 301, 252),
                ).forEach { (metric, eaten, target) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NumberWithUnit(eaten.toLong(), stringResource(R.string.form_unit_g), t.rowNumber, color = c.inkOf(metric))
                        Bar(eaten / target.toFloat(), c.of(metric), null, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                CompositionBar(proteinKcal = 288f, fatKcal = 594f, carbsKcal = 1004f)
            }

            Caption("Day markers · on plan, over, today, nothing logged, ahead")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayMarker(MarkerState.Good, null, label = "Sat")
                DayMarker(MarkerState.Over, null, label = "Sun")
                DayMarker(MarkerState.Good, null, label = "Mon")
                DayMarker(MarkerState.Empty, null, label = "Tue")
                DayMarker(MarkerState.Today, null, label = "Wed", progress = .62f)
                DayMarker(MarkerState.Future, null, label = "Thu", dayNumber = "15")
                DayMarker(MarkerState.Future, null, label = "Fri", dayNumber = "16")
            }

            Caption("Buttons · ink, fill, plain, water, danger; tools; icon buttons")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberButton(stringResource(R.string.save), {})
                EmberButton(stringResource(R.string.cancel), {}, variant = ButtonVariant.Fill)
                EmberButton(stringResource(R.string.water_undo), {}, variant = ButtonVariant.Plain)
                EmberButton(stringResource(R.string.water_add_250) + " " + stringResource(R.string.ml_unit), {}, variant = ButtonVariant.Water)
                EmberButton(stringResource(R.string.delete), {}, variant = ButtonVariant.Danger, icon = EmberIcons.Trash)
            }
            EmberButton(stringResource(R.string.add_food), {}, size = ButtonSize.Lg, icon = EmberIcons.Plus)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(EmberIcons.Lock, stringResource(R.string.unlock_meal), {}, selected = true)
                ToolButton(EmberIcons.Shuffle, stringResource(R.string.swap_meal), {})
                ToolButton(EmberIcons.Pot, stringResource(R.string.fridge_cooked_action), {})
                ToolButton(EmberIcons.Plus, stringResource(R.string.log_meal), {})
                Spacer(Modifier.weight(1f))
                EmberIconButton(EmberIcons.Share, stringResource(R.string.share_action), {}, style = IconButtonStyle.Surface)
                EmberIconButton(EmberIcons.Settings, stringResource(R.string.settings_title), {})
            }

            Caption("Choices · segmented control and chips")
            SegmentedControl(
                listOf(
                    stringResource(R.string.dashboard_title),
                    stringResource(R.string.stats_range_week),
                    stringResource(R.string.stats_range_month),
                    stringResource(R.string.stats_range_90),
                ),
                selectedIndex = 1,
                onSelect = {},
                role = SegmentRole.Tab,
            )
            ChipGroup(
                MealSlot.entries.map { slotName(it) },
                selected = setOf(1),
                onToggle = {},
            )

            Caption("Rows · inset group")
            InsetGroup {
                row(dividerStart = 62.dp) {
                    ListRow(
                        slotName(MealSlot.BREAKFAST),
                        leading = { MealTile(MealSlot.BREAKFAST, size = 30.dp) },
                        trailing = {
                            TimePill("08:30", {}, stringResource(R.string.reminder_time_cd, slotName(MealSlot.BREAKFAST), "08:30"), on = true)
                        },
                    )
                }
                row { SwitchRow(stringResource(R.string.vegetarian_label), checked = true, onCheckedChange = {}) }
                row { OptionRow(stringResource(R.string.goal_maintain), selected = true, onClick = {}) }
                row { ListRow(stringResource(R.string.settings_title), onClick = {}, chevron = true) }
            }
            SectionFooter(stringResource(R.string.settings_saves_now))

            Caption("States · notice, empty state, loading")
            Notice(
                NoticeTone.Info, stringResource(R.string.hc_banner_no_permission), icon = EmberIcons.Heart,
                action = { EmberButton(stringResource(R.string.hc_grant), {}, size = ButtonSize.Sm) },
            )
            EmberCard {
                EmptyState(
                    stringResource(R.string.workouts_empty),
                    icon = EmberIcons.Dumbbell,
                    body = stringResource(R.string.plan_footnote),
                )
            }
            EmberCard {
                SkeletonRows(3)
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    EmberSpinner(contentDescription = stringResource(R.string.loading))
                }
            }
        }
    }

    // ------------------------------------------------------------------ Today's cards

    @Test
    @KeyScreen
    @Tall(2600)
    fun dashboardCards() = shoot("components-dashboard-cards") {
        Gallery {
            HcBannerState.entries.filter { it != HcBannerState.NONE }.forEach { banner ->
                Caption("Health Connect · $banner")
                HcBannerCard(banner = banner, onInstallOrUpdate = {}, onGrant = {})
            }
            Caption("Today's menu")
            TodayMenuCard(menu = FakeData.todayMenu, onOpenRecipe = { _, _ -> })
            Caption("Macros")
            MacroCard(FakeData.dashboard())
            Caption("Water · empty and 1,250 ml")
            WaterCard(waterMl = 0, onAdd = {}, onUndo = {})
            WaterCard(waterMl = 1250, onAdd = {}, onUndo = {})
            Caption("Activity · watch tiles and manual workouts")
            ActivityCard(FakeData.dashboard(), onAddWorkout = {}, onDeleteWorkout = {})
        }
    }

    @Test
    @Tall(1700)
    fun dashboardCardsSparse() = shoot("components-dashboard-cards-sparse") {
        Gallery {
            Caption("Activity · no watch data")
            ActivityCard(FakeData.dashboardNewUser(), onAddWorkout = {}, onDeleteWorkout = {})
            NoDataHint()
            Caption("Weight · one point")
            WeightCard(FakeData.dashboardNewUser())
            Caption("Weight · 30 days")
            WeightCard(FakeData.dashboard())
            Caption("Macros · over the limits")
            MacroCard(FakeData.dashboardOver())
        }
    }

    // ------------------------------------------------------------------ the shared profile form

    /**
     * The controls onboarding and Settings share (ui/onboarding/ProfileForm.kt), and the Ember pieces
     * that replaced the old SwitchRow, RadioOptionRow, ErrorCard and DatePickerField.
     */
    @Test
    @KeyScreen
    @Tall(1500)
    fun formControls() = shoot("components-form-controls") {
        Gallery {
            Caption("About you · sex, date of birth, height and weight")
            EmberCard {
                SexControl(Sex.FEMALE, {}, groupLabel = stringResource(R.string.onboarding_title_sex))
                Spacer(Modifier.height(16.dp))
                DateField(
                    label = stringResource(R.string.onboarding_title_birth),
                    date = LocalDate.of(1993, 4, 17),
                    onPick = {},
                    placeholder = stringResource(R.string.choose_date),
                )
                Spacer(Modifier.height(16.dp))
                MeasurementFields(
                    heightText = "168",
                    weightText = "250",
                    heightInvalid = false,
                    weightInvalid = true,
                    onHeightChange = {},
                    onWeightChange = {},
                )
            }
            DateField(
                label = stringResource(R.string.onboarding_title_birth),
                date = null,
                onPick = {},
                placeholder = stringResource(R.string.choose_date),
            )
            Caption("Activity · option rows with a description")
            InsetGroup { ActivityRows(ActivityLevel.MODERATE) {} }
            Caption("Goal · option rows and the pace")
            InsetGroup {
                GoalRows(Goal.LOSE) {}
                row { Box(Modifier.padding(16.dp)) { RateControl(0.5, {}) } }
            }
            Caption("Food preferences · switches and allergy chips")
            InsetGroup {
                row { SwitchRow(stringResource(R.string.vegetarian_label), checked = true, onCheckedChange = {}) }
                row { SwitchRow(stringResource(R.string.no_pork_label), checked = false, onCheckedChange = {}) }
            }
            AllergyChips(setOf(Allergen.NUTS, Allergen.SHELLFISH), {})
            Caption("Errors")
            Notice(NoticeTone.Error, stringResource(R.string.underage_error))
        }
    }

    // ------------------------------------------------------------------ the fridge assistant

    @Test
    @Tall(3000)
    fun fridgeAiStates() = shoot("components-fridge-ai-states") {
        Gallery {
            Caption("No key")
            FridgeAiBlock(FakeData.aiNoKey(), canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {})
            Caption("Ready")
            FridgeAiBlock(AiUiState(hasKey = true), canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {})
            Caption("Asking")
            FridgeAiBlock(AiUiState(hasKey = true, loading = true), canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {})
            Caption("Answer, truncated")
            val ok = FakeData.aiAnswer().result as AiResult.Ok
            FridgeAiBlock(
                AiUiState(hasKey = true, result = ok.copy(truncated = true)),
                canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {},
            )
            listOf(
                AiResult.BadKey, AiResult.Offline, AiResult.TooSlow, AiResult.Busy,
                AiResult.Refused, AiResult.Empty, AiResult.Failed,
            ).forEach { result ->
                Caption("Error · $result")
                FridgeAiBlock(
                    AiUiState(hasKey = true, result = result),
                    canAsk = true, onAsk = {}, onDismiss = {}, onOpenSettings = {},
                )
            }
        }
    }

    // ------------------------------------------------------------------ the shared sheets

    /** The stage a sheet rises over in these shots (the screens that open them have their own shots). */
    @Composable
    private fun Stage() = Box(Modifier.fillMaxSize().background(Ember.colors.bg))

    @Test
    @KeyScreen
    fun addWeightDialog() = shoot("dialog-add-weight") {
        Stage()
        AddWeightDialog(currentWeightKg = 78.4, onConfirm = {}, onDismiss = {})
    }

    @Test
    @KeyScreen
    fun addWorkoutDialog() = shoot("dialog-add-workout") {
        Stage()
        AddWorkoutDialog(weightKg = 78.4, onConfirm = { _, _ -> }, onDismiss = {})
    }

    @Test
    @KeyScreen
    fun cookedPortionsDialog() = shoot("dialog-cooked-portions") {
        Stage()
        CookedPortionsDialog(onConfirm = {}, onDismiss = {})
    }

    @Composable
    private fun slotName(slot: MealSlot) = stringResource(
        when (slot) {
            MealSlot.BREAKFAST -> R.string.meal_breakfast
            MealSlot.LUNCH -> R.string.meal_lunch
            MealSlot.DINNER -> R.string.meal_dinner
            MealSlot.SNACK -> R.string.meal_snack
        }
    )
}
