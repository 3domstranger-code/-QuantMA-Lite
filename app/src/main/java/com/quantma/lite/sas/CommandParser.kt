package com.quantma.lite.sas

import java.util.UUID

/**
 * Secure Agent Shell — Command Parser.
 *
 * Dual-protocol parser that handles both:
 * 1. **JSON protocol** (primary): `{"tool": "read_file", "args": {"path": "src/main.kt"}}`
 * 2. **ACTION protocol** (fallback): `ACTION: read_file src/main.kt`
 *
 * Designed for robustness with local LLMs that may produce imperfect output:
 * - Extracts JSON from surrounding text/markdown
 * - Handles missing quotes, trailing commas, extra whitespace
 * - Falls back to ACTION protocol if JSON parsing fails
 * - Generates UUID if not provided
 */
class CommandParser {

    /**
     * Parse LLM output into a [ToolCommand].
     *
     * @param llmOutput Raw text output from the LLM
     * @return Parsed [ToolCommand] or null if output is regular chat (no tool call detected)
     */
    fun parse(llmOutput: String): ToolCommand? {
        // Try JSON protocol first
        tryParseJson(llmOutput)?.let { return it }
        // Fall back to ACTION protocol
        return tryParseAction(llmOutput)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON Protocol Parser
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Try to parse a JSON tool command from the LLM output.
     * Handles JSON embedded in text, markdown code blocks, etc.
     */
    private fun tryParseJson(text: String): ToolCommand? {
        val jsonStr = extractJsonObject(text) ?: return null
        return try {
            parseJsonObject(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract the first JSON object `{...}` from text.
     * Handles nested braces correctly.
     */
    private fun extractJsonObject(text: String): String? {
        // Strip markdown code fences if present
        val cleaned = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val startIdx = cleaned.indexOf('{')
        if (startIdx == -1) return null

        var depth = 0
        var inString = false
        var escaped = false

        for (i in startIdx until cleaned.length) {
            val ch = cleaned[i]

            if (escaped) {
                escaped = false
                continue
            }

            when {
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                ch == '{' && !inString -> depth++
                ch == '}' && !inString -> {
                    depth--
                    if (depth == 0) {
                        return cleaned.substring(startIdx, i + 1)
                    }
                }
            }
        }

        return null
    }

    /**
     * Parse a JSON object string into a [ToolCommand].
     * Lightweight recursive-descent parser — no external dependencies.
     */
    private fun parseJsonObject(json: String): ToolCommand? {
        val map = JsonParser(json).parseObject() ?: return null

        val toolName = (map["tool"] as? String) ?: return null
        val toolType = ToolType.fromName(toolName) ?: return null

        val id = (map["id"] as? String) ?: UUID.randomUUID().toString()
        val thought = map["thought"] as? String

        @Suppress("UNCHECKED_CAST")
        val args = (map["args"] as? Map<String, Any>) ?: emptyMap<String, Any>()

        return ToolCommand(
            id = id,
            tool = toolType,
            args = args,
            thought = thought
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTION Protocol Parser (Legacy)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parse legacy ACTION: protocol.
     *
     * Formats:
     * - `ACTION: read_file <path>`
     * - `ACTION: list_files <path>`
     * - `ACTION: write_file <path>\nCONTENT:\n<content>\nEND_CONTENT`
     * - `ACTION: git_status <path>`
     * - `DONE`
     */
    private fun tryParseAction(text: String): ToolCommand? {
        val lines = text.lines()

        // Check for DONE signal
        if (lines.any { it.trim().equals("DONE", ignoreCase = true) }) {
            return ToolCommand(tool = ToolType.DONE)
        }

        // Find ACTION: line
        val actionLine = lines.firstOrNull {
            it.trim().startsWith("ACTION:", ignoreCase = true)
        } ?: return null

        val trimmed = actionLine.trim()
        val actionContent = trimmed.substring(trimmed.indexOf(':') + 1).trim()
        val parts = actionContent.split(Regex("\\s+"), limit = 2)
        if (parts.isEmpty()) return null

        val toolName = parts[0].lowercase()
        val toolType = ToolType.fromName(toolName) ?: return null
        val arg = parts.getOrNull(1)?.trim() ?: ""

        return when (toolType) {
            ToolType.WRITE_FILE, ToolType.APPEND_FILE -> {
                // Extract multiline content between CONTENT: and END_CONTENT
                val content = extractContent(text)
                ToolCommand(
                    tool = toolType,
                    args = buildMap {
                        put("path", arg)
                        if (content != null) put("content", content)
                    }
                )
            }
            ToolType.SEARCH_GREP -> {
                // ACTION: search_grep <pattern> <path>
                val grepParts = arg.split(Regex("\\s+"), limit = 2)
                ToolCommand(
                    tool = toolType,
                    args = buildMap {
                        put("pattern", grepParts.getOrElse(0) { "" })
                        put("path", grepParts.getOrElse(1) { "." })
                    }
                )
            }
            ToolType.GIT_COMMIT -> {
                // ACTION: git_commit <path> <message>
                val commitParts = arg.split(Regex("\\s+"), limit = 2)
                ToolCommand(
                    tool = toolType,
                    args = buildMap {
                        put("path", commitParts.getOrElse(0) { "." })
                        put("message", commitParts.getOrElse(1) { "Auto commit" })
                    }
                )
            }
            ToolType.GIT_LOG -> {
                ToolCommand(
                    tool = toolType,
                    args = mapOf("path" to arg.ifEmpty { "." }, "max_count" to 10)
                )
            }
            ToolType.GIT_STASH -> {
                // ACTION: git_stash <operation> [message]
                val stashParts = arg.split(Regex("\\s+"), limit = 2)
                ToolCommand(
                    tool = toolType,
                    args = buildMap {
                        put("operation", stashParts.getOrElse(0) { "list" })
                        if (stashParts.size > 1) put("message", stashParts[1])
                    }
                )
            }
            ToolType.GIT_COMMIT_AUTO -> {
                // ACTION: git_commit_auto [message]
                ToolCommand(
                    tool = toolType,
                    args = if (arg.isNotEmpty()) mapOf("message" to arg) else emptyMap()
                )
            }
            else -> {
                // Simple tools: read_file, list_files/list_dir, create_file, git_status, git_add, git_branch
                ToolCommand(
                    tool = toolType,
                    args = if (arg.isNotEmpty()) mapOf("path" to arg) else emptyMap()
                )
            }
        }
    }

    /**
     * Extract file content from LLM output with multiple fallback strategies.
     *
     * Strategy 1 (canonical): CONTENT: ... END_CONTENT markers
     * Strategy 2 (common fallback): Markdown code block ```...```
     * Strategy 3 (last resort): All text after the ACTION: line
     *
     * Local 7B models often skip the CONTENT:/END_CONTENT protocol and embed
     * the file content in a code fence or plain text below the tool call.
     */
    private fun extractContent(text: String): String? {
        // Strategy 1: canonical CONTENT: / END_CONTENT markers
        val contentStart = text.indexOf("CONTENT:")
        if (contentStart != -1) {
            val afterMarker = text.substring(contentStart + "CONTENT:".length)
            val endIdx = afterMarker.indexOf("END_CONTENT")
            return if (endIdx != -1) {
                afterMarker.substring(0, endIdx).trimStart('\n').trimEnd('\n')
            } else {
                afterMarker.trimStart('\n').trimEnd()
            }
        }

        // Strategy 2: markdown code block ```[lang]\n...\n```
        val codeBlock = Regex("```(?:\\w+)?\\n([\\s\\S]*?)```").find(text)
        if (codeBlock != null) {
            val extracted = codeBlock.groupValues[1].trimEnd('\n')
            if (extracted.isNotBlank()) return extracted
        }

        // Strategy 3: everything after the ACTION: line
        // Models sometimes just put content on the next line without any marker
        val lines = text.lines()
        val actionIdx = lines.indexOfFirst { it.trim().startsWith("ACTION:", ignoreCase = true) }
        if (actionIdx != -1 && actionIdx < lines.lastIndex) {
            val afterAction = lines.drop(actionIdx + 1).joinToString("\n").trim()
            if (afterAction.isNotBlank()) return afterAction
        }

        // Strategy 4: look for content BEFORE the ACTION: line
        // Local 7B models often say "write exactly this: <code>" then call ACTION: write_file
        // Patterns: "это:\n<code>", "содержимым:\n<code>", "content:\n<code>", inline code block
        val preAction = if (actionIdx > 0) lines.take(actionIdx).joinToString("\n") else text
        // 4a: inline code block before ACTION:
        val preCodeBlock = Regex("```(?:\\w+)?\\n([\\s\\S]*?)```").find(preAction)
        if (preCodeBlock != null) {
            val extracted = preCodeBlock.groupValues[1].trimEnd('\n')
            if (extracted.isNotBlank()) return extracted
        }
        // 4b: text after "это:" / "content:" / "содержимым:" pattern on same or next line
        val afterKeyword = Regex(
            "(?:это:|content:|содержимым:|следующее:|following:)\\s*\\n?([^\\n]+(?:\\n[^\\n]+)*)",
            RegexOption.IGNORE_CASE
        ).find(preAction)
        if (afterKeyword != null) {
            val extracted = afterKeyword.groupValues[1].trim()
            if (extracted.isNotBlank()) return extracted
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lightweight JSON Parser (Recursive Descent)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Minimal JSON parser that handles the subset needed for tool commands.
     * Tolerant of minor formatting issues from local LLMs.
     */
    private class JsonParser(private val input: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any>? {
            skipWhitespace()
            if (!consume('{')) return null

            val map = mutableMapOf<String, Any>()

            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }

            while (pos < input.length) {
                skipWhitespace()

                // Parse key
                val key = parseString() ?: return null
                skipWhitespace()
                if (!consume(':')) return null
                skipWhitespace()

                // Parse value
                val value = parseValue() ?: return null
                map[key] = value

                skipWhitespace()
                if (peek() == ',') {
                    pos++
                    skipWhitespace()
                    // Allow trailing comma
                    if (peek() == '}') {
                        pos++
                        return map
                    }
                } else if (consume('}')) {
                    return map
                } else {
                    return null
                }
            }
            return null
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '"' -> parseString()
                '{' -> parseObject()
                '[' -> parseArray()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseString(): String? {
            if (!consume('"')) return null
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos]
                when {
                    ch == '\\' -> {
                        pos++
                        if (pos >= input.length) return null
                        when (val escaped = input[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'u' -> {
                                // Unicode escape \uXXXX
                                if (pos + 4 < input.length) {
                                    val hex = input.substring(pos + 1, pos + 5)
                                    try {
                                        sb.append(hex.toInt(16).toChar())
                                        pos += 4
                                    } catch (_: NumberFormatException) {
                                        sb.append("\\u").append(hex)
                                        pos += 4
                                    }
                                } else {
                                    sb.append("\\u")
                                }
                            }
                            else -> {
                                // Tolerate unknown escapes (local LLMs may produce them)
                                sb.append(escaped)
                            }
                        }
                        pos++
                    }
                    ch == '"' -> {
                        pos++
                        return sb.toString()
                    }
                    else -> {
                        sb.append(ch)
                        pos++
                    }
                }
            }
            // Unterminated string — return what we have (tolerance for LLM output)
            return sb.toString()
        }

        private fun parseArray(): List<Any>? {
            if (!consume('[')) return null
            val list = mutableListOf<Any>()

            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }

            while (pos < input.length) {
                skipWhitespace()
                val value = parseValue() ?: return null
                list.add(value)

                skipWhitespace()
                if (peek() == ',') {
                    pos++
                    skipWhitespace()
                    // Allow trailing comma
                    if (peek() == ']') {
                        pos++
                        return list
                    }
                } else if (consume(']')) {
                    return list
                } else {
                    return null
                }
            }
            return null
        }

        private fun parseNumber(): Number? {
            val start = pos
            if (peek() == '-') pos++
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos < input.length && input[pos] == '.') {
                pos++
                while (pos < input.length && input[pos].isDigit()) pos++
            }
            if (pos == start) return null
            val numStr = input.substring(start, pos)
            return if ('.' in numStr) numStr.toDoubleOrNull() else numStr.toLongOrNull()
        }

        private fun parseBoolean(): Boolean? {
            return when {
                input.startsWith("true", pos) -> { pos += 4; true }
                input.startsWith("false", pos) -> { pos += 5; false }
                else -> null
            }
        }

        private fun parseNull(): Any? {
            return if (input.startsWith("null", pos)) {
                pos += 4; ""  // Return empty string for null (useful for LLM output)
            } else null
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < input.length) input[pos] else '\u0000'

        private fun consume(expected: Char): Boolean {
            if (pos < input.length && input[pos] == expected) {
                pos++
                return true
            }
            return false
        }
    }
}
