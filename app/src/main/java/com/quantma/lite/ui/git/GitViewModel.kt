package com.quantma.lite.ui.git

import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.preferences.GitCredentialsStore
import com.quantma.lite.data.local.preferences.SettingsDataStore
import com.quantma.lite.domain.model.GitBranch
import com.quantma.lite.domain.model.GitDiffEntry
import com.quantma.lite.domain.model.GitLogEntry
import com.quantma.lite.domain.model.GitRemote
import com.quantma.lite.domain.model.GitStatus
import com.quantma.lite.domain.model.StashEntry
import com.quantma.lite.domain.repository.GitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GitViewModel @Inject constructor(
    private val gitRepository: GitRepository,
    private val gitCredentialsStore: GitCredentialsStore,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _repoPath = MutableStateFlow("")
    val repoPath: StateFlow<String> = _repoPath.asStateFlow()

    private val _currentBranch = MutableStateFlow("")
    val currentBranch: StateFlow<String> = _currentBranch.asStateFlow()

    private val _gitStatus = MutableStateFlow(GitStatus())
    val gitStatus: StateFlow<GitStatus> = _gitStatus.asStateFlow()

    private val _logEntries = MutableStateFlow<List<GitLogEntry>>(emptyList())
    val logEntries: StateFlow<List<GitLogEntry>> = _logEntries.asStateFlow()

    // ---- Phase 3 (v1.2.0) ----
    private val _branches = MutableStateFlow<List<GitBranch>>(emptyList())
    val branches: StateFlow<List<GitBranch>> = _branches.asStateFlow()

    private val _diffEntries = MutableStateFlow<List<GitDiffEntry>>(emptyList())
    val diffEntries: StateFlow<List<GitDiffEntry>> = _diffEntries.asStateFlow()

    private val _stashList = MutableStateFlow<List<StashEntry>>(emptyList())
    val stashList: StateFlow<List<StashEntry>> = _stashList.asStateFlow()

    // ---- Phase 4 (v1.3.0) ----
    private val _remotes = MutableStateFlow<List<GitRemote>>(emptyList())
    val remotes: StateFlow<List<GitRemote>> = _remotes.asStateFlow()

    private val _isCloning = MutableStateFlow(false)
    val isCloning: StateFlow<Boolean> = _isCloning.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _commitMessage = MutableStateFlow("")
    val commitMessage: StateFlow<String> = _commitMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(GitTab.STATUS)
    val selectedTab: StateFlow<GitTab> = _selectedTab.asStateFlow()

    fun openRepo(path: String) {
        _repoPath.value = path
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    refreshInternal()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun refreshInternal() {
        val path = _repoPath.value
        if (path.isEmpty()) return

        gitRepository.getCurrentBranch(path)
            .onSuccess { _currentBranch.value = it }
            .onFailure { Timber.w(it, "Failed to get branch") }

        gitRepository.getStatus(path)
            .onSuccess { _gitStatus.value = it }
            .onFailure {
                _errorMessage.value = "Failed to get status: ${it.message}"
                Timber.e(it, "getStatus failed")
            }

        gitRepository.getLog(path)
            .onSuccess { _logEntries.value = it }
            .onFailure {
                _logEntries.value = emptyList()
                Timber.w(it, "getLog failed (may be empty repo)")
            }

        gitRepository.getRemotes(path)
            .onSuccess { _remotes.value = it }
            .onFailure { Timber.w(it, "getRemotes failed") }
    }

    fun stageAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    gitRepository.addAll(_repoPath.value)
                }
                result
                    .onSuccess {
                        _successMessage.value = "All changes staged"
                        withContext(Dispatchers.IO) { refreshInternal() }
                    }
                    .onFailure {
                        _errorMessage.value = "Stage failed: ${it.message}"
                        Timber.e(it, "addAll failed")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun commit() {
        val message = _commitMessage.value.trim()
        if (message.isEmpty()) {
            _errorMessage.value = "Commit message cannot be empty"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val authorName = settingsDataStore.gitAuthorName.first()
                val authorEmail = settingsDataStore.gitAuthorEmail.first()
                val result = withContext(Dispatchers.IO) {
                    gitRepository.commit(
                        repoPath = _repoPath.value,
                        message = message,
                        authorName = authorName,
                        authorEmail = authorEmail
                    )
                }
                result
                    .onSuccess { hash ->
                        _successMessage.value = "Committed: $hash"
                        _commitMessage.value = ""
                        withContext(Dispatchers.IO) { refreshInternal() }
                    }
                    .onFailure {
                        _errorMessage.value = "Commit failed: ${it.message}"
                        Timber.e(it, "commit failed")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onCommitMessageChanged(message: String) {
        _commitMessage.value = message
    }

    fun selectTab(tab: GitTab) {
        _selectedTab.value = tab
        when (tab) {
            GitTab.BRANCHES -> loadBranches()
            GitTab.DIFF -> loadDiff()
            else -> {}
        }
    }

    // ---- Phase 3 operations ----

    fun loadBranches() {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.listBranches(_repoPath.value)
                .onSuccess { _branches.value = it }
                .onFailure { Timber.e(it, "listBranches failed") }
            gitRepository.stashList(_repoPath.value)
                .onSuccess { _stashList.value = it }
                .onFailure { Timber.w(it, "stashList failed") }
        }
    }

    fun createBranch(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.createBranch(_repoPath.value, name)
                .onSuccess {
                    _successMessage.value = "Branch '$name' created"
                    loadBranches()
                }
                .onFailure { _errorMessage.value = "Create branch failed: ${it.message}" }
        }
    }

    fun checkoutBranch(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.checkoutBranch(_repoPath.value, name)
                .onSuccess {
                    _successMessage.value = "Switched to '$name'"
                    _currentBranch.value = name
                    loadBranches()
                }
                .onFailure { _errorMessage.value = "Checkout failed: ${it.message}" }
        }
    }

    fun deleteBranch(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.deleteBranch(_repoPath.value, name)
                .onSuccess {
                    _successMessage.value = "Branch '$name' deleted"
                    loadBranches()
                }
                .onFailure { _errorMessage.value = "Delete branch failed: ${it.message}" }
        }
    }

    fun merge(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.merge(_repoPath.value, branchName)
                .onSuccess { result ->
                    if (result.success) {
                        _successMessage.value = "Merged: ${result.mergeStatus}"
                    } else {
                        val conflicts = result.conflicts.joinToString(", ")
                        _errorMessage.value = if (conflicts.isNotEmpty())
                            "Conflicts: $conflicts"
                        else
                            "Merge failed: ${result.mergeStatus}"
                    }
                    refreshInternal()
                }
                .onFailure { _errorMessage.value = "Merge failed: ${it.message}" }
        }
    }

    fun loadDiff() {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.getDiff(_repoPath.value)
                .onSuccess { _diffEntries.value = it }
                .onFailure { Timber.e(it, "getDiff failed") }
        }
    }

    fun stash() {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.stash(_repoPath.value)
                .onSuccess {
                    _successMessage.value = "Changes stashed"
                    loadBranches()
                    refreshInternal()
                }
                .onFailure { _errorMessage.value = "Stash failed: ${it.message}" }
        }
    }

    fun stashPop(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.stashPop(_repoPath.value, index)
                .onSuccess {
                    _successMessage.value = "Stash applied"
                    loadBranches()
                    refreshInternal()
                }
                .onFailure { _errorMessage.value = "Stash pop failed: ${it.message}" }
        }
    }

    // ---- Phase 4 remote operations ----

    fun loadRemotes() {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.getRemotes(_repoPath.value)
                .onSuccess { _remotes.value = it }
                .onFailure { Timber.w(it, "getRemotes failed") }
        }
    }

    fun pull() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = gitCredentialsStore.getToken()
                val result = withContext(Dispatchers.IO) {
                    gitRepository.pull(_repoPath.value, token)
                }
                result
                    .onSuccess {
                        _successMessage.value = "Pulled: $it"
                        withContext(Dispatchers.IO) { refreshInternal() }
                    }
                    .onFailure {
                        _errorMessage.value = "Pull failed: ${it.message}"
                        Timber.e(it, "pull failed")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun push() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = gitCredentialsStore.getToken()
                if (token.isBlank()) {
                    _errorMessage.value = "Token required for push. Set it in Settings → Git."
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    gitRepository.push(_repoPath.value, token)
                }
                result
                    .onSuccess { _successMessage.value = "Push successful" }
                    .onFailure {
                        _errorMessage.value = "Push failed: ${it.message}"
                        Timber.e(it, "push failed")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = gitCredentialsStore.getToken()
                val result = withContext(Dispatchers.IO) {
                    gitRepository.fetch(_repoPath.value, token)
                }
                result
                    .onSuccess { _successMessage.value = "Fetch successful" }
                    .onFailure {
                        _errorMessage.value = "Fetch failed: ${it.message}"
                        Timber.e(it, "fetch failed")
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clone(url: String, destPath: String) {
        // Normalize URL: if no protocol, assume GitHub shorthand "owner/repo"
        val normalizedUrl = if ("://" !in url) {
            "https://github.com/${url.trimStart('/')}"
        } else url

        // Smart destination: if dest already exists and is non-empty, append repo name
        val repoName = normalizedUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
        val destFile = File(destPath)
        val finalDest = if (destFile.exists() && destFile.listFiles()?.isNotEmpty() == true) {
            "${destPath.trimEnd('/')}/$repoName"
        } else destPath

        viewModelScope.launch {
            _isCloning.value = true
            try {
                val token = gitCredentialsStore.getToken()
                val result = withContext(Dispatchers.IO) {
                    gitRepository.clone(normalizedUrl, finalDest, token)
                }
                result
                    .onSuccess {
                        _successMessage.value = "Cloned to $finalDest"
                        _repoPath.value = finalDest
                        withContext(Dispatchers.IO) { refreshInternal() }
                    }
                    .onFailure {
                        _errorMessage.value = "Clone failed: ${it.message}"
                        Timber.e(it, "clone failed")
                    }
            } finally {
                _isCloning.value = false
            }
        }
    }

    fun addRemote(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            gitRepository.addRemote(_repoPath.value, name, url)
                .onSuccess {
                    _successMessage.value = "Remote '$name' added"
                    loadRemotes()
                }
                .onFailure { _errorMessage.value = "Add remote failed: ${it.message}" }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSuccess() {
        _successMessage.value = null
    }
}

enum class GitTab {
    STATUS,
    LOG,
    BRANCHES,
    DIFF
}
