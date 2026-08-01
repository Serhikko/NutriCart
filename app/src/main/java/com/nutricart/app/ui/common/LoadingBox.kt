package com.nutricart.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nutricart.app.R

/**
 * Full-screen loading spinner, reused by every screen's loading state.
 * The contentDescription makes the spinner announce "Loading…" to screen readers.
 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
