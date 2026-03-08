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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.haven.core.data.db.entities.ConnectionProfile

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditDialog(
    existing: ConnectionProfile? = null,
    discoveredDestinations: List<ConnectionsViewModel.DiscoveredDestination> = emptyList(),
    discoveredHosts: List<DiscoveredHost> = emptyList(),
    sshProfiles: List<ConnectionProfile> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var connectionType by remember { mutableStateOf(existing?.connectionType ?: "SSH") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var destinationHash by remember { mutableStateOf(existing?.destinationHash ?: "") }
    var jumpProfileId by remember { mutableStateOf(existing?.jumpProfileId) }
    var sshOptions by remember { mutableStateOf(existing?.sshOptions ?: "") }
    var localSideband by remember {
        mutableStateOf(
            existing == null ||
                (existing.reticulumHost in listOf("127.0.0.1", "localhost", "::1") &&
                    existing.reticulumPort == 37428),
        )
    }
    var rnsHost by remember { mutableStateOf(existing?.reticulumHost ?: "") }
    var rnsPort by remember { mutableStateOf(existing?.reticulumPort?.toString() ?: "4242") }

    val isEdit = existing != null
    val title = if (isEdit) "Edit Connection" else "New Connection"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Type selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = connectionType == "SSH",
                        onClick = { connectionType = "SSH" },
                        label = { Text("SSH") },
                    )
                    FilterChip(
                        selected = connectionType == "RETICULUM",
                        onClick = { connectionType = "RETICULUM" },
                        label = { Text("Reticulum") },
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = {
                        Text(if (connectionType == "SSH") "My Server" else "My Node")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (connectionType == "SSH") {
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
                                label = { Text("Host") },
                                placeholder = { Text("192.168.1.1") },
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostExpanded)
                                },
                                supportingText = if (filteredHosts.isNotEmpty()) {{
                                    Text("${filteredHosts.size} hosts discovered")
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
                            Text(
                                text = "Discovered (${filteredHosts.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            label = { Text("Host") },
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
                            label = { Text("Username") },
                            placeholder = { Text("root") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") },
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
                                value = selectedJump?.label ?: "None (direct)",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Jump Host (-J)") },
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

                    // SSH options
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sshOptions,
                        onValueChange = { sshOptions = it },
                        label = { Text("SSH Options") },
                        placeholder = { Text("ServerAliveInterval 60\nServerAliveCountMax 3") },
                        supportingText = { Text("ssh_config format: Key Value (one per line)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                                "Discovered (${filtered.size} of ${discoveredDestinations.size})"
                            } else {
                                "Discovered (${filtered.size})"
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
                        label = { Text("Destination Hash") },
                        placeholder = { Text("32-character hex") },
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
                        label = { Text("Local Sideband") },
                    )
                    if (!localSideband) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = rnsHost,
                                onValueChange = { rnsHost = it },
                                label = { Text("Gateway Host") },
                                placeholder = { Text("192.168.0.2") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = rnsPort,
                                onValueChange = { rnsPort = it.filter { c -> c.isDigit() } },
                                label = { Text("Port") },
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
            val canSave = if (connectionType == "SSH") {
                host.isNotBlank() && username.isNotBlank()
            } else {
                destinationHash.length == 32 && (localSideband || rnsHost.isNotBlank())
            }
            TextButton(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 22
                    val profile = if (connectionType == "SSH") {
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
                            sshOptions = sshOptions.ifBlank { null },
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
