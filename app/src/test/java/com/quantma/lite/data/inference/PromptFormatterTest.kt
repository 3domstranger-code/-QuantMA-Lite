package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import org.junit.Assert.*
import org.junit.Test

class PromptFormatterTest {

    // -------------------------------------------------------------------------
    // trimHistory
    // -------------------------------------------------------------------------

    @Test
    fun `trimHistory empty list returns empty`() {
        assertEquals(emptyList<ChatMessage>(), PromptFormatter.trimHistory(emptyList()))
    }

    @Test
    fun `trimHistory fewer messages than limit returns all`() {
        val messages = listOf(
            msg(Role.USER, "hello"),
            msg(Role.ASSISTANT, "hi")
        )
        assertEquals(messages, PromptFormatter.trimHistory(messages, maxTurns = 6))
    }

    @Test
    fun `trimHistory exactly at limit returns all`() {
        val messages = (1..12).map { i ->
            if (i % 2 == 1) msg(Role.USER, "q$i") else msg(Role.ASSISTANT, "a$i")
        }
        val trimmed = PromptFormatter.trimHistory(messages, maxTurns = 6)
        assertEquals(12, trimmed.size)
    }

    @Test
    fun `trimHistory over limit returns last maxTurns pairs`() {
        val messages = (1..20).map { i ->
            if (i % 2 == 1) msg(Role.USER, "q$i") else msg(Role.ASSISTANT, "a$i")
        }
        val trimmed = PromptFormatter.trimHistory(messages, maxTurns = 4)
        assertEquals(8, trimmed.size) // 4 pairs = 8 messages
        // Should be the LAST 8 messages
        assertEquals(messages.takeLast(8), trimmed)
    }

    @Test
    fun `trimHistory always filters system messages`() {
        val messages = listOf(
            msg(Role.SYSTEM, "system prompt"),
            msg(Role.USER, "hello"),
            msg(Role.ASSISTANT, "hi")
        )
        val trimmed = PromptFormatter.trimHistory(messages)
        assertFalse(trimmed.any { it.role == Role.SYSTEM })
        assertEquals(2, trimmed.size)
    }

    @Test
    fun `trimHistory maxTurns 1 returns only last 2 messages`() {
        val messages = listOf(
            msg(Role.USER, "q1"),
            msg(Role.ASSISTANT, "a1"),
            msg(Role.USER, "q2"),
            msg(Role.ASSISTANT, "a2")
        )
        val trimmed = PromptFormatter.trimHistory(messages, maxTurns = 1)
        assertEquals(2, trimmed.size)
        assertEquals("q2", trimmed[0].content)
        assertEquals("a2", trimmed[1].content)
    }

    // -------------------------------------------------------------------------
    // formatPrompt — structure
    // -------------------------------------------------------------------------

    @Test
    fun `formatPrompt starts with BOS token`() {
        // Default format is ChatML — prompt starts with <|im_start|>
        val prompt = PromptFormatter.formatPrompt(emptyList())
        assertTrue("Prompt should start with <|im_start|>", prompt.startsWith("<|im_start|>"))
    }

    @Test
    fun `formatPrompt single user message has system prompt and INST markers`() {
        // Use CODELLAMA_INSTRUCT format to test [INST]/<<SYS>> markers
        val messages = listOf(msg(Role.USER, "What is Kotlin?"))
        val prompt = PromptFormatter.formatPrompt(messages, "Be helpful.", PromptFormatter.Format.CODELLAMA_INSTRUCT)

        assertTrue(prompt.contains("[INST]"))
        assertTrue(prompt.contains("[/INST]"))
        assertTrue(prompt.contains("<<SYS>>"))
        assertTrue(prompt.contains("<</SYS>>"))
        assertTrue(prompt.contains("Be helpful."))
        assertTrue(prompt.contains("What is Kotlin?"))
    }

    @Test
    fun `formatPrompt system prompt only injected in first USER message`() {
        // Use CODELLAMA_INSTRUCT format to test single <<SYS>> injection
        val messages = listOf(
            msg(Role.USER, "First question"),
            msg(Role.ASSISTANT, "First answer"),
            msg(Role.USER, "Second question")
        )
        val prompt = PromptFormatter.formatPrompt(messages, "SYS_PROMPT", PromptFormatter.Format.CODELLAMA_INSTRUCT)

        // <<SYS>> should appear exactly once
        assertEquals(1, prompt.split("<<SYS>>").size - 1)
        // System prompt should be between first <<SYS>> and <</SYS>>
        val sysStart = prompt.indexOf("<<SYS>>")
        val sysEnd = prompt.indexOf("<</SYS>>")
        val sysSection = prompt.substring(sysStart, sysEnd)
        assertTrue(sysSection.contains("SYS_PROMPT"))
    }

    @Test
    fun `formatPrompt user-assistant pair has correct delimiters`() {
        // Use CODELLAMA_INSTRUCT format to test </s> separator
        val messages = listOf(
            msg(Role.USER, "Hello"),
            msg(Role.ASSISTANT, "World")
        )
        val prompt = PromptFormatter.formatPrompt(messages, "SYS", PromptFormatter.Format.CODELLAMA_INSTRUCT)

        assertTrue(prompt.contains("[/INST]"))
        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.contains("World"))
        assertTrue(prompt.contains("</s>"))
    }

    @Test
    fun `formatPrompt multiple turns each user message wrapped in INST`() {
        // Use CODELLAMA_INSTRUCT format to test [INST] per user message
        val messages = listOf(
            msg(Role.USER, "Q1"),
            msg(Role.ASSISTANT, "A1"),
            msg(Role.USER, "Q2"),
            msg(Role.ASSISTANT, "A2")
        )
        val prompt = PromptFormatter.formatPrompt(messages, "SYS", PromptFormatter.Format.CODELLAMA_INSTRUCT)

        // Should have 2 [INST] blocks (one per user message)
        val instCount = prompt.split("[INST]").size - 1
        assertEquals(2, instCount)
    }

    @Test
    fun `formatPrompt system messages in list are ignored`() {
        val messages = listOf(
            msg(Role.SYSTEM, "This should be ignored"),
            msg(Role.USER, "Hello"),
            msg(Role.ASSISTANT, "Hi")
        )
        val prompt = PromptFormatter.formatPrompt(messages, "INJECTED_SYS")

        // The system message content should NOT appear in prompt
        assertFalse(prompt.contains("This should be ignored"))
        // But the injected system prompt should
        assertTrue(prompt.contains("INJECTED_SYS"))
    }

    @Test
    fun `formatPrompt empty messages returns only BOS token in ChatML`() {
        // Default is ChatML — empty message list produces only the system header + open assistant turn
        val prompt = PromptFormatter.formatPrompt(emptyList(), "SYS")
        assertTrue("Should start with <|im_start|>", prompt.startsWith("<|im_start|>"))
        assertTrue("Should contain system prompt", prompt.contains("SYS"))
        // No user or assistant content beyond the system block and open assistant turn
        assertFalse(prompt.contains("<|im_start|>user"), )
    }

    @Test
    fun `formatPrompt empty messages in CODELLAMA format returns only BOS`() {
        val prompt = PromptFormatter.formatPrompt(emptyList(), "SYS", PromptFormatter.Format.CODELLAMA_INSTRUCT)
        assertEquals("<s>", prompt)
    }

    @Test
    fun `formatPrompt uses default system prompt when none specified`() {
        val messages = listOf(msg(Role.USER, "Hello"))
        val prompt = PromptFormatter.formatPrompt(messages)
        // Default CHAT_SYSTEM_PROMPT contains "helpful AI assistant"
        assertTrue(prompt.contains("helpful AI assistant"))
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun msg(role: Role, content: String) =
        ChatMessage(id = 0L, sessionId = 1L, role = role, content = content)
}
