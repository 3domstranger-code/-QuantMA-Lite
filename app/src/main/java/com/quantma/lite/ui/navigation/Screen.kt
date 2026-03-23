package com.quantma.lite.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    data object Files : Screen("files", "Files", Icons.Default.Folder)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
