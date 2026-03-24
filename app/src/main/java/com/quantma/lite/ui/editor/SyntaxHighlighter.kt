package com.quantma.lite.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

enum class CodeLanguage {
    KOTLIN, JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT,
    XML, JSON, YAML, MARKDOWN, SHELL,
    C, CPP, RUST, GO, SQL, GROOVY, PLAIN
}

object SyntaxHighlighter {

    // ── Dracula-inspired palette (works on dark & light) ──────────────────────
    private val COLOR_KEYWORD   = Color(0xFF8BE9FD)  // cyan
    private val COLOR_STRING    = Color(0xFFF1FA8C)  // yellow
    private val COLOR_COMMENT   = Color(0xFF6272A4)  // muted blue-gray
    private val COLOR_NUMBER    = Color(0xFFBD93F9)  // purple
    private val COLOR_ANNOTATION= Color(0xFF50FA7B)  // green
    private val COLOR_TYPE      = Color(0xFFFFB86C)  // orange
    private val COLOR_FUNCTION  = Color(0xFF50FA7B)  // green
    private val COLOR_TAG       = Color(0xFFFF79C6)  // pink
    private val COLOR_ATTR      = Color(0xFF50FA7B)  // green
    private val COLOR_OPERATOR  = Color(0xFFFF79C6)  // pink
    private val COLOR_PLAIN     = Color(0xFFF8F8F2)  // near-white

    fun getLanguage(fileName: String): CodeLanguage {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts"              -> CodeLanguage.KOTLIN
            "java"                   -> CodeLanguage.JAVA
            "py", "pyw"              -> CodeLanguage.PYTHON
            "js", "mjs", "cjs"       -> CodeLanguage.JAVASCRIPT
            "ts", "tsx"              -> CodeLanguage.TYPESCRIPT
            "xml", "html", "htm"     -> CodeLanguage.XML
            "json", "jsonc"          -> CodeLanguage.JSON
            "yaml", "yml"            -> CodeLanguage.YAML
            "md", "markdown"         -> CodeLanguage.MARKDOWN
            "sh", "bash", "zsh"      -> CodeLanguage.SHELL
            "c", "h"                 -> CodeLanguage.C
            "cpp", "cc", "cxx", "hpp"-> CodeLanguage.CPP
            "rs"                     -> CodeLanguage.RUST
            "go"                     -> CodeLanguage.GO
            "sql"                    -> CodeLanguage.SQL
            "gradle", "groovy", "gvy"-> CodeLanguage.GROOVY
            else                     -> CodeLanguage.PLAIN
        }
    }

    fun highlight(language: CodeLanguage, text: String): AnnotatedString {
        if (language == CodeLanguage.PLAIN) return AnnotatedString(text)
        return when (language) {
            CodeLanguage.XML, CodeLanguage.MARKDOWN -> highlightXmlOrMd(language, text)
            CodeLanguage.JSON                       -> highlightJson(text)
            CodeLanguage.YAML                       -> highlightYaml(text)
            CodeLanguage.SHELL                      -> highlightShell(text)
            else                                    -> highlightCode(language, text)
        }
    }

    // ── Generic C-family + Kotlin/Java/Python highlighter ────────────────────

    private fun highlightCode(lang: CodeLanguage, text: String): AnnotatedString {
        val rules = getRules(lang)
        return applyRules(text, rules)
    }

    private data class Rule(val regex: Regex, val color: Color, val bold: Boolean = false, val italic: Boolean = false)

    private fun getRules(lang: CodeLanguage): List<Rule> {
        val keywords = getKeywords(lang)
        val kwRegex = "\\b(${keywords.joinToString("|")})\\b".toRegex()

        val base = listOf(
            // Block comments
            Rule(Regex("/\\*[\\s\\S]*?\\*/"), COLOR_COMMENT, italic = true),
            // Line comments
            Rule(Regex(if (lang == CodeLanguage.PYTHON) "#[^\n]*" else "//[^\n]*"), COLOR_COMMENT, italic = true),
            // Triple-quoted strings (Kotlin/Python)
            Rule(Regex("\"\"\"[\\s\\S]*?\"\"\""), COLOR_STRING),
            // Double-quoted strings
            Rule(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING),
            // Single-quoted strings
            Rule(Regex("'(?:[^'\\\\]|\\\\.)*'"), COLOR_STRING),
            // Numbers
            Rule(Regex("\\b(0x[0-9a-fA-F]+|\\d+\\.?\\d*[fFdDlL]?)\\b"), COLOR_NUMBER),
            // Annotations / decorators
            Rule(Regex("@[\\w.]+"), COLOR_ANNOTATION),
            // Types (CamelCase identifiers)
            Rule(Regex("\\b[A-Z][a-zA-Z0-9]*\\b"), COLOR_TYPE),
            // Function/method calls
            Rule(Regex("\\b([a-z_][a-zA-Z0-9_]*)(?=\\s*\\()"), COLOR_FUNCTION),
            // Keywords
            Rule(kwRegex, COLOR_KEYWORD, bold = true),
        )

        // Python uses # for comments, so remove // rule for Python
        return if (lang == CodeLanguage.PYTHON) {
            base.filterNot { it.regex.pattern.startsWith("//") }
        } else base
    }

    private fun getKeywords(lang: CodeLanguage): List<String> = when (lang) {
        CodeLanguage.KOTLIN -> listOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "continue", "crossinline", "data", "delegate",
            "do", "dynamic", "else", "enum", "expect", "external", "false", "field", "file",
            "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
            "inline", "inner", "interface", "internal", "is", "it", "lateinit", "noinline",
            "null", "object", "open", "operator", "out", "override", "package", "param",
            "private", "property", "protected", "public", "receiver", "reified", "return",
            "sealed", "set", "setparam", "super", "suspend", "tailrec", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "value", "vararg", "var", "when",
            "where", "while"
        )
        CodeLanguage.JAVA -> listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "true", "try", "var", "void", "volatile", "while"
        )
        CodeLanguage.PYTHON -> listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
        )
        CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> listOf(
            "abstract", "any", "async", "await", "boolean", "break", "case", "catch",
            "class", "const", "continue", "debugger", "declare", "default", "delete",
            "do", "else", "enum", "export", "extends", "false", "finally", "for",
            "from", "function", "if", "implements", "import", "in", "instanceof",
            "interface", "keyof", "let", "module", "namespace", "new", "null", "number",
            "of", "package", "private", "protected", "public", "readonly", "return",
            "static", "string", "super", "switch", "symbol", "this", "throw", "true",
            "try", "type", "typeof", "undefined", "var", "void", "while", "with", "yield"
        )
        CodeLanguage.C, CodeLanguage.CPP -> listOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "inline", "int", "long", "namespace", "new", "nullptr", "operator",
            "private", "protected", "public", "register", "return", "short",
            "signed", "sizeof", "static", "struct", "switch", "template",
            "this", "throw", "try", "typedef", "union", "unsigned", "using",
            "virtual", "void", "volatile", "while", "class", "delete", "false",
            "true", "typename"
        )
        CodeLanguage.RUST -> listOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn",
            "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in",
            "let", "loop", "match", "mod", "move", "mut", "pub", "ref", "return",
            "self", "Self", "static", "struct", "super", "trait", "true", "type",
            "union", "unsafe", "use", "where", "while"
        )
        CodeLanguage.GO -> listOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch",
            "type", "var", "false", "true", "nil", "iota"
        )
        CodeLanguage.SQL -> listOf(
            "SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
            "ON", "AND", "OR", "NOT", "NULL", "IS", "IN", "LIKE", "BETWEEN",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "INSERT", "INTO",
            "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "INDEX",
            "DROP", "ALTER", "ADD", "COLUMN", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "UNIQUE", "DEFAULT", "CONSTRAINT", "AS", "DISTINCT",
            "COUNT", "SUM", "AVG", "MIN", "MAX", "CASE", "WHEN", "THEN", "ELSE", "END",
            "WITH", "UNION", "ALL", "EXCEPT", "INTERSECT", "EXISTS", "COALESCE"
        )
        CodeLanguage.GROOVY -> listOf(
            "abstract", "as", "assert", "break", "case", "catch", "class", "const",
            "continue", "def", "default", "do", "else", "enum", "extends", "false",
            "final", "finally", "for", "goto", "if", "implements", "import",
            "in", "instanceof", "interface", "new", "null", "package", "private",
            "protected", "public", "return", "static", "super", "switch", "this",
            "throw", "throws", "trait", "true", "try", "var", "void", "while"
        )
        else -> emptyList()
    }

    // ── XML / HTML / Markdown ─────────────────────────────────────────────────

    private fun highlightXmlOrMd(lang: CodeLanguage, text: String): AnnotatedString {
        if (lang == CodeLanguage.MARKDOWN) return highlightMarkdown(text)

        val xmlRules = listOf(
            Rule(Regex("<!--[\\s\\S]*?-->"), COLOR_COMMENT, italic = true),
            Rule(Regex("</?[\\w:.]+"), COLOR_TAG, bold = true),
            Rule(Regex("[\\w:.-]+(?=\\s*=)"), COLOR_ATTR),
            Rule(Regex("\"[^\"]*\"|'[^']*'"), COLOR_STRING),
            Rule(Regex("/>|>|<"), COLOR_TAG),
        )
        return applyRules(text, xmlRules)
    }

    private fun highlightMarkdown(text: String): AnnotatedString {
        val sb = AnnotatedString.Builder()
        val lines = text.split('\n')
        lines.forEachIndexed { i, line ->
            when {
                line.startsWith("# ")   -> sb.withStyle(SpanStyle(color = COLOR_TYPE, fontWeight = FontWeight.Bold)) { append(line) }
                line.startsWith("## ")  -> sb.withStyle(SpanStyle(color = COLOR_TYPE, fontWeight = FontWeight.Bold)) { append(line) }
                line.startsWith("### ") -> sb.withStyle(SpanStyle(color = COLOR_KEYWORD, fontWeight = FontWeight.Bold)) { append(line) }
                line.startsWith("```")  -> sb.withStyle(SpanStyle(color = COLOR_COMMENT)) { append(line) }
                line.startsWith("> ")   -> sb.withStyle(SpanStyle(color = COLOR_COMMENT, fontStyle = FontStyle.Italic)) { append(line) }
                line.startsWith("- ") || line.startsWith("* ") || line.matches(Regex("\\d+\\..*")) ->
                    sb.withStyle(SpanStyle(color = COLOR_ANNOTATION)) { append(line) }
                else -> sb.append(line)
            }
            if (i < lines.size - 1) sb.append('\n')
        }
        return sb.toAnnotatedString()
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    private fun highlightJson(text: String): AnnotatedString {
        val rules = listOf(
            Rule(Regex("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:"), COLOR_TYPE),   // keys
            Rule(Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\""), COLOR_STRING),  // string values
            Rule(Regex("\\b(true|false|null)\\b"), COLOR_KEYWORD, bold = true),
            Rule(Regex("\\b-?\\d+\\.?\\d*([eE][+-]?\\d+)?\\b"), COLOR_NUMBER),
            Rule(Regex("[{}\\[\\],:]"), COLOR_OPERATOR),
        )
        return applyRules(text, rules)
    }

    // ── YAML ──────────────────────────────────────────────────────────────────

    private fun highlightYaml(text: String): AnnotatedString {
        val rules = listOf(
            Rule(Regex("#[^\n]*"), COLOR_COMMENT, italic = true),
            Rule(Regex("^[\\w./-]+\\s*:", RegexOption.MULTILINE), COLOR_TYPE),
            Rule(Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'"), COLOR_STRING),
            Rule(Regex("\\b(true|false|null|yes|no|on|off)\\b"), COLOR_KEYWORD),
            Rule(Regex("\\b-?\\d+\\.?\\d*\\b"), COLOR_NUMBER),
            Rule(Regex("^\\s*-"), COLOR_OPERATOR),
            Rule(Regex("^---"), COLOR_OPERATOR),
        )
        return applyRules(text, rules)
    }

    // ── Shell ─────────────────────────────────────────────────────────────────

    private fun highlightShell(text: String): AnnotatedString {
        val rules = listOf(
            Rule(Regex("#[^\n]*"), COLOR_COMMENT, italic = true),
            Rule(Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|`[^`]*`"), COLOR_STRING),
            Rule(Regex("\\$\\{?[\\w@#?*!-]+}?"), COLOR_ANNOTATION),
            Rule(Regex("\\b(if|then|else|elif|fi|for|do|done|while|until|case|esac|in|function|return|exit|echo|export|local|source|set|shift|trap|readonly)\\b"), COLOR_KEYWORD, bold = true),
            Rule(Regex("\\b\\d+\\b"), COLOR_NUMBER),
            Rule(Regex("[|&;><(){}]"), COLOR_OPERATOR),
        )
        return applyRules(text, rules)
    }

    // ── Core: apply rules with span tracking ─────────────────────────────────

    private fun applyRules(text: String, rules: List<Rule>): AnnotatedString {
        // Collect all (start, end, Rule) non-overlapping spans, first-match wins
        data class Span(val start: Int, val end: Int, val rule: Rule)
        val spans = mutableListOf<Span>()
        val covered = BooleanArray(text.length)

        for (rule in rules) {
            for (match in rule.regex.findAll(text)) {
                val s = match.range.first
                val e = match.range.last + 1
                if ((s until e).any { covered[it] }) continue
                spans.add(Span(s, e, rule))
                for (i in s until e) covered[i] = true
            }
        }
        spans.sortBy { it.start }

        val sb = AnnotatedString.Builder()
        var pos = 0
        for (span in spans) {
            if (span.start > pos) sb.append(text.substring(pos, span.start))
            val fw = if (span.rule.bold) FontWeight.Bold else null
            val fs = if (span.rule.italic) FontStyle.Italic else null
            sb.withStyle(SpanStyle(color = span.rule.color, fontWeight = fw, fontStyle = fs)) {
                append(text.substring(span.start, span.end))
            }
            pos = span.end
        }
        if (pos < text.length) sb.append(text.substring(pos))
        return sb.toAnnotatedString()
    }
}
