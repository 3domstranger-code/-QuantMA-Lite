package com.quantma.lite.ui.git.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantma.lite.R
import com.quantma.lite.domain.model.GitDiffEntry

@Composable
fun DiffViewer(
    entries: List<GitDiffEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_diff),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            entries.forEach { entry ->
                item(key = "header_${entry.filePath}") {
                    DiffFileHeader(entry)
                }
                val lines = entry.unified.lines()
                items(lines, key = { "${entry.filePath}_${lines.indexOf(it)}_$it" }) { line ->
                    DiffLine(line)
                }
            }
        }
    }
}

@Composable
private fun DiffFileHeader(entry: GitDiffEntry) {
    Text(
        text = "── ${entry.filePath}  [${entry.changeType}]",
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun DiffLine(line: String) {
    val (bg, fg) = when {
        line.startsWith("+") && !line.startsWith("+++") ->
            Color(0xFF1B5E20) to Color(0xFFB9F6CA)
        line.startsWith("-") && !line.startsWith("---") ->
            Color(0xFF4E0808) to Color(0xFFFFCDD2)
        line.startsWith("@@") ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        ),
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 8.dp)
    )
}
