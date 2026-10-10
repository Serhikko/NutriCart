package com.nutricart.app.ui.dashboard.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nutricart.app.R
import com.nutricart.app.ui.dashboard.HcBannerState
import com.nutricart.app.ui.ember.EmberButton
import com.nutricart.app.ui.ember.EmberIcons
import com.nutricart.app.ui.ember.Notice
import com.nutricart.app.ui.ember.NoticeTone

internal const val HEALTH_CONNECT_PLAY_URL =
    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"

/**
 * One notice per Health Connect problem, each with the one action that fixes it: Install / Update
 * (the Play Store page) or Allow access (the permission dialog). Titled "Health Connect" with the
 * heart glyph; the explanation and the ink button below.
 */
@Composable
internal fun HcBannerCard(
    banner: HcBannerState,
    onInstallOrUpdate: () -> Unit,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textRes = when (banner) {
        HcBannerState.NOT_INSTALLED -> R.string.hc_banner_not_installed
        HcBannerState.UPDATE_REQUIRED -> R.string.hc_banner_update
        else -> R.string.hc_banner_no_permission
    }
    Notice(
        tone = NoticeTone.Info,
        title = stringResource(R.string.hc_section),
        detail = stringResource(textRes),
        icon = EmberIcons.Heart,
        modifier = modifier,
        action = {
            when (banner) {
                HcBannerState.NOT_INSTALLED -> EmberButton(stringResource(R.string.hc_install), onInstallOrUpdate)
                HcBannerState.UPDATE_REQUIRED -> EmberButton(stringResource(R.string.hc_update), onInstallOrUpdate)
                else -> EmberButton(stringResource(R.string.hc_grant), onGrant)
            }
        },
    )
}

/**
 * Health Connect is connected but holds no steps or calories (the watch app is probably not sharing
 * with it): a quiet notice with the way to fix it.
 */
@Composable
internal fun NoDataHint(modifier: Modifier = Modifier) {
    Notice(
        tone = NoticeTone.Info,
        title = stringResource(R.string.hc_section),
        detail = stringResource(R.string.hc_no_data_hint),
        icon = EmberIcons.Heart,
        modifier = modifier,
    )
}
