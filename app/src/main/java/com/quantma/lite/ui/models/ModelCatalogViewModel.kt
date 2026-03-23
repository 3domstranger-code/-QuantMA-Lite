package com.quantma.lite.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.download.DownloadState
import com.quantma.lite.data.download.ModelDownloader
import com.quantma.lite.data.model.RecommendedModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for ModelCatalogScreen.
 * Manages download state per model (keyed by filename) with cancel support.
 * Phase 9 (v1.9.1) → v2.0: cancel downloads, track download IDs.
 */
@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val modelDownloader: ModelDownloader
) : ViewModel() {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /** Active download IDs keyed by model filename. */
    private val activeDownloads = mutableMapOf<String, Long>()

    /** Active observation jobs keyed by model filename. */
    private val observeJobs = mutableMapOf<String, Job>()

    fun startDownload(model: RecommendedModel) {
        // Don't start if already downloading
        val current = _downloadStates.value[model.filename]
        if (current is DownloadState.Downloading) return

        val job = viewModelScope.launch {
            try {
                val (downloadId, filename) = modelDownloader.startDownload(model.url)
                activeDownloads[model.filename] = downloadId
                modelDownloader.observeProgress(downloadId, filename).collect { state ->
                    _downloadStates.value = _downloadStates.value + (model.filename to state)
                    if (state is DownloadState.Done || state is DownloadState.Error) {
                        activeDownloads.remove(model.filename)
                        observeJobs.remove(model.filename)
                    }
                }
            } catch (e: Exception) {
                _downloadStates.value = _downloadStates.value +
                    (model.filename to DownloadState.Error(e.message ?: "Unknown error"))
                activeDownloads.remove(model.filename)
                observeJobs.remove(model.filename)
            }
        }
        observeJobs[model.filename] = job
    }

    fun cancelDownload(model: RecommendedModel) {
        activeDownloads[model.filename]?.let { downloadId ->
            modelDownloader.cancel(downloadId)
        }
        observeJobs[model.filename]?.cancel()
        activeDownloads.remove(model.filename)
        observeJobs.remove(model.filename)
        _downloadStates.value = _downloadStates.value + (model.filename to DownloadState.Idle)
    }
}
