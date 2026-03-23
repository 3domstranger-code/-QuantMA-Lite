package com.quantma.lite.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for OnboardingScreen.
 * Phase 9 (v1.9.1)
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingComplete(true)
        }
    }
}
