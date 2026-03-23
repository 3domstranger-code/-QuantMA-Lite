package com.quantma.lite.domain.repository

import com.quantma.lite.domain.model.GitBranch
import com.quantma.lite.domain.model.GitRemote
import com.quantma.lite.domain.model.GitDiffEntry
import com.quantma.lite.domain.model.GitLogEntry
import com.quantma.lite.domain.model.GitStatus
import com.quantma.lite.domain.model.MergeResult
import com.quantma.lite.domain.model.StashEntry

/**
 * Interface for Git operations.
 * All methods are synchronous — call from Dispatchers.IO.
 */
interface GitRepository {

    /** Check if the given directory is inside a Git repository. */
    fun isGitRepo(path: String): Boolean

    /** Find the .git root for the given path, or null. */
    fun findGitRoot(path: String): String?

    /** Initialize a new Git repo at the given directory. */
    fun initRepo(path: String): Result<Unit>

    /** Get the working tree status. */
    fun getStatus(repoPath: String): Result<GitStatus>

    /** Stage all changes (equivalent to `git add -A`). */
    fun addAll(repoPath: String): Result<Unit>

    /** Stage specific files. */
    fun addFiles(repoPath: String, paths: List<String>): Result<Unit>

    /** Create a commit with the given message. Returns short hash. */
    fun commit(
        repoPath: String,
        message: String,
        authorName: String,
        authorEmail: String
    ): Result<String>

    /** Get the commit log, newest first, limited to [maxCount] entries. */
    fun getLog(repoPath: String, maxCount: Int = 50): Result<List<GitLogEntry>>

    /** Get current branch name. */
    fun getCurrentBranch(repoPath: String): Result<String>

    // ---- Phase 3 (v1.2.0) — Branch / Merge / Diff / Stash ----

    /** List all local branches. */
    fun listBranches(repoPath: String): Result<List<GitBranch>>

    /** Create a new branch (does not checkout). */
    fun createBranch(repoPath: String, name: String): Result<Unit>

    /** Checkout (switch to) an existing branch. */
    fun checkoutBranch(repoPath: String, name: String): Result<Unit>

    /** Delete a local branch (force). */
    fun deleteBranch(repoPath: String, name: String): Result<Unit>

    /** Merge [branchName] into the currently checked-out branch. */
    fun merge(repoPath: String, branchName: String): Result<MergeResult>

    /** Get unified diff of working tree changes vs HEAD (staged + unstaged). */
    fun getDiff(repoPath: String): Result<List<GitDiffEntry>>

    /** Create a stash. */
    fun stash(repoPath: String, message: String = ""): Result<Unit>

    /** List all stash entries. */
    fun stashList(repoPath: String): Result<List<StashEntry>>

    /** Pop (apply + drop) the stash at the given index. */
    fun stashPop(repoPath: String, index: Int = 0): Result<Unit>

    // ---- Phase 4 (v1.3.0) — Remote operations ----

    /** Clone a remote repository. */
    fun clone(url: String, destPath: String, token: String = ""): Result<Unit>

    /** Pull from remote. Returns merge status string. */
    fun pull(repoPath: String, token: String = ""): Result<String>

    /** Push to remote. */
    fun push(repoPath: String, token: String = ""): Result<Unit>

    /** Fetch from all remotes. */
    fun fetch(repoPath: String, token: String = ""): Result<Unit>

    /** List configured remotes. */
    fun getRemotes(repoPath: String): Result<List<GitRemote>>

    /** Add a new remote. */
    fun addRemote(repoPath: String, name: String, url: String): Result<Unit>
}
