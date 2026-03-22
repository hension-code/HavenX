package sh.haven.feature.sftp

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(
    pendingSmbProfileId: String? = null,
    viewModel: SftpViewModel = hiltViewModel(),
) {
    val zh = ComposeLocale.current.language == "zh"
    val connectedProfiles by viewModel.connectedProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val lastDownload by viewModel.lastDownload.collectAsState()

    LaunchedEffect(pendingSmbProfileId) {
        pendingSmbProfileId?.let { viewModel.setPendingSmbProfile(it) }
    }

    viewModel.syncConnectedProfiles()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(lastDownload) {
        val dl = lastDownload ?: return@LaunchedEffect
        viewModel.dismissMessage() // clear the plain message so it doesn't double-show
        val result = snackbarHostState.showSnackbar(
            message = if (zh) "已下载 ${dl.fileName}" else "Downloaded ${dl.fileName}",
            actionLabel = if (zh) "打开" else "Open",
            duration = androidx.compose.material3.SnackbarDuration.Long,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            try {
                val mimeType = context.contentResolver.getType(dl.uri) ?: "*/*"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(dl.uri, mimeType)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(if (zh) "未找到可打开该文件的应用" else "No app found to open this file")
            }
        }
        viewModel.clearLastDownload()
    }

    LaunchedEffect(message) {
        // Only show plain messages when there's no download result (download has its own snackbar)
        val msg = message ?: return@LaunchedEffect
        if (lastDownload == null) {
            snackbarHostState.showSnackbar(msg)
        }
        viewModel.dismissMessage()
    }

    // File picker for upload
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Query the actual display name from the content resolver
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
            viewModel.uploadFile(fileName, uri)
        }
    }

    // Directory picker for download
    var pendingDownload by remember { mutableStateOf<SftpEntry?>(null) }
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            pendingDownload?.let { entry ->
                viewModel.downloadFile(entry, uri)
            }
        }
        pendingDownload = null
    }

    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentPath,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (currentPath != "/" && activeProfileId != null) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, if (zh) "返回上级目录" else "Navigate up")
                        }
                    }
                },
                actions = {
                    if (activeProfileId != null) {
                        IconButton(onClick = { viewModel.toggleShowHidden() }) {
                            Icon(
                                if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (showHidden) {
                                    if (zh) "隐藏隐藏文件" else "Hide hidden files"
                                } else {
                                    if (zh) "显示隐藏文件" else "Show hidden files"
                                },
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, if (zh) "排序" else "Sort")
                            }
                            SortDropdown(
                                expanded = showSortMenu,
                                currentMode = sortMode,
                                zh = zh,
                                onDismiss = { showSortMenu = false },
                                onSelect = { mode ->
                                    viewModel.setSortMode(mode)
                                    showSortMenu = false
                                },
                            )
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, if (zh) "刷新" else "Refresh")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (activeProfileId != null) {
                FloatingActionButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Upload, if (zh) "上传文件" else "Upload file")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (loading) {
                val progress = transferProgress
                if (progress != null && progress.totalBytes > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                progress.fileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${Formatter.formatFileSize(context, progress.transferredBytes)} / ${Formatter.formatFileSize(context, progress.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (connectedProfiles.isEmpty()) {
                EmptyState(zh = zh)
            } else {
                // Server tabs
                if (connectedProfiles.size > 1) {
                    val activeIndex = connectedProfiles.indexOfFirst { it.id == activeProfileId }
                        .coerceAtLeast(0)
                    PrimaryScrollableTabRow(
                        selectedTabIndex = activeIndex,
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 8.dp,
                    ) {
                        connectedProfiles.forEach { profile ->
                            Tab(
                                selected = profile.id == activeProfileId,
                                onClick = { viewModel.selectProfile(profile.id) },
                                text = { Text(profile.label, maxLines = 1) },
                            )
                        }
                    }
                }

                // File list
                if (entries.isEmpty() && !loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (zh) "空目录" else "Empty directory",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.path }) { entry ->
                            FileListItem(
                                entry = entry,
                                onTap = {
                                    if (entry.isDirectory) {
                                        viewModel.navigateTo(entry.path)
                                    }
                                },
                                onDownload = {
                                    pendingDownload = entry
                                    downloadLauncher.launch(entry.name)
                                },
                                onDelete = { viewModel.deleteEntry(entry) },
                                onCopyPath = {
                                    clipboardManager.setText(AnnotatedString(entry.path))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(if (zh) "路径已复制" else "Path copied")
                                    }
                                },
                                zh = zh,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    entry: SftpEntry,
    onTap: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCopyPath: () -> Unit,
    zh: Boolean,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Box {
        ListItem(
            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val sizeText = if (entry.isDirectory) {
                    if (zh) "文件夹" else "Directory"
                } else {
                    Formatter.formatFileSize(context, entry.size)
                }
                val dateText = dateFormat.format(Date(entry.modifiedTime * 1000))
                Text("$sizeText  $dateText")
            },
            leadingContent = {
                Icon(
                    if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                    contentDescription = if (entry.isDirectory) {
                        if (zh) "文件夹" else "Directory"
                    } else {
                        if (zh) "文件" else "File"
                    },
                    tint = if (entry.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            modifier = Modifier.combinedClickable(
                onClick = onTap,
                onLongClick = { showMenu = true },
            ),
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (!entry.isDirectory) {
                DropdownMenuItem(
                    text = { Text(if (zh) "下载" else "Download") },
                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                    onClick = { showMenu = false; onDownload() },
                )
            }
            DropdownMenuItem(
                text = { Text(if (zh) "复制路径" else "Copy path") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = { showMenu = false; onCopyPath() },
            )
            DropdownMenuItem(
                text = { Text(if (zh) "删除" else "Delete") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { showMenu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun SortDropdown(
    expanded: Boolean,
    currentMode: SortMode,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SortMode) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SortMode.entries.forEach { mode ->
            val label = when (mode) {
                SortMode.NAME_ASC -> if (zh) "名称 A-Z" else "Name A-Z"
                SortMode.NAME_DESC -> if (zh) "名称 Z-A" else "Name Z-A"
                SortMode.SIZE_ASC -> if (zh) "大小（小->大）" else "Size (smallest)"
                SortMode.SIZE_DESC -> if (zh) "大小（大->小）" else "Size (largest)"
                SortMode.DATE_ASC -> if (zh) "日期（最早）" else "Date (oldest)"
                SortMode.DATE_DESC -> if (zh) "日期（最新）" else "Date (newest)"
            }
            DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = {
                    RadioButton(
                        selected = mode == currentMode,
                        onClick = null,
                    )
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun EmptyState(zh: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (zh) "SFTP 文件浏览器" else "SFTP File Browser",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            if (zh) "连接到服务器后可浏览文件" else "Connect to a server to browse files",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
