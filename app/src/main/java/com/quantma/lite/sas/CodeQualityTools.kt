package com.quantma.lite.sas

import java.io.File

/**
 * Secure Agent Shell — Code Quality Tools.
 *
 * Implements 4 code quality instruments as a delegate for [ShellExecutor]:
 *
 * - **LINT_FILE**: Pure regex-based linter (no external tools). Checks naming conventions,
 *   unused imports, line length, trailing whitespace, wildcard imports, empty catch blocks.
 * - **FORMAT_FILE**: Auto-formatting (trailing whitespace, line endings, EOF newline).
 *   Returns [ToolResponse.NeedsApproval] with a diff preview.
 * - **COUNT_LINES**: LOC counter for single files or entire directories.
 * - **FIND_TODO**: Searches for TODO/FIXME/HACK/XXX/NOTE comments across project files.
 *
 * All methods are pure/synchronous — caller handles threading.
 */
class CodeQualityTools(
    private val config: SasConfig,
    private val sanitizer: PathSanitizer,
    private val diffEngine: DiffEngine = DiffEngine()
) {

    // ═════════════════════════════════════════════════════════════════════════
    // LINT_FILE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Lint a file or directory. Returns issues grouped by file.
     *
     * Args: `path` (file or dir), optional `fix` (boolean — suggest auto-fix)
     */
    fun executeLint(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: ""
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }
        val relPath = sanitizer.relativePath(file)

        return if (file.isDirectory) {
            lintDirectory(file, relPath)
        } else {
            sanitizer.validateForRead(file).getOrElse {
                return ToolResponse.Error("READ_ERROR", it.message ?: "Cannot read")
            }
            lintFile(file, relPath)
        }
    }

    private fun lintFile(file: File, relPath: String): ToolResponse {
        val content = file.readText()
        val language = CodeLanguage.fromExtension(file.extension)
        val issues = runLintRules(content, relPath, language)

        if (issues.isEmpty()) {
            return ToolResponse.Success(
                output = "✓ $relPath: no issues found",
                metadata = mapOf("path" to relPath, "issues" to 0)
            )
        }

        val output = buildString {
            appendLine("$relPath: ${issues.size} issue(s)")
            issues.forEach { issue ->
                appendLine("  ${issue.line}: [${issue.severity}] ${issue.rule} — ${issue.message}")
            }
        }
        return ToolResponse.Success(
            output = output.trimEnd(),
            metadata = mapOf("path" to relPath, "issues" to issues.size)
        )
    }

    private fun lintDirectory(dir: File, relPath: String): ToolResponse {
        val allIssues = mutableListOf<LintIssue>()
        var filesChecked = 0

        dir.walkTopDown()
            .filter { it.isFile && sanitizer.isExtensionAllowed(it) }
            .take(config.maxSearchResults)
            .forEach { file ->
                val fileRel = sanitizer.relativePath(file)
                val content = file.readText()
                val language = CodeLanguage.fromExtension(file.extension)
                allIssues.addAll(runLintRules(content, fileRel, language))
                filesChecked++
            }

        if (allIssues.isEmpty()) {
            return ToolResponse.Success(
                output = "✓ Checked $filesChecked files: no issues found",
                metadata = mapOf("path" to relPath, "files_checked" to filesChecked, "issues" to 0)
            )
        }

        val grouped = allIssues.groupBy { it.file }
        val output = buildString {
            appendLine("Checked $filesChecked files, found ${allIssues.size} issue(s):")
            grouped.entries.take(config.maxListEntries).forEach { (filePath, issues) ->
                appendLine("\n$filePath (${issues.size}):")
                issues.take(10).forEach { issue ->
                    appendLine("  ${issue.line}: [${issue.severity}] ${issue.rule} — ${issue.message}")
                }
                if (issues.size > 10) appendLine("  ... and ${issues.size - 10} more")
            }
        }
        return ToolResponse.Success(
            output = output.trimEnd(),
            metadata = mapOf("path" to relPath, "files_checked" to filesChecked, "issues" to allIssues.size)
        )
    }

    private fun runLintRules(content: String, relPath: String, language: CodeLanguage): List<LintIssue> {
        val issues = mutableListOf<LintIssue>()
        val lines = content.lines()

        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1

            // Line length > 120
            if (line.length > MAX_LINE_LENGTH) {
                issues.add(LintIssue("line-length", "Line exceeds $MAX_LINE_LENGTH chars (${line.length})", relPath, lineNum, LintSeverity.WARNING))
            }

            // Trailing whitespace
            if (line.isNotEmpty() && line != line.trimEnd()) {
                issues.add(LintIssue("trailing-whitespace", "Trailing whitespace", relPath, lineNum, LintSeverity.INFO))
            }
        }

        // Language-specific rules
        when (language) {
            CodeLanguage.KOTLIN, CodeLanguage.JAVA -> {
                lintJvmLanguage(content, lines, relPath, language, issues)
            }
            CodeLanguage.PYTHON -> {
                lintPython(content, lines, relPath, issues)
            }
            else -> { /* Generic rules only */ }
        }

        return issues
    }

    private fun lintJvmLanguage(
        content: String,
        lines: List<String>,
        relPath: String,
        language: CodeLanguage,
        issues: MutableList<LintIssue>
    ) {
        val isKotlin = language == CodeLanguage.KOTLIN

        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()

            // Class naming: should be PascalCase
            CLASS_NAME_BAD.find(trimmed)?.let {
                issues.add(LintIssue("naming-class", "Class name '${it.groupValues[1]}' should be PascalCase", relPath, lineNum))
            }

            // Function naming: Kotlin functions should be camelCase (not PascalCase)
            if (isKotlin) {
                FUN_NAME_BAD_KT.find(trimmed)?.let {
                    issues.add(LintIssue("naming-fun", "Function '${it.groupValues[1]}' should be camelCase", relPath, lineNum))
                }
                // Constants should be UPPER_SNAKE_CASE
                CONST_NAME_BAD.find(trimmed)?.let {
                    issues.add(LintIssue("naming-const", "Constant '${it.groupValues[1]}' should be UPPER_SNAKE_CASE", relPath, lineNum))
                }
            }

            // Wildcard imports
            if (trimmed.matches(WILDCARD_IMPORT)) {
                issues.add(LintIssue("wildcard-import", "Avoid wildcard import", relPath, lineNum, LintSeverity.WARNING))
            }
        }

        // Empty catch blocks (multiline)
        EMPTY_CATCH.findAll(content).forEach { match ->
            val line = content.substring(0, match.range.first).count { it == '\n' } + 1
            issues.add(LintIssue("empty-catch", "Empty catch block", relPath, line, LintSeverity.WARNING))
        }

        // Unused imports (heuristic: check if the last segment of import appears elsewhere)
        val importLines = lines.withIndex().filter { it.value.trim().startsWith("import ") }
        val codeWithoutImports = lines.filterNot { it.trim().startsWith("import ") }.joinToString("\n")
        importLines.forEach { (idx, importLine) ->
            val importPath = importLine.trim().removePrefix("import ").removeSuffix(";").trim()
            val simpleName = importPath.substringAfterLast('.')
            if (simpleName != "*" && simpleName.length > 1 && !codeWithoutImports.contains(simpleName)) {
                issues.add(LintIssue("unused-import", "Possibly unused import: $simpleName", relPath, idx + 1, LintSeverity.INFO))
            }
        }
    }

    private fun lintPython(
        content: String,
        lines: List<String>,
        relPath: String,
        issues: MutableList<LintIssue>
    ) {
        lines.forEachIndexed { idx, line ->
            val lineNum = idx + 1
            val trimmed = line.trim()

            // Class naming: PascalCase
            PY_CLASS_BAD.find(trimmed)?.let {
                issues.add(LintIssue("naming-class", "Class name '${it.groupValues[1]}' should be PascalCase", relPath, lineNum))
            }

            // Function naming: snake_case (not camelCase)
            PY_FUN_BAD.find(trimmed)?.let {
                val name = it.groupValues[1]
                if (name != "__init__" && !name.startsWith("_") && name.contains(Regex("[A-Z]"))) {
                    issues.add(LintIssue("naming-fun", "Function '$name' should be snake_case", relPath, lineNum))
                }
            }
        }

        // Bare except
        BARE_EXCEPT.findAll(content).forEach { match ->
            val line = content.substring(0, match.range.first).count { it == '\n' } + 1
            issues.add(LintIssue("bare-except", "Avoid bare 'except:'", relPath, line, LintSeverity.WARNING))
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FORMAT_FILE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Preview formatting changes. Returns [ToolResponse.NeedsApproval] with diff.
     *
     * Args: `path` — file to format
     */
    fun executeFormat(command: ToolCommand): ToolResponse {
        val path = command.requireStringArg("path")
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }
        sanitizer.validateForRead(file).getOrElse {
            return ToolResponse.Error("READ_ERROR", it.message ?: "Cannot read")
        }

        val relPath = sanitizer.relativePath(file)
        val original = file.readText()
        val formatted = formatContent(original)

        if (formatted == original) {
            return ToolResponse.Success(
                output = "✓ $relPath: already formatted",
                metadata = mapOf("path" to relPath, "changed" to false)
            )
        }

        val diff = diffEngine.computeUnifiedDiff(original, formatted, relPath)
        val stats = diffEngine.computeStats(original, formatted)

        return ToolResponse.NeedsApproval(
            description = "Format $relPath ($stats)",
            command = command,
            preview = diff
        )
    }

    /**
     * Apply formatting after user approval.
     */
    fun performFormat(file: File, relPath: String): ToolResponse {
        val original = file.readText()
        val formatted = formatContent(original)
        file.writeText(formatted)

        val stats = diffEngine.computeStats(original, formatted)
        return ToolResponse.Success(
            output = "Formatted: $relPath ($stats)",
            metadata = mapOf("path" to relPath, "stats" to stats.toString())
        )
    }

    private fun formatContent(content: String): String {
        return content
            .replace("\r\n", "\n")       // Normalize line endings
            .replace("\r", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }  // Remove trailing whitespace
            .trimEnd() + "\n"            // Single newline at EOF
    }

    // ═════════════════════════════════════════════════════════════════════════
    // COUNT_LINES
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Count lines of code in a file or directory.
     *
     * Args: `path` — file or directory
     */
    fun executeCountLines(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: ""
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }
        val relPath = sanitizer.relativePath(file)

        if (file.isFile) {
            sanitizer.validateForRead(file).getOrElse {
                return ToolResponse.Error("READ_ERROR", it.message ?: "Cannot read")
            }
            return countSingleFile(file, relPath)
        }

        return countDirectory(file, relPath)
    }

    private fun countSingleFile(file: File, relPath: String): ToolResponse {
        val lines = file.readLines()
        val total = lines.size
        val blank = lines.count { it.isBlank() }
        val comment = lines.count { isCommentLine(it, file.extension) }
        val code = total - blank - comment

        return ToolResponse.Success(
            output = "$relPath: $total lines (code: $code, blank: $blank, comment: $comment)",
            metadata = mapOf("path" to relPath, "total" to total, "code" to code, "blank" to blank, "comment" to comment)
        )
    }

    private fun countDirectory(dir: File, relPath: String): ToolResponse {
        var totalFiles = 0
        var totalLines = 0
        var totalCode = 0
        var totalBlank = 0
        var totalComment = 0
        val perFile = mutableListOf<String>()

        dir.walkTopDown()
            .filter { it.isFile && sanitizer.isExtensionAllowed(it) }
            .take(config.maxSearchResults)
            .forEach { file ->
                val lines = file.readLines()
                val blank = lines.count { it.isBlank() }
                val comment = lines.count { isCommentLine(it, file.extension) }
                val code = lines.size - blank - comment

                totalFiles++
                totalLines += lines.size
                totalCode += code
                totalBlank += blank
                totalComment += comment

                if (perFile.size < config.maxListEntries) {
                    perFile.add("${sanitizer.relativePath(file)}: ${lines.size} (code: $code)")
                }
            }

        val output = buildString {
            appendLine("Directory: $relPath")
            appendLine("Files: $totalFiles | Total: $totalLines lines (code: $totalCode, blank: $totalBlank, comment: $totalComment)")
            if (perFile.isNotEmpty()) {
                appendLine()
                perFile.forEach { appendLine("  $it") }
                if (totalFiles > config.maxListEntries) {
                    appendLine("  ... and ${totalFiles - config.maxListEntries} more files")
                }
            }
        }
        return ToolResponse.Success(
            output = output.trimEnd(),
            metadata = mapOf("path" to relPath, "files" to totalFiles, "total_lines" to totalLines, "code_lines" to totalCode)
        )
    }

    private fun isCommentLine(line: String, extension: String): Boolean {
        val trimmed = line.trim()
        return when (extension.lowercase()) {
            "kt", "kts", "java", "js", "ts", "cpp", "c", "h", "hpp", "go", "rs" ->
                trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
            "py" -> trimmed.startsWith("#")
            "xml", "html" -> trimmed.startsWith("<!--")
            "yaml", "yml", "toml", "sh", "bat" -> trimmed.startsWith("#")
            else -> false
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FIND_TODO
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Find TODO/FIXME/HACK/XXX/NOTE comments in files.
     *
     * Args: `path` — directory to search (default: working dir),
     *        `pattern` — custom regex (default: TODO|FIXME|HACK|XXX|NOTE)
     */
    fun executeFindTodo(command: ToolCommand): ToolResponse {
        val path = command.stringArg("path") ?: ""
        val customPattern = command.stringArg("pattern")
        val file = sanitizer.resolve(path).getOrElse {
            return ToolResponse.Error("PATH_ERROR", it.message ?: "Invalid path")
        }
        val relPath = sanitizer.relativePath(file)

        val pattern = try {
            Regex(customPattern ?: DEFAULT_TODO_PATTERN, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return ToolResponse.Error("REGEX_ERROR", "Invalid pattern: ${e.message}")
        }

        val matches = mutableListOf<SearchMatch>()
        val searchDir = if (file.isDirectory) file else file.parentFile

        searchDir.walkTopDown()
            .filter { it.isFile && sanitizer.isExtensionAllowed(it) }
            .forEach { f ->
                if (matches.size >= config.maxSearchResults) return@forEach
                val fRel = sanitizer.relativePath(f)
                f.readLines().forEachIndexed { idx, line ->
                    if (matches.size < config.maxSearchResults && pattern.containsMatchIn(line)) {
                        matches.add(SearchMatch(fRel, idx + 1, line.trim()))
                    }
                }
            }

        if (matches.isEmpty()) {
            return ToolResponse.Success(
                output = "No TODO/FIXME comments found in $relPath",
                metadata = mapOf("path" to relPath, "count" to 0)
            )
        }

        // Group by tag type
        val tagPattern = Regex("(TODO|FIXME|HACK|XXX|NOTE)", RegexOption.IGNORE_CASE)
        val grouped = matches.groupBy { match ->
            tagPattern.find(match.content)?.value?.uppercase() ?: "OTHER"
        }

        val output = buildString {
            appendLine("Found ${matches.size} comment(s) in $relPath:")
            grouped.forEach { (tag, items) ->
                appendLine("\n── $tag (${items.size}) ──")
                items.forEach { appendLine("  ${it.file}:${it.line}: ${it.content}") }
            }
        }
        return ToolResponse.Success(
            output = output.trimEnd(),
            metadata = mapOf("path" to relPath, "count" to matches.size)
        )
    }

    // ─── Constants ──────────────────────────────────────────────────────────

    companion object {
        private const val MAX_LINE_LENGTH = 120
        private const val DEFAULT_TODO_PATTERN = "(TODO|FIXME|HACK|XXX|NOTE)(\\(.*?\\))?:?\\s*"

        // JVM language lint patterns
        private val CLASS_NAME_BAD = Regex("""(?:class|interface|enum)\s+([a-z]\w*)""")
        private val FUN_NAME_BAD_KT = Regex("""fun\s+([A-Z]\w*)""")
        private val CONST_NAME_BAD = Regex("""const\s+val\s+([a-z]\w*)""")
        private val WILDCARD_IMPORT = Regex("""import\s+.*\.\*;?\s*""")
        private val EMPTY_CATCH = Regex("""catch\s*\([^)]*\)\s*\{\s*\}""")

        // Python lint patterns
        private val PY_CLASS_BAD = Regex("""class\s+([a-z]\w*)""")
        private val PY_FUN_BAD = Regex("""def\s+(\w+)\s*\(""")
        private val BARE_EXCEPT = Regex("""except\s*:""")
    }
}
