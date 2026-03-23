package com.quantma.lite.sas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSystemPromptTest {

    // ═══════════════════════════════════════════════════════════════════════
    // buildSystemPrompt — Content Checks
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `buildSystemPrompt includes File Operations category`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("File Operations"))
    }

    @Test
    fun `buildSystemPrompt includes Search category`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Search"))
    }

    @Test
    fun `buildSystemPrompt includes Git category`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Git"))
    }

    @Test
    fun `buildSystemPrompt includes Code Quality category`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Code Quality"))
    }

    @Test
    fun `buildSystemPrompt includes all tool categories`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        for (category in ToolType.Category.entries) {
            if (category == ToolType.Category.AGENT) continue // DONE is excluded from tool list
            assertTrue(
                "Missing category: ${category.label}",
                prompt.contains(category.label)
            )
        }
    }

    @Test
    fun `buildSystemPrompt includes working directory`() {
        val workDir = "/storage/emulated/0/myproject"
        val prompt = AgentSystemPrompt.buildSystemPrompt(workDir)
        assertTrue(prompt.contains(workDir))
    }

    @Test
    fun `buildSystemPrompt includes rules section`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Rules:"))
        assertTrue(prompt.contains("RELATIVE paths"))
        assertTrue(prompt.contains("read a file before modifying"))
    }

    @Test
    fun `buildSystemPrompt includes chain workflow examples`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Common workflows:"))
        assertTrue(prompt.contains("list_dir"))
        assertTrue(prompt.contains("read_file"))
        assertTrue(prompt.contains("write_file"))
        assertTrue(prompt.contains("done"))
    }

    @Test
    fun `buildSystemPrompt includes example interaction`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Example interaction"))
        assertTrue(prompt.contains("create_file"))
    }

    @Test
    fun `buildSystemPrompt includes key tool names`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("read_file"))
        assertTrue(prompt.contains("write_file"))
        assertTrue(prompt.contains("create_file"))
        assertTrue(prompt.contains("list_dir"))
        assertTrue(prompt.contains("search_grep"))
        assertTrue(prompt.contains("git_status"))
        assertTrue(prompt.contains("git_commit"))
        assertTrue(prompt.contains("git_log"))
    }

    @Test
    fun `buildSystemPrompt includes extended git tools`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("git_stash"))
        assertTrue(prompt.contains("git_create_branch"))
        assertTrue(prompt.contains("git_checkout"))
        assertTrue(prompt.contains("git_merge"))
        assertTrue(prompt.contains("git_push_safe"))
    }

    @Test
    fun `buildSystemPrompt includes code quality tools`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("lint_file"))
        assertTrue(prompt.contains("format_file"))
        assertTrue(prompt.contains("count_lines"))
        assertTrue(prompt.contains("find_todo"))
    }

    @Test
    fun `buildSystemPrompt excludes done from tool listing`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        // DONE should not appear as a listed tool with description
        // but "done" appears in examples and protocol spec
        assertFalse(prompt.contains("- done:"))
    }

    @Test
    fun `buildSystemPrompt includes JSON protocol spec`() {
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project")
        assertTrue(prompt.contains("Command format"))
        assertTrue(prompt.contains("\"tool\""))
        assertTrue(prompt.contains("\"args\""))
    }

    @Test
    fun `buildSystemPrompt with custom tool subset only includes those tools`() {
        val tools = listOf(ToolType.READ_FILE, ToolType.WRITE_FILE)
        val prompt = AgentSystemPrompt.buildSystemPrompt("/sdcard/project", tools)
        assertTrue(prompt.contains("read_file"))
        assertTrue(prompt.contains("write_file"))
        // Tools not in the subset should not appear in the "Available tools" section
        // (they may still appear in examples/rules but not as listed tools)
        assertFalse(prompt.contains("- search_grep:"))
        assertFalse(prompt.contains("- git_log:"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // buildToolResultPrompt
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `buildToolResultPrompt formats Success correctly`() {
        val response = ToolResponse.Success(output = "file content here")
        val result = AgentSystemPrompt.buildToolResultPrompt("read_file", response)
        assertTrue(result.contains("RESULT of read_file:"))
        assertTrue(result.contains("file content here"))
    }

    @Test
    fun `buildToolResultPrompt formats Error correctly`() {
        val response = ToolResponse.Error(code = "NOT_FOUND", message = "File not found: test.kt")
        val result = AgentSystemPrompt.buildToolResultPrompt("read_file", response)
        assertTrue(result.contains("RESULT of read_file:"))
        assertTrue(result.contains("ERROR [NOT_FOUND]: File not found: test.kt"))
    }

    @Test
    fun `buildToolResultPrompt formats NeedsApproval correctly`() {
        val command = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "test.kt"))
        val response = ToolResponse.NeedsApproval(
            description = "Write to file test.kt",
            command = command
        )
        val result = AgentSystemPrompt.buildToolResultPrompt("write_file", response)
        assertTrue(result.contains("RESULT of write_file:"))
        assertTrue(result.contains("PENDING_APPROVAL: Write to file test.kt"))
    }

    @Test
    fun `buildToolResultPrompt formats NeedsApproval with preview`() {
        val command = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "test.kt"))
        val response = ToolResponse.NeedsApproval(
            description = "Write to file test.kt",
            command = command,
            preview = "fun main() { println(\"Hello\") }"
        )
        val result = AgentSystemPrompt.buildToolResultPrompt("write_file", response)
        assertTrue(result.contains("PENDING_APPROVAL: Write to file test.kt"))
        assertTrue(result.contains("fun main()"))
    }

    @Test
    fun `buildToolResultPrompt includes tool name in output`() {
        val response = ToolResponse.Success(output = "OK")
        val result = AgentSystemPrompt.buildToolResultPrompt("git_status", response)
        assertTrue(result.startsWith("RESULT of git_status:"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // buildToolListCompact
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `buildToolListCompact includes all tool names except done`() {
        val compact = AgentSystemPrompt.buildToolListCompact()
        for (toolType in ToolType.entries) {
            if (toolType == ToolType.DONE) continue
            assertTrue(
                "Missing tool: ${toolType.toolName}",
                compact.contains(toolType.toolName)
            )
        }
    }

    @Test
    fun `buildToolListCompact does not include done as a listed tool`() {
        val compact = AgentSystemPrompt.buildToolListCompact()
        // "done" appears in the format line but not in the Tools: list
        val toolsLine = compact.lines().first { it.startsWith("Tools:") }
        assertFalse(toolsLine.contains("done"))
    }

    @Test
    fun `buildToolListCompact includes JSON format hint`() {
        val compact = AgentSystemPrompt.buildToolListCompact()
        assertTrue(compact.contains("\"tool\""))
        assertTrue(compact.contains("\"args\""))
    }

    @Test
    fun `buildToolListCompact includes done format`() {
        val compact = AgentSystemPrompt.buildToolListCompact()
        assertTrue(compact.contains("\"done\""))
    }
}
