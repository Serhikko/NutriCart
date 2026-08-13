package com.nutricart.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nutricart.app.MainActivity
import com.nutricart.app.R
import dagger.hilt.android.EntryPointAccessors

/**
 * "Remaining today" on the home screen. The system refreshes it every 30 min
 * (widget_info.xml); MainActivity additionally refreshes it whenever the app
 * goes to the background, so a just-logged meal shows up immediately.
 */
class NutriCartWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance objects are created by the system, not by Hilt — the
        // EntryPoint is the documented way to reach injected classes.
        val data = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .widgetDataSource()
            .today()
        provideContent { WidgetContent(data) }
    }
}

/** The manifest-registered receiver; all real logic lives in the widget. */
class NutriCartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutriCartWidget()
}

@Composable
private fun WidgetContent(data: WidgetData?) {
    GlanceTheme {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (data == null) {
                // Onboarding not finished — the widget politely waits.
                Text(
                    context.getString(R.string.widget_no_data),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                )
            } else {
                Text(
                    data.remainingKcal.toString(),
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary,
                    ),
                )
                Text(
                    context.getString(R.string.widget_remaining),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                )
                Text(
                    context.getString(
                        R.string.widget_eaten_of_target, data.eatenKcal, data.targetKcal,
                    ),
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface),
                )
            }
        }
    }
}
