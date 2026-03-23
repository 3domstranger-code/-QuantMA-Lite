package com.quantma.lite.sas

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExtendedGitToolsTest {

    // ─── Fake GitOperations ──────────────────────────────────────────────────

    private class FakeGitOperations(
        private val isRepo: Boolean = true
    ) : GitOperations {
        var lastStashMessage: String? = null
        var lastCreatedBranch: String? = null
        var lastCheckedOutBranch: String? = null
        var lastDeletedBranch: String? = null
        var lastMergedBranch: String? = null

        override fun isGitRepo(path: String): Boolean = isRepo
        override fun getStatus(repoPath: String): String = "nothing to commit"
        override fun addFiles(repoPath: String, files: List<String>): String = "added"
        override fun addAll(repoPath: String): String = "added all"
        override fun commit(repoPath: String, message: String): String = "committed: $message"
        override fun getLog(repoPath: String, maxCount: Int): String = "log output"
        override fun getCurrentBranch(repoPath: String): String = "main"

        override fun stashSave(repoPath: String, message: String?): String {
            lastStashMessage = message
            return "Saved working directory"
        }

        override fun stashPop(repoPath: String): String = "Applied stash@{0}"

        override fun stashList(repoPath: String): String = "stash@{0}: WIP on main"

        override fun createBranch(repoPath: String, name: String): String {
            lastCreatedBranch = name
            return "Created branch $name"
        }

        override fun checkoutBranch(repoPath: String, name: String): String {
            lastCheckedOutBranch = name
            return "Switched to branch $name"
        }

        override fun deleteBranch(repoPath: String, name: String): String {
            lastDeletedBranch = name
            return "Deleted branch $name"
        }

        override fun mergeBranch(repoPath: String, branchName: String): String {
            lastMergedBranch = branchName
            return "Merged $branchName into main"
        }
    }

    private lateinit var config: SasConfig
    private lateinit var fakeGit: FakeGitOperations
    private lateinit var tools: ExtendedGitTools

    @Before
    fun setUp() {
        config = SasConfig(workingDir = "/test/project")
        fakeGit = FakeGitOperations()
        tools = ExtendedGitTools(config, fakeGit)
    }

    private fun cmd(tool: ToolType, args: Map<String, Any> = emptyMap()) =
        ToolCommand(tool = tool, args = args)

    // ─── GIT_STASH ───────────────────────────────────────────────────────────

    @Test
    fun `executeGitStash save returns success`() {
        val result = tools.executeGitStash(cmd(ToolType.GIT_STASH, mapOf("operation" to "save", "message" to "WIP")))

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Output should mention saved", success.output.contains("Stash saved"))
        assertEquals("WIP", fakeGit.lastStashMessage)
    }

    @Test
    fun `executeGitStash pop returns success`() {
        val result = tools.executeGitStash(cmd(ToolType.GIT_STASH, mapOf("operation" to "pop")))

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Output should mention popped", success.output.contains("Stash popped"))
    }

    @Test
    fun `executeGitStash list returns success with entries`() {
        val result = tools.executeGitStash(cmd(ToolType.GIT_STASH, mapOf("operation" to "list")))

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Output should contain stash entry", success.output.contains("stash@{0}"))
    }

    @Test
    fun `executeGitStash defaults to list when no operation`() {
        val result = tools.executeGitStash(cmd(ToolType.GIT_STASH))

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Default operation should be list", success.output.contains("stash@{0}"))
    }

    @Test
    fun `executeGitStash invalid operation returns error`() {
        val result = tools.executeGitStash(cmd(ToolType.GIT_STASH, mapOf("operation" to "invalid")))

        assertTrue("Should be Error", result is ToolResponse.Error)
        val error = result as ToolResponse.Error
        assertEquals("INVALID_ARG", error.code)
    }

    // ─── GIT_CREATE_BRANCH ───────────────────────────────────────────────────

    @Test
    fun `executeGitCreateBranch success`() {
        val result = tools.executeGitCreateBranch(
            cmd(ToolType.GIT_CREATE_BRANCH, mapOf("name" to "feature/new"))
        )

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Output should mention branch name", success.output.contains("feature/new"))
        assertEquals("feature/new", fakeGit.lastCreatedBranch)
    }

    @Test
    fun `executeGitCreateBranch missing name returns error`() {
        val result = tools.executeGitCreateBranch(cmd(ToolType.GIT_CREATE_BRANCH))

        assertTrue("Should be Error", result is ToolResponse.Error)
        val error = result as ToolResponse.Error
        assertEquals("MISSING_ARG", error.code)
    }

    // ─── GIT_CHECKOUT ────────────────────────────────────────────────────────

    @Test
    fun `executeGitCheckout success`() {
        val result = tools.executeGitCheckout(
            cmd(ToolType.GIT_CHECKOUT, mapOf("name" to "develop"))
        )

        assertTrue("Should be Success", result is ToolResponse.Success)
        val success = result as ToolResponse.Success
        assertTrue("Output should mention branch", success.output.contains("develop"))
        assertEquals("develop", fakeGit.lastCheckedOutBranch)
    }

    @Test
    fun `executeGitCheckout missing name returns error`() {
        val result = tools.executeGitCheckout(cmd(ToolType.GIT_CHECKOUT))

        assertTrue("Should be Error", result is ToolResponse.Error)
        val error = result as ToolResponse.Error
        assertEquals("MISSING_ARG", error.code)
    }

    // ─── GIT_DELETE_BRANCH ───────────────────────────────────────────────────

    @Test
    fun `executeGitDeleteBranch returns NeedsApproval`() {
        val result = tools.executeGitDeleteBranch(
            cmd(ToolType.GIT_DELETE_BRANCH, mapOf("name" to "old-branch"))
        )

        assertTrue("Should be NeedsApproval", result is ToolResponse.NeedsApproval)
        val approval = result as ToolResponse.NeedsApproval
        assertTrue("Description should mention branch", approval.description.contains("old-branch"))
        assertTrue("Preview should mention permanent", approval.preview!!.contains("permanently deleted"))
    }

    @Test
    fun `executeGitDeleteBranch missing name returns error`() {
        val result = tools.executeGitDeleteBranch(cmd(ToolType.GIT_DELETE_BRANCH))

        assertTrue("Should be Error", result is ToolResponse.Error)
    }

    @Test
    fun `performGitDeleteBranch executes deletion`() {
        val result = tools.performGitDeleteBranch(
            cmd(ToolType.GIT_DELETE_BRANCH, mapOf("name" to "old-branch"))
        )

        assertTrue("Should be Success", result is ToolResponse.Success)
        assertEquals("old-branch", fakeGit.lastDeletedBranch)
    }

    // ─── GIT_MERGE ───────────────────────────────────────────────────────────

    @Test
    fun `executeGitMerge returns NeedsApproval`() {
        val result = tools.executeGitMerge(
            cmd(ToolType.GIT_MERGE, mapOf("branch" to "feature/login"))
        )

        assertTrue("Should be NeedsApproval", result is ToolResponse.NeedsApproval)
        val approval = result as ToolResponse.NeedsApproval
        assertTrue("Description should mention branch", approval.description.contains("feature/login"))
    }

    @Test
    fun `executeGitMerge missing branch returns error`() {
        val result = tools.executeGitMerge(cmd(ToolType.GIT_MERGE))

        assertTrue("Should be Error", result is ToolResponse.Error)
        val error = result as ToolResponse.Error
        assertEquals("MISSING_ARG", error.code)
    }

    @Test
    fun `performGitMerge executes merge`() {
        val result = tools.performGitMerge(
            cmd(ToolType.GIT_MERGE, mapOf("branch" to "feature/login"))
        )

        assertTrue("Should be Success", result is ToolResponse.Success)
        assertEquals("feature/login", fakeGit.lastMergedBranch)
    }

    // ─── requireGitRepo ──────────────────────────────────────────────────────

    @Test
    fun `requireGitRepo returns error when not a repo`() {
        val notRepoGit = FakeGitOperations(isRepo = false)
        val notRepoTools = ExtendedGitTools(config, notRepoGit)

        val result = notRepoTools.executeGitStash(cmd(ToolType.GIT_STASH, mapOf("operation" to "list")))

        assertTrue("Should be Error", result is ToolResponse.Error)
        val error = result as ToolResponse.Error
        assertEquals("NOT_GIT_REPO", error.code)
    }

    @Test
    fun `requireGitRepo blocks all operations when not a repo`() {
        val notRepoGit = FakeGitOperations(isRepo = false)
        val notRepoTools = ExtendedGitTools(config, notRepoGit)

        val createResult = notRepoTools.executeGitCreateBranch(
            cmd(ToolType.GIT_CREATE_BRANCH, mapOf("name" to "x"))
        )
        assertTrue("Create branch should fail", createResult is ToolResponse.Error)
        assertEquals("NOT_GIT_REPO", (createResult as ToolResponse.Error).code)

        val checkoutResult = notRepoTools.executeGitCheckout(
            cmd(ToolType.GIT_CHECKOUT, mapOf("name" to "x"))
        )
        assertTrue("Checkout should fail", checkoutResult is ToolResponse.Error)
        assertEquals("NOT_GIT_REPO", (checkoutResult as ToolResponse.Error).code)

        val deleteResult = notRepoTools.executeGitDeleteBranch(
            cmd(ToolType.GIT_DELETE_BRANCH, mapOf("name" to "x"))
        )
        assertTrue("Delete branch should fail", deleteResult is ToolResponse.Error)
        assertEquals("NOT_GIT_REPO", (deleteResult as ToolResponse.Error).code)

        val mergeResult = notRepoTools.executeGitMerge(
            cmd(ToolType.GIT_MERGE, mapOf("branch" to "x"))
        )
        assertTrue("Merge should fail", mergeResult is ToolResponse.Error)
        assertEquals("NOT_GIT_REPO", (mergeResult as ToolResponse.Error).code)
    }
}
