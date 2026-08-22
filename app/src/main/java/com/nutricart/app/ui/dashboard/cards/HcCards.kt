package com.nutricart.app.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.HcBannerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val HEALTH_CONNECT_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

/** One card per Health Connect problem, each with the action that fixes it. */
@Composable
internal fun HcBannerCard(
    banner: HcBannerState,
    onInstallOrUpdate: () -> Unit,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val textRes = when (banner) {
                HcBannerState.NOT_INSTALLED -> R.string.hc_banner_not_installed
                HcBannerState.UPDATE_REQUIRED -> R.string.hc_banner_update
                else -> R.string.hc_banner_no_permission
            }
            Text(stringResource(textRes), style = MaterialTheme.typography.bodyMedium)

            when (banner) {
                HcBannerState.NOT_INSTALLED -> Button(onClick = onInstallOrUpdate) {
                    Text(stringResource(R.string.hc_install))
                }
                HcBannerState.UPDATE_REQUIRED -> Button(onClick = onInstallOrUpdate) {
                    Text(stringResource(R.string.hc_update))
                }
                else -> Button(onClick = onGrant) {
                    Text(stringResource(R.string.hc_grant))
                }
            }
        }
    }
}

@Composable
internal fun LastSyncedText(epochMillis: Long) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val text = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(formatter)
    Text(
        stringResource(R.string.last_synced, text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
