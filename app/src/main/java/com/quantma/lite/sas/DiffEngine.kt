package com.quantma.lite.sas

import kotlin.math.max
import kotlin.math.min

/**
 * Secure Agent Shell — Diff Engine.
 *
 * Computes line-by-line diffs using the Myers algorithm (LCS-based).
 * No external dependencies — pure Kotlin implementation.
 *
 * Output formats:
 * - Unified diff (for LLM context and terminal display)
 * - HTML diff (for WebView/Compose rendering with color highlighting)
 * - Statistics (additions/deletions count)
 */
class DiffEngine {

    /**
     * Compute a unified diff between old and new content.
     *
     * @param oldContent Original file content (empty string for new files)
     * @param newContent Modified file content
     * @param fileName File name for diff header (default: "file")
     * @param contextLines Number of unchanged lines to show around changes (default: 3)
     * @return Unified diff string, or empty string if contents are identical
     */
    fun computeUnifiedDiff(
        oldContent: String,
        newContent: String,
        fileName: String = "file",
        contextLines: Int = 3
    ): String {
        if (oldContent == newContent) return ""

        val oldLines = oldContent.lines()
        val newLines = newContent.lines()

        val edits = computeEdits(oldLines, newLines)
        if (edits.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("--- a/$fileName")
        sb.appendLine("+++ b/$fileName")

        // Group edits into hunks with context
        val hunks = groupIntoHunks(edits, oldLines.size, newLines.size, contextLines)

        for (hunk in hunks) {
            sb.appendLine(hunk.header())
            for (line in hunk.lines) {
                sb.appendLine(line)
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Compute an HTML-formatted diff for WebView or Compose rendering.
     *
     * Output uses CSS classes for styling:
     * - `.diff-add` — green background for added lines
     * - `.diff-del` — red background for deleted lines
     * - `.diff-info` — blue for @@ hunk headers
     * - `.diff-ctx` — default for context lines
     *
     * @return Complete HTML document string, or empty if no changes
     */
    fun computeHtmlDiff(
        oldContent: String,
        newContent: String,
        fileName: String = "file",
        contextLines: Int = 3
    ): String {
        val unifiedDiff = computeUnifiedDiff(oldContent, newContent, fileName, contextLines)
        if (unifiedDiff.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine(HTML_DIFF_HEADER)
        sb.appendLine("<div class=\"diff-container\">")
        sb.appendLine("<pre class=\"diff-content\">")

        for (line in unifiedDiff.lines()) {
            val escaped = escapeHtml(line)
            when {
                line.startsWith("+++") || line.startsWith("---") ->
                    sb.appendLine("<span class=\"diff-file\">$escaped</span>")
                line.startsWith("@@") ->
                    sb.appendLine("<span class=\"diff-info\">$escaped</span>")
                line.startsWith("+") ->
                    sb.appendLine("<span class=\"diff-add\">$escaped</span>")
                line.startsWith("-") ->
                    sb.appendLine("<span class=\"diff-del\">$escaped</span>")
                else ->
                    sb.appendLine("<span class=\"diff-ctx\">$escaped</span>")
            }
        }

        sb.appendLine("</pre>")
        sb.appendLine("</div>")
        sb.appendLine(HTML_DIFF_FOOTER)

        return sb.toString()
    }

    /**
     * Compute diff statistics (additions and deletions).
     */
    fun computeStats(oldContent: String, newContent: String): DiffStats {
        if (oldContent == newContent) return DiffStats(0, 0)

        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val edits = computeEdits(oldLines, newLines)

        var additions = 0
        var deletions = 0
        for (edit in edits) {
            when (edit) {
                is Edit.Insert -> additions++
                is Edit.Delete -> deletions++
                is Edit.Equal -> { /* no-op */ }
            }
        }
        return DiffStats(additions, deletions)
    }

    // ─── Myers Diff Algorithm ───────────────────────────────────────────────

    /**
     * Compute the edit sequence between two lists of lines using Myers diff.
     * Returns a list of [Edit] operations (Equal, Insert, Delete).
     */
    private fun computeEdits(oldLines: List<String>, newLines: List<String>): List<Edit> {
        val n = oldLines.size
        val m = newLines.size
        val max = n + m

        if (max == 0) return emptyList()

        // Special cases for better performance
        if (n == 0) return newLines.mapIndexed { i, line -> Edit.Insert(i, line) }
        if (m == 0) return oldLines.mapIndexed { i, line -> Edit.Delete(i, line) }

        // Myers algorithm: find shortest edit script
        // V[k] = farthest reaching point on diagonal k
        val vSize = 2 * max + 1
        val v = IntArray(vSize) { -1 }
        v[max + 1] = 0 // offset by max to allow negative indices

        // Store trace for backtracking
        val trace = mutableListOf<IntArray>()

        for (d in 0..max) {
            val snapshot = v.copyOf()
            trace.add(snapshot)

            for (k in -d..d step 2) {
                val kIdx = k + max

                // Decide whether to go down or right
                val x: Int = if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                    v[kIdx + 1]  // move down (insert)
                } else {
                    v[kIdx - 1] + 1  // move right (delete)
                }

                var curX = x
                var curY = curX - k

                // Follow diagonal (equal lines)
                while (curX < n && curY < m && oldLines[curX] == newLines[curY]) {
                    curX++
                    curY++
                }

                v[kIdx] = curX

                if (curX >= n && curY >= m) {
                    // Found the shortest edit script, backtrack to build edits
                    return backtrack(trace, oldLines, newLines, d, max)
                }
            }
        }

        // Fallback: shouldn't happen with correct algorithm
        return emptyList()
    }

    /**
     * Backtrack through the trace to reconstruct the edit sequence.
     */
    private fun backtrack(
        trace: List<IntArray>,
        oldLines: List<String>,
        newLines: List<String>,
        finalD: Int,
        max: Int
    ): List<Edit> {
        val edits = mutableListOf<Edit>()

        var x = oldLines.size
        var y = newLines.size

        for (d in finalD downTo 0) {
            val v = trace[d]
            val k = x - y
            val kIdx = k + max

            val prevK: Int = if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                k + 1  // came from above (insert)
            } else {
                k - 1  // came from left (delete)
            }

            val prevX = v[prevK + max]
            val prevY = prevX - prevK

            // Add diagonal (equal) edits
            while (x > prevX && y > prevY) {
                x--
                y--
                edits.add(0, Edit.Equal(x, oldLines[x]))
            }

            if (d > 0) {
                if (x == prevX) {
                    // Insert: y decreased
                    y--
                    edits.add(0, Edit.Insert(y, newLines[y]))
                } else {
                    // Delete: x decreased
                    x--
                    edits.add(0, Edit.Delete(x, oldLines[x]))
                }
            }
        }

        return edits
    }

    // ─── Hunk Generation ────────────────────────────────────────────────────

    /**
     * Group edit operations into hunks with context lines.
     */
    private fun groupIntoHunks(
        edits: List<Edit>,
        oldSize: Int,
        newSize: Int,
        contextLines: Int
    ): List<Hunk> {
        if (edits.isEmpty()) return emptyList()

        // Find ranges of changes (non-Equal edits)
        data class ChangeRange(val startIdx: Int, val endIdx: Int)

        val changeRanges = mutableListOf<ChangeRange>()
        var i = 0
        while (i < edits.size) {
            if (edits[i] !is Edit.Equal) {
                val start = i
                while (i < edits.size && edits[i] !is Edit.Equal) i++
                changeRanges.add(ChangeRange(start, i - 1))
            } else {
                i++
            }
        }

        if (changeRanges.isEmpty()) return emptyList()

        // Merge nearby change ranges and build hunks
        val hunks = mutableListOf<Hunk>()
        var rangeIdx = 0

        while (rangeIdx < changeRanges.size) {
            val firstRange = changeRanges[rangeIdx]
            var lastRange = firstRange

            // Merge adjacent ranges that overlap within context
            while (rangeIdx + 1 < changeRanges.size) {
                val nextRange = changeRanges[rangeIdx + 1]
                val gapBetween = nextRange.startIdx - lastRange.endIdx - 1
                if (gapBetween <= contextLines * 2) {
                    lastRange = nextRange
                    rangeIdx++
                } else {
                    break
                }
            }
            rangeIdx++

            // Build hunk with context
            val hunkStartIdx = max(0, firstRange.startIdx - contextLines)
            val hunkEndIdx = min(edits.size - 1, lastRange.endIdx + contextLines)

            val hunkLines = mutableListOf<String>()
            var oldStart = -1
            var oldCount = 0
            var newStart = -1
            var newCount = 0

            for (j in hunkStartIdx..hunkEndIdx) {
                val edit = edits[j]
                when (edit) {
                    is Edit.Equal -> {
                        if (oldStart == -1) {
                            oldStart = edit.lineNum
                            newStart = findNewLineNum(edits, j)
                        }
                        hunkLines.add(" ${edit.content}")
                        oldCount++
                        newCount++
                    }
                    is Edit.Delete -> {
                        if (oldStart == -1) {
                            oldStart = edit.lineNum
                            newStart = findNewLineNum(edits, j)
                        }
                        hunkLines.add("-${edit.content}")
                        oldCount++
                    }
                    is Edit.Insert -> {
                        if (oldStart == -1) {
                            oldStart = findOldLineNum(edits, j)
                            newStart = edit.lineNum
                        }
                        hunkLines.add("+${edit.content}")
                        newCount++
                    }
                }
            }

            hunks.add(
                Hunk(
                    oldStart = oldStart + 1,  // 1-indexed
                    oldCount = oldCount,
                    newStart = newStart + 1,  // 1-indexed
                    newCount = newCount,
                    lines = hunkLines
                )
            )
        }

        return hunks
    }

    /** Find the corresponding new-file line number at a given edit index. */
    private fun findNewLineNum(edits: List<Edit>, index: Int): Int {
        var newLine = 0
        for (j in 0 until index) {
            when (edits[j]) {
                is Edit.Equal -> newLine++
                is Edit.Insert -> newLine++
                is Edit.Delete -> { /* no-op */ }
            }
        }
        return newLine
    }

    /** Find the corresponding old-file line number at a given edit index. */
    private fun findOldLineNum(edits: List<Edit>, index: Int): Int {
        var oldLine = 0
        for (j in 0 until index) {
            when (edits[j]) {
                is Edit.Equal -> oldLine++
                is Edit.Delete -> oldLine++
                is Edit.Insert -> { /* no-op */ }
            }
        }
        return oldLine
    }

    // ─── HTML Helpers ────────────────────────────────────────────────────────

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val HTML_DIFF_HEADER = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { margin: 0; padding: 8px; background: #1e1e2e; color: #cdd6f4; font-family: monospace; }
.diff-container { border-radius: 8px; overflow: hidden; background: #181825; }
.diff-content { margin: 0; padding: 12px; font-size: 13px; line-height: 1.5; overflow-x: auto; white-space: pre; }
.diff-content span { display: block; padding: 1px 8px; border-radius: 2px; }
.diff-add { background: rgba(166, 227, 161, 0.15); color: #a6e3a1; }
.diff-del { background: rgba(243, 139, 168, 0.15); color: #f38ba8; }
.diff-info { background: rgba(137, 180, 250, 0.10); color: #89b4fa; font-style: italic; }
.diff-file { color: #f9e2af; font-weight: bold; }
.diff-ctx { color: #6c7086; }
</style></head><body>"""

        private const val HTML_DIFF_FOOTER = "</body></html>"
    }

    // ─── Internal Models ────────────────────────────────────────────────────

    /** A single edit operation. */
    private sealed class Edit {
        data class Equal(val lineNum: Int, val content: String) : Edit()
        data class Insert(val lineNum: Int, val content: String) : Edit()
        data class Delete(val lineNum: Int, val content: String) : Edit()
    }

    /** A diff hunk with header and lines. */
    private data class Hunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val lines: List<String>
    ) {
        fun header(): String = "@@ -$oldStart,$oldCount +$newStart,$newCount @@"
    }
}
