package sh.haven.feature.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.haven.core.data.db.entities.SshKey
import sh.haven.core.security.SshKeyGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
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
                    Icon(Icons.Filled.Add, contentDescription = if (zh) "添加密钥" else "Add key")
                }
            }
        },
    ) { innerPadding ->
        if (keys.isEmpty() && !generating) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Filled.VpnKey,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (zh) "暂无 SSH 密钥" else "No SSH keys",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    if (zh) "点击 + 生成或导入密钥" else "Tap + to generate or import a key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(keys, key = { it.id }) { sshKey ->
                    Box {
                        ListItem(
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    copyPublicKey(context, sshKey)
                                },
                                onLongClick = {
                                    contextMenuKeyId = sshKey.id
                                },
                            ),
                            headlineContent = { Text(sshKey.label) },
                            supportingContent = {
                                Column {
                                    Text(
                                        sshKey.keyType,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        sshKey.fingerprintSha256,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                Text(
                                    formatDate(sshKey.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (sshKey.keyType.startsWith("sk-"))
                                        Icons.Filled.Key
                                    else
                                        Icons.Filled.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )

                        DropdownMenu(
                            expanded = contextMenuKeyId == sshKey.id,
                            onDismissRequest = { contextMenuKeyId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (zh) "复制公钥" else "Copy public key") },
                                onClick = {
                                    copyPublicKey(context, sshKey)
                                    contextMenuKeyId = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                },
                            )
                            if (!sshKey.keyType.startsWith("sk-")) {
                                DropdownMenuItem(
                                    text = { Text(if (zh) "导出私钥" else "Export private key") },
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
                                    Text(if (zh) "删除" else "Delete", color = MaterialTheme.colorScheme.error)
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
                    viewModel.showError(if (zh) "剪贴板为空" else "Clipboard is empty")
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
        title = { Text(if (zh) "添加 SSH 密钥" else "Add SSH Key") },
        text = {
            Column {
                ListItem(
                    modifier = Modifier.clickable { onGenerate() },
                    headlineContent = { Text(if (zh) "生成新密钥" else "Generate new key") },
                    supportingContent = { Text(if (zh) "Ed25519、RSA 或 ECDSA" else "Ed25519, RSA, or ECDSA") },
                    leadingContent = {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    },
                )
                ListItem(
                    modifier = Modifier.clickable { onImport() },
                    headlineContent = { Text(if (zh) "从文件导入" else "Import from file") },
                    supportingContent = { Text(if (zh) "PEM 或 OpenSSH 格式" else "PEM or OpenSSH format") },
                    leadingContent = {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                    },
                )
                ListItem(
                    modifier = Modifier.clickable { onPaste() },
                    headlineContent = { Text(if (zh) "从剪贴板粘贴" else "Paste from clipboard") },
                    supportingContent = { Text(if (zh) "粘贴 PEM 或 OpenSSH 私钥" else "Paste a PEM or OpenSSH private key") },
                    leadingContent = {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenerateKeyDialog(
    zh: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (label: String, keyType: SshKeyGenerator.KeyType) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SshKeyGenerator.KeyType.ED25519) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "生成 SSH 密钥" else "Generate SSH Key") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(if (zh) "名称" else "Label") },
                    placeholder = { Text(if (zh) "例如：my-server" else "e.g. my-server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (zh) "密钥类型" else "Key type") },
                        modifier = Modifier
                            .fillMaxWidth(),
                    )
                    // Invisible clickable overlay to open dropdown
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(onClick = { expanded = true }),
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        SshKeyGenerator.KeyType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                },
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
                Text(if (zh) "生成" else "Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
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
        title = { Text(if (zh) "加密密钥" else "Encrypted Key") },
        text = {
            Column {
                Text(
                    if (zh) "该密钥受口令保护。" else "This key is protected with a passphrase.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(if (zh) "口令" else "Passphrase") },
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
                Text(if (zh) "解锁" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
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
        title = { Text(if (zh) "导入 SSH 密钥" else "Import SSH Key") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(if (zh) "名称" else "Label") },
                    placeholder = { Text(if (zh) "例如：my-server" else "e.g. my-server") },
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
                Text(if (zh) "导入" else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
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
