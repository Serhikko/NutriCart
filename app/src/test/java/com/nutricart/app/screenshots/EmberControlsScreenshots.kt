package com.nutricart.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.domain.model.MealSlot
import com.nutricart.app.ui.ember.AmountStepper
import com.nutricart.app.ui.ember.ButtonSize
import com.nutricart.app.ui.ember.ButtonVariant
import com.nutricart.app.ui.ember.CardHead
import com.nutricart.app.ui.ember.CheckDisc
import com.nutricart.app.ui.ember.CheckState
import com.nutricart.app.ui.ember.ChipGroup
import com.nutricart.app.ui.ember.CloseButton
import com.nutricart.app.ui.ember.DateField
import com.nutricart.app.ui.ember.Ember
import com.nutricart.app.ui.ember.EmberBrushes
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberCard
import com.nutricart.app.ui.ember.EmberIcon
import com.nutricart.app.ui.ember.EmberIconButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.EmberSearchField
import com.nutricart.app.ui.ember.EmberShapes
import com.nutricart.app.ui.ember.EmberSwitch
import com.nutricart.app.ui.ember.EmberTextField
import com.nutricart.app.ui.ember.EmptyState
import com.nutricart.app.ui.ember.FooterTone
import com.nutricart.app.ui.ember.IconButtonStyle
import com.nutricart.app.ui.ember.InsetGroup
import com.nutricart.app.ui.ember.ListRow
import com.nutricart.app.ui.ember.MealTile
import com.nutricart.app.ui.ember.Metric
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone
import com.nutricart.app.ui.ember.NumberWithUnit
import com.nutricart.app.ui.ember.OptionRow
import com.nutricart.app.ui.ember.PlainLink
import com.nutricart.app.ui.ember.Ring
import com.nutricart.app.ui.ember.SectionFooter
import com.nutricart.app.ui.ember.SectionHeader
import com.nutricart.app.ui.ember.SegmentRole
import com.nutricart.app.ui.ember.SegmentedControl
import com.nutricart.app.ui.ember.StatTile
import com.nutricart.app.ui.ember.SwitchRow
import com.nutricart.app.ui.ember.TimePill
import com.nutricart.app.ui.ember.ToolButton
import org.junit.Test
import java.time.LocalDate

/**
 * The Ember controls (wave 1, B2): every variant and state of the buttons, cards, inset-group rows,
 * segmented controls, chips, fields, the amount stepper, empty states and notices, in both themes and
 * (key screens) Ukrainian, font scales 1.3 / 2.0 and "Remove animations". Real app strings, so the
 * Ukrainian shots carry the long labels. Targets: app-design/shots/settings*.png, shopping, fridge,
 * onboarding-diet, sheet-workout, and the web's add-amount / add-custom phone shots.
 */
class EmberControlsScreenshots(variant: Variant) : ScreenshotTest(variant) {

    /** A scrolling page on the stage, the gutter of a 393 dp phone. */
    @Composable
    private fun Page(content: @Composable ColumnScope.() -> Unit) {
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

    @Composable
    private fun Caption(text: String) =
        Text(text, Modifier.padding(start = 4.dp, top = 8.dp), style = Ember.type.caption, color = Ember.colors.label2)

    // ------------------------------------------------------------------ buttons

    @Test
    @KeyScreen
    @Tall(1500)
    fun buttons() = shoot("ember-controls-buttons") {
        Page {
            Caption("Capsules · Md")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberButton(stringResource(R.string.save), {})
                EmberButton(stringResource(R.string.cancel), {}, variant = ButtonVariant.Fill)
                EmberButton(stringResource(R.string.water_undo), {}, variant = ButtonVariant.Plain)
                EmberButton(stringResource(R.string.water_add_250) + " " + stringResource(R.string.ml_unit), {}, variant = ButtonVariant.Water)
                EmberButton(stringResource(R.string.delete), {}, variant = ButtonVariant.Danger, icon = EmberIcons.Trash)
                EmberButton(stringResource(R.string.save), {}, enabled = false)
            }
            Caption("Lg · icon · loading · disabled")
            EmberButton(stringResource(R.string.fridge_cooked_action), {}, size = ButtonSize.Lg, icon = EmberIcons.Pot)
            EmberButton(stringResource(R.string.regenerate_plan), {}, size = ButtonSize.Lg, icon = EmberIcons.Refresh, loading = true)
            EmberButton(stringResource(R.string.shopping_regenerate), {}, size = ButtonSize.Lg, variant = ButtonVariant.Fill, icon = EmberIcons.Refresh)
            EmberButton(stringResource(R.string.add_action), {}, size = ButtonSize.Lg, icon = EmberIcons.Plus, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmberButton(stringResource(R.string.cancel), {}, Modifier.weight(1f), variant = ButtonVariant.Fill, size = ButtonSize.Lg)
                EmberButton(stringResource(R.string.delete), {}, Modifier.weight(2f), variant = ButtonVariant.Danger, size = ButtonSize.Lg)
            }
            Caption("Sm")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberButton(stringResource(R.string.hc_grant), {}, size = ButtonSize.Sm)
                EmberButton(stringResource(R.string.cloud_code_new), {}, size = ButtonSize.Sm, variant = ButtonVariant.Fill, icon = EmberIcons.Refresh)
                EmberButton(stringResource(R.string.already_have_action), {}, size = ButtonSize.Sm, variant = ButtonVariant.Plain)
            }
            Caption("Icon buttons · close · tools")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.Center) {
                EmberIconButton(EmberIcons.Settings, stringResource(R.string.settings_title), {})
                EmberIconButton(EmberIcons.Share, stringResource(R.string.share_action), {}, style = IconButtonStyle.Surface)
                EmberIconButton(EmberIcons.Trash, stringResource(R.string.delete_entry), {}, style = IconButtonStyle.Plain)
                EmberIconButton(EmberIcons.Share, stringResource(R.string.share_day_action), {}, style = IconButtonStyle.Surface, enabled = false)
                EmberIconButton(EmberIcons.Scan, stringResource(R.string.scan_barcode), {}, size = 48.dp, style = IconButtonStyle.Surface)
                CloseButton(stringResource(R.string.cancel), {})
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(EmberIcons.Lock, stringResource(R.string.unlock_meal), {}, selected = true)
                ToolButton(EmberIcons.LockOpen, stringResource(R.string.lock_meal), {})
                ToolButton(EmberIcons.Shuffle, stringResource(R.string.swap_meal), {}, enabled = false)
                ToolButton(EmberIcons.Pot, stringResource(R.string.fridge_cooked_action), {})
                ToolButton(EmberIcons.Plus, stringResource(R.string.log_meal), {})
            }
            Caption("Plain links")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlainLink(stringResource(R.string.dashboard_title), {}, icon = EmberIcons.Left)
                PlainLink(stringResource(R.string.add_food), {}, icon = EmberIcons.Plus)
                PlainLink(stringResource(R.string.settings_title), {})
                PlainLink(stringResource(R.string.cloud_unlink), {}, color = Ember.colors.danger)
            }
        }
    }

    // ------------------------------------------------------------------ cards, notices, empty states

    @Test
    @KeyScreen
    @Tall(1300)
    fun cards() = shoot("ember-controls-cards") {
        Page {
            val c = Ember.colors
            EmberCard {
                CardHead(stringResource(R.string.today_menu_title), icon = EmberIcons.Plan, metric = Metric.Kcal, meta = stringResource(R.string.today_menu_meta))
                MealSlot.entries.take(3).forEach { slot ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MealTile(slot)
                        Text(slotName(slot), Modifier.weight(1f), style = Ember.type.callout)
                        NumberWithUnit(525L, stringResource(R.string.kcal_unit), Ember.type.rowNumber)
                    }
                }
            }
            EmberCard {
                CardHead(
                    stringResource(R.string.workouts_section_title), icon = EmberIcons.Dumbbell,
                    action = { PlainLink(stringResource(R.string.workout_add), {}, icon = EmberIcons.Plus) },
                )
                Text(stringResource(R.string.workouts_empty), style = Ember.type.footnote, color = c.label2)
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.steps_label), EmberIcons.Steps, Metric.Steps,
                    value = { NumberWithUnit(9184L, "", Ember.type.stat) },
                    footnote = stringResource(R.string.tile_source, "16:52"),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatTile(
                    stringResource(R.string.active_kcal_label), EmberIcons.Flame, Metric.Kcal,
                    value = { NumberWithUnit(412L, stringResource(R.string.kcal_unit), Ember.type.stat) },
                    footnote = stringResource(R.string.tile_source, "16:52"),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            EmberCard(onClick = {}) {
                CardHead(stringResource(R.string.day_note_label), icon = EmberIcons.Note)
                Text("Long walk after lunch; skipped the evening snack.", style = Ember.type.callout, maxLines = 3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MealSlot.entries.forEach { MealTile(it) }
                MealSlot.entries.forEach { MealTile(it, size = 30.dp) }
            }
            Notice(
                NoticeTone.Info, stringResource(R.string.hc_banner_no_permission), icon = EmberIcons.Heart,
                action = { EmberButton(stringResource(R.string.hc_grant), {}, size = ButtonSize.Sm) },
            )
            Notice(NoticeTone.Warning, stringResource(R.string.underage_error))
            Notice(NoticeTone.Error, stringResource(R.string.ai_error_offline), detail = stringResource(R.string.plan_error_unreachable))
            EmberCard {
                CardHead(stringResource(R.string.ai_card_title), icon = EmberIcons.Sparkle)
                Notice(NoticeTone.Error, stringResource(R.string.ai_error_offline), nested = true)
                Spacer(Modifier.height(8.dp))
                Notice(NoticeTone.Info, stringResource(R.string.offline_results_note), nested = true)
            }
        }
    }

    @Test
    @KeyScreen
    @Tall(1100)
    fun emptyStates() = shoot("ember-controls-empty") {
        Page {
            val c = Ember.colors
            EmberCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                EmptyState(
                    title = stringResource(R.string.fridge_title),
                    icon = EmberIcons.Fridge,
                    body = stringResource(R.string.fridge_empty_hint),
                    action = { EmberButton(stringResource(R.string.fridge_add_action), {}, icon = EmberIcons.Plus) },
                )
            }
            EmberCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                EmptyState(
                    title = stringResource(R.string.generate_plan),
                    art = {
                        Box(contentAlignment = Alignment.Center) {
                            Ring(0f, 1f, 96.dp, 12.dp)
                            EmberIcon(EmberIcons.Plan, null, size = 36.dp, brush = EmberBrushes.emberIcon(c))
                        }
                    },
                    body = stringResource(R.string.plan_empty_hint),
                    action = {
                        EmberButton(stringResource(R.string.generate_plan), {}, size = ButtonSize.Lg, icon = EmberIcons.Sparkle)
                    },
                )
            }
            EmptyState(title = stringResource(R.string.no_results), icon = EmberIcons.Search, body = stringResource(R.string.add_empty_hint))
        }
    }

    // ------------------------------------------------------------------ grouped lists (Settings)

    @Test
    @KeyScreen
    @Tall(2600)
    fun groups() = shoot("ember-controls-groups") {
        var sex by remember { mutableIntStateOf(0) }
        var activity by remember { mutableIntStateOf(2) }
        var manual by remember { mutableStateOf(true) }
        val wide = LocalDensity.current.fontScale < 1.5f
        Page {
            Column {
                SectionHeader(stringResource(R.string.onboarding_title_sex))
                InsetGroup {
                    row {
                        SegmentedControl(
                            listOf(stringResource(R.string.sex_male), stringResource(R.string.sex_female)), sex, { sex = it },
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    row { DateField(stringResource(R.string.onboarding_title_birth), LocalDate.of(1990, 9, 2), {}, stringResource(R.string.choose_date)) }
                }
            }
            Column {
                SectionHeader(stringResource(R.string.onboarding_title_body))
                InsetGroup {
                    row {
                        BodyFields(wide)
                    }
                }
                SectionFooter(stringResource(R.string.weight_edit_hint))
            }
            Column {
                SectionHeader(stringResource(R.string.onboarding_title_activity))
                InsetGroup {
                    listOf(
                        R.string.activity_sedentary to R.string.activity_sedentary_desc,
                        R.string.activity_light to R.string.activity_light_desc,
                        R.string.activity_moderate to R.string.activity_moderate_desc,
                        R.string.activity_active to R.string.activity_active_desc,
                    ).forEachIndexed { i, (t, d) ->
                        row(dividerStart = 54.dp) {
                            OptionRow(stringResource(t), activity == i, { activity = i }, detail = stringResource(d))
                        }
                    }
                }
            }
            Column {
                SectionHeader(stringResource(R.string.targets_section))
                InsetGroup {
                    row { SwitchRow(stringResource(R.string.targets_manual_switch), manual, { manual = it }) }
                    row(dividerStart = 0.dp) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EmberTextField("2240", {}, stringResource(R.string.target_kcal_label), Modifier.weight(1f))
                            EmberTextField(
                                "980", {}, stringResource(R.string.target_carbs_label), Modifier.weight(1f),
                                isError = true, errorText = stringResource(R.string.target_invalid),
                            )
                        }
                    }
                }
                SectionFooter(stringResource(R.string.targets_manual_note))
            }
            Column {
                SectionHeader(stringResource(R.string.reset_button))
                InsetGroup {
                    row {
                        ListRow(
                            stringResource(R.string.reset_button),
                            leading = { EmberIcon(EmberIcons.Trash, null, size = 22.dp, tint = Ember.colors.danger) },
                            titleColor = Ember.colors.danger, onClick = {},
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BodyFields(wide: Boolean) {
        val height = @Composable { m: Modifier ->
            EmberTextField(
                "181", {}, stringResource(R.string.height_label), m, suffix = "cm",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        val weight = @Composable { m: Modifier ->
            EmberTextField(
                "78,4", {}, stringResource(R.string.weight_label), m, suffix = "kg",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (wide) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                height(Modifier.weight(1f))
                weight(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                height(Modifier.fillMaxWidth())
                weight(Modifier.fillMaxWidth())
            }
        }
    }

    @Test
    @KeyScreen
    @Tall(2800)
    fun rows() = shoot("ember-controls-rows") {
        val reminders = remember { mutableStateOf(setOf(MealSlot.BREAKFAST, MealSlot.LUNCH)) }
        Page {
            val c = Ember.colors
            Column {
                SectionHeader(stringResource(R.string.recurring_section))
                InsetGroup {
                    row(dividerStart = 58.dp) {
                        ListRow(
                            stringResource(R.string.workout_strength),
                            subtitle = stringResource(R.string.minutes_value, 45) + " · Mon Wed Fri",
                            leading = { IconTile(EmberIcons.Dumbbell) },
                            trailing = { EmberIconButton(EmberIcons.Trash, stringResource(R.string.recurring_delete), {}, style = IconButtonStyle.Plain) },
                        )
                    }
                    row(dividerStart = 58.dp) {
                        ListRow(
                            stringResource(R.string.recurring_add),
                            leading = { Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { EmberIcon(EmberIcons.Plus, null, size = 22.dp, tint = c.tint) } },
                            titleColor = c.tint, onClick = {},
                        )
                    }
                }
            }
            Column {
                SectionHeader(stringResource(R.string.reminders_section))
                InsetGroup {
                    MealSlot.entries.forEach { slot ->
                        row(dividerStart = 58.dp) {
                            val on = slot in reminders.value
                            ListRow(
                                slotName(slot),
                                leading = { MealTile(slot, size = 30.dp) },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        TimePill("08:30", {}, stringResource(R.string.reminder_time_cd, slotName(slot), "08:30"), on)
                                        EmberSwitch(on)
                                    }
                                },
                                onClick = { reminders.value = if (on) reminders.value - slot else reminders.value + slot },
                            )
                        }
                    }
                }
                SectionFooter(stringResource(R.string.reminders_hint))
            }
            Column {
                SectionHeader(stringResource(R.string.partner_section))
                InsetGroup {
                    row { SwitchRow(stringResource(R.string.partner_share_meals), true, {}) }
                    row { SwitchRow(stringResource(R.string.partner_notify_missed), false, {}) }
                    row { SwitchRow(stringResource(R.string.partner_inbox), true, {}) }
                    row { SwitchRow(stringResource(R.string.cloud_switch), true, {}, subtitle = "Last upload today, 02:01 · 2 waiting", enabled = false) }
                    row(dividerStart = 58.dp) {
                        ListRow(
                            stringResource(R.string.partner_test_action),
                            leading = { Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { EmberIcon(EmberIcons.Send, null, size = 20.dp, tint = c.tint) } },
                            titleColor = c.tint, onClick = {},
                        )
                    }
                }
                SectionFooter(stringResource(R.string.partner_notice_test_sent), live = true, tone = FooterTone.Good)
                SectionFooter(stringResource(R.string.partner_notice_offline), live = true, tone = FooterTone.Error)
            }
            Column {
                SectionHeader(stringResource(R.string.aisle_produce))
                InsetGroup {
                    // The builder is composable: rows read resources and loop like any content.
                    val inFridge = stringResource(R.string.fridge_in_stock_note)
                    listOf(
                        Triple("Броколі", "600 g", CheckState.On),
                        Triple("Морква", "350 g (~5 pcs)", CheckState.Off),
                        Triple("Цибуля ріпчаста", "300 g (~4 pcs) · $inFridge", CheckState.Have),
                    ).forEach { (name, amount, state) ->
                        row(dividerStart = 58.dp) {
                            ListRow(
                                name, subtitle = amount,
                                leading = { CheckDisc(state) },
                                trailing = {
                                    EmberButton(
                                        stringResource(if (state == CheckState.Have) R.string.need_it_action else R.string.already_have_action),
                                        {}, variant = ButtonVariant.Plain, size = ButtonSize.Sm,
                                    )
                                },
                                onClick = {},
                            )
                        }
                    }
                }
            }
            Column {
                SectionHeader(stringResource(R.string.hc_section))
                InsetGroup {
                    row { ListRow(stringResource(R.string.last_synced, "").trim().trimEnd(':'), trailing = { Text("10/10/26, 16:52", style = Ember.type.callout, color = c.label2) }) }
                    row { ListRow(stringResource(R.string.hc_open_settings), titleColor = c.tint, onClick = {}, chevron = true) }
                }
            }
        }
    }

    @Composable
    private fun IconTile(icon: EmberIcons) = Box(
        Modifier.size(30.dp).background(Ember.colors.fill, EmberShapes.rowIcon),
        contentAlignment = Alignment.Center,
    ) { EmberIcon(icon, null, size = 18.dp, tint = Ember.colors.label) }

    @Composable
    private fun slotName(slot: MealSlot) = stringResource(
        when (slot) {
            MealSlot.BREAKFAST -> R.string.meal_breakfast
            MealSlot.LUNCH -> R.string.meal_lunch
            MealSlot.DINNER -> R.string.meal_dinner
            MealSlot.SNACK -> R.string.meal_snack
        },
    )

    // ------------------------------------------------------------------ segmented controls and chips

    @Test
    @KeyScreen
    @Tall(1400)
    fun segmentsAndChips() = shoot("ember-controls-segments") {
        var range by remember { mutableIntStateOf(0) }
        var cooking by remember { mutableIntStateOf(1) }
        var allergens by remember { mutableStateOf(setOf(6)) }
        var workout by remember { mutableIntStateOf(1) }
        Page {
            SegmentedControl(
                listOf(
                    stringResource(R.string.tab_today), stringResource(R.string.stats_range_week),
                    stringResource(R.string.stats_range_month), stringResource(R.string.stats_range_90),
                ),
                range, { range = it }, role = SegmentRole.Tab,
            )
            SegmentedControl(
                listOf(stringResource(R.string.fridge_tab_stock), stringResource(R.string.fridge_tab_to_buy)), 1, {}, role = SegmentRole.Tab,
            )
            Caption("No choice yet · rate · disabled")
            SegmentedControl(listOf(stringResource(R.string.sex_male), stringResource(R.string.sex_female)), -1, {})
            SegmentedControl(listOf("0.25", "0.5", "0.75", "1.0"), 1, {})
            SegmentedControl(listOf("0", "1", "2"), 1, {}, enabled = false)
            Caption(stringResource(R.string.cooking_label))
            SegmentedControl(
                listOf(stringResource(R.string.cooking_few), stringResource(R.string.cooking_every_other), stringResource(R.string.cooking_daily)),
                cooking, { cooking = it }, groupLabel = stringResource(R.string.cooking_label),
            )
            Caption(stringResource(R.string.allergies_label))
            EmberCard {
                ChipGroup(
                    listOf(
                        R.string.allergen_gluten, R.string.allergen_dairy, R.string.allergen_eggs, R.string.allergen_nuts,
                        R.string.allergen_peanuts, R.string.allergen_fish, R.string.allergen_shellfish, R.string.allergen_soy,
                    ).map { stringResource(it) },
                    allergens, { i -> allergens = if (i in allergens) allergens - i else allergens + i }, multiSelect = true,
                    groupLabel = stringResource(R.string.allergies_label),
                )
            }
            Caption("Workout type · centred")
            ChipGroup(
                listOf(
                    R.string.workout_walking, R.string.workout_running, R.string.workout_cycling, R.string.workout_swimming,
                    R.string.workout_strength, R.string.workout_yoga, R.string.workout_push_ups, R.string.workout_squats,
                    R.string.workout_hiit, R.string.workout_hiking, R.string.workout_other,
                ).map { stringResource(it) },
                setOf(workout), { workout = it }, center = true,
            )
            Caption(stringResource(R.string.shopping_days_label))
            ChipGroup(listOf("Sat 10", "Sun 11", "Mon 12", "Tue 13", "Wed 14", "Thu 15", "Fri 16"), setOf(0, 1, 2), {}, multiSelect = true)
            Caption("Amount")
            ChipGroup(listOf("100 g", "1 portion · 207 g", "2 portions · 414 g"), setOf(1), {})
        }
    }

    // ------------------------------------------------------------------ fields and the stepper

    @Composable
    private fun FieldsPage() {
        var note by remember { mutableStateOf("Long walk after lunch; skipped the evening snack.\nSlept badly.") }
        var key by remember { mutableStateOf("demo-key-0123456789abcdef") }
        var shown by remember { mutableStateOf(false) }
        var grams by remember { mutableStateOf("207") }
        var portions by remember { mutableStateOf(false) }
        Page {
            EmberTextField("", {}, stringResource(R.string.custom_food_name), placeholder = stringResource(R.string.search_hint))
            EmberTextField(
                "29", {}, stringResource(R.string.weight_label), suffix = "kg",
                isError = true, errorText = stringResource(R.string.invalid_weight),
            )
            EmberTextField("Olena", {}, stringResource(R.string.cloud_name_label), supportingText = stringResource(R.string.cloud_name_hint))
            EmberTextField(
                note, { note = it }, stringResource(R.string.day_note_hint), singleLine = false, minLines = 2, maxLines = 5,
            )
            EmberTextField(
                key, { key = it }, stringResource(R.string.ai_key_label),
                visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    EmberIconButton(
                        if (shown) EmberIcons.EyeOff else EmberIcons.Eye,
                        stringResource(if (shown) R.string.ai_key_hide else R.string.ai_key_show),
                        { shown = !shown }, style = IconButtonStyle.Plain,
                    )
                },
            )
            EmberTextField("Heinz", {}, stringResource(R.string.custom_food_brand), enabled = false)
            EmberSearchField("", {}, {}, stringResource(R.string.search_hint), stringResource(R.string.search_action))
            EmberSearchField("beans", {}, {}, stringResource(R.string.search_hint), stringResource(R.string.search_action))
            AmountStepper(
                text = grams, onTextChange = { grams = it }, unit = if (portions) "portions" else "g",
                onDecrease = {}, onIncrease = {},
                fieldLabel = stringResource(if (portions) R.string.portions_mode else R.string.grams_mode),
                decreaseLabel = stringResource(R.string.amount_less), increaseLabel = stringResource(R.string.amount_more),
                onSwitchUnit = { portions = !portions },
            )
            AmountStepper(
                text = "30", onTextChange = {}, unit = stringResource(R.string.minutes_value, 0).replace("0", "").trim(),
                onDecrease = {}, onIncrease = {}, fieldLabel = stringResource(R.string.workout_minutes_label),
                decreaseLabel = stringResource(R.string.amount_less), increaseLabel = stringResource(R.string.amount_more),
                keyboardType = KeyboardType.Number,
            )
            AmountStepper(
                text = "0", onTextChange = {}, unit = "g", onDecrease = {}, onIncrease = {},
                fieldLabel = stringResource(R.string.fridge_grams_label),
                decreaseLabel = stringResource(R.string.amount_less), increaseLabel = stringResource(R.string.amount_more),
                isError = true,
            )
            EmberCard {
                InsetGroupDate()
            }
        }
    }

    @Composable
    private fun InsetGroupDate() {
        DateField(stringResource(R.string.onboarding_title_birth), null, {}, stringResource(R.string.choose_date))
    }

    @Test
    @KeyScreen
    @Tall(2100)
    fun fields() = shoot("ember-controls-fields") { FieldsPage() }

    /** The focused well: white with the blue ring (a tap on the label starts typing, like an HTML label). */
    @Test
    fun fieldFocused() = shoot(
        "ember-controls-field-focused",
        interact = { onNodeWithText(str(R.string.custom_food_name)).performClick() },
    ) { FieldsPage() }

    /** The date sheet: the Material calendar in an Ember sheet with the single OK button. */
    @Test
    @KeyScreen
    fun dateSheet() = shoot(
        "ember-controls-date-sheet",
        interact = { onNodeWithText(str(R.string.onboarding_title_birth)).performClick() },
    ) {
        Page {
            InsetGroup {
                row { DateField(stringResource(R.string.onboarding_title_birth), LocalDate.of(1990, 9, 2), {}, stringResource(R.string.choose_date)) }
            }
        }
    }

    /**
     * The same sheet after the pencil: the picker's typed entry (a birth date decades back is quicker to
     * type than to page to).
     */
    @Test
    @KeyScreen
    fun dateSheetTyped() = shoot(
        "ember-controls-date-sheet-typed",
        interact = {
            onNodeWithText(str(R.string.onboarding_title_birth)).performClick()
            waitForIdle()
            onNodeWithContentDescription(str(androidx.compose.material3.R.string.m3c_date_picker_switch_to_input_mode))
                .performClick()
        },
    ) {
        Page {
            InsetGroup {
                row { DateField(stringResource(R.string.onboarding_title_birth), LocalDate.of(1990, 9, 2), {}, stringResource(R.string.choose_date)) }
            }
        }
    }

    // ------------------------------------------------------------------ motion

    /**
     * Frames after one tap on each: the segmented thumb slides Today → Month (and squeezes), the switch
     * knob springs, the option disc pops, the chip fills, the shopping tick fills from the centre while
     * its check draws.
     */
    @Composable
    private fun MotionPage() {
        var range by remember { mutableIntStateOf(0) }
        var sw by remember { mutableStateOf(false) }
        var option by remember { mutableIntStateOf(0) }
        var chip by remember { mutableIntStateOf(0) }
        var tick by remember { mutableStateOf(false) }
        Page {
            SegmentedControl(
                listOf(
                    stringResource(R.string.tab_today), stringResource(R.string.stats_range_week),
                    stringResource(R.string.stats_range_month), stringResource(R.string.stats_range_90),
                ),
                range, { range = it }, role = SegmentRole.Tab,
            )
            InsetGroup {
                row { SwitchRow(stringResource(R.string.vegetarian_label), sw, { sw = it }) }
                row(dividerStart = 54.dp) { OptionRow(stringResource(R.string.goal_lose), option == 0, { option = 0 }) }
                row(dividerStart = 54.dp) { OptionRow(stringResource(R.string.goal_maintain), option == 1, { option = 1 }) }
                row(dividerStart = 58.dp) {
                    ListRow("Броколі", subtitle = "600 g", leading = { CheckDisc(if (tick) CheckState.On else CheckState.Off) }, onClick = { tick = !tick })
                }
            }
            ChipGroup(listOf(stringResource(R.string.meal_breakfast), stringResource(R.string.meal_lunch), stringResource(R.string.meal_dinner)), setOf(chip), { chip = it })
        }
    }

    @Test
    @KeyScreen
    fun motionFrames() = shootFrames(
        "ember-controls-motion",
        times = listOf(16, 80, 160, 260, 420, 800),
        interact = {
            onNodeWithText(str(R.string.stats_range_month)).performClick()
            onNodeWithText(str(R.string.vegetarian_label)).performClick()
            onNodeWithText(str(R.string.goal_maintain)).performClick()
            onNodeWithText("Броколі").performClick()
            onNodeWithText(str(R.string.meal_dinner)).performClick()
        },
    ) { MotionPage() }

    // ------------------------------------------------------------------ keyboard focus

    /**
     * Keyboard and switch access: the focus ring (3 dp `focus`, inside the shape) on a button, and the
     * focused field well. Touch never shows it: clickables are focusable only in keyboard mode.
     */
    @Test
    fun focusRing() = shoot(
        "ember-controls-focus",
        interact = {
            onNodeWithText(str(R.string.save)).requestFocus()
        },
    ) {
        val modes = LocalInputModeManager.current
        SideEffect { modes.requestInputMode(InputMode.Keyboard) }
        Page {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberButton(stringResource(R.string.save), {})
                EmberButton(stringResource(R.string.cancel), {}, variant = ButtonVariant.Fill)
            }
            InsetGroup {
                row { SwitchRow(stringResource(R.string.vegetarian_label), true, {}) }
                row { ListRow(stringResource(R.string.hc_open_settings), titleColor = Ember.colors.tint, onClick = {}, chevron = true) }
            }
        }
    }
}
