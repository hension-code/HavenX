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
import androidx.compose.ui.res.stringResource
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
        stringResource(R.string.edit_connection)
    } else {
        stringResource(R.string.new_connection)
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
                    "ET" to stringResource(R.string.eternal_terminal),
                    "VNC" to stringResource(R.string.vnc_desktop),
                    "RDP" to stringResource(R.string.rdp_desktop),
                    "SMB" to stringResource(R.string.smb_file_share),
                    "RETICULUM" to stringResource(R.string.reticulum),
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
                        label = { Text(stringResource(R.string.transport)) },
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
                    label = { Text(stringResource(R.string.label)) },
                    placeholder = {
                        Text(
                            when (connectionType) {
                                "VNC" -> stringResource(R.string.my_vnc_desktop)
                                "RDP" -> stringResource(R.string.my_rdp_desktop)
                                "SMB" -> stringResource(R.string.my_file_share)
                                "RETICULUM" -> stringResource(R.string.my_node)
                                else -> stringResource(R.string.my_server)
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
                        label = { Text(stringResource(R.string.host)) },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.port)) },
                        placeholder = { Text("5900") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vncPassword,
                        onValueChange = { vncPassword = it },
                        label = { Text(stringResource(R.string.password_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.vnc_passwords_are_limited_to),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (connectionType == "RDP") {
                    // RDP: host, port, username, domain
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.host)) },
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
                            label = { Text(stringResource(R.string.username)) },
                            placeholder = { Text("user") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rdpPassword,
                        onValueChange = { rdpPassword = it },
                        label = { Text(stringResource(R.string.password_optional)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rdpDomain,
                        onValueChange = { rdpDomain = it },
                        label = { Text(stringResource(R.string.domain_optional)) },
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
                        label = { Text(stringResource(R.string.ssh_tunnel)) },
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
                                    value = selectedSsh?.label ?: stringResource(R.string.select_ssh_connection),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.ssh_connection)) },
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
                                stringResource(R.string.add_an_ssh_connection_first),
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
                                stringResource(R.string.discovered_filteredsmbhosts_size),
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
                                Text(stringResource(R.string.scanning), style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.scan_network), style = MaterialTheme.typography.labelSmall)
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
                                label = { Text(stringResource(R.string.host)) },
                                placeholder = { Text("192.168.1.100") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = smbHostExpanded)
                                },
                                supportingText = if (filteredSmbHosts.isNotEmpty()) {{
                                    Text(stringResource(R.string.filteredsmbhosts_size_hosts_discovered))
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
                            label = { Text(stringResource(R.string.host)) },
                            placeholder = { Text("192.168.1.100") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbShare,
                        onValueChange = { smbShare = it },
                        label = { Text(stringResource(R.string.share_name)) },
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
                            label = { Text(stringResource(R.string.username)) },
                            placeholder = { Text("user") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbPassword,
                        onValueChange = { smbPassword = it },
                        label = { Text(stringResource(R.string.password_optional)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbDomain,
                        onValueChange = { smbDomain = it },
                        label = { Text(stringResource(R.string.domain_optional)) },
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
                        label = { Text(stringResource(R.string.ssh_tunnel)) },
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
                                    value = selectedSsh?.label ?: stringResource(R.string.select_ssh_connection),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.ssh_connection)) },
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
                                stringResource(R.string.add_an_ssh_connection_first),
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
                                stringResource(R.string.discovered_filteredhosts_size),
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
                                Text(stringResource(R.string.scanning), style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.scan_network), style = MaterialTheme.typography.labelSmall)
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
                                label = { Text(stringResource(R.string.host)) },
                                placeholder = { Text("192.168.1.1") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostExpanded)
                                },
                                supportingText = if (filteredHosts.isNotEmpty()) {{
                                    Text(stringResource(R.string.filteredhosts_size_hosts_discovered))
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
                            label = { Text(stringResource(R.string.host)) },
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
                            label = { Text(stringResource(R.string.username)) },
                            placeholder = { Text("root") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.port)) },
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
                                value = selectedJump?.label ?: stringResource(R.string.none_direct),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.jump_host_j)) },
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
                        null to stringResource(R.string.default_globalsessionmanagerlabel, globalSessionManagerLabel),
                        "NONE" to stringResource(R.string.none),
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
                                ?: stringResource(R.string.default_globalsessionmanagerlabel, globalSessionManagerLabel),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.session_manager)) },
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
                            stringResource(R.string.requires_mosh_server_on_remote),
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
                        label = { Text(stringResource(R.string.ssh_options)) },
                        placeholder = { Text("ServerAliveInterval 60\nServerAliveCountMax 3") },
                        supportingText = { Text(stringResource(R.string.ssh_config_format_key_value)) },
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
                                value = selectedKey?.label ?: stringResource(R.string.any_try_all_keys),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ssh_key)) },
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
                                    text = { Text(stringResource(R.string.any_try_all_keys)) },
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
                                stringResource(R.string.discovered_filtered_size_of_discovereddestinations)
                            } else {
                                stringResource(R.string.discovered_filtered_size)
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
                        label = { Text(stringResource(R.string.destination_hash)) },
                        placeholder = { Text(stringResource(R.string.str_32_character_hex)) },
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
                        label = { Text(stringResource(R.string.local_sideband)) },
                    )
                    if (!localSideband) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = rnsHost,
                                onValueChange = { rnsHost = it },
                                label = { Text(stringResource(R.string.gateway_host)) },
                                placeholder = { Text("192.168.0.2") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = rnsPort,
                                onValueChange = { rnsPort = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.port)) },
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
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
