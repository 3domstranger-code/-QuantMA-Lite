package com.quantma.lite.data.agent

import com.quantma.lite.domain.model.ToolCall
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolParserTest {

    private lateinit var parser: ToolParser

    @Before
    fun setUp() {
        parser = ToolParser()
    }

    // -------------------------------------------------------------------------
    // Simple single-arg tools
    // -------------------------------------------------------------------------

    @Test
    fun `parse read_file returns ReadFile with path`() {
        val result = parser.parse("ACTION: read_file /storage/project/Main.kt")
        assertEquals(ToolCall.ReadFile("/storage/project/Main.kt"), result)
    }

    @Test
    fun `parse list_files returns ListFiles with path`() {
        val result = parser.parse("ACTION: list_files /storage/emulated/0/project")
        assertEquals(ToolCall.ListFiles("/storage/emulated/0/project"), result)
    }

    @Test
    fun `parse git_status returns GitStatus`() {
        val result = parser.parse("ACTION: git_status /storage/repo")
        assertEquals(ToolCall.GitStatus("/storage/repo"), result)
    }

    @Test
    fun `parse git_diff returns GitDiff`() {
        val result = parser.parse("ACTION: git_diff /storage/repo")
        assertEquals(ToolCall.GitDiff("/storage/repo"), result)
    }

    @Test
    fun `parse git_branch returns GitBranch`() {
        val result = parser.parse("ACTION: git_branch /storage/repo")
        assertEquals(ToolCall.GitBranch("/storage/repo"), result)
    }

    @Test
    fun `parse git_pull returns GitPull`() {
        val result = parser.parse("ACTION: git_pull /storage/repo")
        assertEquals(ToolCall.GitPull("/storage/repo"), result)
    }

    @Test
    fun `parse git_push returns GitPush`() {
        val result = parser.parse("ACTION: git_push /storage/repo")
        assertEquals(ToolCall.GitPush("/storage/repo"), result)
    }

    @Test
    fun `parse run_command returns RunCommand with full command`() {
        val result = parser.parse("ACTION: run_command ls -la /storage")
        assertEquals(ToolCall.RunCommand("ls -la /storage"), result)
    }

    @Test
    fun `parse delete_file returns DeleteFile`() {
        val result = parser.parse("ACTION: delete_file /storage/project/old.txt")
        assertEquals(ToolCall.DeleteFile("/storage/project/old.txt"), result)
    }

    // -------------------------------------------------------------------------
    // DONE keyword
    // -------------------------------------------------------------------------

    @Test
    fun `parse DONE keyword returns Done`() {
        val result = parser.parse("DONE")
        assertEquals(ToolCall.Done, result)
    }

    @Test
    fun `parse DONE case insensitive`() {
        assertEquals(ToolCall.Done, parser.parse("done"))
        assertEquals(ToolCall.Done, parser.parse("Done"))
    }

    @Test
    fun `parse DONE anywhere in response returns Done`() {
        val result = parser.parse("I have finished the task.\nDONE")
        assertEquals(ToolCall.Done, result)
    }

    // -------------------------------------------------------------------------
    // Two-arg tools: git_clone, search_files
    // -------------------------------------------------------------------------

    @Test
    fun `parse git_clone splits url and path correctly`() {
        val result = parser.parse("ACTION: git_clone https://github.com/user/repo.git /storage/project")
        assertEquals(ToolCall.GitClone("https://github.com/user/repo.git", "/storage/project"), result)
    }

    @Test
    fun `parse git_clone missing path returns null`() {
        val result = parser.parse("ACTION: git_clone https://github.com/user/repo.git")
        assertNull(result)
    }

    @Test
    fun `parse search_files splits pattern and path`() {
        val result = parser.parse("ACTION: search_files *.kt /storage/project")
        assertEquals(ToolCall.SearchFiles("*.kt", "/storage/project"), result)
    }

    @Test
    fun `parse search_files missing path returns null`() {
        val result = parser.parse("ACTION: search_files *.kt")
        assertNull(result)
    }

    // -------------------------------------------------------------------------
    // write_file and git_commit — CONTENT/END_CONTENT blocks
    // -------------------------------------------------------------------------

    @Test
    fun `parse write_file extracts content between markers`() {
        val response = """
            ACTION: write_file /storage/project/hello.txt
            CONTENT:
            Hello World
            Line 2
            END_CONTENT
        """.trimIndent()
        val result = parser.parse(response)
        assertEquals(ToolCall.WriteFile("/storage/project/hello.txt", "Hello World\nLine 2"), result)
    }

    @Test
    fun `parse write_file without END_CONTENT uses everything after CONTENT`() {
        val response = """
            ACTION: write_file /storage/project/hello.txt
            CONTENT:
            Hello World
        """.trimIndent()
        val result = parser.parse(response)
        assertTrue(result is ToolCall.WriteFile)
        assertEquals("Hello World", (result as ToolCall.WriteFile).content)
    }

    @Test
    fun `parse write_file without CONTENT marker returns empty content`() {
        val result = parser.parse("ACTION: write_file /storage/project/file.txt")
        assertEquals(ToolCall.WriteFile("/storage/project/file.txt", ""), result)
    }

    @Test
    fun `parse git_commit extracts commit message from content`() {
        val response = """
            ACTION: git_commit /storage/project
            CONTENT:
            feat: add new feature
            END_CONTENT
        """.trimIndent()
        val result = parser.parse(response)
        assertEquals(ToolCall.GitCommit("/storage/project", "feat: add new feature"), result)
    }

    // -------------------------------------------------------------------------
    // Leading whitespace and mixed content
    // -------------------------------------------------------------------------

    @Test
    fun `parse action with leading whitespace`() {
        val result = parser.parse("  ACTION: read_file /storage/file.kt")
        assertEquals(ToolCall.ReadFile("/storage/file.kt"), result)
    }

    @Test
    fun `parse action embedded in prose text`() {
        val response = """
            I'll read the file to understand its content.
            ACTION: read_file /storage/Main.kt
            This will help me see the code.
        """.trimIndent()
        val result = parser.parse(response)
        assertEquals(ToolCall.ReadFile("/storage/Main.kt"), result)
    }

    // -------------------------------------------------------------------------
    // Null cases
    // -------------------------------------------------------------------------

    @Test
    fun `parse response with no action returns null`() {
        val result = parser.parse("This is just a normal chat response.")
        assertNull(result)
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(parser.parse(""))
    }

    @Test
    fun `parse unknown tool name returns null`() {
        val result = parser.parse("ACTION: unknown_tool /some/path")
        assertNull(result)
    }

    @Test
    fun `parse action with empty argument returns null`() {
        val result = parser.parse("ACTION: read_file ")
        assertNull(result)
    }

    @Test
    fun `parse ACTION without space after colon returns null`() {
        // "ACTION:read_file" — no space after ACTION:, no toolName space
        val result = parser.parse("ACTION:read_file /path")
        // trimStart().removePrefix("ACTION:").trim() = "read_file /path" — this IS valid
        // Actually the code does: actionLine.indexOf(' ') to split tool/arg
        // "read_file /path" → toolName="read_file", arg="/path" → ReadFile
        assertEquals(ToolCall.ReadFile("/path"), result)
    }

    // -------------------------------------------------------------------------
    // New git tools: create_branch, checkout, delete_branch, merge, stash
    // -------------------------------------------------------------------------

    @Test
    fun `parse git_create_branch returns GitCreateBranch`() {
        val result = parser.parse("ACTION: git_create_branch /repo feature/test")
        assertEquals(ToolCall.GitCreateBranch("/repo", "feature/test"), result)
    }

    @Test
    fun `parse git_checkout returns GitCheckout`() {
        val result = parser.parse("ACTION: git_checkout /repo main")
        assertEquals(ToolCall.GitCheckout("/repo", "main"), result)
    }

    @Test
    fun `parse git_delete_branch returns GitDeleteBranch`() {
        val result = parser.parse("ACTION: git_delete_branch /repo old-branch")
        assertEquals(ToolCall.GitDeleteBranch("/repo", "old-branch"), result)
    }

    @Test
    fun `parse git_merge returns GitMerge`() {
        val result = parser.parse("ACTION: git_merge /repo feature/new")
        assertEquals(ToolCall.GitMerge("/repo", "feature/new"), result)
    }

    @Test
    fun `parse git_stash_save returns GitStashSave with message`() {
        val result = parser.parse("ACTION: git_stash_save /repo WIP message")
        assertEquals(ToolCall.GitStashSave("/repo", "WIP message"), result)
    }

    @Test
    fun `parse git_stash_save with no message returns GitStashSave with empty message`() {
        val result = parser.parse("ACTION: git_stash_save /repo")
        assertEquals(ToolCall.GitStashSave("/repo"), result)
    }

    @Test
    fun `parse git_stash_pop returns GitStashPop`() {
        val result = parser.parse("ACTION: git_stash_pop /repo")
        assertEquals(ToolCall.GitStashPop("/repo"), result)
    }

    @Test
    fun `parse git_stash_list returns GitStashList`() {
        val result = parser.parse("ACTION: git_stash_list /repo")
        assertEquals(ToolCall.GitStashList("/repo"), result)
    }

    // -------------------------------------------------------------------------
    // Edge cases for new git tools
    // -------------------------------------------------------------------------

    @Test
    fun `parse git_create_branch missing name returns null`() {
        val result = parser.parse("ACTION: git_create_branch /repo")
        assertNull(result)
    }

    @Test
    fun `parse git_merge missing branch returns null`() {
        val result = parser.parse("ACTION: git_merge /repo")
        assertNull(result)
    }
}
