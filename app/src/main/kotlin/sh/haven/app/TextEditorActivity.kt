package com.hension.havenx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.widget.NestedScrollView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.haven.core.ui.theme.HavenTheme
import sh.haven.feature.sftp.transfer.SftpTransferManager
import java.io.File
import javax.inject.Inject

/**
 * EditText that overrides onSelectionChanged to fire a callback whenever the
 * cursor moves — whether by typing, tapping, or arrow keys.
 *
 * This is the only reliable hook: TextWatcher.afterTextChanged fires before the
 * layout is updated, so cursor rect coordinates are stale. onSelectionChanged
 * fires after layout, so line coordinates are accurate.
 */
class CursorTrackingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    var onCursorMoved: ((lineTop: Int, lineBottom: Int) -> Unit)? = null
    private val searchHighlightSpans = mutableListOf<BackgroundColorSpan>()
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val lineNumberDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val gutterStartPadding = (8 * density).toInt()
    private val gutterEndPadding = (10 * density).toInt()
    private val gutterDividerWidth = (1 * density).coerceAtLeast(1f)
    private val clipBounds = Rect()
    private var basePaddingLeft = (16 * density).toInt()
    private var basePaddingTop = (12 * density).toInt()
    private var basePaddingRight = (16 * density).toInt()
    private var basePaddingBottom = (120 * density).toInt()
    private var lineNumberGutterWidth = (56 * density).toInt()

    init {
        lineNumberPaint.color = 0x66000000
        lineNumberDividerPaint.color = 0x1F000000
        lineNumberDividerPaint.strokeWidth = gutterDividerWidth
    }

    override fun onDraw(canvas: Canvas) {
        drawLineNumbers(canvas)
        super.onDraw(canvas)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val l = layout ?: return
        val safeOffset = selStart.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(safeOffset)
        onCursorMoved?.invoke(l.getLineTop(line), l.getLineBottom(line))
    }

    fun setEditorPadding(left: Int, top: Int, right: Int, bottom: Int) {
        basePaddingLeft = left
        basePaddingTop = top
        basePaddingRight = right
        basePaddingBottom = bottom
        updateLineNumberGutter()
    }

    fun setEditorColors(lineNumberColor: Int, dividerColor: Int) {
        lineNumberPaint.color = lineNumberColor
        lineNumberDividerPaint.color = dividerColor
        invalidate()
    }

    fun forceScroll() {
        val l = layout ?: return
        val safeOffset = selectionStart.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(safeOffset)
        onCursorMoved?.invoke(l.getLineTop(line), l.getLineBottom(line))
    }

    fun scrollOffsetIntoView(offset: Int) {
        val l = layout ?: return
        val safeOffset = offset.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(safeOffset)
        onCursorMoved?.invoke(l.getLineTop(line), l.getLineBottom(line))
    }

    fun setSearchHighlights(
        ranges: List<IntRange>,
        currentIndex: Int,
        matchColor: Int,
        currentMatchColor: Int,
    ) {
        val editable = text ?: return
        clearSearchHighlights()
        ranges.forEachIndexed { index, range ->
            val start = range.first.coerceIn(0, editable.length)
            val end = (range.last + 1).coerceIn(start, editable.length)
            if (start == end) return@forEachIndexed
            val span = BackgroundColorSpan(
                if (index == currentIndex) currentMatchColor else matchColor
            )
            editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            searchHighlightSpans += span
        }
    }

    fun clearSearchHighlights() {
        val editable = text ?: return
        searchHighlightSpans.forEach { editable.removeSpan(it) }
        searchHighlightSpans.clear()
    }

    private fun updateLineNumberGutter() {
        lineNumberPaint.textSize = textSize
        lineNumberPaint.typeface = typeface
        val numberWidth = lineNumberPaint.measureText("9999").toInt()
        lineNumberGutterWidth = maxOf(
            (56 * density).toInt(),
            gutterStartPadding + numberWidth + gutterEndPadding,
        )
        val left = lineNumberGutterWidth + basePaddingLeft
        if (
            paddingLeft == left &&
            paddingTop == basePaddingTop &&
            paddingRight == basePaddingRight &&
            paddingBottom == basePaddingBottom
        ) {
            return
        }
        super.setPadding(
            left,
            basePaddingTop,
            basePaddingRight,
            basePaddingBottom,
        )
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val l = layout ?: return
        val editable = text ?: return
        lineNumberPaint.textSize = textSize
        lineNumberPaint.typeface = typeface

        val dividerX = lineNumberGutterWidth - gutterDividerWidth / 2f
        canvas.getClipBounds(clipBounds)
        canvas.drawLine(
            dividerX,
            clipBounds.top.toFloat(),
            dividerX,
            clipBounds.bottom.toFloat(),
            lineNumberDividerPaint,
        )

        val numberRight = lineNumberGutterWidth - gutterEndPadding.toFloat()
        val textTop = totalPaddingTop
        val firstVisibleLine = l.getLineForVertical((clipBounds.top - textTop).coerceAtLeast(0))
        val lastVisibleLine = l.getLineForVertical((clipBounds.bottom - textTop).coerceAtLeast(0))
        var textOffset = l.getLineStart(firstVisibleLine).coerceAtLeast(0)
        var logicalLine = 1
        for (i in 0 until textOffset.coerceAtMost(editable.length)) {
            if (editable[i] == '\n') logicalLine += 1
        }
        for (visualLine in firstVisibleLine..lastVisibleLine) {
            val lineStart = l.getLineStart(visualLine)
            while (textOffset < lineStart && textOffset < editable.length) {
                if (editable[textOffset] == '\n') logicalLine += 1
                textOffset += 1
            }
            val isLogicalLineStart = lineStart == 0 ||
                (lineStart - 1 in 0 until editable.length && editable[lineStart - 1] == '\n')
            if (isLogicalLineStart) {
                canvas.drawText(
                    logicalLine.toString(),
                    numberRight,
                    textTop + l.getLineBaseline(visualLine).toFloat(),
                    lineNumberPaint,
                )
            }
        }
    }
}

private fun findTextMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val results = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex <= text.length - query.length) {
        val matchIndex = text.indexOf(query, startIndex, ignoreCase = true)
        if (matchIndex < 0) break
        results += matchIndex until (matchIndex + query.length)
        startIndex = matchIndex + query.length.coerceAtLeast(1)
    }
    return results
}

@AndroidEntryPoint
class TextEditorActivity : ComponentActivity() {

    @Inject
    lateinit var transferManager: SftpTransferManager

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("FILE_PATH")
        val remotePath = intent.getStringExtra("REMOTE_PATH")
        val profileId = intent.getStringExtra("PROFILE_ID")
        val isSmb = intent.getBooleanExtra("IS_SMB", false)

        if (filePath == null || remotePath == null) {
            finish()
            return
        }

        setContent {
            HavenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val fileName = remotePath.substringAfterLast('/')
                    val snackbarHostState = remember { SnackbarHostState() }

                    var isLoading by remember { mutableStateOf(true) }
                    var isUploading by remember { mutableStateOf(false) }
                    var uploadProgress by remember { mutableFloatStateOf(0f) }

                    val editTextRef = remember { mutableStateOf<CursorTrackingEditText?>(null) }
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                    val editorBackgroundColor = if (isSystemInDarkTheme()) {
                        Color(0xFF27231F)
                    } else {
                        Color(0xFFFAF7F0)
                    }
                    val searchMatchColor = MaterialTheme.colorScheme.tertiaryContainer
                        .copy(alpha = 0.75f)
                        .toArgb()
                    val currentSearchMatchColor = MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.35f)
                        .toArgb()
                    val searchFocusRequester = remember { FocusRequester() }
                    var searchActive by remember { mutableStateOf(false) }
                    var searchQuery by remember { mutableStateOf("") }
                    var searchMatches by remember { mutableStateOf<List<IntRange>>(emptyList()) }
                    var currentSearchIndex by remember { mutableIntStateOf(-1) }
                    var textVersion by remember { mutableIntStateOf(0) }

                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

                    fun applySearchState(ranges: List<IntRange>, index: Int) {
                        val et = editTextRef.value ?: return
                        et.setSearchHighlights(
                            ranges = ranges,
                            currentIndex = index,
                            matchColor = searchMatchColor,
                            currentMatchColor = currentSearchMatchColor,
                        )
                        if (index in ranges.indices) {
                            et.post { et.scrollOffsetIntoView(ranges[index].first) }
                        }
                    }

                    fun refreshTextSearch(preferredIndex: Int = currentSearchIndex) {
                        val et = editTextRef.value
                        if (!searchActive || searchQuery.isEmpty() || et == null) {
                            searchMatches = emptyList()
                            currentSearchIndex = -1
                            et?.clearSearchHighlights()
                            return
                        }
                        val ranges = findTextMatches(et.text?.toString().orEmpty(), searchQuery)
                        val nextIndex = if (ranges.isEmpty()) {
                            -1
                        } else if (preferredIndex in ranges.indices) {
                            preferredIndex
                        } else {
                            0
                        }
                        searchMatches = ranges
                        currentSearchIndex = nextIndex
                        applySearchState(ranges, nextIndex)
                    }

                    fun moveTextSearch(delta: Int) {
                        if (searchMatches.isEmpty()) return
                        val nextIndex = if (currentSearchIndex in searchMatches.indices) {
                            (currentSearchIndex + delta + searchMatches.size) % searchMatches.size
                        } else {
                            0
                        }
                        currentSearchIndex = nextIndex
                        applySearchState(searchMatches, nextIndex)
                    }

                    fun exitTextSearch() {
                        searchActive = false
                        searchQuery = ""
                        searchMatches = emptyList()
                        currentSearchIndex = -1
                        editTextRef.value?.clearSearchHighlights()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }

                    BackHandler(enabled = searchActive) {
                        exitTextSearch()
                    }

                    val imeVisible = WindowInsets.isImeVisible
                    LaunchedEffect(imeVisible, searchActive) {
                        if (imeVisible && !searchActive) {
                            delay(350)
                            editTextRef.value?.forceScroll()
                        }
                    }
                    LaunchedEffect(searchActive) {
                        if (searchActive) {
                            delay(100)
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                    LaunchedEffect(searchActive, searchQuery, textVersion) {
                        refreshTextSearch()
                    }
                    DisposableEffect(Unit) {
                        onDispose { editTextRef.value?.clearSearchHighlights() }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Column {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = fileName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { finish() }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = {
                                                if (searchActive) {
                                                    exitTextSearch()
                                                } else {
                                                    searchActive = true
                                                }
                                            },
                                        ) {
                                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_text))
                                        }
                                        IconButton(
                                            onClick = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                scope.launch {
                                                    val et = editTextRef.value ?: return@launch
                                                    if (profileId != null) {
                                                        isUploading = true
                                                        uploadProgress = 0f
                                                        try {
                                                            val currentText = et.text.toString()
                                                            val file = File(filePath)
                                                            withContext(Dispatchers.IO) { file.writeText(currentText) }
                                                            transferManager.performDirectUpload(
                                                                sourceUri = Uri.fromFile(file),
                                                                destPath = remotePath,
                                                                profileId = profileId,
                                                                isSmb = isSmb,
                                                                totalBytes = file.length()
                                                            ) { transferred ->
                                                                val total = file.length()
                                                                uploadProgress = if (total > 0) transferred.toFloat() / total else 1f
                                                                true
                                                            }
                                                            isUploading = false
                                                            snackbarHostState.showSnackbar(getString(R.string.toast_saved_uploading))
                                                        } catch (e: Exception) {
                                                            isUploading = false
                                                            snackbarHostState.showSnackbar(getString(R.string.toast_failed_save, e.message))
                                                        } finally {
                                                            isUploading = false
                                                        }
                                                    } else {
                                                        snackbarHostState.showSnackbar(getString(R.string.toast_cannot_upload_disconnected))
                                                    }
                                                }
                                            },
                                            enabled = !isUploading
                                        ) {
                                            if (isUploading) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Filled.Save, contentDescription = "Save")
                                            }
                                        }
                                    }
                                )
                                if (searchActive) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                        tonalElevation = 3.dp,
                                        shadowElevation = 2.dp,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 6.dp),
                                        ) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .focusRequester(searchFocusRequester),
                                                singleLine = true,
                                                placeholder = { Text(stringResource(R.string.search_in_file)) },
                                                leadingIcon = {
                                                    Icon(Icons.Filled.Search, contentDescription = null)
                                                },
                                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                                    {
                                                        IconButton(onClick = { searchQuery = "" }) {
                                                            Icon(
                                                                Icons.Filled.Close,
                                                                contentDescription = stringResource(R.string.clear_search),
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    null
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                ),
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                                keyboardActions = KeyboardActions(onSearch = {
                                                    keyboardController?.hide()
                                                    focusManager.clearFocus()
                                                }),
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 2.dp),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = if (searchQuery.isEmpty()) {
                                                        ""
                                                    } else if (searchMatches.isEmpty()) {
                                                        stringResource(R.string.no_text_matches)
                                                    } else {
                                                        "${currentSearchIndex + 1}/${searchMatches.size}"
                                                    },
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp)
                                                        .widthIn(min = 44.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                IconButton(
                                                    onClick = { moveTextSearch(-1) },
                                                    enabled = searchMatches.isNotEmpty(),
                                                ) {
                                                    Icon(
                                                        Icons.Filled.KeyboardArrowUp,
                                                        contentDescription = stringResource(R.string.previous_match),
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { moveTextSearch(1) },
                                                    enabled = searchMatches.isNotEmpty(),
                                                ) {
                                                    Icon(
                                                        Icons.Filled.KeyboardArrowDown,
                                                        contentDescription = stringResource(R.string.next_match),
                                                    )
                                                }
                                                IconButton(onClick = { exitTextSearch() }) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = stringResource(R.string.close_search),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .imePadding()
                                .background(editorBackgroundColor)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }

                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    // Create NestedScrollView first so the cursor callback
                                    // can close over it without a lateinit var.
                                    val scrollView = NestedScrollView(ctx).apply {
                                        isFillViewport = true
                                        setBackgroundColor(editorBackgroundColor.toArgb())
                                    }

                                    CursorTrackingEditText(ctx).apply {
                                        typeface = android.graphics.Typeface.MONOSPACE
                                        textSize = 14f
                                        setTextColor(onSurfaceColor.toArgb())
                                        setHintTextColor(onSurfaceColor.copy(alpha = 0.4f).toArgb())
                                        background = null
                                        val dp = resources.displayMetrics.density
                                        setEditorPadding(
                                            (16 * dp).toInt(), (12 * dp).toInt(),
                                            (16 * dp).toInt(), (120 * dp).toInt(),
                                        )
                                        setEditorColors(
                                            lineNumberColor = onSurfaceColor.copy(alpha = 0.46f).toArgb(),
                                            dividerColor = onSurfaceColor.copy(alpha = 0.14f).toArgb(),
                                        )
                                        isSingleLine = false
                                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                        setHorizontallyScrolling(false)
                                        // Hand all scroll events to the parent NestedScrollView
                                        isScrollContainer = false
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                        )
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(
                                                s: CharSequence?,
                                                start: Int,
                                                count: Int,
                                                after: Int,
                                            ) = Unit

                                            override fun onTextChanged(
                                                s: CharSequence?,
                                                start: Int,
                                                before: Int,
                                                count: Int,
                                            ) = Unit

                                            override fun afterTextChanged(s: Editable?) {
                                                textVersion += 1
                                            }
                                        })

                                        onCursorMoved = { lineTop, lineBottom ->
                                            // lineTop/lineBottom are in EditText-local coordinates.
                                            // Offset by EditText.top (its position inside ScrollView)
                                            // and its own paddingTop.
                                            val etTop = top
                                            val absoluteTop = etTop + paddingTop + lineTop
                                            val absoluteBottom = etTop + paddingTop + lineBottom

                                            val svHeight = scrollView.height
                                            val currentScroll = scrollView.scrollY

                                            // Breathing room = 2 line-heights above and below
                                            val margin = (lineBottom - lineTop) * 2

                                            val targetScroll: Int? = when {
                                                absoluteTop - margin < currentScroll ->
                                                    (absoluteTop - margin).coerceAtLeast(0)
                                                absoluteBottom + margin > currentScroll + svHeight ->
                                                    absoluteBottom + margin - svHeight
                                                else -> null  // already visible, skip
                                            }

                                            if (targetScroll != null) {
                                                scrollView.smoothScrollTo(0, targetScroll)
                                            }
                                        }

                                        editTextRef.value = this

                                        scope.launch {
                                            try {
                                                val text = withContext(Dispatchers.IO) {
                                                    File(filePath).readText()
                                                }
                                                setText(text)
                                                setSelection(0)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    ctx,
                                                    ctx.getString(R.string.toast_failed_load_file),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                finish()
                                            } finally {
                                                isLoading = false
                                            }
                                        }

                                        scrollView.addView(this)
                                    }

                                    scrollView
                                },
                                update = { scrollView ->
                                    scrollView.setBackgroundColor(editorBackgroundColor.toArgb())
                                    val et = (scrollView as NestedScrollView)
                                        .getChildAt(0) as? CursorTrackingEditText
                                    et?.setTextColor(onSurfaceColor.toArgb())
                                    et?.setEditorColors(
                                        lineNumberColor = onSurfaceColor.copy(alpha = 0.46f).toArgb(),
                                        dividerColor = onSurfaceColor.copy(alpha = 0.14f).toArgb(),
                                    )
                                }
                            )

                            if (isUploading) {
                                LinearProgressIndicator(
                                    progress = { uploadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
