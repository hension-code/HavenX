package sh.haven.feature.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import sh.haven.core.data.db.entities.SshKey
import sh.haven.core.security.SshKeyGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    viewModel: KeysViewModel = hiltViewModel(),
) {
    val zh = ComposeLocale.current.language == "zh"
    val keys by viewModel.keys.collectAsState()
    val generating by viewModel.generating.collectAsState()
    val error by viewModel.error.collectAsState()
    val needsPassphrase by viewModel.needsPassphrase.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingExportKeyId by viewModel.pendingExportKeyId.collectAsState()

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var contextMenuKeyId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.importFromUri(context, it)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-pem-file"),
    ) { uri ->
        val keyId = pendingExportKeyId
        viewModel.clearPendingExport()
        if (uri != null && keyId != null) {
            viewModel.exportPrivateKey(context, keyId, uri)
        }
    }

    LaunchedEffect(pendingExportKeyId) {
        pendingExportKeyId?.let { keyId ->
            exportLauncher.launch(viewModel.getExportFileName(keyId))
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 1.dp) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (zh) "SSH 密钥" else "SSH Keys",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddKeyDialog = true }) {
                if (generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_key))
                }
            }
        },
    ) { innerPadding ->
        if (keys.isEmpty() && !generating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.no_ssh_keys),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    stringResource(R.string.tap_to_generate_or_import),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = { showAddKeyDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_key))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(keys, key = { it.id }) { sshKey ->
                    val isHardwareKey = sshKey.keyType.startsWith("sk-")
                    val keyTint = if (isHardwareKey) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val copiedMsg = if (zh) "公钥已复制到剪贴板" else "Public key copied"
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .combinedClickable(
                                onClick = {
                                    copyPublicKey(context, sshKey)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(copiedMsg)
                                    }
                                },
                                onLongClick = { contextMenuKeyId = sshKey.id },
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(keyTint.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isHardwareKey) Icons.Filled.Key else Icons.Filled.VpnKey,
                                    contentDescription = null,
                                    tint = keyTint,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = sshKey.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = sshKey.keyType.removePrefix("ssh-").uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = sshKey.fingerprintSha256,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = formatDate(sshKey.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            FilledTonalIconButton(
                                onClick = {
                                    copyPublicKey(context, sshKey)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(copiedMsg)
                                    }
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_public_key),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { contextMenuKeyId = sshKey.id },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = contextMenuKeyId == sshKey.id,
                                    onDismissRequest = { contextMenuKeyId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.copy_public_key)) },
                                        onClick = {
                                            copyPublicKey(context, sshKey)
                                            contextMenuKeyId = null
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar(copiedMsg)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                        },
                                    )
                                    if (!isHardwareKey) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.export_private_key)) },
                                            onClick = {
                                                contextMenuKeyId = null
                                                viewModel.requestExport(sshKey.id)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            viewModel.deleteKey(sshKey.id)
                                            contextMenuKeyId = null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val clipboardEmptyError = stringResource(R.string.clipboard_is_empty)

    if (showAddKeyDialog) {
        AddKeyChooser(
            zh = zh,
            onGenerate = {
                showAddKeyDialog = false
                showGenerateDialog = true
            },
            onImport = {
                showAddKeyDialog = false
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onPaste = {
                showAddKeyDialog = false
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (text.isNullOrBlank()) {
                    viewModel.showError(clipboardEmptyError)
                } else {
                    viewModel.startImport(text.toByteArray())
                }
            },
            onDismiss = { showAddKeyDialog = false },
        )
    }

    if (showGenerateDialog) {
        GenerateKeyDialog(
            zh = zh,
            onDismiss = { showGenerateDialog = false },
            onGenerate = { label, keyType ->
                viewModel.generateKey(label, keyType)
                showGenerateDialog = false
            },
        )
    }

    if (needsPassphrase) {
        PassphraseDialog(
            zh = zh,
            onConfirm = { viewModel.retryImportWithPassphrase(it) },
            onDismiss = { viewModel.cancelImport() },
        )
    }

    importResult?.let { result ->
        ImportLabelDialog(
            keyType = result.keyType,
            fingerprint = result.fingerprintSha256,
            zh = zh,
            onConfirm = { label -> viewModel.saveImportedKey(label) },
            onDismiss = { viewModel.cancelImport() },
        )
    }
}

@Composable
private fun AddKeyChooser(
    zh: Boolean,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_ssh_key)) },
        text = {
            Column {
                ListItem(
                    modifier = Modifier.clickable { onGenerate() },
                    headlineContent = { Text(stringResource(R.string.generate_new_key)) },
                    supportingContent = { Text(stringResource(R.string.ed25519_rsa_or_ecdsa)) },
                    leadingContent = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                ListItem(
                    modifier = Modifier.clickable { onImport() },
                    headlineContent = { Text(stringResource(R.string.import_from_file)) },
                    supportingContent = { Text(stringResource(R.string.pem_or_openssh_format)) },
                    leadingContent = {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                ListItem(
                    modifier = Modifier.clickable { onPaste() },
                    headlineContent = { Text(stringResource(R.string.paste_from_clipboard)) },
                    supportingContent = { Text(stringResource(R.string.paste_a_pem_or_openssh)) },
                    leadingContent = {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateKeyDialog(
    zh: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (label: String, keyType: SshKeyGenerator.KeyType) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SshKeyGenerator.KeyType.ED25519) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.generate_ssh_key),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label)) },
                    placeholder = { Text(stringResource(R.string.e_g_my_server)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.key_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SshKeyGenerator.KeyType.entries.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.displayName) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(label.ifBlank { selectedType.displayName }, selectedType) },
            ) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PassphraseDialog(
    zh: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.encrypted_key)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.this_key_is_protected_with),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = passphrase.isNotEmpty(),
            ) {
                Text(stringResource(R.string.unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ImportLabelDialog(
    keyType: String,
    fingerprint: String,
    zh: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_ssh_key)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label)) },
                    placeholder = { Text(stringResource(R.string.e_g_my_server)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    keyType,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.ifBlank { keyType }) },
            ) {
                Text(stringResource(R.string.str_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun copyPublicKey(context: Context, sshKey: SshKey) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SSH Public Key", sshKey.publicKeyOpenSsh))
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
