package com.quantma.lite.ui.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.quantma.lite.ui.theme.CodeBlockText

// VS Code Dark+ inspired token colors
private val ColorComment    = Color(0xFF6A9955)
private val ColorString     = Color(0xFFCE9178)
private val ColorKeyword    = Color(0xFF569CD6)
private val ColorNumber     = Color(0xFFB5CEA8)
private val ColorAnnotation = Color(0xFFDCDCAA)

private val keywordsKotlin = setOf(
    "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
    "companion", "const", "constructor", "continue", "crossinline", "data", "delegate",
    "do", "dynamic", "else", "enum", "expect", "external", "false", "field", "file",
    "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
    "inline", "inner", "interface", "internal", "is", "it", "lateinit", "noinline",
    "null", "object", "open", "operator", "out", "override", "package", "param",
    "private", "property", "protected", "public", "receiver", "reified", "return",
    "sealed", "set", "super", "suspend", "tailrec", "this", "throw", "true", "try",
    "typealias", "typeof", "val", "value", "var", "vararg", "when", "where", "while"
)

private val keywordsJava = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "false",
    "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
    "int", "interface", "long", "native", "new", "null", "package", "private", "protected",
    "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
    "this", "throw", "throws", "transient", "true", "try", "var", "void", "volatile", "while"
)

private val keywordsPython = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
    "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
    "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
    "raise", "return", "try", "while", "with", "yield"
)

private val keywordsBash = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
    "in", "function", "return", "exit", "echo", "export", "local", "readonly", "shift",
    "source", "alias", "cd", "ls", "mkdir", "rm", "cp", "mv", "cat", "grep", "find",
    "chmod", "chown", "sudo", "git", "curl", "wget", "sed", "awk", "true", "false"
)

private val keywordsCpp = setOf(
    "auto", "bool", "break", "case", "catch", "char", "class", "const", "constexpr",
    "continue", "default", "delete", "do", "double", "else", "enum", "explicit",
    "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int",
    "long", "mutable", "namespace", "new", "noexcept", "nullptr", "operator",
    "private", "protected", "public", "return", "short", "signed", "sizeof", "static",
    "struct", "switch", "template", "this", "throw", "true", "try", "typedef",
    "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "while"
)

private val keywordsJs = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue",
    "debugger", "default", "delete", "do", "else", "export", "extends", "false",
    "finally", "for", "from", "function", "if", "import", "in", "instanceof", "let",
    "new", "null", "of", "return", "static", "super", "switch", "this", "throw",
    "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
)

private fun getKeywords(lang: String): Set<String> = when (lang) {
    "kotlin", "kts" -> keywordsKotlin
    "java" -> keywordsJava
    "python", "py" -> keywordsPython
    "bash", "sh", "shell", "zsh" -> keywordsBash
    "cpp", "c++", "c", "cc", "cxx" -> keywordsCpp
    "javascript", "js", "typescript", "ts", "jsx", "tsx" -> keywordsJs
    else -> emptySet()
}

private fun isHashCommentLang(lang: String) =
    lang in setOf("python", "py", "bash", "sh", "shell", "zsh", "ruby", "rb")

/**
 * Syntax-highlight [code] for the given [language].
 * Returns a plain AnnotatedString for unknown languages.
 */
fun highlightCode(code: String, language: String): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString(code)

    val lang = language.trim().lowercase()

    return when {
        lang == "json" -> highlightJson(code)
        lang == "xml" || lang == "html" -> highlightXml(code)
        else -> highlightGeneric(code, lang)
    }
}

// ──────────────────────────────────────────────────────────────
// Generic highlighter (Kotlin, Java, Python, Bash, C/C++, JS/TS)
// ──────────────────────────────────────────────────────────────
private fun highlightGeneric(code: String, lang: String): AnnotatedString {
    val keywords = getKeywords(lang)
    val hashComments = isHashCommentLang(lang)

    return buildAnnotatedString {
        fun colored(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text) }
        fun plain(text: String) = withStyle(SpanStyle(color = CodeBlockText)) { append(text) }

        var pos = 0
        val len = code.length

        while (pos < len) {
            val rem = code.substring(pos)

            // Triple-quoted strings (Kotlin / Python)
            if (rem.startsWith("\"\"\"") || rem.startsWith("'''")) {
                val delim = rem.substring(0, 3)
                val end = rem.indexOf(delim, 3)
                val str = if (end != -1) rem.substring(0, end + 3) else rem
                colored(str, ColorString); pos += str.length; continue
            }

            // Block comment /* … */
            if (rem.startsWith("/*")) {
                val end = rem.indexOf("*/")
                val s = if (end != -1) rem.substring(0, end + 2) else rem
                colored(s, ColorComment); pos += s.length; continue
            }

            // Line comment //
            if (!hashComments && rem.startsWith("//")) {
                val nl = rem.indexOf('\n')
                val s = if (nl != -1) rem.substring(0, nl) else rem
                colored(s, ColorComment); pos += s.length; continue
            }

            // Hash comment #
            if (hashComments && rem[0] == '#') {
                val nl = rem.indexOf('\n')
                val s = if (nl != -1) rem.substring(0, nl) else rem
                colored(s, ColorComment); pos += s.length; continue
            }

            // Double-quoted string
            if (rem[0] == '"') {
                var i = 1
                while (i < rem.length && rem[i] != '"') { if (rem[i] == '\\') i++; i++ }
                if (i < rem.length) i++
                colored(rem.substring(0, i), ColorString); pos += i; continue
            }

            // Single-quoted string
            if (rem[0] == '\'') {
                var i = 1
                while (i < rem.length && rem[i] != '\'') { if (rem[i] == '\\') i++; i++ }
                if (i < rem.length) i++
                colored(rem.substring(0, i), ColorString); pos += i; continue
            }

            // Annotation / decorator @Ident
            if (rem[0] == '@' && rem.length > 1 && rem[1].isLetter()) {
                var i = 1
                while (i < rem.length && (rem[i].isLetterOrDigit() || rem[i] == '_')) i++
                colored(rem.substring(0, i), ColorAnnotation); pos += i; continue
            }

            // Number (decimal, hex, float)
            if (rem[0].isDigit()) {
                var i = 0
                if (rem.startsWith("0x", ignoreCase = true)) {
                    i = 2; while (i < rem.length && rem[i].isLetterOrDigit()) i++
                } else {
                    while (i < rem.length && (rem[i].isDigit() || rem[i] == '.' ||
                            rem[i].lowercaseChar() in "efldu_")) i++
                }
                if (i == 0) i = 1
                colored(rem.substring(0, i), ColorNumber); pos += i; continue
            }

            // Identifier / keyword
            if (rem[0].isLetter() || rem[0] == '_') {
                var i = 0
                while (i < rem.length && (rem[i].isLetterOrDigit() || rem[i] == '_')) i++
                val word = rem.substring(0, i)
                if (word in keywords) colored(word, ColorKeyword) else plain(word)
                pos += i; continue
            }

            // Anything else — single character
            plain(rem[0].toString()); pos++
        }
    }
}

// ──────────────────────────────────────────────────────────────
// JSON highlighter
// ──────────────────────────────────────────────────────────────
private val ColorJsonKey = Color(0xFF9CDCFE)  // light blue

private fun highlightJson(code: String): AnnotatedString = buildAnnotatedString {
    fun colored(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text) }
    fun plain(text: String) = withStyle(SpanStyle(color = CodeBlockText)) { append(text) }

    var pos = 0
    val len = code.length

    while (pos < len) {
        val ch = code[pos]
        when {
            // String — key or value
            ch == '"' -> {
                var i = pos + 1
                while (i < len && code[i] != '"') { if (code[i] == '\\') i++; i++ }
                if (i < len) i++ // closing "
                val str = code.substring(pos, i)
                // Peek past whitespace to see if next non-ws char is ':'
                var j = i
                while (j < len && code[j] in " \t\r\n") j++
                if (j < len && code[j] == ':') colored(str, ColorJsonKey)
                else colored(str, ColorString)
                pos = i
            }
            // Number
            ch.isDigit() || (ch == '-' && pos + 1 < len && code[pos + 1].isDigit()) -> {
                var i = pos
                if (code[i] == '-') i++
                while (i < len && (code[i].isDigit() || code[i] == '.' ||
                        code[i].lowercaseChar() in "e+-")) i++
                colored(code.substring(pos, i), ColorNumber); pos = i
            }
            // true / false / null
            ch == 't' && code.startsWith("true", pos) -> { colored("true", ColorKeyword); pos += 4 }
            ch == 'f' && code.startsWith("false", pos) -> { colored("false", ColorKeyword); pos += 5 }
            ch == 'n' && code.startsWith("null", pos) -> { colored("null", ColorKeyword); pos += 4 }
            else -> { plain(ch.toString()); pos++ }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// XML / HTML highlighter
// ──────────────────────────────────────────────────────────────
private val ColorXmlTag   = Color(0xFF4EC9B0)
private val ColorXmlAttr  = Color(0xFF9CDCFE)

private fun highlightXml(code: String): AnnotatedString = buildAnnotatedString {
    fun colored(text: String, color: Color) = withStyle(SpanStyle(color = color)) { append(text) }
    fun plain(text: String) = withStyle(SpanStyle(color = CodeBlockText)) { append(text) }

    val tagRegex = Regex("<[^>]*>")
    val attrRegex = Regex("""(\s[\w:-]+)(="[^"]*")?""")
    var pos = 0

    tagRegex.findAll(code).forEach { m ->
        if (m.range.first > pos) plain(code.substring(pos, m.range.first))
        val tag = m.value
        // Color tag name teal, attributes light blue
        var tagPos = 0
        val nameEnd = tag.indexOfFirst { it == ' ' || it == '>' || it == '/' }.takeIf { it > 0 } ?: tag.length
        colored(tag.substring(0, nameEnd), ColorXmlTag)
        tagPos = nameEnd
        attrRegex.findAll(tag, tagPos).forEach { a ->
            colored(a.value, ColorXmlAttr)
            tagPos = a.range.last + 1
        }
        if (tagPos < tag.length) colored(tag.substring(tagPos), ColorXmlTag)
        pos = m.range.last + 1
    }
    if (pos < code.length) plain(code.substring(pos))
}
