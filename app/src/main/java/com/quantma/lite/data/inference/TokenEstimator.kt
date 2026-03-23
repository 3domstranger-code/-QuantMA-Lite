package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role

/**
 * Heuristic token counter for CodeLlama-Instruct models.
 *
 * CodeLlama uses a SentencePiece BPE tokenizer.
 * For English code/text, 1 token ≈ 3.7 characters on average.
 */
object TokenEstimator {

    private const val CHARS_PER_TOKEN = 3.7f

    // Fixed overhead for CodeLlama-Instruct format markers (in tokens)
    private const val INST_OPEN_TOKENS = 3     // "[INST] "
    private const val INST_CLOSE_TOKENS = 3    // " [/INST] "
    private const val SYS_OPEN_TOKENS = 4      // "<<SYS>>\n"
    private const val SYS_CLOSE_TOKENS = 4     // "\n<</SYS>>\n\n"
    private const val BOS_TOKEN = 1             // "<s>"
    private const val EOS_TOKEN = 1             // "</s>"
    private const val TURN_OVERHEAD = BOS_TOKEN + EOS_TOKEN + INST_OPEN_TOKENS + INST_CLOSE_TOKENS
    private const val SYSTEM_PROMPT_OVERHEAD = SYS_OPEN_TOKENS + SYS_CLOSE_TOKENS

    /** Estimate tokens for a raw text string. */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / CHARS_PER_TOKEN).toInt() + 1
    }

    /**
     * Estimate the total token count for a formatted CodeLlama-Instruct prompt.
     * Mirrors PromptFormatter.formatPrompt() logic.
     */
    fun estimatePromptTokens(
        messages: List<ChatMessage>,
        systemPrompt: String
    ): Int {
        var total = BOS_TOKEN + estimateTokens(systemPrompt) + SYSTEM_PROMPT_OVERHEAD

        for (msg in messages) {
            when (msg.role) {
                Role.USER -> {
                    total += INST_OPEN_TOKENS + estimateTokens(msg.content) + INST_CLOSE_TOKENS
                }
                Role.ASSISTANT -> {
                    total += estimateTokens(msg.content) + EOS_TOKEN + BOS_TOKEN
                }
                Role.SYSTEM -> { /* handled via systemPrompt parameter */ }
            }
        }
        return total
    }
}
