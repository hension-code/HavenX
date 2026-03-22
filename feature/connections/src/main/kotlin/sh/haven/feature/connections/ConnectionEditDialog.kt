package sh.haven.feature.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.haven.core.data.db.entities.ConnectionProfile

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditDialog(
    existing: ConnectionProfile? = null,
    discoveredDestinations: List<ConnectionsViewModel.DiscoveredDestination> = emptyList(),
    discoveredHosts: List<DiscoveredHost> = emptyList(),
    discoveredSmbHosts: List<DiscoveredHost> = emptyList(),
    sshProfiles: List<ConnectionProfile> = emptyList(),
    sshKeys: List<sh.haven.core.data.db.entities.SshKey> = emptyList(),
    globalSessionManagerLabel: String = "None",
    subnetScanning: Boolean = false,
    smbSubnetScanning: Boolean = false,
    onScanSubnet: () -> Unit = {},
    onScanSubnetSmb: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    // Transport dropdown maps to: connectionType + useMosh + useEternalTerminal
    val initialTransport = when {
        existing?.isVnc == true -> "VNC"
        existing?.isRdp == true -> "RDP"
        existing?.isSmb == true -> "SMB"
        existing?.isEternalTerminal == true -> "ET"
        existing?.isMosh == true -> "MOSH"
        existing?.isReticulum == true -> "RETICULUM"
        else -> "SSH"
    }
    var selectedTransport by rememberSaveable { mutableStateOf(initialTransport) }
    // Derived connectionType for field visibility
    val connectionType = when (selectedTransport) {
        "RETICULUM" -> "RETICULUM"
        "VNC" -> "VNC"
        "RDP" -> "RDP"
        "SMB" -> "SMB"
        else -> "SSH"
    }
    var label by rememberSaveable { mutableStateOf(existing?.label ?: "") }
    var host by rememberSaveable { mutableStateOf(existing?.host ?: "") }
    var port by rememberSaveable {
        mutableStateOf(
            when {
                existing?.isVnc == true -> (existing.vncPort ?: 5900).toString()
                existing?.isRdp == true -> existing.rdpPort.toString()
                existing?.isSmb == true -> existing.smbPort.toString()
                else -> existing?.port?.toString() ?: "22"
            }
        )
    }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var rdpUsername by rememberSaveable { mutableStateOf(existing?.rdpUsername ?: "") }
    var rdpPassword by rememberSaveable { mutableStateOf(existing?.rdpPassword ?: "") }
    var rdpDomain by rememberSaveable { mutableStateOf(existing?.rdpDomain ?: "") }
    var rdpSshForward by rememberSaveable { mutableStateOf(existing?.rdpSshForward ?: false) }
    var rdpSshProfileId by rememberSaveable { mutableStateOf(existing?.rdpSshProfileId) }
    var smbShare by rememberSaveable { mutableStateOf(existing?.smbShare ?: "") }
    var smbPassword by rememberSaveable { mutableStateOf(existing?.smbPassword ?: "") }
    var smbDomain by rememberSaveable { mutableStateOf(existing?.smbDomain ?: "") }
    var smbSshForward by rememberSaveable { mutableStateOf(existing?.smbSshForward ?: false) }
    var smbSshProfileId by rememberSaveable { mutableStateOf(existing?.smbSshProfileId) }
    var vncPassword by rememberSaveable { mutableStateOf(existing?.vncPassword ?: "") }
    var destinationHash by rememberSaveable { mutableStateOf(existing?.destinationHash ?: "") }
    var jumpProfileId by rememberSaveable { mutableStateOf(existing?.jumpProfileId) }
    var keyId by rememberSaveable { mutableStateOf(existing?.keyId) }
    var sshOptions by rememberSaveable { mutableStateOf(existing?.sshOptions ?: "") }
    var selectedSessionManager by rememberSaveable { mutableStateOf(existing?.sessionManager) }
    var etPort by rememberSaveable { mutableStateOf(existing?.etPort?.toString() ?: "2022") }
    var localSideband by rememberSaveable {
        mutableStateOf(
            existing == null ||
                (existing.reticulumHost in listOf("127.0.0.1", "localhost", "::1") &&
                    existing.reticulumPort == 37428),
        )
    }
    var rnsHost by rememberSaveable { mutableStateOf(existing?.reticulumHost ?: "") }
    var rnsPort by rememberSaveable { mutableStateOf(existing?.reticulumPort?.toString() ?: "4242") }

    val isEdit = existing != null
    val zh = Locale.current.language == "zh"
    val title = if (isEdit) {
        if (zh) "编辑连接" else "Edit Connection"
    } else {
        if (zh) "新建连接" else "New Connection"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Transport selector
                val transportOptions = listOf(
                    "SSH" to "SSH",
                    "MOSH" to "Mosh",
                    "ET" to if (zh) "Eternal Terminal" else "Eternal Terminal",
                    "VNC" to if (zh) "VNC（桌面）" else "VNC (Desktop)",
                    "RDP" to if (zh) "RDP（桌面）" else "RDP (Desktop)",
                    "SMB" to if (zh) "SMB（文件共享）" else "SMB (File Share)",
                    "RETICULUM" to if (zh) "Reticulum" else "Reticulum",
                )
                var transportExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = transportExpanded,
                    onExpandedChange = { transportExpanded = it },
                ) {
                    OutlinedTextField(
                        value = transportOptions.first { it.first == selectedTransport }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (zh) "协议" else "Transport") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(transportExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = transportExpanded,
                        onDismissRequest = { transportExpanded = false },
                    ) {
                        transportOptions.forEach { (value, displayLabel) ->
                            DropdownMenuItem(
                                text = { Text(displayLabel) },
                                onClick = {
                                    selectedTransport = value
                                    transportExpanded = false
                                    // Update port to transport default when switching
                                    val defaultPort = when (value) {
                                        "VNC" -> "5900"
                                        "RDP" -> "3389"
                                        "SMB" -> "445"
                                        "ET" -> "22"
                                        else -> "22"
                                    }
                                    if (port == "22" || port == "5900" || port == "3389" || port == "445" || port == "2022") {
                                        port = defaultPort
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(if (zh) "名称" else "Label") },
                    placeholder = {
                        Text(
                            when (connectionType) {
                                "VNC" -> if (zh) "我的 VNC 桌面" else "My VNC Desktop"
                                "RDP" -> if (zh) "我的 RDP 桌面" else "My RDP Desktop"
                                "SMB" -> if (zh) "我的文件共享" else "My File Share"
                                "RETICULUM" -> if (zh) "我的节点" else "My Node"
                                else -> if (zh) "我的服务器" else "My Server"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (connectionType == "VNC") {
                    // VNC: host, port, password
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(if (zh) "主机" else "Host") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text(if (zh) "端口" else "Port") },
                        placeholder = { Text("5900") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vncPassword,
                        onValueChange = { vncPassword = it },
                        label = { Text(if (zh) "密码（可选）" else "Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (zh) "VNC 密码最多 8 个字符" else "VNC passwords are limited to 8 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (connectionType == "RDP") {
                    // RDP: host, port, username, domain
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(if (zh) "主机" else "Host") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = rdpUsername,
                            onValueChange = { rdpUsername = it },
                            label = { Text(if (zh) "用户名" else "Username") },
                            placeholder = { Text("user") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(if (zh) "端口" else "Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rdpPassword,
                        onValueChange = { rdpPassword = it },
                        label = { Text(if (zh) "密码（可选）" else "Password (optional)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rdpDomain,
                        onValueChange = { rdpDomain = it },
                        label = { Text(if (zh) "域（可选）" else "Domain (optional)") },
                        placeholder = { Text("WORKGROUP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = rdpSshForward,
                        onClick = {
                            rdpSshForward = !rdpSshForward
                            if (rdpSshForward) {
                                if (host.isBlank()) host = "localhost"
                            } else {
                                rdpSshProfileId = null
                                if (host == "localhost") host = ""
                            }
                        },
                        label = { Text(if (zh) "SSH 隧道" else "SSH tunnel") },
                    )
                    if (rdpSshForward) {
                        val sshCandidates = sshProfiles.filter { it.isSsh }
                        if (sshCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            var sshExpanded by remember { mutableStateOf(false) }
                            val selectedSsh = sshCandidates.firstOrNull { it.id == rdpSshProfileId }
                            ExposedDropdownMenuBox(
                                expanded = sshExpanded,
                                onExpandedChange = { sshExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = selectedSsh?.label ?: if (zh) "选择 SSH 连接" else "Select SSH connection",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (zh) "SSH 连接" else "SSH connection") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sshExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(
                                    expanded = sshExpanded,
                                    onDismissRequest = { sshExpanded = false },
                                ) {
                                    sshCandidates.forEach { candidate ->
                                        DropdownMenuItem(
                                            text = { Text(candidate.label) },
                                            onClick = {
                                                rdpSshProfileId = candidate.id
                                                sshExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                if (zh) "请先添加一个 SSH 连接" else "Add an SSH connection first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else if (connectionType == "SMB") {
                    // SMB: host (with discovery), share, username, port, password, domain, SSH tunnel
                    val filteredSmbHosts = remember(discoveredSmbHosts, host) {
                        val prefix = host.lowercase()
                        discoveredSmbHosts
                            .filter {
                                prefix.isEmpty() ||
                                    it.address.startsWith(prefix) ||
                                    it.hostname?.lowercase()?.contains(prefix) == true
                            }
                            .take(8)
                    }

                    // Scan network button
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (filteredSmbHosts.isNotEmpty()) {
                            Text(
                                if (zh) "已发现（${filteredSmbHosts.size}）" else "Discovered (${filteredSmbHosts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = onScanSubnetSmb,
                            enabled = !smbSubnetScanning,
                        ) {
                            if (smbSubnetScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(if (zh) "扫描中" else "Scanning", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (zh) "扫描网络" else "Scan Network", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    var smbHostExpanded by remember { mutableStateOf(false) }
                    if (discoveredSmbHosts.size > 3) {
                        ExposedDropdownMenuBox(
                            expanded = smbHostExpanded,
                            onExpandedChange = { smbHostExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = {
                                    host = it
                                    smbHostExpanded = true
                                },
                                label = { Text(if (zh) "主机" else "Host") },
                                placeholder = { Text("192.168.1.100") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = smbHostExpanded)
                                },
                                supportingText = if (filteredSmbHosts.isNotEmpty()) {{
                                    Text(if (zh) "已发现 ${filteredSmbHosts.size} 台主机" else "${filteredSmbHosts.size} hosts discovered")
                                }} else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                            )
                            if (filteredSmbHosts.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = smbHostExpanded,
                                    onDismissRequest = { smbHostExpanded = false },
                                ) {
                                    filteredSmbHosts.forEach { disc ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(disc.hostname ?: disc.address)
                                                    if (disc.hostname != null) {
                                                        Text(
                                                            disc.address,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                host = disc.address
                                                if (label.isBlank() && disc.hostname != null) {
                                                    label = disc.hostname
                                                }
                                                smbHostExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (filteredSmbHosts.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                filteredSmbHosts.forEach { disc ->
                                    val chipLabel = disc.hostname ?: disc.address
                                    SuggestionChip(
                                        onClick = {
                                            host = disc.address
                                            if (label.isBlank() && disc.hostname != null) {
                                                label = disc.hostname
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chipLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text(if (zh) "主机" else "Host") },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbShare,
                        onValueChange = { smbShare = it },
                        label = { Text(if (zh) "共享名" else "Share Name") },
                        placeholder = { Text("shared") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(if (zh) "用户名" else "Username") },
                            placeholder = { Text("user") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(if (zh) "端口" else "Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbPassword,
                        onValueChange = { smbPassword = it },
                        label = { Text(if (zh) "密码（可选）" else "Password (optional)") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbDomain,
                        onValueChange = { smbDomain = it },
                        label = { Text(if (zh) "域（可选）" else "Domain (optional)") },
                        placeholder = { Text("WORKGROUP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = smbSshForward,
                        onClick = {
                            smbSshForward = !smbSshForward
                            if (smbSshForward) {
                                if (host.isBlank()) host = "localhost"
                            } else {
                                smbSshProfileId = null
                                if (host == "localhost") host = ""
                            }
                        },
                        label = { Text(if (zh) "SSH 隧道" else "SSH tunnel") },
                    )
                    if (smbSshForward) {
                        val sshCandidates = sshProfiles.filter { it.isSsh }
                        if (sshCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            var sshExpanded by remember { mutableStateOf(false) }
                            val selectedSsh = sshCandidates.firstOrNull { it.id == smbSshProfileId }
                            ExposedDropdownMenuBox(
                                expanded = sshExpanded,
                                onExpandedChange = { sshExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = selectedSsh?.label ?: if (zh) "选择 SSH 连接" else "Select SSH connection",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(if (zh) "SSH 连接" else "SSH connection") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sshExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(
                                    expanded = sshExpanded,
                                    onDismissRequest = { sshExpanded = false },
                                ) {
                                    sshCandidates.forEach { candidate ->
                                        DropdownMenuItem(
                                            text = { Text(candidate.label) },
                                            onClick = {
                                                smbSshProfileId = candidate.id
                                                sshExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                if (zh) "请先添加一个 SSH 连接" else "Add an SSH connection first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else if (connectionType == "SSH") {
                    // Discovered hosts — filter by typed prefix
                    val filteredHosts = remember(discoveredHosts, host) {
                        val prefix = host.lowercase()
                        discoveredHosts
                            .filter {
                                prefix.isEmpty() ||
                                    it.address.startsWith(prefix) ||
                                    it.hostname?.lowercase()?.contains(prefix) == true
                            }
                            .take(8)
                    }

                    // Scan network button
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (filteredHosts.isNotEmpty()) {
                            Text(
                                if (zh) "已发现（${filteredHosts.size}）" else "Discovered (${filteredHosts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = onScanSubnet,
                            enabled = !subnetScanning,
                        ) {
                            if (subnetScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(if (zh) "扫描中" else "Scanning", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (zh) "扫描网络" else "Scan Network", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Use dropdown when there are many discovered hosts (decision
                    // based on unfiltered count to avoid switching widgets mid-typing)
                    var hostExpanded by remember { mutableStateOf(false) }
                    if (discoveredHosts.size > 3) {
                        ExposedDropdownMenuBox(
                            expanded = hostExpanded,
                            onExpandedChange = { hostExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = host,
                                onValueChange = {
                                    host = it
                                    hostExpanded = true
                                },
                                label = { Text(if (zh) "主机" else "Host") },
                                placeholder = { Text("192.168.1.1") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostExpanded)
                                },
                                supportingText = if (filteredHosts.isNotEmpty()) {{
                                    Text(if (zh) "已发现 ${filteredHosts.size} 台主机" else "${filteredHosts.size} hosts discovered")
                                }} else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryEditable),
                            )
                            if (filteredHosts.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = hostExpanded,
                                    onDismissRequest = { hostExpanded = false },
                                ) {
                                    filteredHosts.forEach { disc ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(disc.hostname ?: disc.address)
                                                    if (disc.hostname != null) {
                                                        Text(
                                                            disc.address + if (disc.port != 22) ":${disc.port}" else "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                host = disc.address
                                                if (disc.port != 22) port = disc.port.toString()
                                                if (label.isBlank() && disc.hostname != null) {
                                                    label = disc.hostname
                                                }
                                                hostExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Few or no discovered hosts — show chips + plain text field
                        if (filteredHosts.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                filteredHosts.forEach { disc ->
                                    val chipLabel = disc.hostname ?: disc.address
                                    SuggestionChip(
                                        onClick = {
                                            host = disc.address
                                            if (disc.port != 22) port = disc.port.toString()
                                            if (label.isBlank() && disc.hostname != null) {
                                                label = disc.hostname
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chipLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text(if (zh) "主机" else "Host") },
                            placeholder = { Text("192.168.1.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(if (zh) "用户名" else "Username") },
                            placeholder = { Text("root") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(if (zh) "端口" else "Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }

                    // Jump host selector — exclude self to prevent circular references
                    val jumpCandidates = sshProfiles.filter { it.id != existing?.id && it.isSsh }
                    if (jumpCandidates.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        var jumpExpanded by remember { mutableStateOf(false) }
                        val selectedJump = jumpCandidates.firstOrNull { it.id == jumpProfileId }

                        ExposedDropdownMenuBox(
                            expanded = jumpExpanded,
                            onExpandedChange = { jumpExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedJump?.label ?: if (zh) "无（直连）" else "None (direct)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (zh) "跳板机（-J）" else "Jump Host (-J)") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = jumpExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = jumpExpanded,
                                onDismissRequest = { jumpExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (direct)") },
                                    onClick = {
                                        jumpProfileId = null
                                        jumpExpanded = false
                                    },
                                )
                                jumpCandidates.forEach { candidate ->
                                    DropdownMenuItem(
                                        text = { Text(candidate.label) },
                                        onClick = {
                                            jumpProfileId = candidate.id
                                            jumpExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // Visual chain indicator
                        if (selectedJump != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PhoneAndroid, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text(selectedJump.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.Storage, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Session manager
                    Spacer(Modifier.height(8.dp))
                    val sessionManagerOptions = listOf(
                        null to if (zh) "默认（$globalSessionManagerLabel）" else "Default ($globalSessionManagerLabel)",
                        "NONE" to if (zh) "无" else "None",
                        "TMUX" to "tmux",
                        "ZELLIJ" to "zellij",
                        "SCREEN" to "screen",
                        "BYOBU" to "byobu",
                    )
                    var smExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = smExpanded,
                        onExpandedChange = { smExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = sessionManagerOptions.firstOrNull { it.first == selectedSessionManager }?.second
                                ?: if (zh) "默认（$globalSessionManagerLabel）" else "Default ($globalSessionManagerLabel)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (zh) "会话管理器" else "Session Manager") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(smExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = smExpanded,
                            onDismissRequest = { smExpanded = false },
                        ) {
                            sessionManagerOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedSessionManager = value
                                        smExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    // ET port (shown only for Eternal Terminal)
                    if (selectedTransport == "ET") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = etPort,
                            onValueChange = { etPort = it.filter { c -> c.isDigit() } },
                            label = { Text("ET Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp),
                        )
                    }

                    // Transport helper text
                    if (selectedTransport == "MOSH") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (zh) "需要远端安装 mosh-server" else "Requires mosh-server on remote host",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selectedTransport == "ET") {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (zh) "需要远端安装 etserver（端口 ${etPort.ifBlank { "2022" }}）" else "Requires etserver on remote host (port ${etPort.ifBlank { "2022" }})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // SSH options
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sshOptions,
                        onValueChange = { sshOptions = it },
                        label = { Text(if (zh) "SSH 选项" else "SSH Options") },
                        placeholder = { Text("ServerAliveInterval 60\nServerAliveCountMax 3") },
                        supportingText = { Text(if (zh) "ssh_config 格式：Key Value（每行一条）" else "ssh_config format: Key Value (one per line)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // SSH key selector
                    if (sshKeys.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        var keyExpanded by remember { mutableStateOf(false) }
                        val selectedKey = sshKeys.firstOrNull { it.id == keyId }
                        ExposedDropdownMenuBox(
                            expanded = keyExpanded,
                            onExpandedChange = { keyExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = selectedKey?.label ?: if (zh) "任意（尝试所有密钥）" else "Any (try all keys)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (zh) "SSH 密钥" else "SSH Key") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(keyExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = keyExpanded,
                                onDismissRequest = { keyExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (zh) "任意（尝试所有密钥）" else "Any (try all keys)") },
                                    onClick = {
                                        keyId = null
                                        keyExpanded = false
                                    },
                                )
                                sshKeys.forEach { key ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(key.label)
                                                Text(
                                                    "${key.keyType} ${key.fingerprintSha256.take(20)}...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            keyId = key.id
                                            keyExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Discovered destinations — filter by typed prefix, cap at 8
                    val filtered = remember(discoveredDestinations, destinationHash) {
                        val prefix = destinationHash.lowercase()
                        discoveredDestinations
                            .filter { prefix.isEmpty() || it.hash.startsWith(prefix) }
                            .take(8)
                    }
                    if (filtered.isNotEmpty()) {
                        val hiddenCount = discoveredDestinations.size - filtered.size
                        Text(
                            text = if (hiddenCount > 0) {
                                if (zh) "已发现（${filtered.size}/${discoveredDestinations.size}）" else "Discovered (${filtered.size} of ${discoveredDestinations.size})"
                            } else {
                                if (zh) "已发现（${filtered.size}）" else "Discovered (${filtered.size})"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            filtered.forEach { dest ->
                                val hopsLabel = if (dest.hops >= 0) " (${dest.hops}h)" else ""
                                SuggestionChip(
                                    onClick = { destinationHash = dest.hash },
                                    label = {
                                        Text(
                                            text = dest.hash.take(12) + ".." + hopsLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = destinationHash,
                        onValueChange = {
                            destinationHash = it.filter { c -> c in "0123456789abcdefABCDEF" }
                                .take(32)
                        },
                        label = { Text(if (zh) "目标哈希" else "Destination Hash") },
                        placeholder = { Text(if (zh) "32 位十六进制" else "32-character hex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = localSideband,
                        onClick = {
                            localSideband = !localSideband
                            if (localSideband) {
                                rnsHost = "127.0.0.1"
                                rnsPort = "37428"
                            }
                        },
                        label = { Text(if (zh) "本地 Sideband" else "Local Sideband") },
                    )
                    if (!localSideband) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = rnsHost,
                                onValueChange = { rnsHost = it },
                                label = { Text(if (zh) "网关主机" else "Gateway Host") },
                                placeholder = { Text("192.168.0.2") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = rnsPort,
                                onValueChange = { rnsPort = it.filter { c -> c.isDigit() } },
                                label = { Text(if (zh) "端口" else "Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = when (connectionType) {
                "SSH" -> host.isNotBlank() && username.isNotBlank()
                "VNC" -> host.isNotBlank()
                "RDP" -> host.isNotBlank() && rdpUsername.isNotBlank() && (!rdpSshForward || rdpSshProfileId != null)
                "SMB" -> host.isNotBlank() && smbShare.isNotBlank() && (!smbSshForward || smbSshProfileId != null)
                else -> destinationHash.length == 32 && (localSideband || rnsHost.isNotBlank())
            }
            TextButton(
                onClick = {
                    val etPortInt = etPort.toIntOrNull() ?: 2022
                    val profile = if (connectionType == "VNC") {
                        val vncPortInt = port.toIntOrNull() ?: 5900
                        (existing ?: ConnectionProfile(
                            label = label,
                            host = host,
                            username = "",
                        )).copy(
                            label = label.ifBlank { "VNC: $host" },
                            host = host,
                            port = vncPortInt,
                            username = "",
                            connectionType = "VNC",
                            vncPort = vncPortInt,
                            vncPassword = vncPassword.ifBlank { null },
                        )
                    } else if (connectionType == "RDP") {
                        val rdpPortInt = port.toIntOrNull() ?: 3389
                        (existing ?: ConnectionProfile(
                            label = label,
                            host = host,
                            username = rdpUsername,
                        )).copy(
                            label = label.ifBlank { "RDP: $rdpUsername@$host" },
                            host = host,
                            port = rdpPortInt,
                            username = rdpUsername,
                            connectionType = "RDP",
                            rdpPort = rdpPortInt,
                            rdpUsername = rdpUsername.ifBlank { null },
                            rdpPassword = rdpPassword.ifBlank { null },
                            rdpDomain = rdpDomain.ifBlank { null },
                            rdpSshForward = rdpSshForward,
                            rdpSshProfileId = if (rdpSshForward) rdpSshProfileId else null,
                        )
                    } else if (connectionType == "SMB") {
                        val smbPortInt = port.toIntOrNull() ?: 445
                        (existing ?: ConnectionProfile(
                            label = label,
                            host = host,
                            username = username,
                        )).copy(
                            label = label.ifBlank { "SMB: \\\\$host\\$smbShare" },
                            host = host,
                            port = smbPortInt,
                            username = username,
                            connectionType = "SMB",
                            smbPort = smbPortInt,
                            smbShare = smbShare.ifBlank { null },
                            smbPassword = smbPassword.ifBlank { null },
                            smbDomain = smbDomain.ifBlank { null },
                            smbSshForward = smbSshForward,
                            smbSshProfileId = if (smbSshForward) smbSshProfileId else null,
                        )
                    } else if (connectionType == "SSH") {
                        val portInt = port.toIntOrNull() ?: 22
                        (existing ?: ConnectionProfile(
                            label = label,
                            host = host,
                            port = portInt,
                            username = username,
                        )).copy(
                            label = label.ifBlank { "$username@$host" },
                            host = host,
                            port = portInt,
                            username = username,
                            connectionType = "SSH",
                            destinationHash = null,
                            jumpProfileId = jumpProfileId,
                            keyId = keyId,
                            sshOptions = sshOptions.ifBlank { null },
                            sessionManager = selectedSessionManager,
                            useMosh = selectedTransport == "MOSH",
                            useEternalTerminal = selectedTransport == "ET",
                            etPort = etPortInt,
                        )
                    } else {
                        val savedHost = if (localSideband) "127.0.0.1" else rnsHost
                        val savedPort = if (localSideband) 37428 else (rnsPort.toIntOrNull() ?: 4242)
                        (existing ?: ConnectionProfile(
                            label = label,
                            host = "",
                            port = 0,
                            username = "",
                        )).copy(
                            label = label.ifBlank { "RNS:${destinationHash.take(12)}" },
                            host = "",
                            port = 0,
                            username = "",
                            connectionType = "RETICULUM",
                            destinationHash = destinationHash.lowercase(),
                            reticulumHost = savedHost,
                            reticulumPort = savedPort,
                        )
                    }
                    onSave(profile)
                },
                enabled = canSave,
            ) {
                Text(if (zh) "保存" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}
