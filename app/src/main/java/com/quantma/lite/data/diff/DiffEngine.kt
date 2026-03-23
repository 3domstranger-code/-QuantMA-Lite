package com.quantma.lite.data.diff

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType

enum class DiffType { ADDED, REMOVED, UNCHANGED }

data class DiffLine(
    val type: DiffType,
    val text: String,
    val lineNum: Int? = null
)

/**
 * Computes a Myers diff between two text strings.
 * Returns a list of DiffLine objects (ADDED, REMOVED, UNCHANGED).
 * Phase 6 (v1.5.0)
 */
object DiffEngine {

    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        if (oldText == newText) {
            return oldText.lines().mapIndexed { i, line ->
                DiffLine(DiffType.UNCHANGED, line, i + 1)
            }
        }

        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val patch = DiffUtils.diff(oldLines, newLines)

        val result = mutableListOf<DiffLine>()
        var oldIdx = 0
        var newIdx = 0

        for (delta in patch.deltas) {
            val srcPos = delta.source.position

            // Emit unchanged lines before this delta
            while (oldIdx < srcPos) {
                result.add(DiffLine(DiffType.UNCHANGED, oldLines[oldIdx], newIdx + 1))
                oldIdx++
                newIdx++
            }

            // In java-diff-utils, Chunk.getLines() maps to Kotlin property .lines (no parens)
            when (delta.type) {
                DeltaType.DELETE -> {
                    for (line in delta.source.lines) {
                        result.add(DiffLine(DiffType.REMOVED, line))
                        oldIdx++
                    }
                }
                DeltaType.INSERT -> {
                    for (line in delta.target.lines) {
                        result.add(DiffLine(DiffType.ADDED, line))
                        newIdx++
                    }
                }
                DeltaType.CHANGE -> {
                    for (line in delta.source.lines) {
                        result.add(DiffLine(DiffType.REMOVED, line))
                        oldIdx++
                    }
                    for (line in delta.target.lines) {
                        result.add(DiffLine(DiffType.ADDED, line))
                        newIdx++
                    }
                }
                DeltaType.EQUAL -> {
                    for (line in delta.source.lines) {
                        result.add(DiffLine(DiffType.UNCHANGED, line, newIdx + 1))
                        oldIdx++
                        newIdx++
                    }
                }
            }
        }

        // Remaining unchanged lines after last delta
        while (oldIdx < oldLines.size) {
            result.add(DiffLine(DiffType.UNCHANGED, oldLines[oldIdx], newIdx + 1))
            oldIdx++
            newIdx++
        }

        return result
    }
}
