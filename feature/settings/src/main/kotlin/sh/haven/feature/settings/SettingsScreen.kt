package sh.haven.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import sh.haven.core.data.preferences.ToolbarItem
import sh.haven.core.data.preferences.ToolbarKey
import sh.haven.core.data.preferences.ToolbarLayout
import sh.haven.core.data.preferences.UserPreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val lockTimeout by viewModel.lockTimeout.collectAsState()
    val fontSize by viewModel.terminalFontSize.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val languageMode by viewModel.languageMode.collectAsState()
    val sessionManager by viewModel.sessionManager.collectAsState()
    val colorScheme by viewModel.terminalColorScheme.collectAsState()
    val toolbarLayout by viewModel.toolbarLayout.collectAsState()
    val toolbarLayoutJson by viewModel.toolbarLayoutJson.collectAsState()
    val backupStatus by viewModel.backupStatus.collectAsState()
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showSessionManagerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showToolbarConfigDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf<BackupAction?>(null) }
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val zh = Locale.current.language == "zh"

    // SAF launchers for backup/restore
    var pendingPassword by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri, pendingPassword)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showBackupPasswordDialog = BackupAction.Restore(uri)
        }
    }

    // Show toast on backup status changes
    LaunchedEffect(backupStatus) {
        when (val status = backupStatus) {
            is SettingsViewModel.BackupStatus.Success -> {
                Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                viewModel.clearBackupStatus()
            }
            is SettingsViewModel.BackupStatus.Error -> {
                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                viewModel.clearBackupStatus()
            }
            else -> {}
        }
    }

    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (zh) "设置" else "Settings") })
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        if (viewModel.biometricAvailable) {
            SettingsToggleItem(
                icon = Icons.Filled.Fingerprint,
                title = if (zh) "生物识别解锁" else "Biometric unlock",
                subtitle = if (zh) "需要生物识别才能打开 HavenX" else "Require biometrics to open HavenX",
                checked = biometricEnabled,
                onCheckedChange = viewModel::setBiometricEnabled,
            )
            if (biometricEnabled) {
                SettingsItem(
                    icon = Icons.Filled.Timer,
                    title = if (zh) "锁定超时" else "Lock timeout",
                    subtitle = lockTimeoutLabel(lockTimeout, zh),
                    onClick = { showLockTimeoutDialog = true },
                )
            }
        }
        SettingsItem(
            icon = Icons.Filled.Terminal,
            title = if (zh) "会话持久化" else "Session persistence",
            subtitle = if (sessionManager == UserPreferencesRepository.SessionManager.NONE) {
                if (zh) "无" else "None"
            } else {
                sessionManager.label
            },
            onClick = { showSessionManagerDialog = true },
        )
        SettingsItem(
            icon = Icons.Filled.TextFields,
            title = if (zh) "终端字体大小" else "Terminal font size",
            subtitle = "${fontSize}sp",
            onClick = { showFontSizeDialog = true },
        )
        SettingsItem(
            icon = Icons.Filled.Palette,
            title = if (zh) "终端配色方案" else "Terminal color scheme",
            subtitle = colorSchemeLabel(colorScheme, zh),
            onClick = { showColorSchemeDialog = true },
        )
        SettingsItem(
            icon = Icons.Filled.KeyboardAlt,
            title = if (zh) "键盘工具栏" else "Keyboard toolbar",
            subtitle = if (zh) "配置工具栏按键和布局" else "Configure toolbar keys and layout",
            onClick = { showToolbarConfigDialog = true },
        )
        SettingsItem(
            icon = Icons.Filled.ColorLens,
            title = if (zh) "主题" else "Theme",
            subtitle = themeModeLabel(theme, zh),
            onClick = { showThemeDialog = true },
        )
        SettingsItem(
            icon = Icons.Filled.Language,
            title = if (zh) "语言" else "Language",
            subtitle = when (languageMode) {
                UserPreferencesRepository.LanguageMode.SYSTEM -> if (zh) "跟随系统" else "System default"
                UserPreferencesRepository.LanguageMode.ENGLISH -> "English"
                UserPreferencesRepository.LanguageMode.CHINESE_SIMPLIFIED -> "简体中文"
            },
            onClick = { showLanguageDialog = true },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsItem(
            icon = Icons.Filled.CloudUpload,
            title = if (zh) "导出备份" else "Export backup",
            subtitle = if (zh) "密钥、连接和设置" else "Keys, connections, and settings",
            onClick = {
                showBackupPasswordDialog = BackupAction.Export
            },
        )
        SettingsItem(
            icon = Icons.Filled.CloudDownload,
            title = if (zh) "恢复备份" else "Restore backup",
            subtitle = if (zh) "从备份文件导入" else "Import from a backup file",
            onClick = {
                importLauncher.launch(arrayOf("*/*"))
            },
        )

        if (backupStatus is SettingsViewModel.BackupStatus.InProgress) {
            ListItem(
                headlineContent = { Text(if (zh) "处理中..." else "Working...") },
                leadingContent = {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsItem(
            icon = Icons.Filled.Info,
            title = if (zh) "关于 HavenX" else "About HavenX",
            subtitle = "v${packageInfo.versionName}",
            onClick = { showAboutDialog = true },
        )

    } // scrollable Column
    } // outer Column

    if (showAboutDialog) {
        AboutDialog(
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            zh = zh,
            onDismiss = { showAboutDialog = false },
            onOpenGitHub = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
            },
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = theme,
            zh = zh,
            onDismiss = { showThemeDialog = false },
            onSelect = { selected ->
                viewModel.setTheme(selected)
                showThemeDialog = false
            },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguage = languageMode,
            onDismiss = { showLanguageDialog = false },
            onSelect = { selected ->
                viewModel.setLanguageMode(selected)
                showLanguageDialog = false
            },
        )
    }

    if (showColorSchemeDialog) {
        ColorSchemeDialog(
            currentScheme = colorScheme,
            zh = zh,
            onDismiss = { showColorSchemeDialog = false },
            onSelect = { selected ->
                viewModel.setTerminalColorScheme(selected)
                showColorSchemeDialog = false
            },
        )
    }

    if (showLockTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text(if (zh) "锁定超时" else "Lock timeout") },
            text = {
                Column {
                    UserPreferencesRepository.LockTimeout.entries.forEach { timeout ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setLockTimeout(timeout)
                                showLockTimeoutDialog = false
                            },
                            headlineContent = { Text(lockTimeoutLabel(timeout, zh)) },
                            leadingContent = {
                                RadioButton(
                                    selected = lockTimeout == timeout,
                                    onClick = null,
                                )
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) {
                    Text(if (zh) "取消" else "Cancel")
                }
            },
        )
    }

    if (showSessionManagerDialog) {
        val sessionCmdOverride by viewModel.sessionCommandOverride.collectAsState()
        SessionManagerDialog(
            current = sessionManager,
            commandOverride = sessionCmdOverride,
            zh = zh,
            onDismiss = { showSessionManagerDialog = false },
            onSelect = { selected ->
                viewModel.setSessionManager(selected)
                showSessionManagerDialog = false
            },
            onCommandOverrideChange = viewModel::setSessionCommandOverride,
        )
    }

    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSize = fontSize,
            zh = zh,
            onDismiss = { showFontSizeDialog = false },
            onConfirm = { newSize ->
                viewModel.setTerminalFontSize(newSize)
                showFontSizeDialog = false
            },
        )
    }

    if (showToolbarConfigDialog) {
        ToolbarConfigDialog(
            layout = toolbarLayout,
            layoutJson = toolbarLayoutJson,
            zh = zh,
            onDismiss = { showToolbarConfigDialog = false },
            onSaveLayout = { layout ->
                viewModel.setToolbarLayout(layout)
                showToolbarConfigDialog = false
            },
            onSaveJson = { json ->
                viewModel.setToolbarLayoutJson(json)
                showToolbarConfigDialog = false
            },
        )
    }

    showBackupPasswordDialog?.let { action ->
        BackupPasswordDialog(
            isExport = action is BackupAction.Export,
            zh = zh,
            onDismiss = { showBackupPasswordDialog = null },
            onConfirm = { password ->
                showBackupPasswordDialog = null
                when (action) {
                    is BackupAction.Export -> {
                        pendingPassword = password
                        exportLauncher.launch("haven-backup.enc")
                    }
                    is BackupAction.Restore -> {
                        viewModel.importBackup(action.uri, password)
                    }
                }
            },
        )
    }
}

private sealed interface BackupAction {
    data object Export : BackupAction
    data class Restore(val uri: Uri) : BackupAction
}

@Composable
private fun BackupPasswordDialog(
    isExport: Boolean,
    zh: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val title = if (isExport) {
        if (zh) "导出备份" else "Export Backup"
    } else {
        if (zh) "恢复备份" else "Restore Backup"
    }
    val passwordError = if (isExport && password.length in 1..5) {
        if (zh) "至少 6 个字符" else "At least 6 characters"
    } else null
    val confirmError = if (isExport && confirmPassword.isNotEmpty() && confirmPassword != password) {
        if (zh) "两次密码不一致" else "Passwords don't match"
    } else null
    val canConfirm = if (isExport) {
        password.length >= 6 && password == confirmPassword
    } else {
        password.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = if (isExport) {
                        if (zh) {
                            "使用密码加密备份，以保护你的 SSH 密钥和连接数据。"
                        } else {
                            "Encrypt your backup with a password. This protects your SSH keys and connection data."
                        }
                    } else {
                        if (zh) "输入创建备份时使用的密码。" else "Enter the password used when the backup was created."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (zh) "密码" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isExport) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(if (zh) "确认密码" else "Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = confirmError != null,
                        supportingText = confirmError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = canConfirm) {
                Text(
                    if (isExport) {
                        if (zh) "导出" else "Export"
                    } else {
                        if (zh) "恢复" else "Restore"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (zh) "取消" else "Cancel") }
        },
    )
}

private const val GITHUB_URL = "https://github.com/hension-code/HavenX"

@Composable
private fun AboutDialog(
    versionName: String,
    versionCode: Long,
    zh: Boolean,
    onDismiss: () -> Unit,
    onOpenGitHub: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "关于 HavenX" else "About HavenX") },
        text = {
            Column {
                Text(
                    text = "HavenX",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (zh) "面向 Android 的开源远程连接客户端" else "Open source remote client for Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (zh) {
                        "版本 $versionName（构建 $versionCode）"
                    } else {
                        "Version $versionName (build $versionCode)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "关闭" else "Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenGitHub) {
                Text("GitHub")
            }
        },
    )
}

@Composable
private fun LanguageDialog(
    currentLanguage: UserPreferencesRepository.LanguageMode,
    onDismiss: () -> Unit,
    onSelect: (UserPreferencesRepository.LanguageMode) -> Unit,
) {
    val zh = Locale.current.language == "zh"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "语言" else "Language") },
        text = {
            Column {
                UserPreferencesRepository.LanguageMode.entries.forEach { mode ->
                    val label = when (mode) {
                        UserPreferencesRepository.LanguageMode.SYSTEM -> if (zh) "跟随系统" else "System default"
                        UserPreferencesRepository.LanguageMode.ENGLISH -> "English"
                        UserPreferencesRepository.LanguageMode.CHINESE_SIMPLIFIED -> "简体中文"
                    }
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(
                                selected = mode == currentLanguage,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable(role = Role.RadioButton) {
                            onSelect(mode)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}

@Composable
private fun ThemeDialog(
    currentTheme: UserPreferencesRepository.ThemeMode,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (UserPreferencesRepository.ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "主题" else "Theme") },
        text = {
            Column {
                UserPreferencesRepository.ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(themeModeLabel(mode, zh)) },
                        leadingContent = {
                            RadioButton(
                                selected = mode == currentTheme,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable(role = Role.RadioButton) {
                            onSelect(mode)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}

@Composable
private fun ColorSchemeDialog(
    currentScheme: UserPreferencesRepository.TerminalColorScheme,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (UserPreferencesRepository.TerminalColorScheme) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "终端配色方案" else "Terminal color scheme") },
        text = {
            Column {
                UserPreferencesRepository.TerminalColorScheme.entries.forEach { scheme ->
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(scheme.background))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(4.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "A",
                                        color = Color(scheme.foreground),
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(colorSchemeLabel(scheme, zh))
                            }
                        },
                        leadingContent = {
                            RadioButton(
                                selected = scheme == currentScheme,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable(role = Role.RadioButton) {
                            onSelect(scheme)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "取消" else "Cancel")
            }
        },
    )
}

@Composable
private fun SessionManagerDialog(
    current: UserPreferencesRepository.SessionManager,
    commandOverride: String?,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (UserPreferencesRepository.SessionManager) -> Unit,
    onCommandOverrideChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    var overrideText by remember(commandOverride) { mutableStateOf(commandOverride ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "会话持久化" else "Session persistence") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                UserPreferencesRepository.SessionManager.entries.forEach { manager ->
                    ListItem(
                        headlineContent = {
                            if (manager.url != null) {
                                Text(
                                    text = sessionManagerLabel(manager, zh),
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(manager.url))
                                        )
                                    },
                                )
                            } else {
                                Text(sessionManagerLabel(manager, zh))
                            }
                        },
                        supportingContent = if (!manager.supportsScrollback) {
                            { Text(if (zh) "不支持触摸回滚" else "No touch scrollback") }
                        } else null,
                        leadingContent = {
                            RadioButton(
                                selected = manager == current,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable(role = Role.RadioButton) {
                            onSelect(manager)
                        },
                    )
                }

                if (current != UserPreferencesRepository.SessionManager.NONE) {
                    Spacer(Modifier.height(12.dp))
                    val defaultCommand = current.command?.invoke("{name}") ?: ""
                    OutlinedTextField(
                        value = overrideText,
                        onValueChange = { overrideText = it },
                        label = { Text(if (zh) "自定义命令" else "Custom command") },
                        placeholder = { Text(defaultCommand, maxLines = 1) },
                        supportingText = {
                            Text(
                                if (zh) {
                                    "使用 {name} 作为会话名。留空则使用默认命令。"
                                } else {
                                    "Use {name} for session name. Leave blank for default."
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (overrideText != (commandOverride ?: "")) {
                        TextButton(
                            onClick = {
                                onCommandOverrideChange(overrideText.ifBlank { null })
                            },
                        ) {
                            Text(if (zh) "保存命令" else "Save command")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (zh) "关闭" else "Close")
            }
        },
    )
}

@Composable
private fun FontSizeDialog(
    currentSize: Int,
    zh: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(currentSize.toFloat()) }
    val displaySize = sliderValue.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "终端字体大小" else "Terminal font size") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (zh) "示例文本" else "Sample text",
                    fontFamily = FontFamily.Monospace,
                    fontSize = displaySize.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text(
                    text = "${displaySize}sp",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = UserPreferencesRepository.MIN_FONT_SIZE.toFloat()..
                        UserPreferencesRepository.MAX_FONT_SIZE.toFloat(),
                    steps = UserPreferencesRepository.MAX_FONT_SIZE -
                        UserPreferencesRepository.MIN_FONT_SIZE - 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(displaySize) }) {
                Text(if (zh) "确定" else "OK")
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
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

private fun themeModeLabel(mode: UserPreferencesRepository.ThemeMode, zh: Boolean): String =
    when (mode) {
        UserPreferencesRepository.ThemeMode.SYSTEM -> if (zh) "跟随系统" else "System default"
        UserPreferencesRepository.ThemeMode.LIGHT -> if (zh) "浅色" else "Light"
        UserPreferencesRepository.ThemeMode.DARK -> if (zh) "深色" else "Dark"
    }

private fun lockTimeoutLabel(timeout: UserPreferencesRepository.LockTimeout, zh: Boolean): String =
    when (timeout) {
        UserPreferencesRepository.LockTimeout.IMMEDIATE -> if (zh) "立即" else "Immediately"
        UserPreferencesRepository.LockTimeout.THIRTY_SECONDS -> if (zh) "30 秒" else "30 seconds"
        UserPreferencesRepository.LockTimeout.ONE_MINUTE -> if (zh) "1 分钟" else "1 minute"
        UserPreferencesRepository.LockTimeout.FIVE_MINUTES -> if (zh) "5 分钟" else "5 minutes"
        UserPreferencesRepository.LockTimeout.NEVER -> if (zh) "从不" else "Never"
    }

private fun sessionManagerLabel(
    manager: UserPreferencesRepository.SessionManager,
    zh: Boolean,
): String = when (manager) {
    UserPreferencesRepository.SessionManager.NONE -> if (zh) "无" else "None"
    else -> manager.label
}

private fun colorSchemeLabel(
    scheme: UserPreferencesRepository.TerminalColorScheme,
    zh: Boolean,
): String = when (scheme) {
    UserPreferencesRepository.TerminalColorScheme.HAVEN -> "Haven"
    UserPreferencesRepository.TerminalColorScheme.CLASSIC_GREEN -> if (zh) "经典绿色" else "Classic Green"
    UserPreferencesRepository.TerminalColorScheme.LIGHT -> if (zh) "浅色" else "Light"
    UserPreferencesRepository.TerminalColorScheme.SOLARIZED_DARK -> if (zh) "Solarized 深色" else "Solarized Dark"
    UserPreferencesRepository.TerminalColorScheme.DRACULA -> "Dracula"
    UserPreferencesRepository.TerminalColorScheme.MONOKAI -> "Monokai"
}

/** Assignment for a key in the toolbar config dialog. */
private enum class KeyAssignment { ROW1, ROW2, OFF }

@Composable
private fun ToolbarConfigDialog(
    layout: ToolbarLayout,
    layoutJson: String,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSaveLayout: (ToolbarLayout) -> Unit,
    onSaveJson: (String) -> Unit,
) {
    var advancedMode by remember { mutableStateOf(false) }

    if (advancedMode) {
        ToolbarJsonEditor(
            json = layoutJson,
            zh = zh,
            onDismiss = onDismiss,
            onSave = onSaveJson,
            onSimpleMode = { advancedMode = false },
        )
    } else {
        ToolbarSimpleEditor(
            layout = layout,
            zh = zh,
            onDismiss = onDismiss,
            onSave = onSaveLayout,
            onAdvancedMode = { advancedMode = true },
        )
    }
}

@Composable
private fun ToolbarSimpleEditor(
    layout: ToolbarLayout,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSave: (ToolbarLayout) -> Unit,
    onAdvancedMode: () -> Unit,
) {
    // Build assignment map from current layout (built-in keys only)
    val row1BuiltIns = remember(layout) {
        layout.row1.filterIsInstance<ToolbarItem.BuiltIn>().map { it.key }.toSet()
    }
    val row2BuiltIns = remember(layout) {
        layout.row2.filterIsInstance<ToolbarItem.BuiltIn>().map { it.key }.toSet()
    }

    var assignments by remember(layout) {
        mutableStateOf(
            ToolbarKey.entries.associateWith { key ->
                when (key) {
                    in row1BuiltIns -> KeyAssignment.ROW1
                    in row2BuiltIns -> KeyAssignment.ROW2
                    else -> KeyAssignment.OFF
                }
            }
        )
    }

    // Track whether layout has custom keys (can't be edited in simple mode)
    val hasCustomKeys = remember(layout) {
        layout.rows.any { row -> row.any { it is ToolbarItem.Custom } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "键盘工具栏" else "Keyboard toolbar") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (zh) "为每个按键分配到第 1 行、第 2 行或关闭" else "Assign each key to Row 1, Row 2, or Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                if (hasCustomKeys) {
                    Text(
                        text = if (zh) "自定义按键会被保留。使用“编辑 JSON”可进行完整控制。" else "Custom keys are preserved. Use Edit JSON for full control.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                Text(
                    if (zh) "功能键" else "Function keys",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                ToolbarKey.entries.filter { it.isAction || it.isModifier }.forEach { key ->
                    ToolbarKeyRow(
                        label = key.label,
                        zh = zh,
                        assignment = assignments[key] ?: KeyAssignment.OFF,
                        onAssign = { assignments = assignments + (key to it) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    if (zh) "符号" else "Symbols",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ToolbarKey.entries.filter { !it.isAction && !it.isModifier }.forEach { key ->
                    ToolbarKeyRow(
                        label = key.label,
                        zh = zh,
                        assignment = assignments[key] ?: KeyAssignment.OFF,
                        onAssign = { assignments = assignments + (key to it) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Preserve custom keys in their original rows
                val customRow1 = layout.row1.filterIsInstance<ToolbarItem.Custom>()
                val customRow2 = layout.row2.filterIsInstance<ToolbarItem.Custom>()
                val newRow1 = ToolbarKey.entries
                    .filter { assignments[it] == KeyAssignment.ROW1 }
                    .map { ToolbarItem.BuiltIn(it) } + customRow1
                val newRow2 = ToolbarKey.entries
                    .filter { assignments[it] == KeyAssignment.ROW2 }
                    .map { ToolbarItem.BuiltIn(it) } + customRow2
                onSave(ToolbarLayout(listOf(newRow1, newRow2)))
            }) {
                Text(if (zh) "保存" else "Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    assignments = ToolbarKey.entries.associateWith { key ->
                        when (key) {
                            in ToolbarKey.DEFAULT_ROW1 -> KeyAssignment.ROW1
                            in ToolbarKey.DEFAULT_ROW2 -> KeyAssignment.ROW2
                            else -> KeyAssignment.OFF
                        }
                    }
                    }) {
                    Text(if (zh) "重置" else "Reset")
                }
                TextButton(onClick = onAdvancedMode) {
                    Text(if (zh) "编辑 JSON" else "Edit JSON")
                }
                TextButton(onClick = onDismiss) {
                    Text(if (zh) "取消" else "Cancel")
                }
            }
        },
    )
}

@Composable
private fun ToolbarJsonEditor(
    json: String,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSimpleMode: () -> Unit,
) {
    var text by remember(json) { mutableStateOf(json) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (zh) "编辑工具栏 JSON" else "Edit toolbar JSON") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (zh) {
                        "字符串 = 内置按键 ID，对象 = 自定义按键 {\"label\": \"...\", \"send\": \"...\"}"
                    } else {
                        "String = built-in key ID, Object = custom key {\"label\": \"...\", \"send\": \"...\"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (zh) {
                        "内置 ID：keyboard, esc, tab, shift, ctrl, alt, arrow_left, arrow_up, arrow_down, arrow_right, home, end, pgup, pgdn, sym_pipe, sym_tilde, sym_slash, sym_dash, sym_underscore, sym_equals, sym_plus, sym_backslash, sym_squote, sym_dquote, sym_semicolon, sym_colon, sym_bang, sym_question, sym_at, sym_hash, sym_dollar, sym_percent, sym_caret, sym_amp, sym_star, sym_lparen, sym_rparen, sym_lbracket, sym_rbracket, sym_lbrace, sym_rbrace, sym_lt, sym_gt, sym_backtick"
                    } else {
                        "Built-in IDs: keyboard, esc, tab, shift, ctrl, alt, arrow_left, arrow_up, arrow_down, arrow_right, home, end, pgup, pgdn, sym_pipe, sym_tilde, sym_slash, sym_dash, sym_underscore, sym_equals, sym_plus, sym_backslash, sym_squote, sym_dquote, sym_semicolon, sym_colon, sym_bang, sym_question, sym_at, sym_hash, sym_dollar, sym_percent, sym_caret, sym_amp, sym_star, sym_lparen, sym_rparen, sym_lbracket, sym_rbracket, sym_lbrace, sym_rbrace, sym_lt, sym_gt, sym_backtick"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val validationError = ToolbarLayout.validate(text)
                if (validationError != null) {
                    error = validationError
                } else {
                    onSave(text)
                }
            }) {
                Text(if (zh) "保存" else "Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    text = ToolbarLayout.DEFAULT.toJson()
                    error = null
                }) {
                    Text(if (zh) "重置" else "Reset")
                }
                TextButton(onClick = onSimpleMode) {
                    Text(if (zh) "简易模式" else "Simple")
                }
                TextButton(onClick = onDismiss) {
                    Text(if (zh) "取消" else "Cancel")
                }
            }
        },
    )
}

@Composable
private fun ToolbarKeyRow(
    label: String,
    zh: Boolean,
    assignment: KeyAssignment,
    onAssign: (KeyAssignment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        KeyAssignment.entries.forEach { option ->
            FilterChip(
                selected = assignment == option,
                onClick = { onAssign(option) },
                label = {
                    Text(
                        when (option) {
                            KeyAssignment.ROW1 -> "R1"
                            KeyAssignment.ROW2 -> "R2"
                            KeyAssignment.OFF -> if (zh) "关" else "Off"
                        },
                        fontSize = 11.sp,
                    )
                },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
