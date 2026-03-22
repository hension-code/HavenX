package com.hension.havenx.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector
import com.hension.havenx.R

enum class Screen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Connections("connections", R.string.nav_connections, Icons.Filled.Cable),
    Terminal("terminal", R.string.nav_terminal, Icons.Filled.Terminal),
    Desktop("desktop", R.string.nav_desktop, Icons.Filled.DesktopWindows),
    Sftp("sftp", R.string.nav_sftp, Icons.Filled.Folder),
    Keys("keys", R.string.nav_keys, Icons.Filled.VpnKey),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings),
}
