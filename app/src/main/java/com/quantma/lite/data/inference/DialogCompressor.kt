package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role

/**
 * Template-based dialog compression for context window management.
 *
 * Strategy:
 * 1. Keep the last N messages (PRESERVE_RECENT_COUNT) untouched.
 * 2. For older messages, compress user+assistant pairs into short summaries.
 * 3. Uses string templates — no LLM calls, instant execution.
 */
object DialogCompressor {

    const val PRESERVE_RECENT_COUNT = 4    // 2 user + 2 assistant turns
    private const val MAX_SUMMARY_CHARS = 120
    private const val COMPRESSION_THRESHOLD_RATIO = 0.75f

    /**
     * Plan which messages need compression.
     *
     * @param messages all messages for the session, ordered by timestamp ASC
     * @param contextSize the configured context window size in tokens
     * @param reserveForGeneration tokens to reserve for the LLM response
     * @return list of CompressionAction, or empty if no compression needed
     */
    fun planCompression(
        messages: List<ChatMessage>,
        contextSize: Int,
        reserveForGeneration: Int = 384
    ): List<CompressionAction> {
        if (messages.size <= PRESERVE_RECENT_COUNT) return emptyList()

        val totalEstimated = TokenEstimator.estimatePromptTokens(messages, "")
        val budget = contextSize - reserveForGeneration
        val threshold = (budget * COMPRESSION_THRESHOLD_RATIO).toInt()

        if (totalEstimated <= threshold) return emptyList()

        val compressibleCount = messages.size - PRESERVE_RECENT_COUNT
        if (compressibleCount <= 0) return emptyList()

        val actions = mutableListOf<CompressionAction>()
        var i = 0

        while (i < compressibleCount) {
            val msg = messages[i]

            if (msg.isCompressed) {
                i++
                continue
            }

            // Try to form a user+assistant pair
            if (msg.role == Role.USER && i + 1 < compressibleCount) {
                val next = messages[i + 1]
                if (next.role == Role.ASSISTANT && !next.isCompressed) {
                    val userSummary = extractTopic(msg.content)
                    val assistantSummary = extractKeyInfo(next.content)

                    actions.add(CompressionAction(
                        messageId = msg.id,
                        compressedContent = "[Q: $userSummary]",
                        originalTokenCount = TokenEstimator.estimateTokens(msg.content)
                    ))
                    actions.add(CompressionAction(
                        messageId = next.id,
                        compressedContent = "[A: $assistantSummary]",
                        originalTokenCount = TokenEstimator.estimateTokens(next.content)
                    ))
                    i += 2
                    continue
                }
            }

            // Single message compression
            val prefix = if (msg.role == Role.USER) "Q" else "A"
            val summary = if (msg.role == Role.USER) extractTopic(msg.content) else extractKeyInfo(msg.content)
            actions.add(CompressionAction(
                messageId = msg.id,
                compressedContent = "[$prefix: $summary]",
                originalTokenCount = TokenEstimator.estimateTokens(msg.content)
            ))
            i++
        }

        return actions
    }

    /**
     * Extract the topic from a user message — first sentence or first line.
     */
    private fun extractTopic(content: String): String {
        val firstLine = content.lineSequence().firstOrNull()?.trim() ?: content.trim()
        val sentenceEnd = firstLine.indexOfFirst { it == '.' || it == '?' || it == '!' }
        val topic = if (sentenceEnd > 0 && sentenceEnd < MAX_SUMMARY_CHARS) {
            firstLine.substring(0, sentenceEnd + 1)
        } else {
            firstLine.take(MAX_SUMMARY_CHARS)
        }
        return topic.ifEmpty { "(empty)" }
    }

    /**
     * Extract key info from an assistant response.
     * Prioritizes code identifiers and file paths.
     */
    private fun extractKeyInfo(content: String): String {
        // Try to find function/class names in code blocks
        val codeBlockMatch = Regex("```\\w*\\n([\\s\\S]*?)```").find(content)
        if (codeBlockMatch != null) {
            val code = codeBlockMatch.groupValues[1]
            val funcMatch = Regex("(?:fun|def|function|class|struct)\\s+(\\w+)").find(code)
            if (funcMatch != null) {
                return "code: ${funcMatch.groupValues[1]}()".take(MAX_SUMMARY_CHARS)
            }
        }

        // Try to find file paths
        val pathMatch = Regex("[/\\\\]\\w+[/\\\\][\\w./-]+").find(content)
        if (pathMatch != null) {
            return "re: ${pathMatch.value}".take(MAX_SUMMARY_CHARS)
        }

        // Fall back to first sentence
        return extractTopic(content)
    }
}

data class CompressionAction(
    val messageId: Long,
    val compressedContent: String,
    val originalTokenCount: Int
)
