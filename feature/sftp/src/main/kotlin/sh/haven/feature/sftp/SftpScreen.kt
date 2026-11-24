package sh.haven.feature.sftp

import android.text.format.Formatter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import sh.haven.core.data.db.entities.DownloadRecord
import kotlinx.coroutines.delay
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
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val favoriteDirectories by viewModel.favoriteDirectories.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val transfers by viewModel.transfers.collectAsState()
    val downloadHistory by viewModel.downloadHistory.collectAsState()
    val profileNames by viewModel.profileNames.collectAsState()
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
    val downloadedTemplate = stringResource(R.string.downloaded_dl_filename)

    LaunchedEffect(lastDownload) {
        val dl = lastDownload ?: return@LaunchedEffect
        viewModel.dismissMessage() // clear the plain message so it doesn't double-show
        
        val result = snackbarHostState.showSnackbar(
            message = String.format(downloadedTemplate, dl.fileName),
            actionLabel = downloadActionLabel,
            duration = androidx.compose.material3.SnackbarDuration.Long,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            try {
                val contentUri = if (dl.uri.scheme == "file") {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        java.io.File(dl.uri.path ?: ""),
                    )
                } else {
                    dl.uri
                }
                val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
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
                MediaTypeResolver.MediaType.TEXT -> Intent().setClassName(
                    context.packageName,
                    "com.hension.havenx.TextEditorActivity",
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
                    putExtra("PROFILE_ID", preview.profileId)
                    putExtra("IS_SMB", preview.isSmb)
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
    var showTransferSheet by remember { mutableStateOf(false) }
    var showFavoritesMenu by remember { mutableStateOf(false) }
    var pathEditMode by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf(TextFieldValue(currentPath)) }
    var pathEditorEverFocused by remember { mutableStateOf(false) }
    var imeVisibleWhilePathEditing by remember { mutableStateOf(false) }
    val pathFocusRequester = remember { FocusRequester() }
    var requestPathFocus by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val isSearching = searchActive && searchQuery.trim().isNotEmpty()
    val visibleEntries = if (isSearching) searchResults else entries
    fun exitSearch() {
        searchActive = false
        searchQuery = ""
        requestSearchFocus = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        viewModel.clearSearch()
    }
    LaunchedEffect(searchActive, searchQuery, currentPath, activeProfileId, showHidden) {
        if (!searchActive) {
            viewModel.clearSearch()
            return@LaunchedEffect
        }
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            viewModel.clearSearchResults()
            return@LaunchedEffect
        }
        delay(300L)
        viewModel.searchCurrentDirectory(query)
    }
    LaunchedEffect(currentPath, pathEditMode) {
        if (!pathEditMode) pathInput = TextFieldValue(currentPath)
    }
    LaunchedEffect(currentPath, activeProfileId) {
        searchActive = false
        searchQuery = ""
        requestSearchFocus = false
        viewModel.clearSearch()
    }
    LaunchedEffect(searchActive) {
        if (searchActive) {
            requestSearchFocus = isActive
        } else {
            requestSearchFocus = false
            viewModel.clearSearch()
        }
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

    BackHandler(
        enabled = isActive && activeProfileId != null && (searchActive || canGoBack || currentPath != "/"),
    ) {
        when {
            searchActive -> exitSearch()
            canGoBack -> viewModel.goBack()
            currentPath != "/" -> viewModel.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    val canInteract = activeProfileId != null
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left navigation group
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.goBack() },
                                enabled = canInteract && canGoBack,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                            IconButton(
                                onClick = { viewModel.goForward() },
                                enabled = canInteract && canGoForward,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward))
                            }
                            IconButton(
                                onClick = { viewModel.navigateUp() },
                                enabled = canInteract && currentPath != "/",
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.up))
                            }
                        }

                        // Right tools group
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.refresh() },
                                enabled = canInteract,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.refresh))
                            }

                            Box {
                                val activeCount = transfers.count {
                                    it.state == TransferState.DOWNLOADING ||
                                        it.state == TransferState.PAUSED ||
                                        it.state == TransferState.ERROR
                                }
                                IconButton(
                                    onClick = { showTransferSheet = true },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (activeCount > 0) {
                                                Badge { Text(activeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.SwapVert,
                                            stringResource(R.string.transfer_manager_title),
                                        )
                                    }
                                }
                            }

                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    enabled = canInteract,
                                    modifier = Modifier.size(40.dp),
                                ) {
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
                                    onRemove = { path ->
                                        viewModel.toggleFavoriteDirectory(path)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (pathEditMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                shape = RoundedCornerShape(12.dp),
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
                        }
                    } else {
                        // Modern pill-style address bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        stringResource(R.string.edit_path),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (searchActive) {
                                            exitSearch()
                                        } else {
                                            pathEditMode = false
                                            searchActive = true
                                            requestSearchFocus = isActive
                                        }
                                    },
                                    enabled = activeProfileId != null,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        stringResource(R.string.search_files_folders),
                                        modifier = Modifier.size(19.dp),
                                        tint = if (searchActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = searchActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .focusRequester(searchFocusRequester)
                                .onGloballyPositioned {
                                    if (isActive && searchActive && requestSearchFocus) {
                                        searchFocusRequester.requestFocus()
                                        keyboardController?.show()
                                        requestSearchFocus = false
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_current_directory)) },
                            leadingIcon = {
                                IconButton(onClick = { exitSearch() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cancel))
                                }
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Filled.Close, stringResource(R.string.cancel_button))
                                    }
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }),
                        )
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
            } else if (searchLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val runningTransfers = remember(transfers) {
                transfers.filter { it.state == TransferState.DOWNLOADING }
            }
            AnimatedVisibility(
                visible = runningTransfers.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MiniTransferBar(
                    runningTransfers = runningTransfers,
                    onClick = { showTransferSheet = true },
                )
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
                if (visibleEntries.isEmpty() && !loading && !searchLoading) {
                    DirectoryEmptyState(isSearching = isSearching)
                } else {
                    var entryToDelete by remember { mutableStateOf<SftpEntry?>(null) }

                    if (entryToDelete != null) {
                        val entry = entryToDelete!!
                        val formatText = if (entry.isDirectory) stringResource(R.string.delete_folder_confirm) else stringResource(R.string.delete_file_confirm)
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { entryToDelete = null },
                            title = { Text(stringResource(R.string.delete)) },
                            text = { Text(String.format(formatText, entry.name)) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        viewModel.deleteEntry(entry)
                                        entryToDelete = null
                                    }
                                ) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(
                                    onClick = { entryToDelete = null }
                                ) {
                                    Text(stringResource(R.string.cancel_button))
                                }
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 84.dp),
                    ) {
                        items(visibleEntries, key = { it.path }) { entry ->
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
                                onDelete = { entryToDelete = entry },
                                onPreview = { viewModel.previewMedia(entry) },
                                showPath = isSearching,
                                isFavoriteDirectory = favoriteDirectories.contains(entry.path),
                                onToggleFavorite = {
                                    val wasFav = favoriteDirectories.contains(entry.path)
                                    viewModel.toggleFavoriteDirectory(entry.path)
                                    val msg = if (wasFav) {
                                        if (zh) "已取消收藏" else "Favorite removed"
                                    } else {
                                        if (zh) "成功收藏目录" else "Directory favorited"
                                    }
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                },
                                onCopyPath = {
                                    clipboardManager.setText(AnnotatedString(entry.path))
                                    scope.launch {
                                        snackbarHostState.showSnackbar(pathCopiedMsg)
                                    }
                                },
                                zh = zh,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTransferSheet) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val pathCopiedMsg = stringResource(R.string.path_copied)
        val shareChooserTitle = stringResource(R.string.transfer_action_share)
        ModalBottomSheet(
            onDismissRequest = { showTransferSheet = false },
            sheetState = sheetState,
        ) {
            TransferManagerSheetContent(
                transfers = transfers,
                history = downloadHistory,
                connectedProfileIds = connectedProfiles.map { it.id }.toSet(),
                profileNames = profileNames,
                onPause = { viewModel.pauseTransfer(it) },
                onResume = { viewModel.resumeTransfer(it) },
                onCancel = { viewModel.cancelTransfer(it) },
                onPauseAll = { viewModel.pauseAllTransfers() },
                onResumeAll = { viewModel.resumeAllTransfers() },
                onRetryFailed = { viewModel.retryFailedTransfers() },
                onResumeHistory = { viewModel.resumeHistoryRecord(it) },
                onDeleteHistory = { id, deleteLocalFile -> viewModel.deleteHistoryRecord(id, deleteLocalFile) },
                onClearHistory = { deleteLocalFiles -> viewModel.clearDownloadHistory(deleteLocalFiles) },
                onOpenFile = { uriString ->
                    try {
                        val contentUri = resolveShareableUri(context, uriString)
                        val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(openIntent)
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
                    }
                },
                onShareFile = { uriString ->
                    try {
                        val contentUri = resolveShareableUri(context, uriString)
                        val mimeType = context.contentResolver.getType(contentUri) ?: "*/*"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
                    }
                },
                onCopyRemotePath = { remotePath ->
                    clipboardManager.setText(AnnotatedString(remotePath))
                    scope.launch { snackbarHostState.showSnackbar(pathCopiedMsg) }
                },
            )
        }
    }
}

private fun resolveShareableUri(context: android.content.Context, uriString: String): android.net.Uri {
    val uri = android.net.Uri.parse(uriString)
    return if (uri.scheme == "file") {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            java.io.File(uri.path ?: ""),
        )
    } else {
        uri
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
    showPath: Boolean,
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
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = Modifier.fillMaxWidth()) {
        val fileVisual = remember(entry.name, entry.isDirectory) { resolveFileVisual(entry) }
        val iconTint = fileVisual.tintColor(MaterialTheme.colorScheme)
        ListItem(
            headlineContent = {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                val sizeText = if (entry.isDirectory) {
                    stringResource(R.string.directory)
                } else {
                    Formatter.formatFileSize(context, entry.size)
                }
                val dateText = dateFormat.format(Date(entry.modifiedTime * 1000))
                val parentPath = entry.path.substringBeforeLast('/', "/")
                val details = if (showPath) {
                    "$sizeText · $dateText\n$parentPath"
                } else {
                    "$sizeText · $dateText"
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (showPath) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(iconTint.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = fileVisual.icon,
                        contentDescription = if (zh) fileVisual.zhLabel else fileVisual.enLabel,
                        tint = iconTint,
                        modifier = Modifier.size(21.dp),
                    )
                }
            },
            trailingContent = {
                if (entry.isDirectory) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFavoriteDirectory) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(R.string.favorite),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            modifier = Modifier
                .indication(interactionSource, ripple())
                .pointerInput(entry.path) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interactionSource.emit(press)
                            val released = tryAwaitRelease()
                            interactionSource.emit(
                                if (released) PressInteraction.Release(press)
                                else PressInteraction.Cancel(press)
                            )
                        },
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

internal data class FileVisual(
    val icon: ImageVector,
    val zhLabel: String,
    val enLabel: String,
    val tintColor: (androidx.compose.material3.ColorScheme) -> androidx.compose.ui.graphics.Color,
)

internal fun resolveFileVisual(entry: SftpEntry): FileVisual =
    resolveFileVisualByName(entry.name, entry.isDirectory)

internal fun resolveFileVisualByName(name: String, isDirectory: Boolean = false): FileVisual {
    if (isDirectory) {
        return FileVisual(
            icon = Icons.Filled.Folder,
            zhLabel = "文件夹",
            enLabel = "Directory",
            tintColor = { it.primary },
        )
    }

    val ext = name.substringAfterLast('.', "").lowercase()
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
private fun FavoritesDropdown(
    expanded: Boolean,
    favorites: List<String>,
    zh: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
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
                leadingIcon = { 
                    IconButton(
                        onClick = { onRemove(path) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Star, stringResource(R.string.remove_favorite), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = { onSelect(path) },
            )
        }
    }
}

private fun isMediaPreviewSupported(name: String): Boolean {
    return MediaTypeResolver.resolve(name) != MediaTypeResolver.MediaType.UNSUPPORTED
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
    LaunchedEffect(path) {
        scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        modifier = modifier
            .horizontalScroll(scroll)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            if (index == 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSegmentClick(segment.second) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = stringResource(R.string.root),
                        tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = segment.first,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSegmentClick(segment.second) }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
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
private fun MiniTransferBar(
    runningTransfers: List<TransferTask>,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val single = runningTransfers.singleOrNull()
    val totalSpeed = runningTransfers.sumOf { it.speedBytesPerSec }
    val knownTotal = runningTransfers.filter { it.totalBytes > 0L }.sumOf { it.totalBytes }
    val knownTransferred = runningTransfers.filter { it.totalBytes > 0L }.sumOf {
        it.transferredBytes.coerceAtMost(it.totalBytes)
    }
    val rawFraction = if (knownTotal > 0L) (knownTransferred.toFloat() / knownTotal).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetValue = rawFraction, label = "miniTransferProgress")

    val icon = when {
        single == null -> Icons.Filled.SwapVert
        single.isUpload -> Icons.Filled.ArrowUpward
        else -> Icons.Filled.ArrowDownward
    }
    val title = single?.fileName ?: stringResource(R.string.transfer_summary_active, runningTransfers.size)
    val meta = buildList {
        if (totalSpeed > 0L) add("${Formatter.formatShortFileSize(context, totalSpeed)}/s")
        if (knownTotal > 0L) add("${(rawFraction * 100f).toInt()}%")
    }.joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (knownTotal > 0L) {
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun DirectoryEmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Filled.SearchOff else Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(if (isSearching) R.string.no_search_results else R.string.empty_directory),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(zh: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.sftp_file_browser),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            stringResource(R.string.connect_to_a_server_to),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
