package com.quantma.lite.data.agent

import com.quantma.lite.data.local.preferences.GitCredentialsStore
import com.quantma.lite.domain.repository.GitRepository
import com.quantma.lite.sas.GitOperations

/**
 * Adapts [GitRepository] (JGit-backed) to the SAS [GitOperations] interface.
 * All methods are synchronous — call from Dispatchers.IO.
 */
class GitOperationsAdapter(
    private val git: GitRepository,
    private val credentialsStore: GitCredentialsStore
) : GitOperations {

    override fun isGitRepo(path: String): Boolean = git.isGitRepo(path)

    override fun getStatus(repoPath: String): String =
        git.getStatus(repoPath).fold(
            onSuccess = { status ->
                buildString {
                    if (status.added.isNotEmpty())     appendLine("Staged (new): ${status.added.joinToString()}")
                    if (status.changed.isNotEmpty())   appendLine("Staged (modified): ${status.changed.joinToString()}")
                    if (status.removed.isNotEmpty())   appendLine("Staged (deleted): ${status.removed.joinToString()}")
                    if (status.modified.isNotEmpty())  appendLine("Modified: ${status.modified.joinToString()}")
                    if (status.untracked.isNotEmpty()) appendLine("Untracked: ${status.untracked.joinToString()}")
                    if (status.missing.isNotEmpty())   appendLine("Missing: ${status.missing.joinToString()}")
                    if (status.conflicting.isNotEmpty()) appendLine("Conflicts: ${status.conflicting.joinToString()}")
                    if (isEmpty()) append("nothing to commit, working tree clean")
                }.trimEnd()
            },
            onFailure = { "Error getting status: ${it.message}" }
        )

    override fun addFiles(repoPath: String, files: List<String>): String =
        git.addFiles(repoPath, files).fold(
            onSuccess = { "Staged ${files.size} file(s)." },
            onFailure = { "Error staging files: ${it.message}" }
        )

    override fun addAll(repoPath: String): String =
        git.addAll(repoPath).fold(
            onSuccess = { "All changes staged." },
            onFailure = { "Error staging all: ${it.message}" }
        )

    override fun commit(repoPath: String, message: String): String =
        git.commit(repoPath, message, "QuantMA", "agent@quantma.local").fold(
            onSuccess = { hash -> "Committed: $message ($hash)" },
            onFailure = { "Error committing: ${it.message}" }
        )

    override fun getLog(repoPath: String, maxCount: Int): String =
        git.getLog(repoPath, maxCount).fold(
            onSuccess = { entries ->
                if (entries.isEmpty()) "No commits found."
                else entries.joinToString("\n") { "${it.shortHash} ${it.message}" }
            },
            onFailure = { "Error getting log: ${it.message}" }
        )

    override fun getCurrentBranch(repoPath: String): String =
        git.getCurrentBranch(repoPath).fold(
            onSuccess = { it },
            onFailure = { "unknown" }
        )

    // ── Branch / Merge ────────────────────────────────────────────────────────

    override fun createBranch(repoPath: String, name: String): String =
        git.createBranch(repoPath, name).fold(
            onSuccess = { "Branch '$name' created." },
            onFailure = { "Error creating branch: ${it.message}" }
        )

    override fun checkoutBranch(repoPath: String, name: String): String =
        git.checkoutBranch(repoPath, name).fold(
            onSuccess = { "Switched to branch '$name'." },
            onFailure = { "Checkout failed: ${it.message}" }
        )

    override fun deleteBranch(repoPath: String, name: String): String =
        git.deleteBranch(repoPath, name).fold(
            onSuccess = { "Branch '$name' deleted." },
            onFailure = { "Delete branch failed: ${it.message}" }
        )

    override fun mergeBranch(repoPath: String, branchName: String): String =
        git.merge(repoPath, branchName).fold(
            onSuccess = { result ->
                buildString {
                    append(if (result.success) "Merge successful" else "Merge conflicts")
                    append(": ${result.mergeStatus}")
                    if (result.conflicts.isNotEmpty()) {
                        append("\nConflicts: ${result.conflicts.joinToString(", ")}")
                    }
                }
            },
            onFailure = { "Merge failed: ${it.message}" }
        )

    // ── Extended git (optional) ──────────────────────────────────────────────

    override fun getDetailedStatus(repoPath: String): String = getStatus(repoPath)

    override fun push(repoPath: String, remote: String, branch: String?): String =
        git.push(repoPath, credentialsStore.getToken()).fold(
            onSuccess = { "Pushed to $remote." },
            onFailure = { "Push failed: ${it.message}" }
        )

    override fun stashSave(repoPath: String, message: String?): String =
        git.stash(repoPath, message ?: "").fold(
            onSuccess = { "Changes stashed." },
            onFailure = { "Stash failed: ${it.message}" }
        )

    override fun stashPop(repoPath: String): String =
        git.stashPop(repoPath, 0).fold(
            onSuccess = { "Stash popped." },
            onFailure = { "Stash pop failed: ${it.message}" }
        )

    override fun stashList(repoPath: String): String =
        git.stashList(repoPath).fold(
            onSuccess = { entries ->
                if (entries.isEmpty()) "No stash entries."
                else entries.joinToString("\n") { "stash@{${it.index}}: ${it.message}" }
            },
            onFailure = { "Error listing stash: ${it.message}" }
        )
}
