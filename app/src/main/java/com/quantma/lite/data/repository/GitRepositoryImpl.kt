package com.quantma.lite.data.repository

import timber.log.Timber
import com.quantma.lite.domain.model.GitBranch
import com.quantma.lite.domain.model.GitDiffEntry
import com.quantma.lite.domain.model.GitLogEntry
import com.quantma.lite.domain.model.GitRemote
import com.quantma.lite.domain.model.GitStatus
import com.quantma.lite.domain.model.MergeResult
import com.quantma.lite.domain.model.StashEntry
import com.quantma.lite.domain.repository.GitRepository
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.FileTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitRepositoryImpl @Inject constructor() : GitRepository {

    override fun isGitRepo(path: String): Boolean {
        return findGitRoot(path) != null
    }

    override fun findGitRoot(path: String): String? {
        return try {
            val repo = FileRepositoryBuilder()
                .findGitDir(File(path))
                .build()
            val workTree = repo.workTree?.absolutePath
            repo.close()
            workTree
        } catch (e: Exception) {
            null
        }
    }

    override fun initRepo(path: String): Result<Unit> = runCatching {
        val git = Git.init().setDirectory(File(path)).call()
        git.close()
        Timber.i("Initialized git repo at $path")
    }

    override fun getStatus(repoPath: String): Result<GitStatus> = runCatching {
        openGit(repoPath).use { git ->
            val status = git.status().call()
            GitStatus(
                added = status.added,
                changed = status.changed,
                removed = status.removed,
                untracked = status.untracked,
                modified = status.modified,
                missing = status.missing,
                conflicting = status.conflicting
            )
        }
    }

    override fun addAll(repoPath: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            // Stage new and modified files
            git.add().addFilepattern(".").call()
            // Stage deletions
            git.add().addFilepattern(".").setUpdate(true).call()
        }
        Timber.i("Staged all changes in $repoPath")
    }

    override fun addFiles(repoPath: String, paths: List<String>): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            val addCommand = git.add()
            paths.forEach { addCommand.addFilepattern(it) }
            addCommand.call()
        }
    }

    override fun commit(
        repoPath: String,
        message: String,
        authorName: String,
        authorEmail: String
    ): Result<String> = runCatching {
        openGit(repoPath).use { git ->
            val commit = git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .call()
            val hash = commit.id.abbreviate(7).name()
            Timber.i("Committed $hash: $message")
            hash
        }
    }

    override fun getLog(repoPath: String, maxCount: Int): Result<List<GitLogEntry>> = runCatching {
        openGit(repoPath).use { git ->
            git.log()
                .setMaxCount(maxCount)
                .call()
                .map { commit ->
                    GitLogEntry(
                        hash = commit.name,
                        shortHash = commit.abbreviate(7).name(),
                        message = commit.fullMessage.trim(),
                        authorName = commit.authorIdent.name,
                        authorEmail = commit.authorIdent.emailAddress,
                        timestamp = commit.authorIdent.whenAsInstant.toEpochMilli()
                    )
                }
        }
    }

    override fun getCurrentBranch(repoPath: String): Result<String> = runCatching {
        openGit(repoPath).use { git ->
            git.repository.branch
        }
    }

    // ---- Phase 3 (v1.2.0) ----

    override fun listBranches(repoPath: String): Result<List<GitBranch>> = runCatching {
        openGit(repoPath).use { git ->
            val currentBranch = git.repository.branch
            git.branchList()
                .call()
                .map { ref ->
                    val name = ref.name.removePrefix("refs/heads/")
                    GitBranch(
                        name = name,
                        isCurrent = name == currentBranch,
                        isRemote = false
                    )
                }
        }
    }

    override fun createBranch(repoPath: String, name: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            git.branchCreate().setName(name).call()
            Timber.i("Created branch $name in $repoPath")
        }
    }

    override fun checkoutBranch(repoPath: String, name: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            git.checkout().setName(name).call()
            Timber.i("Checked out branch $name in $repoPath")
        }
    }

    override fun deleteBranch(repoPath: String, name: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            git.branchDelete().setBranchNames(name).setForce(true).call()
            Timber.i("Deleted branch $name in $repoPath")
        }
    }

    override fun merge(repoPath: String, branchName: String): Result<MergeResult> = runCatching {
        openGit(repoPath).use { git ->
            val ref = git.repository.findRef("refs/heads/$branchName")
                ?: error("Branch not found: $branchName")
            val result = git.merge()
                .include(ref)
                .setMessage("Merge branch '$branchName'")
                .call()
            MergeResult(
                success = result.mergeStatus.isSuccessful,
                mergeStatus = result.mergeStatus.name,
                conflicts = result.conflicts?.keys?.toList() ?: emptyList()
            )
        }
    }

    override fun getDiff(repoPath: String): Result<List<GitDiffEntry>> = runCatching {
        openGit(repoPath).use { git ->
            val out = ByteArrayOutputStream()
            val formatter = DiffFormatter(out)
            formatter.setRepository(git.repository)
            formatter.setContext(3)

            val headTree = git.repository.resolve("HEAD^{tree}")
            val diffEntries = if (headTree != null) {
                val reader = git.repository.newObjectReader()
                val oldTree = CanonicalTreeParser().also { it.reset(reader, headTree) }
                formatter.scan(oldTree, FileTreeIterator(git.repository))
            } else {
                // Empty repo — all files are new
                formatter.scan(null, FileTreeIterator(git.repository))
            }

            diffEntries.map { diffEntry ->
                out.reset()
                formatter.format(diffEntry)
                val filePath = if (diffEntry.newPath != "/dev/null") diffEntry.newPath
                               else diffEntry.oldPath
                GitDiffEntry(
                    filePath = filePath,
                    changeType = diffEntry.changeType.name,
                    unified = out.toString(Charsets.UTF_8.name())
                )
            }.also { formatter.close() }
        }
    }

    override fun stash(repoPath: String, message: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            val msg = message.ifEmpty { "WIP on ${git.repository.branch}" }
            git.stashCreate().setIndexMessage(msg).call()
            Timber.i("Stashed changes in $repoPath")
        }
    }

    override fun stashList(repoPath: String): Result<List<StashEntry>> = runCatching {
        openGit(repoPath).use { git ->
            git.stashList().call().mapIndexed { index, commit ->
                // Stash message format: "WIP on <branch>: <shortHash> <msg>"
                val rawMsg = commit.fullMessage.trim()
                val branchName = rawMsg.substringAfter("WIP on ").substringBefore(":").trim()
                StashEntry(
                    index = index,
                    message = rawMsg,
                    branchName = branchName.ifEmpty { "unknown" }
                )
            }
        }
    }

    override fun stashPop(repoPath: String, index: Int): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            git.stashApply().setStashRef("stash@{$index}").call()
            // Drop the applied stash entry
            git.stashDrop().setStashRef(index).call()
            Timber.i("Popped stash@{$index} in $repoPath")
        }
    }

    // ---- Phase 4 (v1.3.0) — Remote operations ----

    override fun clone(url: String, destPath: String, token: String): Result<Unit> = runCatching {
        val cmd = Git.cloneRepository()
            .setURI(url)
            .setDirectory(File(destPath))
        credentialsProvider(token)?.let { cmd.setCredentialsProvider(it) }
        cmd.call().close()
        Timber.i("Cloned $url to $destPath")
    }

    override fun pull(repoPath: String, token: String): Result<String> = runCatching {
        openGit(repoPath).use { git ->
            val cmd = git.pull()
            credentialsProvider(token)?.let { cmd.setCredentialsProvider(it) }
            val result = cmd.call()
            val status = result.mergeResult?.mergeStatus?.name ?: "OK"
            Timber.i("Pull result: $status in $repoPath")
            status
        }
    }

    override fun push(repoPath: String, token: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            val cmd = git.push()
            credentialsProvider(token)?.let { cmd.setCredentialsProvider(it) }
            cmd.call()
            Timber.i("Pushed from $repoPath")
        }
    }

    override fun fetch(repoPath: String, token: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            val cmd = git.fetch()
            credentialsProvider(token)?.let { cmd.setCredentialsProvider(it) }
            cmd.call()
            Timber.i("Fetched in $repoPath")
        }
    }

    override fun getRemotes(repoPath: String): Result<List<GitRemote>> = runCatching {
        openGit(repoPath).use { git ->
            git.remoteList().call().map { remote ->
                GitRemote(
                    name = remote.name,
                    url = remote.urIs.firstOrNull()?.toString() ?: ""
                )
            }
        }
    }

    override fun addRemote(repoPath: String, name: String, url: String): Result<Unit> = runCatching {
        openGit(repoPath).use { git ->
            git.remoteAdd()
                .setName(name)
                .setUri(URIish(url))
                .call()
            Timber.i("Added remote '$name' -> $url in $repoPath")
        }
    }

    /**
     * Open a JGit Git instance for the given repo path.
     * Caller must close via .use {} block.
     */
    private fun openGit(repoPath: String): Git {
        val repo = FileRepositoryBuilder()
            .setWorkTree(File(repoPath))
            .build()
        return Git(repo)
    }

    private fun credentialsProvider(token: String): UsernamePasswordCredentialsProvider? {
        if (token.isBlank()) return null
        return UsernamePasswordCredentialsProvider(token, "")
    }
}
