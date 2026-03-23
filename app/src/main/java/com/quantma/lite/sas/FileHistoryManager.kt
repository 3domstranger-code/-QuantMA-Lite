package com.quantma.lite.sas

import java.io.File
import java.security.MessageDigest

/**
 * Secure Agent Shell — File History Manager.
 *
 * Provides undo/redo functionality for file operations by maintaining snapshots
 * in a `.sas_history/` directory within the working directory.
 *
 * Architecture:
 * - Before every write/create/append, [ShellExecutor] calls [saveSnapshot]
 * - Each snapshot stores the file's previous content (or null for new files)
 * - Snapshots are organized by file hash of the path: `.sas_history/<hash>/`
 * - Each snapshot is a timestamped file: `<timestamp>.snapshot`
 * - Undo restores the most recent snapshot; redo reverses undo
 *
 * Limits:
 * - Max [maxSnapshotsPerFile] snapshots per file (FIFO eviction)
 * - Max [maxTotalSizeBytes] total snapshot storage
 */
class FileHistoryManager(
    private val workingDir: String,
    private val maxSnapshotsPerFile: Int = DEFAULT_MAX_SNAPSHOTS_PER_FILE,
    private val maxTotalSizeBytes: Long = DEFAULT_MAX_TOTAL_SIZE
) {
    private val historyDir = File(workingDir, HISTORY_DIR_NAME)

    // In-memory redo stacks per file (lost on process death — acceptable for mobile)
    private val redoStacks = mutableMapOf<String, MutableList<FileSnapshot>>()

    /**
     * Save a snapshot of the file's current state before modification.
     * Call this BEFORE writing new content.
     *
     * @param file The file about to be modified
     * @return [FileSnapshot] that was saved, or null if file doesn't exist (new file creation)
     */
    fun saveSnapshot(file: File): FileSnapshot? {
        val relativePath = file.toRelativeString(File(workingDir))
        val content = if (file.exists()) file.readText() else null

        val snapshot = FileSnapshot(
            path = relativePath,
            timestamp = System.currentTimeMillis(),
            content = content,
            sizeBytes = content?.toByteArray()?.size?.toLong() ?: 0L
        )

        // Write snapshot to disk
        val snapshotDir = getSnapshotDir(relativePath)
        snapshotDir.mkdirs()

        val snapshotFile = File(snapshotDir, "${snapshot.timestamp}.snapshot")
        if (content != null) {
            snapshotFile.writeText(content)
        } else {
            // Mark as "file didn't exist" with a special marker
            snapshotFile.writeText(NEW_FILE_MARKER)
        }

        // Clear redo stack for this file (new change invalidates redo history)
        redoStacks.remove(relativePath)

        // Enforce per-file limit
        enforcePerFileLimit(snapshotDir)

        // Enforce total size limit (lazy — only on save)
        enforceTotalSizeLimit()

        return snapshot
    }

    /**
     * Undo the last change to a file.
     * Restores the file to its state before the most recent modification.
     *
     * @param relativePath Relative path from working directory
     * @return Success with description, or failure if no history available
     */
    fun undo(relativePath: String): Result<String> {
        val snapshotDir = getSnapshotDir(relativePath)
        val snapshots = listSnapshots(snapshotDir)

        if (snapshots.isEmpty()) {
            return Result.failure(IllegalStateException("No history available for: $relativePath"))
        }

        val latestSnapshot = snapshots.last()
        val targetFile = File(workingDir, relativePath)

        // Save current state to redo stack before restoring
        val currentContent = if (targetFile.exists()) targetFile.readText() else null
        val redoSnapshot = FileSnapshot(
            path = relativePath,
            content = currentContent
        )
        redoStacks.getOrPut(relativePath) { mutableListOf() }.add(redoSnapshot)

        // Restore from snapshot
        val snapshotContent = latestSnapshot.readText()
        if (snapshotContent == NEW_FILE_MARKER) {
            // File didn't exist before — delete it
            if (targetFile.exists()) targetFile.delete()
        } else {
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(snapshotContent)
        }

        // Remove the used snapshot
        latestSnapshot.delete()

        val description = if (snapshotContent == NEW_FILE_MARKER) {
            "Undo: deleted $relativePath (file was newly created)"
        } else {
            "Undo: restored $relativePath to previous version"
        }

        return Result.success(description)
    }

    /**
     * Redo the last undone change to a file.
     *
     * @param relativePath Relative path from working directory
     * @return Success with description, or failure if no redo available
     */
    fun redo(relativePath: String): Result<String> {
        val redoStack = redoStacks[relativePath]
        if (redoStack.isNullOrEmpty()) {
            return Result.failure(IllegalStateException("No redo history for: $relativePath"))
        }

        val redoSnapshot = redoStack.removeLast()
        val targetFile = File(workingDir, relativePath)

        // Save current state as snapshot before redo (so undo works again)
        saveSnapshot(targetFile)
        // Re-add redo entries that saveSnapshot cleared (except the one we just consumed)
        // Note: saveSnapshot clears redoStacks[relativePath], so we restore it
        if (redoStack.isNotEmpty()) {
            redoStacks[relativePath] = redoStack
        }

        // Apply redo
        if (redoSnapshot.content == null) {
            if (targetFile.exists()) targetFile.delete()
        } else {
            targetFile.parentFile?.mkdirs()
            targetFile.writeText(redoSnapshot.content)
        }

        return Result.success("Redo: restored $relativePath")
    }

    /**
     * Get the history of snapshots for a file.
     *
     * @param relativePath Relative path from working directory
     * @return List of [FileSnapshot] entries, oldest first
     */
    fun getHistory(relativePath: String): List<FileSnapshot> {
        val snapshotDir = getSnapshotDir(relativePath)
        val files = listSnapshots(snapshotDir)

        return files.map { file ->
            val content = file.readText()
            FileSnapshot(
                path = relativePath,
                timestamp = file.nameWithoutExtension.toLongOrNull() ?: 0L,
                content = if (content == NEW_FILE_MARKER) null else content,
                sizeBytes = if (content == NEW_FILE_MARKER) 0L else file.length()
            )
        }
    }

    /**
     * Check if undo is available for a file.
     */
    fun canUndo(relativePath: String): Boolean {
        val snapshotDir = getSnapshotDir(relativePath)
        return snapshotDir.exists() && (snapshotDir.listFiles()?.isNotEmpty() == true)
    }

    /**
     * Check if redo is available for a file.
     */
    fun canRedo(relativePath: String): Boolean {
        return redoStacks[relativePath]?.isNotEmpty() == true
    }

    /**
     * Clear all history for a specific file.
     */
    fun clearFileHistory(relativePath: String) {
        val snapshotDir = getSnapshotDir(relativePath)
        snapshotDir.deleteRecursively()
        redoStacks.remove(relativePath)
    }

    /**
     * Clear all snapshot history.
     */
    fun clearAllHistory() {
        historyDir.deleteRecursively()
        redoStacks.clear()
    }

    /**
     * Get total size of all snapshots in bytes.
     */
    fun getTotalSnapshotSize(): Long {
        if (!historyDir.exists()) return 0L
        return historyDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Get count of all snapshots.
     */
    fun getTotalSnapshotCount(): Int {
        if (!historyDir.exists()) return 0
        return historyDir.walkTopDown()
            .filter { it.isFile && it.extension == "snapshot" }
            .count()
    }

    // ─── Private Helpers ────────────────────────────────────────────────────

    private fun getSnapshotDir(relativePath: String): File {
        val hash = hashPath(relativePath)
        return File(historyDir, hash)
    }

    private fun listSnapshots(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "snapshot" }
            ?.sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            ?: emptyList()
    }

    private fun hashPath(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(path.toByteArray())
        // Use first 16 hex chars — enough for uniqueness, short for filesystem
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun enforcePerFileLimit(snapshotDir: File) {
        val snapshots = listSnapshots(snapshotDir)
        if (snapshots.size > maxSnapshotsPerFile) {
            // Remove oldest snapshots
            val toRemove = snapshots.size - maxSnapshotsPerFile
            snapshots.take(toRemove).forEach { it.delete() }
        }
    }

    private fun enforceTotalSizeLimit() {
        var totalSize = getTotalSnapshotSize()
        if (totalSize <= maxTotalSizeBytes) return

        // Collect all snapshot files sorted by age (oldest first)
        val allSnapshots = historyDir.walkTopDown()
            .filter { it.isFile && it.extension == "snapshot" }
            .sortedBy { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .toList()

        for (file in allSnapshots) {
            if (totalSize <= maxTotalSizeBytes) break
            totalSize -= file.length()
            file.delete()
        }

        // Clean up empty directories
        historyDir.walkBottomUp()
            .filter { it.isDirectory && it != historyDir && (it.listFiles()?.isEmpty() == true) }
            .forEach { it.delete() }
    }

    companion object {
        const val HISTORY_DIR_NAME = ".sas_history"
        const val NEW_FILE_MARKER = "<<SAS_FILE_DID_NOT_EXIST>>"
        const val DEFAULT_MAX_SNAPSHOTS_PER_FILE = 50
        const val DEFAULT_MAX_TOTAL_SIZE = 100L * 1024 * 1024  // 100 MB
    }
}
