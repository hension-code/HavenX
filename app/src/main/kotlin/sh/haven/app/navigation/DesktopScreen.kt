package com.hension.havenx.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.haven.core.data.preferences.ToolbarLayout
import sh.haven.feature.rdp.RdpScreen
import sh.haven.feature.rdp.RdpViewModel
import sh.haven.feature.vnc.VncScreen
import sh.haven.feature.vnc.VncViewModel

/**
 * Combined desktop screen with VNC/RDP protocol tabs.
 * When connected, the tab bar is hidden and the active viewer fills the screen.
 */
@Composable
fun DesktopScreen(
    isActive: Boolean = true,
    pendingVncHost: String? = null,
    pendingVncPort: Int? = null,
    pendingVncPassword: String? = null,
    pendingVncSshForward: Boolean = false,
    pendingVncSshSessionId: String? = null,
    pendingRdpHost: String? = null,
    pendingRdpPort: Int? = null,
    pendingRdpUsername: String? = null,
    pendingRdpPassword: String? = null,
    pendingRdpDomain: String? = null,
    pendingRdpSshForward: Boolean = false,
    pendingRdpSshSessionId: String? = null,
    pendingRdpSshProfileId: String? = null,
    toolbarLayout: ToolbarLayout = ToolbarLayout.DEFAULT,
    onPendingConsumed: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
) {
    // 0 = VNC, 1 = RDP — persisted across recompositions
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(pendingVncHost) {
        if (pendingVncHost != null) selectedTab = 0
    }
    LaunchedEffect(pendingRdpHost) {
        if (pendingRdpHost != null) selectedTab = 1
    }

    val vncViewModel: VncViewModel = hiltViewModel()
    val rdpViewModel: RdpViewModel = hiltViewModel()
    val vncConnected by vncViewModel.connected.collectAsState()
    val rdpConnected by rdpViewModel.connected.collectAsState()
    val anyConnected = vncConnected || rdpConnected

    if (vncConnected) selectedTab = 0
    if (rdpConnected) selectedTab = 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Modern pill-style protocol switcher — hidden when connected
        if (!anyConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DesktopProtocolPill(
                            label = "VNC",
                            icon = Icons.Filled.ScreenShare,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            modifier = Modifier.weight(1f),
                        )
                        DesktopProtocolPill(
                            label = "RDP",
                            icon = Icons.Filled.DesktopWindows,
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> VncScreen(
                isActive = isActive,
                pendingHost = pendingVncHost,
                pendingPort = pendingVncPort,
                pendingPassword = pendingVncPassword,
                pendingSshForward = pendingVncSshForward,
                pendingSshSessionId = pendingVncSshSessionId,
                toolbarLayout = toolbarLayout,
                onPendingConsumed = onPendingConsumed,
                onFullscreenChanged = onFullscreenChanged,
                viewModel = vncViewModel,
            )
            1 -> RdpScreen(
                isActive = isActive,
                pendingHost = pendingRdpHost,
                pendingPort = pendingRdpPort,
                pendingUsername = pendingRdpUsername,
                pendingPassword = pendingRdpPassword,
                pendingDomain = pendingRdpDomain,
                pendingSshForward = pendingRdpSshForward,
                pendingSshSessionId = pendingRdpSshSessionId,
                pendingSshProfileId = pendingRdpSshProfileId,
                toolbarLayout = toolbarLayout,
                onPendingConsumed = onPendingConsumed,
                onFullscreenChanged = onFullscreenChanged,
                viewModel = rdpViewModel,
            )
        }
    }
}

@Composable
private fun DesktopProtocolPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}
