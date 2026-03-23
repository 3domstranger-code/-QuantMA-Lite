package com.quantma.lite.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Android DownloadManager for downloading .gguf model files.
 * Downloads go to context.getExternalFilesDir("models").
 * Phase 10 (v1.9.0)
 */

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val filename: String
    ) : DownloadState()
    data class Done(val filePath: String, val filename: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Enqueues a download and returns the download ID.
     * Filename is derived from the URL, ".gguf" is appended if missing.
     */
    fun startDownload(url: String): Pair<Long, String> {
        val rawName = url.substringAfterLast('/').substringBefore('?').substringBefore('#').trim()
        val filename = if (rawName.endsWith(".gguf", ignoreCase = true)) rawName
                       else if (rawName.isNotEmpty()) "$rawName.gguf"
                       else "model_${System.currentTimeMillis()}.gguf"

        val destDir = context.getExternalFilesDir("models")
            ?: throw IllegalStateException("External storage unavailable")
        destDir.mkdirs()
        val dest = File(destDir, filename)

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading $filename")
            .setDescription("QuantMA model")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationUri(Uri.fromFile(dest))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = dm.enqueue(request)
        return Pair(downloadId, filename)
    }

    /**
     * Polls download progress every 500ms.
     * Emits DownloadState until Done or Error.
     */
    fun observeProgress(downloadId: Long, filename: String): Flow<DownloadState> = flow {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)
            if (cursor == null) {
                emit(DownloadState.Error("Download record not found"))
                return@flow
            }
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    emit(DownloadState.Error("Download record disappeared"))
                    return@flow
                }

                val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)

                val status = c.getInt(statusIdx)
                val downloaded = c.getLong(downloadedIdx)
                val total = c.getLong(totalIdx)
                val localUri = if (localUriIdx >= 0) c.getString(localUriIdx) ?: "" else ""
                val reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else 0

                when (status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_RUNNING -> {
                        val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                        emit(DownloadState.Downloading(progress, downloaded, total, filename))
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadState.Done(localUri, filename))
                        return@flow
                    }
                    DownloadManager.STATUS_FAILED -> {
                        emit(DownloadState.Error("Download failed (code $reason)"))
                        return@flow
                    }
                }
            }
            delay(500)
        }
    }

    /** Cancels an active download. */
    fun cancel(downloadId: Long) {
        dm.remove(downloadId)
    }
}
