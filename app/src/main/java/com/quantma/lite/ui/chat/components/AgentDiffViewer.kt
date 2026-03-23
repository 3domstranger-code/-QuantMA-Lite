package com.quantma.lite.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantma.lite.data.diff.DiffLine
import com.quantma.lite.data.diff.DiffType

private val AddedBg     = Color(0x3300CC44)
private val RemovedBg   = Color(0x33CC2200)
private val AddedText   = Color(0xFF66DD77)
private val RemovedText = Color(0xFFEE6655)
private val UnchangedText = Color(0xFFBBBBBB)

/**
 * Composable that renders a computed diff (List<DiffLine>) with colored lines.
 * Green for additions, red for removals, neutral for unchanged.
 * Phase 6 (v1.5.0)
 */
@Composable
fun AgentDiffViewer(
    lines: List<DiffLine>,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(lines) { diffLine ->
            val bgColor = when (diffLine.type) {
                DiffType.ADDED     -> AddedBg
                DiffType.REMOVED   -> RemovedBg
                DiffType.UNCHANGED -> Color.Transparent
            }
            val textColor = when (diffLine.type) {
                DiffType.ADDED     -> AddedText
                DiffType.REMOVED   -> RemovedText
                DiffType.UNCHANGED -> UnchangedText
            }
            val prefix = when (diffLine.type) {
                DiffType.ADDED     -> "+"
                DiffType.REMOVED   -> "-"
                DiffType.UNCHANGED -> " "
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Prefix symbol column
                Text(
                    text = prefix,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textColor,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .width(14.dp)
                        .padding(end = 2.dp)
                )
                // Line content
                Text(
                    text = diffLine.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = textColor,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
