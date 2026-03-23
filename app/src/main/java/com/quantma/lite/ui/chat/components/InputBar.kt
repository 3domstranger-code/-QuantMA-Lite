package com.quantma.lite.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quantma.lite.R

@Composable
fun InputBar(
    onSend: (String) -> Unit,
    onStop: () -> Unit = {},
    isGenerating: Boolean = false,
    isAgentMode: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }

    // Agent mode glow animation
    val glowAlpha = if (isAgentMode) {
        val transition = rememberInfiniteTransition(label = "agentGlow")
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        ).value
    } else 0f

    val borderColor by animateColorAsState(
        targetValue = if (isAgentMode)
            MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
        else
            Color.Transparent,
        label = "borderColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isAgentMode) Modifier.border(
                            width = 1.5.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(24.dp)
                        ) else Modifier
                    ),
                placeholder = {
                    Text(
                        if (isAgentMode) stringResource(R.string.input_agent_placeholder)
                        else stringResource(R.string.input_placeholder),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                enabled = enabled,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isAgentMode)
                        Color.Transparent
                    else
                        MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (isGenerating) {
                // Stop button
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 4.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.stop),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // Send button
                FilledIconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        }
                    },
                    enabled = enabled && text.isNotBlank(),
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 4.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
