package com.quantma.lite.domain.model

/**
 * Represents the working tree status of a Git repository.
 */
data class GitStatus(
    val added: Set<String> = emptySet(),       // Staged, new files
    val changed: Set<String> = emptySet(),     // Staged, modified files
    val removed: Set<String> = emptySet(),     // Staged, deleted files
    val untracked: Set<String> = emptySet(),   // Not tracked at all
    val modified: Set<String> = emptySet(),    // Unstaged modifications
    val missing: Set<String> = emptySet(),     // Tracked but deleted from disk
    val conflicting: Set<String> = emptySet()  // Merge conflicts
) {
    val isClean: Boolean
        get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty() &&
                untracked.isEmpty() && modified.isEmpty() && missing.isEmpty() &&
                conflicting.isEmpty()

    val stagedCount: Int
        get() = added.size + changed.size + removed.size

    val unstagedCount: Int
        get() = untracked.size + modified.size + missing.size
}

/**
 * A single entry in the git log.
 */
data class GitLogEntry(
    val hash: String,          // Full SHA-1
    val shortHash: String,     // First 7 characters
    val message: String,       // Full commit message
    val authorName: String,
    val authorEmail: String,
    val timestamp: Long        // Epoch millis
)

// ---- Phase 3 (v1.2.0) — Branch / Merge / Diff / Stash ----

/** Local or remote branch. */
data class GitBranch(
    val name: String,
    val isCurrent: Boolean,
    val isRemote: Boolean = false
)

/** Result of a merge operation. */
data class MergeResult(
    val success: Boolean,
    val mergeStatus: String,              // e.g. "FAST_FORWARD", "MERGED", "CONFLICTING"
    val conflicts: List<String> = emptyList()
)

/** A stash entry (stash@{index}). */
data class StashEntry(
    val index: Int,
    val message: String,
    val branchName: String
)

/** One file's unified diff within a `getDiff()` result. */
data class GitDiffEntry(
    val filePath: String,
    val changeType: String,   // "MODIFY", "ADD", "DELETE", "RENAME"
    val unified: String       // raw unified-diff text for this file
)
