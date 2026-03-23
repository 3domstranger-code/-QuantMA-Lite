package com.quantma.lite.data.inference

import com.quantma.lite.domain.model.ChatMessage
import com.quantma.lite.domain.model.Role
import timber.log.Timber

/**
 * Formats conversation messages into prompt strings for various LLM architectures.
 *
 * Supports:
 * - **ChatML** (Qwen, Phi, DeepSeek, most modern models) — default
 * - **Llama 3** (Meta Llama 3.x family)
 * - **CodeLlama-Instruct** (legacy CodeLlama / Llama 2 family)
 *
 * The format is auto-detected from the model filename via [detectFormat].
 * If the GGUF file has a built-in chat template, llama.cpp may override
 * this formatting on the C++ side.
 */
object PromptFormatter {

    /** Prompt template format. */
    enum class Format {
        CHATML,             // <|im_start|>system\n...\n<|im_end|>
        LLAMA3,             // <|begin_of_text|><|start_header_id|>system<|end_header_id|>
        CODELLAMA_INSTRUCT  // <s>[INST] <<SYS>>\n...\n<</SYS>>
    }

    // ── System prompts ──────────────────────────────────────────────

    /** System prompt for simple chat mode — a genuinely helpful assistant. */
    const val CHAT_SYSTEM_PROMPT =
        "You are a helpful AI assistant running locally on the user's device. " +
        "You can help with: explaining code, answering programming questions, " +
        "writing snippets, giving advice on architecture and best practices, " +
        "general knowledge, learning, and everyday questions. " +
        "Be concise but thorough. Use markdown for code blocks. " +
        "If the user asks to create, edit, or delete files, or run terminal commands, " +
        "briefly explain that they should switch to Agent Mode (robot icon in the top bar) " +
        "for file and git operations. " +
        "Answer in the same language the user writes in."

    /** Legacy system prompt for CodeLlama-Instruct. */
    private const val LEGACY_SYSTEM_PROMPT =
        "RULE: If the user asks to create, write, or delete files, run commands, use git, " +
        "or do anything with the filesystem — respond with ONLY this one sentence: " +
        "\"Switch to Agent Mode (robot icon in the top bar) for file and git operations.\" " +
        "Do NOT suggest terminal commands. Do NOT explain how to do it manually. " +
        "You are a helpful coding assistant. " +
        "Answer only the user's most recent question concisely. " +
        "Do not repeat or continue previous answers."

    // ── Format detection ────────────────────────────────────────────

    /**
     * Detect prompt format from model filename.
     * Falls back to ChatML for unrecognized models (most modern models use it).
     */
    fun detectFormat(modelFilename: String): Format {
        val lower = modelFilename.lowercase()
        return when {
            // Llama 3.x family
            lower.contains("llama-3") || lower.contains("llama3") ||
            lower.contains("llama_3") -> Format.LLAMA3

            // Legacy CodeLlama / Llama 2
            lower.contains("codellama") || lower.contains("code-llama") ||
            lower.contains("llama-2") || lower.contains("llama2") -> Format.CODELLAMA_INSTRUCT

            // Everything else: Qwen, Phi, DeepSeek, Mistral, Gemma, etc.
            else -> Format.CHATML
        }.also { Timber.d("Detected format $it for $modelFilename") }
    }

    // ── History trimming ────────────────────────────────────────────

    /**
     * Trim conversation history to prevent context overflow.
     * Keeps the last [maxTurns] user+assistant pairs.
     * System messages are always discarded (injected via systemPrompt parameter).
     */
    fun trimHistory(messages: List<ChatMessage>, maxTurns: Int = 12): List<ChatMessage> {
        val nonSystem = messages.filter { it.role != Role.SYSTEM }
        return if (nonSystem.size > maxTurns * 2) nonSystem.takeLast(maxTurns * 2) else nonSystem
    }

    // ── Prompt formatting ───────────────────────────────────────────

    /**
     * Format messages using the specified format.
     * If [format] is null, defaults to ChatML.
     */
    fun formatPrompt(
        messages: List<ChatMessage>,
        systemPrompt: String = CHAT_SYSTEM_PROMPT,
        format: Format = Format.CHATML
    ): String = when (format) {
        Format.CHATML -> formatChatML(messages, systemPrompt)
        Format.LLAMA3 -> formatLlama3(messages, systemPrompt)
        Format.CODELLAMA_INSTRUCT -> formatCodeLlama(messages, systemPrompt)
    }

    /**
     * Legacy overload — auto-selects format from CodeLlama template.
     * Kept for backward compatibility with agent code that passes just messages + system prompt.
     */
    fun formatPrompt(
        messages: List<ChatMessage>,
        systemPrompt: String = CHAT_SYSTEM_PROMPT
    ): String = formatPrompt(messages, systemPrompt, Format.CHATML)

    // ── ChatML ──────────────────────────────────────────────────────

    /**
     * ChatML format (Qwen, Phi, DeepSeek, Mistral, most 2024+ models):
     * ```
     * <|im_start|>system
     * {system_prompt}<|im_end|>
     * <|im_start|>user
     * {user_message}<|im_end|>
     * <|im_start|>assistant
     * {response}<|im_end|>
     * <|im_start|>user
     * ...
     * <|im_start|>assistant
     * ```
     */
    private fun formatChatML(messages: List<ChatMessage>, systemPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<|im_start|>system\n")
        sb.append(systemPrompt)
        sb.append("<|im_end|>\n")

        for (msg in messages) {
            when (msg.role) {
                Role.USER -> {
                    sb.append("<|im_start|>user\n")
                    sb.append(msg.content)
                    sb.append("<|im_end|>\n")
                }
                Role.ASSISTANT -> {
                    sb.append("<|im_start|>assistant\n")
                    sb.append(msg.content)
                    sb.append("<|im_end|>\n")
                }
                Role.SYSTEM -> { /* handled above */ }
            }
        }
        // Open assistant turn for generation
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    // ── Llama 3 ─────────────────────────────────────────────────────

    /**
     * Llama 3 / 3.1 / 3.2 format:
     * ```
     * <|begin_of_text|><|start_header_id|>system<|end_header_id|>
     *
     * {system_prompt}<|eot_id|><|start_header_id|>user<|end_header_id|>
     *
     * {user_message}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
     *
     * {response}<|eot_id|>...
     * ```
     */
    private fun formatLlama3(messages: List<ChatMessage>, systemPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<|begin_of_text|>")
        sb.append("<|start_header_id|>system<|end_header_id|>\n\n")
        sb.append(systemPrompt)
        sb.append("<|eot_id|>")

        for (msg in messages) {
            when (msg.role) {
                Role.USER -> {
                    sb.append("<|start_header_id|>user<|end_header_id|>\n\n")
                    sb.append(msg.content)
                    sb.append("<|eot_id|>")
                }
                Role.ASSISTANT -> {
                    sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
                    sb.append(msg.content)
                    sb.append("<|eot_id|>")
                }
                Role.SYSTEM -> { /* handled above */ }
            }
        }
        // Open assistant turn
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    // ── CodeLlama-Instruct (Legacy) ─────────────────────────────────

    /**
     * CodeLlama-Instruct / Llama 2 format:
     * ```
     * <s>[INST] <<SYS>>
     * {system_prompt}
     * <</SYS>>
     *
     * {user_message_1} [/INST] {response_1} </s><s>[INST] {user_message_2} [/INST]
     * ```
     */
    private fun formatCodeLlama(messages: List<ChatMessage>, systemPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<s>")

        var isFirstUser = true
        for (message in messages) {
            when (message.role) {
                Role.USER -> {
                    sb.append("[INST] ")
                    if (isFirstUser) {
                        sb.append("<<SYS>>\n")
                        sb.append(systemPrompt)
                        sb.append("\n<</SYS>>\n\n")
                        isFirstUser = false
                    }
                    sb.append(message.content)
                    sb.append(" [/INST] ")
                }
                Role.ASSISTANT -> {
                    sb.append(message.content)
                    sb.append(" </s><s>")
                }
                Role.SYSTEM -> { /* handled via systemPrompt */ }
            }
        }
        return sb.toString()
    }
}
