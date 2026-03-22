package sh.haven.feature.sftp

import android.text.format.Formatter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(
    pendingSmbProfileId: String? = null,
    isActive: Boolean = true,
    viewModel: SftpViewModel = hiltViewModel(),
) {
    val zh = ComposeLocale.current.language == "zh"
    val connectedProfiles by viewModel.connectedProfiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val favoriteDirectories by viewModel.favoriteDirectories.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    val error by viewModel.error.collectAsState()
    val message by viewModel.message.collectAsState()
    val lastDownload by viewModel.lastDownload.collectAsState()
    val lastPreview by viewModel.lastPreview.collectAsState()

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

    LaunchedEffect(lastPreview) {
        val preview = lastPreview ?: return@LaunchedEffect
        try {
            val intent = when (preview.mediaType) {
                MediaTypeResolver.MediaType.IMAGE -> Intent().setClassName(
                    context.packageName,
                    "com.hension.havenx.ImagePreviewActivity",
                )
                MediaTypeResolver.MediaType.VIDEO,
                MediaTypeResolver.MediaType.AUDIO -> Intent().setClassName(
                    context.packageName,
                    "com.hension.havenx.PlayerPreviewActivity",
                )
                MediaTypeResolver.MediaType.UNSUPPORTED -> Intent(Intent.ACTION_VIEW).apply {
                    val uri = preview.uri ?: throw IllegalStateException("Missing preview uri")
                    setDataAndType(uri, preview.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.apply {
                if (preview.mediaType != MediaTypeResolver.MediaType.UNSUPPORTED) {
                    putExtra("FILE_PATH", preview.filePath)
                    putExtra("REMOTE_PATH", preview.remotePath)
                    putExtra("STREAM_URL", preview.streamUrl)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(if (zh) "未找到可预览该媒体的应用" else "No app found to preview this media")
        } finally {
            viewModel.clearLastPreview()
        }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFavoritesMenu by remember { mutableStateOf(false) }
    var pathEditMode by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(TextFieldValue(currentPath)) }
    var pathEditorEverFocused by remember { mutableStateOf(false) }
    val pathFocusRequester = remember { FocusRequester() }
    var requestPathFocus by remember { mutableStateOf(false) }
    LaunchedEffect(currentPath, pathEditMode) {
        if (!pathEditMode) pathInput = TextFieldValue(currentPath)
    }
    LaunchedEffect(pathEditMode) {
        if (!pathEditMode) {
            requestPathFocus = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            requestPathFocus = false
            pathEditMode = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            requestPathFocus = false
            pathEditMode = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                requestPathFocus = false
                pathEditMode = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val canInteract = activeProfileId != null
                    val actionScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(actionScroll),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { viewModel.goBack() }, enabled = canInteract && canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, if (zh) "后退" else "Back")
                        }
                        IconButton(onClick = { viewModel.goForward() }, enabled = canInteract && canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, if (zh) "前进" else "Forward")
                        }
                        IconButton(onClick = { viewModel.navigateUp() }, enabled = canInteract && currentPath != "/") {
                            Icon(Icons.Filled.ArrowUpward, if (zh) "上级目录" else "Up")
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = canInteract) {
                            Icon(Icons.Filled.Refresh, if (zh) "刷新" else "Refresh")
                        }
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
                            IconButton(onClick = { showSortMenu = true }, enabled = canInteract) {
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
                        Box {
                            IconButton(onClick = { showFavoritesMenu = true }, enabled = canInteract) {
                                Icon(Icons.Filled.StarBorder, if (zh) "收藏目录" else "Favorite folders")
                            }
                            FavoritesDropdown(
                                expanded = showFavoritesMenu,
                                favorites = favoriteDirectories.toList().sorted(),
                                zh = zh,
                                onDismiss = { showFavoritesMenu = false },
                                onSelect = { path ->
                                    viewModel.navigateTo(path)
                                    showFavoritesMenu = false
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (pathEditMode) {
                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(pathFocusRequester)
                                .onGloballyPositioned {
                                    if (isActive && pathEditMode && requestPathFocus) {
                                        pathFocusRequester.requestFocus()
                                        requestPathFocus = false
                                    }
                                }
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        pathEditorEverFocused = true
                                    } else if (pathEditMode && pathEditorEverFocused) {
                                        pathEditMode = false
                                    }
                                },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                viewModel.navigateToInput(pathInput.text)
                                focusManager.clearFocus()
                                pathEditMode = false
                            }),
                        )
                        IconButton(
                            onClick = {
                                viewModel.navigateToInput(pathInput.text)
                                focusManager.clearFocus()
                                pathEditMode = false
                            },
                            enabled = activeProfileId != null,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, if (zh) "跳转" else "Go")
                        }
                        IconButton(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                pathEditMode = false
                            },
                        ) {
                            Icon(Icons.Filled.Close, if (zh) "取消编辑" else "Cancel")
                        }
                    } else {
                        BreadcrumbPathBar(
                            path = currentPath,
                            zh = zh,
                            onSegmentClick = { segmentPath -> viewModel.navigateTo(segmentPath) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val editPath = ensureEditablePath(currentPath)
                                pathInput = TextFieldValue(
                                    text = editPath,
                                    selection = TextRange(editPath.length),
                                )
                                pathEditorEverFocused = false
                                requestPathFocus = isActive
                                pathEditMode = true
                            },
                            enabled = activeProfileId != null,
                        ) {
                            Icon(Icons.Filled.Edit, if (zh) "编辑路径" else "Edit path")
                        }
                    }
                    }
                }
            }
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
                .padding(innerPadding)
                .clickable(
                    enabled = pathEditMode,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    focusManager.clearFocus(force = true)
                    pathEditMode = false
                },
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
                                onPreview = { viewModel.previewMedia(entry) },
                                isFavoriteDirectory = viewModel.isFavoriteDirectory(entry.path),
                                onToggleFavorite = { viewModel.toggleFavoriteDirectory(entry.path) },
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
    onPreview: () -> Unit,
    isFavoriteDirectory: Boolean,
    onToggleFavorite: () -> Unit,
    onCopyPath: () -> Unit,
    zh: Boolean,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Box {
        val fileVisual = remember(entry.name, entry.isDirectory) { resolveFileVisual(entry) }
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
                    imageVector = fileVisual.icon,
                    contentDescription = if (zh) fileVisual.zhLabel else fileVisual.enLabel,
                    tint = fileVisual.tintColor(MaterialTheme.colorScheme),
                )
            },
            trailingContent = {
                if (entry.isDirectory && isFavoriteDirectory) {
                    Icon(Icons.Filled.Star, contentDescription = if (zh) "已收藏" else "Favorite")
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (entry.isDirectory) {
                        onTap()
                    } else if (isMediaPreviewSupported(entry.name)) {
                        onPreview()
                    }
                },
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
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isFavoriteDirectory) {
                                if (zh) "取消收藏目录" else "Remove favorite"
                            } else {
                                if (zh) "收藏目录" else "Add favorite"
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isFavoriteDirectory) Icons.Filled.Star else Icons.Filled.StarBorder,
                            null,
                        )
                    },
                    onClick = { showMenu = false; onToggleFavorite() },
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

private data class FileVisual(
    val icon: ImageVector,
    val zhLabel: String,
    val enLabel: String,
    val tintColor: (androidx.compose.material3.ColorScheme) -> androidx.compose.ui.graphics.Color,
)

private fun resolveFileVisual(entry: SftpEntry): FileVisual {
    if (entry.isDirectory) {
        return FileVisual(
            icon = Icons.Filled.Folder,
            zhLabel = "文件夹",
            enLabel = "Directory",
            tintColor = { it.primary },
        )
    }

    val ext = entry.name.substringAfterLast('.', "").lowercase()
    val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    val videoExt = setOf("mp4", "m4v", "mkv", "mov", "avi", "webm", "ts")
    val audioExt = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus")
    val pdfExt = setOf("pdf")
    val textExt = setOf("txt", "md", "rtf", "log")
    val codeExt = setOf(
        "kt", "java", "js", "ts", "tsx", "jsx", "py", "go", "rs", "c", "cpp", "h",
        "hpp", "cs", "php", "rb", "swift", "xml", "json", "yaml", "yml", "toml", "sh",
        "sql", "gradle", "kts",
    )
    val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")

    return when {
        ext in imageExt -> FileVisual(
            icon = Icons.Filled.Image,
            zhLabel = "图片",
            enLabel = "Image",
            tintColor = { it.tertiary },
        )
        ext in videoExt -> FileVisual(
            icon = Icons.Filled.Movie,
            zhLabel = "视频",
            enLabel = "Video",
            tintColor = { it.secondary },
        )
        ext in audioExt -> FileVisual(
            icon = Icons.Filled.MusicNote,
            zhLabel = "音频",
            enLabel = "Audio",
            tintColor = { it.secondary },
        )
        ext in pdfExt -> FileVisual(
            icon = Icons.Filled.PictureAsPdf,
            zhLabel = "PDF 文档",
            enLabel = "PDF document",
            tintColor = { it.error },
        )
        ext in codeExt -> FileVisual(
            icon = Icons.Filled.Code,
            zhLabel = "代码文件",
            enLabel = "Code file",
            tintColor = { it.primary },
        )
        ext in textExt -> FileVisual(
            icon = Icons.Filled.TextSnippet,
            zhLabel = "文本文件",
            enLabel = "Text file",
            tintColor = { it.onSurfaceVariant },
        )
        ext in archiveExt -> FileVisual(
            icon = Icons.Filled.Archive,
            zhLabel = "压缩文件",
            enLabel = "Archive",
            tintColor = { it.primary },
        )
        else -> FileVisual(
            icon = Icons.Filled.Description,
            zhLabel = "文件",
            enLabel = "File",
            tintColor = { it.onSurfaceVariant },
        )
    }
}

@Composable
private fun FavoritesDropdown(
    expanded: Boolean,
    favorites: List<String>,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (favorites.isEmpty()) {
            DropdownMenuItem(
                text = { Text(if (zh) "暂无收藏目录" else "No favorites yet") },
                onClick = onDismiss,
            )
            return@DropdownMenu
        }
        favorites.forEach { path ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = path,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Star, null) },
                onClick = { onSelect(path) },
            )
        }
    }
}

private fun isMediaPreviewSupported(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext.isBlank()) return false
    val image = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
    val video = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi")
    val audio = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "flac")
    return ext in image || ext in video || ext in audio
}

@Composable
private fun BreadcrumbPathBar(
    path: String,
    zh: Boolean,
    onSegmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val segments = remember(path) { buildPathSegments(path) }
    Row(
        modifier = modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            if (index == 0) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = if (zh) "根目录" else "Root",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onSegmentClick(segment.second) },
                )
            } else {
                Text("  >  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = segment.first,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSegmentClick(segment.second) },
                )
            }
        }
    }
}

private fun buildPathSegments(path: String): List<Pair<String, String>> {
    if (path == "/") return listOf("root" to "/")
    val raw = path.trim().trim('/')
    if (raw.isEmpty()) return listOf("root" to "/")
    val parts = raw.split('/').filter { it.isNotBlank() }
    val result = mutableListOf<Pair<String, String>>("root" to "/")
    var current = ""
    for (p in parts) {
        current += "/$p"
        result += p to current
    }
    return result
}

private fun ensureEditablePath(path: String): String {
    if (path == "/") return "/"
    return if (path.endsWith("/")) path else "$path/"
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
