package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import org.junit.Assert.*
import org.junit.Test

class DialogCompressorTest {

    // -------------------------------------------------------------------------
    // No compression needed
    // -------------------------------------------------------------------------

    @Test
    fun `planCompression fewer than PRESERVE_RECENT_COUNT returns empty`() {
        val messages = listOf(
            msg(1, Role.USER, "Hello"),
            msg(2, Role.ASSISTANT, "Hi"),
            msg(3, Role.USER, "How are you?")
        )
        // 3 messages < PRESERVE_RECENT_COUNT (4)
        val actions = DialogCompressor.planCompression(messages, contextSize = 2048)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `planCompression exactly PRESERVE_RECENT_COUNT messages returns empty`() {
        val messages = (1..4).map { i ->
            if (i % 2 == 1) msg(i.toLong(), Role.USER, "Q$i")
            else msg(i.toLong(), Role.ASSISTANT, "A$i")
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 2048)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `planCompression under token budget returns empty`() {
        // Create 6 short messages — total tokens well under 2048 * 0.75 threshold
        val messages = (1..6).map { i ->
            msg(i.toLong(), if (i % 2 == 1) Role.USER else Role.ASSISTANT, "Short message $i")
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 2048)
        assertTrue("Under budget should not compress", actions.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Compression triggered
    // -------------------------------------------------------------------------

    @Test
    fun `planCompression compresses old user-assistant pairs as units`() {
        // Create messages that exceed budget: many long messages
        val longContent = "word ".repeat(200) // ~200 tokens each
        val messages = (1..10).map { i ->
            msg(i.toLong(), if (i % 2 == 1) Role.USER else Role.ASSISTANT, longContent)
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 512)

        // Should have some compression actions
        assertTrue("Should produce compression actions when over budget", actions.isNotEmpty())

        // Compressed content should be shorter than original
        for (action in actions) {
            assertTrue(
                "Compressed content should be shorter than original (${action.originalTokenCount} tokens)",
                action.compressedContent.length < action.originalTokenCount * 4  // rough check
            )
        }
    }

    @Test
    fun `planCompression preserves last PRESERVE_RECENT_COUNT messages`() {
        val longContent = "word ".repeat(200)
        val messages = (1..10).map { i ->
            msg(i.toLong(), if (i % 2 == 1) Role.USER else Role.ASSISTANT, longContent)
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 512)

        val compressedIds = actions.map { it.messageId }.toSet()
        val preservedCount = DialogCompressor.PRESERVE_RECENT_COUNT
        val lastIds = messages.takeLast(preservedCount).map { it.id }.toSet()

        // None of the last PRESERVE_RECENT_COUNT messages should be compressed
        assertTrue(
            "Last $preservedCount messages should NOT be compressed",
            lastIds.none { it in compressedIds }
        )
    }

    @Test
    fun `planCompression skips already compressed messages`() {
        val longContent = "word ".repeat(200)
        val messages = listOf(
            msg(1, Role.USER, longContent, isCompressed = true),   // already compressed
            msg(2, Role.ASSISTANT, longContent, isCompressed = true),
            msg(3, Role.USER, longContent),
            msg(4, Role.ASSISTANT, longContent),
            msg(5, Role.USER, longContent),
            msg(6, Role.ASSISTANT, longContent),
        )
        val actions = DialogCompressor.planCompression(messages, contextSize = 512)

        // Already compressed messages should not appear in actions
        val compressedIds = actions.map { it.messageId }.toSet()
        assertFalse("Already compressed msg 1 should not be re-compressed", 1L in compressedIds)
        assertFalse("Already compressed msg 2 should not be re-compressed", 2L in compressedIds)
    }

    @Test
    fun `planCompression compressed pairs have correct format`() {
        val longContent = "word ".repeat(200)
        val messages = (1..8).map { i ->
            msg(i.toLong(), if (i % 2 == 1) Role.USER else Role.ASSISTANT, longContent)
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 512)

        // USER compressions should start with [Q:
        val userActions = actions.filter { action ->
            val originalMsg = messages.find { it.id == action.messageId }
            originalMsg?.role == Role.USER
        }
        assertTrue("Should have user message compressions", userActions.isNotEmpty())
        userActions.forEach { action ->
            assertTrue(
                "User compression should start with [Q:",
                action.compressedContent.startsWith("[Q:")
            )
        }

        // ASSISTANT compressions should start with [A:
        val assistActions = actions.filter { action ->
            val originalMsg = messages.find { it.id == action.messageId }
            originalMsg?.role == Role.ASSISTANT
        }
        assertTrue("Should have assistant message compressions", assistActions.isNotEmpty())
        assistActions.forEach { action ->
            assertTrue(
                "Assistant compression should start with [A:",
                action.compressedContent.startsWith("[A:")
            )
        }
    }

    @Test
    fun `planCompression recordsOriginalTokenCount`() {
        val content = "word ".repeat(200)
        val messages = (1..8).map { i ->
            msg(i.toLong(), if (i % 2 == 1) Role.USER else Role.ASSISTANT, content)
        }
        val actions = DialogCompressor.planCompression(messages, contextSize = 512)

        actions.forEach { action ->
            assertTrue(
                "originalTokenCount should be positive",
                action.originalTokenCount > 0
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun msg(
        id: Long,
        role: Role,
        content: String,
        isCompressed: Boolean = false
    ) = ChatMessage(
        id = id,
        sessionId = 1L,
        role = role,
        content = content,
        isCompressed = isCompressed
    )
}
