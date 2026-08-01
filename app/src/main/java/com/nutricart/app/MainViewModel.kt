package com.nutricart.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutricart.app.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Decides the start screen: null = still reading from disk, then true/false. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsDataStore,
) : ViewModel() {

    // The property type is StateFlow<Boolean?> so the initial value can be null
    // ("don't know yet" — the UI shows a loader until DataStore answers).
    val onboardingCompleted: StateFlow<Boolean?> =
        settings.onboardingCompleted.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
