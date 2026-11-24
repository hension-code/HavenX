package com.hension.havenx.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector
import com.hension.havenx.R

enum class Screen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Connections("connections", R.string.nav_connections, Icons.Filled.Cable, Icons.Outlined.Cable),
    Terminal("terminal", R.string.nav_terminal, Icons.Filled.Terminal, Icons.Outlined.Terminal),
    Desktop("desktop", R.string.nav_desktop, Icons.Filled.DesktopWindows, Icons.Outlined.DesktopWindows),
    Sftp("sftp", R.string.nav_sftp, Icons.Filled.Folder, Icons.Outlined.Folder),
    Keys("keys", R.string.nav_keys, Icons.Filled.VpnKey, Icons.Outlined.VpnKey),
    Settings("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
