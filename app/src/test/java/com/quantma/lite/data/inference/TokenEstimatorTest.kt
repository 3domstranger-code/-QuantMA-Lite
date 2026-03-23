package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import org.junit.Assert.*
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun `estimateTokens empty string returns zero`() {
        assertEquals(0, TokenEstimator.estimateTokens(""))
    }

    @Test
    fun `estimateTokens non-empty returns positive value`() {
        assertTrue(TokenEstimator.estimateTokens("Hello World") > 0)
    }

    @Test
    fun `estimateTokens longer text returns larger estimate`() {
        val short = TokenEstimator.estimateTokens("Hello")
        val long  = TokenEstimator.estimateTokens("Hello World this is a longer piece of text with many words")
        assertTrue("Longer text should have more estimated tokens", long > short)
    }

    @Test
    fun `estimateTokens roughly 1 token per 3-4 chars`() {
        // 37 chars → ~37/3.7 = 10 tokens + 1 = 11
        val text = "Hello World this is some text here!" // 35 chars
        val tokens = TokenEstimator.estimateTokens(text)
        // Allow range of 8–14 tokens for 35 chars
        assertTrue("Token estimate should be in reasonable range", tokens in 8..14)
    }

    @Test
    fun `estimatePromptTokens empty messages with empty system prompt is positive`() {
        // Even empty prompt has BOS token overhead
        val tokens = TokenEstimator.estimatePromptTokens(emptyList(), "")
        assertTrue(tokens > 0)
    }

    @Test
    fun `estimatePromptTokens grows with more messages`() {
        val system = "You are helpful."
        val oneMsg = listOf(ChatMessage(role = Role.USER, content = "Hello"))
        val twoMsg = listOf(
            ChatMessage(role = Role.USER, content = "Hello"),
            ChatMessage(role = Role.ASSISTANT, content = "Hi there!")
        )
        val oneTokens = TokenEstimator.estimatePromptTokens(oneMsg, system)
        val twoTokens = TokenEstimator.estimatePromptTokens(twoMsg, system)
        assertTrue("More messages should produce higher token estimate", twoTokens > oneTokens)
    }

    @Test
    fun `estimatePromptTokens system messages ignored in message list`() {
        val system = "You are helpful."
        val withSystem = listOf(
            ChatMessage(role = Role.SYSTEM, content = "Ignored system"),
            ChatMessage(role = Role.USER, content = "Hello")
        )
        val withoutSystem = listOf(
            ChatMessage(role = Role.USER, content = "Hello")
        )
        // System message in list should not affect count (it's handled via systemPrompt param)
        assertEquals(
            TokenEstimator.estimatePromptTokens(withoutSystem, system),
            TokenEstimator.estimatePromptTokens(withSystem, system)
        )
    }

    @Test
    fun `estimatePromptTokens larger system prompt increases total`() {
        val shortSys = "Be helpful."
        val longSys  = "Be helpful. " + "word ".repeat(100)
        val messages = listOf(ChatMessage(role = Role.USER, content = "Q"))
        assertTrue(
            TokenEstimator.estimatePromptTokens(messages, longSys) >
            TokenEstimator.estimatePromptTokens(messages, shortSys)
        )
    }
}
