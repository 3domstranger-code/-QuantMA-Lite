package com.quantma.lite.data.agent

import com.quantma.lite.domain.model.AgentStep
import com.quantma.lite.domain.model.AgentStepStatus
import com.quantma.lite.domain.model.ToolCall
import com.quantma.lite.domain.model.ToolResult
import org.junit.Assert.*
import org.junit.Test

class AgentPromptFormatterTest {

    private val workingDir = "/storage/emulated/0/project"

    // -------------------------------------------------------------------------
    // Basic structure
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt starts with BOS and INST markers`() {
        // Default format is ChatML — uses <|im_start|> / <|im_end|> markers
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "What files are here?",
            rounds = emptyList(),
            workingDir = workingDir
        )
        assertTrue("Prompt starts with <|im_start|>", prompt.startsWith("<|im_start|>"))
        assertTrue("Prompt contains system section", prompt.contains("<|im_start|>system"))
        assertTrue("Prompt contains user section", prompt.contains("<|im_start|>user"))
    }

    @Test
    fun `formatPrompt injects working directory`() {
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "List files",
            rounds = emptyList(),
            workingDir = workingDir
        )
        assertTrue("Working dir should be in prompt", prompt.contains(workingDir))
    }

    @Test
    fun `formatPrompt contains user message`() {
        val userMessage = "Create a README.md file"
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = userMessage,
            rounds = emptyList(),
            workingDir = workingDir
        )
        assertTrue("User message should be in prompt", prompt.contains(userMessage))
    }

    @Test
    fun `formatPrompt no rounds ends after first INST block`() {
        // Default is ChatML — prompt ends with open assistant turn marker
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "Hello",
            rounds = emptyList(),
            workingDir = workingDir
        )
        // ChatML opens assistant turn at the end for generation
        assertTrue(
            "Should end with open assistant turn for model response",
            prompt.trimEnd().endsWith("<|im_start|>assistant")
        )
    }

    // -------------------------------------------------------------------------
    // Custom system prompt
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt custom system prompt overrides default`() {
        val customSys = "CUSTOM_SYSTEM_INSTRUCTION"
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "Do something",
            rounds = emptyList(),
            workingDir = workingDir,
            systemPrompt = customSys
        )
        assertTrue("Custom system prompt should appear", prompt.contains(customSys))
        // Default agent system prompt should NOT appear
        assertFalse("Default should be replaced", prompt.contains("You are a code agent"))
    }

    @Test
    fun `formatPrompt null system prompt uses built-in agent prompt`() {
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "Do something",
            rounds = emptyList(),
            workingDir = workingDir,
            systemPrompt = null
        )
        // Built-in agent prompt contains tool protocol and done signal
        assertTrue("Should contain tool protocol", prompt.contains("\"tool\""))
        assertTrue("Should mention done", prompt.contains("\"done\""))
    }

    // -------------------------------------------------------------------------
    // Rounds / tool results
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt with one round includes assistant response and tool result`() {
        val round = agentStep(
            response = "I'll read the file.",
            toolCall = ToolCall.ReadFile("/storage/Main.kt"),
            toolResult = ToolResult(
                toolCall = ToolCall.ReadFile("/storage/Main.kt"),
                success = true,
                output = "fun main() {}"
            )
        )
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "Read Main.kt",
            rounds = listOf(round),
            workingDir = workingDir
        )

        assertTrue("Assistant response should be in prompt", prompt.contains("I'll read the file."))
        assertTrue("Tool result output should be in prompt", prompt.contains("fun main() {}"))
        assertTrue("RESULT section should be present", prompt.contains("RESULT of read_file"))
    }

    @Test
    fun `formatPrompt with read_file round shows correct RESULT label`() {
        val round = agentStep(
            response = "Reading file now.",
            toolCall = ToolCall.ReadFile("/storage/Foo.kt"),
            toolResult = ToolResult(ToolCall.ReadFile("/storage/Foo.kt"), true, "class Foo")
        )
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of read_file /storage/Foo.kt"))
    }

    @Test
    fun `formatPrompt with git_clone round shows correct RESULT label`() {
        val round = agentStep(
            response = "Cloning repo.",
            toolCall = ToolCall.GitClone("https://github.com/user/repo.git", "/storage/repo"),
            toolResult = ToolResult(
                ToolCall.GitClone("https://github.com/user/repo.git", "/storage/repo"),
                true, "Cloned successfully"
            )
        )
        val prompt = AgentPromptFormatter.formatPrompt("clone repo", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_clone https://github.com/user/repo.git /storage/repo"))
    }

    @Test
    fun `formatPrompt with multiple rounds includes all round data`() {
        val rounds = listOf(
            agentStep(
                response = "First, I'll list files.",
                toolCall = ToolCall.ListFiles(workingDir),
                toolResult = ToolResult(ToolCall.ListFiles(workingDir), true, "Main.kt\nUtils.kt")
            ),
            agentStep(
                response = "Now reading Main.kt.",
                toolCall = ToolCall.ReadFile("$workingDir/Main.kt"),
                toolResult = ToolResult(ToolCall.ReadFile("$workingDir/Main.kt"), true, "fun main(){}")
            )
        )
        val prompt = AgentPromptFormatter.formatPrompt("Explore project", rounds, workingDir)

        assertTrue(prompt.contains("First, I'll list files."))
        assertTrue(prompt.contains("Now reading Main.kt."))
        assertTrue(prompt.contains("Main.kt\nUtils.kt"))
        assertTrue(prompt.contains("fun main(){}"))
    }

    // -------------------------------------------------------------------------
    // Context budget
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt respects context budget by dropping oldest rounds`() {
        // Create many rounds with large tool results
        val largeOutput = "x".repeat(500)
        val rounds = (1..20).map { i ->
            agentStep(
                response = "Step $i response.",
                toolCall = ToolCall.ReadFile("/file$i.kt"),
                toolResult = ToolResult(ToolCall.ReadFile("/file$i.kt"), true, largeOutput)
            )
        }
        // Very small context budget
        val prompt = AgentPromptFormatter.formatPrompt(
            userMessage = "Do work",
            rounds = rounds,
            workingDir = workingDir,
            contextBudget = 512  // very tight budget
        )

        // With tight budget, older rounds should be dropped (fewer than 20 rounds included)
        val roundsIncluded = "RESULT of read_file".toRegex().findAll(prompt).count()
        assertTrue(
            "With tight budget, should include fewer than all 20 rounds (got $roundsIncluded)",
            roundsIncluded < 20
        )
    }

    // -------------------------------------------------------------------------
    // All ToolCall types in RESULT label
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt includes correct RESULT labels for all tool types`() {
        val toolCases = listOf(
            ToolCall.ListFiles("/path") to "list_files /path",
            ToolCall.WriteFile("/path", "content") to "write_file /path",
            ToolCall.GitStatus("/path") to "git_status /path",
            ToolCall.GitDiff("/path") to "git_diff /path",
            ToolCall.GitPull("/path") to "git_pull /path",
            ToolCall.GitPush("/path") to "git_push /path",
            ToolCall.RunCommand("ls -la") to "run_command ls -la",
            ToolCall.DeleteFile("/path") to "delete_file /path",
            // Phase 3 — Branch / Merge / Stash
            ToolCall.GitCreateBranch("/repo", "feature/x") to "git_create_branch /repo feature/x",
            ToolCall.GitCheckout("/repo", "develop") to "git_checkout /repo develop",
            ToolCall.GitDeleteBranch("/repo", "old") to "git_delete_branch /repo old",
            ToolCall.GitMerge("/repo", "feature/y") to "git_merge /repo feature/y",
            ToolCall.GitStashSave("/repo", "WIP") to "git_stash_save /repo",
            ToolCall.GitStashPop("/repo") to "git_stash_pop /repo",
            ToolCall.GitStashList("/repo") to "git_stash_list /repo"
        )

        for ((toolCall, expectedLabel) in toolCases) {
            val round = agentStep(
                response = "doing something",
                toolCall = toolCall,
                toolResult = ToolResult(toolCall, true, "result")
            )
            val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
            assertTrue(
                "Prompt should contain 'RESULT of $expectedLabel'",
                prompt.contains("RESULT of $expectedLabel")
            )
        }
    }

    // -------------------------------------------------------------------------
    // Git Branch / Merge / Stash RESULT labels
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt git_create_branch includes name in RESULT label`() {
        val tc = ToolCall.GitCreateBranch("/repo", "feature/auth")
        val round = agentStep("creating branch", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_create_branch /repo feature/auth"))
    }

    @Test
    fun `formatPrompt git_checkout includes name in RESULT label`() {
        val tc = ToolCall.GitCheckout("/repo", "main")
        val round = agentStep("switching", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_checkout /repo main"))
    }

    @Test
    fun `formatPrompt git_delete_branch includes name in RESULT label`() {
        val tc = ToolCall.GitDeleteBranch("/repo", "stale")
        val round = agentStep("deleting", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_delete_branch /repo stale"))
    }

    @Test
    fun `formatPrompt git_merge includes branch name in RESULT label`() {
        val tc = ToolCall.GitMerge("/repo", "develop")
        val round = agentStep("merging", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_merge /repo develop"))
    }

    @Test
    fun `formatPrompt git_stash_save includes path in RESULT label`() {
        val tc = ToolCall.GitStashSave("/repo", "wip")
        val round = agentStep("stashing", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_stash_save /repo"))
    }

    @Test
    fun `formatPrompt git_stash_pop includes path in RESULT label`() {
        val tc = ToolCall.GitStashPop("/repo")
        val round = agentStep("popping", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_stash_pop /repo"))
    }

    @Test
    fun `formatPrompt git_stash_list includes path in RESULT label`() {
        val tc = ToolCall.GitStashList("/repo")
        val round = agentStep("listing stash", tc, ToolResult(tc, true, "ok"))
        val prompt = AgentPromptFormatter.formatPrompt("task", listOf(round), workingDir)
        assertTrue(prompt.contains("RESULT of git_stash_list /repo"))
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun agentStep(
        response: String,
        toolCall: ToolCall? = null,
        toolResult: ToolResult? = null
    ) = AgentStep(
        roundIndex = 0,
        response = response,
        toolCall = toolCall,
        toolResult = toolResult,
        status = AgentStepStatus.COMPLETE
    )
}
