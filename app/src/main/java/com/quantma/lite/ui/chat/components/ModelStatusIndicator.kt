package com.quantma.lite.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantma.lite.R
import com.quantma.lite.ui.chat.ModelStatus

@Composable
fun ModelStatusIndicator(
    status: ModelStatus,
    tokensPerSecond: Float = 0f,
    lastGenTps: Float = 0f,
    thermalStatus: Int = 0,
    availRamMb: Long = 0L,
    totalRamMb: Long = 0L,
    backendInfo: String = "CPU only",
    sessionTokensTotal: Int = 0,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (status) {
        ModelStatus.NOT_LOADED -> Color.Gray to stringResource(R.string.status_no_model)
        ModelStatus.LOADING -> Color.Yellow to stringResource(R.string.status_loading)
        ModelStatus.READY -> {
            if (lastGenTps > 0f) {
                Color.Green to stringResource(R.string.status_tok_s, lastGenTps)
            } else {
                Color.Green to stringResource(R.string.status_ready)
            }
        }
        ModelStatus.GENERATING -> {
            if (tokensPerSecond > 0f) {
                Color.Cyan to stringResource(R.string.status_tok_s, tokensPerSecond)
            } else {
                Color.Cyan to stringResource(R.string.status_generating)
            }
        }
        ModelStatus.ERROR -> Color.Red to stringResource(R.string.status_error)
    }

    val thermalColor = when (thermalStatus) {
        0 -> Color.Green
        1 -> Color(0xFFFFD700) // Yellow/Warm
        2 -> Color(0xFFFFA500) // Orange/Hot
        3 -> Color(0xFFFF4500) // OrangeRed/Very Hot
        else -> Color.Red      // Critical (4+)
    }

    val modelLoaded = status != ModelStatus.NOT_LOADED && status != ModelStatus.ERROR

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Circle,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(8.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (modelLoaded) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = null,
                tint = thermalColor,
                modifier = Modifier.size(12.dp)
            )

            if (totalRamMb > 0L) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                val availGb = availRamMb / 1024f
                val totalGb = totalRamMb / 1024f
                Text(
                    text = "%.1f/%.1fG".format(availGb, totalGb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Backend badge: HEX / VK / CPU
            val badge = when {
                backendInfo.contains("Hexagon", ignoreCase = true) || backendInfo.contains("HTP", ignoreCase = true) -> "HEX"
                backendInfo.contains("Vulkan", ignoreCase = true)  -> "VK"
                else -> null
            }
            if (badge != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4FC3F7)
                )
            }

            // Session token counter
            if (sessionTokensTotal > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${sessionTokensTotal}t",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
