package com.quantma.lite.ui.git.credentials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.preferences.GitCredentialsStore
import com.quantma.lite.data.local.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GitCredentialsViewModel @Inject constructor(
    private val gitCredentialsStore: GitCredentialsStore,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val authorName: StateFlow<String> = settingsDataStore.gitAuthorName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "QuantMA User"
    )

    val authorEmail: StateFlow<String> = settingsDataStore.gitAuthorEmail.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "user@codeagent.local"
    )

    private val _token = MutableStateFlow(gitCredentialsStore.getToken())
    val token: StateFlow<String> = _token.asStateFlow()

    private val _savedEvent = MutableStateFlow(false)
    val savedEvent: StateFlow<Boolean> = _savedEvent.asStateFlow()

    fun save(name: String, email: String, token: String) {
        viewModelScope.launch {
            settingsDataStore.setGitAuthorName(name)
            settingsDataStore.setGitAuthorEmail(email)
            gitCredentialsStore.setToken(token)
            _token.value = token
            _savedEvent.value = true
        }
    }

    fun clearSavedEvent() {
        _savedEvent.value = false
    }
}
