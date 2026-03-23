package sh.haven.feature.sftp

import android.text.format.Formatter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
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
    val transfers by viewModel.transfers.collectAsState()
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

    val downloadActionLabel = stringResource(R.string.open)
    val noAppMessage = stringResource(R.string.no_app_found_to_open)

    LaunchedEffect(lastDownload) {
        val dl = lastDownload ?: return@LaunchedEffect
        viewModel.dismissMessage() // clear the plain message so it doesn't double-show
        
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.downloaded_dl_filename, dl.fileName),
            actionLabel = downloadActionLabel,
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
                snackbarHostState.showSnackbar(noAppMessage)
            }
        }
        viewModel.clearLastDownload()
    }

    val noPreviewAppMessage = stringResource(R.string.no_app_found_to_preview)

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
            snackbarHostState.showSnackbar(noPreviewAppMessage)
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



    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showSortMenu by remember { mutableStateOf(false) }
    var showTransfersMenu by remember { mutableStateOf(false) }
    var showFavoritesMenu by remember { mutableStateOf(false) }
    var pathEditMode by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(TextFieldValue(currentPath)) }
    var pathEditorEverFocused by remember { mutableStateOf(false) }
    var imeVisibleWhilePathEditing by remember { mutableStateOf(false) }
    val pathFocusRequester = remember { FocusRequester() }
    var requestPathFocus by remember { mutableStateOf(false) }
    LaunchedEffect(currentPath, pathEditMode) {
        if (!pathEditMode) pathInput = TextFieldValue(currentPath)
    }
    LaunchedEffect(pathEditMode) {
        if (!pathEditMode) {
            requestPathFocus = false
            imeVisibleWhilePathEditing = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    LaunchedEffect(pathEditMode, isActive, imeVisible) {
        if (!pathEditMode || !isActive) return@LaunchedEffect
        if (imeVisible) {
            imeVisibleWhilePathEditing = true
        } else if (imeVisibleWhilePathEditing) {
            requestPathFocus = false
            focusManager.clearFocus(force = true)
            pathEditMode = false
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { viewModel.goBack() }, enabled = canInteract && canGoBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                        IconButton(onClick = { viewModel.goForward() }, enabled = canInteract && canGoForward) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
                        }
                        IconButton(onClick = { viewModel.navigateUp() }, enabled = canInteract && currentPath != "/") {
                            Icon(Icons.Filled.ArrowUpward, stringResource(R.string.up))
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = canInteract) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.refresh))
                        }
                        
                        Box {
                            val activeCount = transfers.count { it.state == TransferState.DOWNLOADING || it.state == TransferState.PAUSED }
                            IconButton(onClick = { showTransfersMenu = true }, enabled = canInteract) {
                                BadgedBox(
                                    badge = {
                                        if (activeCount > 0) {
                                            Badge { Text(activeCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Download, if (zh) "传输任务" else "Transfers")
                                }
                            }
                            TransfersDropdown(
                                expanded = showTransfersMenu,
                                transfers = transfers,
                                zh = zh,
                                onDismiss = { showTransfersMenu = false },
                                onPause = { viewModel.pauseTransfer(it) },
                                onResume = { viewModel.resumeTransfer(it) },
                                onCancel = { viewModel.cancelTransfer(it) },
                                onRemove = { viewModel.removeTransfer(it) },
                            )
                        }

                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }, enabled = canInteract) {
                                Icon(Icons.Filled.MoreVert, if (zh) "更多" else "More")
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(if (showHidden) stringResource(R.string.hide_hidden_files) else stringResource(R.string.show_hidden_files)) },
                                    onClick = { 
                                        viewModel.toggleShowHidden() 
                                        showMoreMenu = false
                                    },
                                    leadingIcon = { 
                                        Icon(if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null) 
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort)) },
                                    onClick = { 
                                        showMoreMenu = false
                                        showSortMenu = true 
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorite_folders)) },
                                    onClick = { 
                                        showMoreMenu = false
                                        showFavoritesMenu = true 
                                    },
                                    leadingIcon = { Icon(Icons.Filled.StarBorder, null) }
                                )
                            }
                            
                            // Nested dropdowns (their anchor is technically irrelevant as long as it draws)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.go))
                        }
                        IconButton(
                            onClick = {
                                focusManager.clearFocus(force = true)
                                pathEditMode = false
                            },
                        ) {
                            Icon(Icons.Filled.Close, stringResource(R.string.cancel))
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
                            Icon(Icons.Filled.Edit, stringResource(R.string.edit_path))
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
                    Icon(Icons.Filled.Upload, stringResource(R.string.upload_file))
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
                            stringResource(R.string.empty_directory),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries, key = { it.path }) { entry ->
                            val pathCopiedMsg = stringResource(R.string.path_copied)
                            FileListItem(
                                entry = entry,
                                onTap = {
                                    if (entry.isDirectory) {
                                        viewModel.navigateTo(entry.path)
                                    }
                                },
                                onDownload = {
                                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                    val havenDir = java.io.File(downloadsDir, "HavenX")
                                    if (!havenDir.exists()) havenDir.mkdirs()
                                    val destFile = java.io.File(havenDir, entry.name)
                                    viewModel.downloadFile(entry, android.net.Uri.fromFile(destFile))
                                },
                                onDelete = { viewModel.deleteEntry(entry) },
                                onPreview = { viewModel.previewMedia(entry) },
                                isFavoriteDirectory = viewModel.isFavoriteDirectory(entry.path),
                                onToggleFavorite = { viewModel.toggleFavoriteDirectory(entry.path) },
                                onCopyPath = {
                                    clipboardManager.setText(AnnotatedString(entry.path))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(pathCopiedMsg)
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
    var pressOffset by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxWidth()) {
        val fileVisual = remember(entry.name, entry.isDirectory) { resolveFileVisual(entry) }
        ListItem(
            headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val sizeText = if (entry.isDirectory) {
                    stringResource(R.string.directory)
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
                    Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.favorite))
                }
            },
            modifier = Modifier.pointerInput(entry.name) {
                detectTapGestures(
                    onTap = {
                        if (entry.isDirectory) {
                            onTap()
                        } else if (isMediaPreviewSupported(entry.name)) {
                            onPreview()
                        }
                    },
                    onLongPress = { offset ->
                        pressOffset = androidx.compose.ui.unit.DpOffset(
                            x = with(density) { offset.x.toDp() },
                            y = with(density) { offset.y.toDp() }
                        )
                        showMenu = true
                    }
                )
            },
        )

        if (showMenu) {
            val positionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val touchX = anchorBounds.left + with(density) { pressOffset.x.roundToPx() }
                        val touchY = anchorBounds.top + with(density) { pressOffset.y.roundToPx() }
                        
                        // If touched on the left half, popup appears entirely to the right of the finger
                        // If touched on the right half, popup appears entirely to the left of the finger
                        val isLeftHalf = touchX < windowSize.width / 2
                        val targetX = if (isLeftHalf) touchX else touchX - popupContentSize.width
                        
                        val yOffset = with(density) { 24.dp.roundToPx() }
                        val targetY = touchY + yOffset
                        
                        val x = targetX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val y = targetY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .wrapContentWidth()
                        .widthIn(max = 340.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                            .padding(vertical = 8.dp),
                    ) {
                        if (!entry.isDirectory) {
                            PopupMenuRow(
                                text = stringResource(R.string.download),
                                icon = { Icon(Icons.Filled.Download, null) },
                                onClick = { showMenu = false; onDownload() },
                            )
                        } else {
                            PopupMenuRow(
                                text = if (isFavoriteDirectory) {
                                    stringResource(R.string.remove_favorite)
                                } else {
                                    stringResource(R.string.add_favorite)
                                },
                                icon = {
                                    Icon(
                                        if (isFavoriteDirectory) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        null,
                                    )
                                },
                                onClick = { showMenu = false; onToggleFavorite() },
                            )
                        }
                        PopupMenuRow(
                            text = stringResource(R.string.copy_path),
                            icon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { showMenu = false; onCopyPath() },
                        )
                        PopupMenuRow(
                            text = stringResource(R.string.delete),
                            icon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupMenuRow(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(text)
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
        "css", "html", "xml", "json", "yaml", "yml", "sh", "bat", "ps1"
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
fun TransfersDropdown(
    expanded: Boolean,
    transfers: List<TransferTask>,
    zh: Boolean,
    onDismiss: () -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = androidx.compose.ui.Modifier.widthIn(max = 350.dp, min = 250.dp)
    ) {
        if (transfers.isEmpty()) {
            DropdownMenuItem(
                text = { Text(if (zh) "没有活动的传输任务" else "No active transfers") },
                onClick = { }
            )
        } else {
            transfers.forEach { task ->
                TransferItem(
                    task = task,
                    zh = zh,
                    onPause = { onPause(task.id) },
                    onResume = { onResume(task.id) },
                    onCancel = { onCancel(task.id) },
                    onRemove = { onRemove(task.id) }
                )
            }
        }
    }
}

@Composable
fun TransferItem(
    task: TransferTask,
    zh: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (task.isUpload) Icons.Filled.Upload else Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(task.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            when (task.state) {
                TransferState.DOWNLOADING -> {
                    IconButton(onClick = onPause, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Pause, null) }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, null) }
                }
                TransferState.PAUSED, TransferState.ERROR -> {
                    IconButton(onClick = onResume, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.PlayArrow, null) }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, null) }
                }
                TransferState.DONE, TransferState.CANCELLED -> {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, null) }
                }
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        androidx.compose.material3.LinearProgressIndicator(progress = { task.fraction }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.size(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val statusText = when (task.state) {
                TransferState.DOWNLOADING -> "${android.text.format.Formatter.formatFileSize(context, task.transferredBytes)} / ${android.text.format.Formatter.formatFileSize(context, task.totalBytes)}"
                TransferState.PAUSED -> if (zh) "已暂停" else "Paused"
                TransferState.ERROR -> if (zh) "出错" else "Error"
                TransferState.DONE -> if (zh) "完成" else "Done"
                TransferState.CANCELLED -> if (zh) "已取消" else "Cancelled"
            }
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                text = { Text(stringResource(R.string.no_favorites_yet)) },
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
                    contentDescription = stringResource(R.string.root),
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
                SortMode.NAME_ASC -> stringResource(R.string.name_a_z)
                SortMode.NAME_DESC -> stringResource(R.string.name_z_a)
                SortMode.SIZE_ASC -> stringResource(R.string.size_smallest)
                SortMode.SIZE_DESC -> stringResource(R.string.size_largest)
                SortMode.DATE_ASC -> stringResource(R.string.date_oldest)
                SortMode.DATE_DESC -> stringResource(R.string.date_newest)
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
            stringResource(R.string.sftp_file_browser),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.connect_to_a_server_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
