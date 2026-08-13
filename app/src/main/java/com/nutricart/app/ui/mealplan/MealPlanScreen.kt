package com.nutricart.app.ui.mealplan

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nutricart.app.R
import com.nutricart.app.domain.logic.MealPlanGenerator
import com.nutricart.app.domain.logic.PlanFailureReason
import com.nutricart.app.ui.common.LoadingBox
import com.nutricart.app.ui.diary.mealSlotLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

/** The weekly plan: 7 day cards with meals, lock/swap/log actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    onOpenRecipe: (recipeId: Long, portionFactor: Double) -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Roll the week forward if the app slept past midnight.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeek()
        onPauseOrDispose { }
    }

    // Generation failures arrive as one-shot errors -> snackbar.
    val noRecipesMessage = stringResource(R.string.plan_error_no_recipes)
    val unreachableMessage = stringResource(R.string.plan_error_unreachable)
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (error) {
                PlanFailureReason.NO_RECIPES_FOR_SLOT -> noRecipesMessage
                PlanFailureReason.TARGET_UNREACHABLE -> unreachableMessage
            }
        )
        viewModel.clearError()
    }

    // "Added to diary" confirmation fires only after the insert succeeded.
    val loggedMessage = stringResource(R.string.logged_to_diary)
    LaunchedEffect(state.loggedToDiary) {
        if (state.loggedToDiary) {
            snackbarHostState.showSnackbar(loggedMessage)
            viewModel.clearLoggedToDiary()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.plan_title)) }) },
    ) { innerPadding ->
        if (state.loading) {
            LoadingBox(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Button(
                        onClick = viewModel::generate,
                        enabled = !state.generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.hasPlan) R.string.regenerate_plan
                                else R.string.generate_plan
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!state.hasPlan) {
                        Text(
                            stringResource(R.string.plan_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(state.days.filter { it.meals.isNotEmpty() }, key = { it.epochDay }) { day ->
                    DayCard(
                        day = day,
                        targetKcal = state.targetKcal,
                        // While a generation runs the plan is being rewritten —
                        // freeze the per-meal actions to avoid racing it.
                        actionsEnabled = !state.generating,
                        // The diary cannot browse into the future, so logging a
                        // future meal would create an entry the user can't see
                        // or delete until that day — offer "+" only for today.
                        canLogToDiary = day.epochDay <= LocalDate.now().toEpochDay(),
                        onOpenRecipe = onOpenRecipe,
                        onToggleLock = viewModel::toggleLock,
                        onSwap = viewModel::swap,
                        onAddToDiary = viewModel::addToDiary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun DayCard(
    day: PlanDayUi,
    targetKcal: Int,
    actionsEnabled: Boolean,
    canLogToDiary: Boolean,
    onOpenRecipe: (Long, Double) -> Unit,
    onToggleLock: (PlanMealUi) -> Unit,
    onSwap: (PlanMealUi) -> Unit,
    onAddToDiary: (PlanMealUi) -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL) }
    Card {
        // animateContentSize: swapping a meal resizes the card smoothly.
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
        ) {
            Text(
                LocalDate.ofEpochDay(day.epochDay).format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
            )
            // Green when the day is inside the band the generator promises.
            val inBand = targetKcal > 0 &&
                abs(day.totalKcal - targetKcal).toDouble() / targetKcal <=
                MealPlanGenerator.KCAL_TOLERANCE
            Text(
                stringResource(R.string.day_kcal_vs_target, day.totalKcal, targetKcal),
                style = MaterialTheme.typography.bodyMedium,
                color = if (inBand) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()

            day.meals.forEach { meal ->
                MealRow(
                    meal = meal,
                    actionsEnabled = actionsEnabled,
                    canLogToDiary = canLogToDiary,
                    onClick = { onOpenRecipe(meal.recipeId, meal.portionFactor) },
                    onToggleLock = { onToggleLock(meal) },
                    onSwap = { onSwap(meal) },
                    onAddToDiary = { onAddToDiary(meal) },
                )
            }
        }
    }
}

@Composable
private fun MealRow(
    meal: PlanMealUi,
    actionsEnabled: Boolean,
    canLogToDiary: Boolean,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onSwap: () -> Unit,
    onAddToDiary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mealSlotLabel(meal.slot),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(meal.recipeName, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.portion_and_kcal, meal.portionFactor, meal.kcal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleLock, enabled = actionsEnabled) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(
                    if (meal.isLocked) R.string.unlock_meal else R.string.lock_meal
                ),
                tint = if (meal.isLocked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onSwap, enabled = actionsEnabled && !meal.isLocked) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.swap_meal),
            )
        }
        if (canLogToDiary) {
            IconButton(onClick = onAddToDiary, enabled = actionsEnabled) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.log_meal),
                )
            }
        }
    }
}
