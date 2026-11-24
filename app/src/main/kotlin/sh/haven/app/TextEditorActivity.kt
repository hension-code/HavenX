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
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
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
 * EditText that overrides onSelectionChanged to fire callbacks whenever the
 * cursor moves — whether by typing, tapping, or arrow keys — and draws an
 * IDE-style line number gutter with active-line highlighting.
 */
class CursorTrackingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    var onCursorMoved: ((lineTop: Int, lineBottom: Int) -> Unit)? = null
    var onCursorPositionChanged: ((line: Int, column: Int) -> Unit)? = null

    private val searchHighlightSpans = mutableListOf<BackgroundColorSpan>()
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val activeLineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val gutterBgPaint = Paint()
    private val activeLineBgPaint = Paint()
    private val lineNumberDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val gutterStartPadding = (8 * density).toInt()
    private val gutterEndPadding = (10 * density).toInt()
    private val gutterDividerWidth = (1 * density).coerceAtLeast(1f)
    private val clipBounds = Rect()
    private var basePaddingLeft = (14 * density).toInt()
    private var basePaddingTop = (12 * density).toInt()
    private var basePaddingRight = (14 * density).toInt()
    private var basePaddingBottom = (120 * density).toInt()
    private var lineNumberGutterWidth = (50 * density).toInt()
    private var showLineNumbers = true
    private var activeLogicalLine = 1

    init {
        lineNumberPaint.color = 0x66000000
        activeLineNumberPaint.color = 0xCC000000.toInt()
        gutterBgPaint.color = 0x0A000000
        activeLineBgPaint.color = 0x0F000000
        lineNumberDividerPaint.color = 0x1F000000
        lineNumberDividerPaint.strokeWidth = gutterDividerWidth
    }

    override fun onDraw(canvas: Canvas) {
        drawActiveLineHighlight(canvas)
        if (showLineNumbers) {
            drawLineNumbers(canvas)
        }
        super.onDraw(canvas)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val editable = text
        val length = editable?.length ?: 0
        val safeOffset = selStart.coerceIn(0, length)
        if (editable != null) {
            var line = 1
            var lastNewline = -1
            for (i in 0 until safeOffset) {
                if (editable[i] == '\n') {
                    line += 1
                    lastNewline = i
                }
            }
            val col = safeOffset - lastNewline
            activeLogicalLine = line
            onCursorPositionChanged?.invoke(line, col)
        }
        val l = layout ?: return
        val visualLine = l.getLineForOffset(safeOffset)
        onCursorMoved?.invoke(l.getLineTop(visualLine), l.getLineBottom(visualLine))
        invalidate()
    }

    fun setEditorPadding(left: Int, top: Int, right: Int, bottom: Int) {
        basePaddingLeft = left
        basePaddingTop = top
        basePaddingRight = right
        basePaddingBottom = bottom
        updateLineNumberGutter()
    }

    fun setEditorColors(
        lineNumberColor: Int,
        activeLineNumberColor: Int,
        gutterBgColor: Int,
        activeLineBgColor: Int,
        dividerColor: Int,
    ) {
        lineNumberPaint.color = lineNumberColor
        activeLineNumberPaint.color = activeLineNumberColor
        gutterBgPaint.color = gutterBgColor
        activeLineBgPaint.color = activeLineBgColor
        lineNumberDividerPaint.color = dividerColor
        invalidate()
    }

    fun setShowLineNumbers(show: Boolean) {
        if (showLineNumbers == show) return
        showLineNumbers = show
        updateLineNumberGutter()
        invalidate()
    }

    fun setEditorTextSizeSp(sizeSp: Float) {
        textSize = sizeSp
        updateLineNumberGutter()
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

    fun scrollToLogicalLine(targetLine: Int) {
        val editable = text ?: return
        val clampedTarget = targetLine.coerceAtLeast(1)
        var currentLine = 1
        var offset = 0
        while (currentLine < clampedTarget && offset < editable.length) {
            if (editable[offset] == '\n') currentLine += 1
            offset += 1
        }
        val safeOffset = offset.coerceIn(0, editable.length)
        setSelection(safeOffset)
        post { scrollOffsetIntoView(safeOffset) }
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
        lineNumberPaint.textSize = textSize * 0.88f
        lineNumberPaint.typeface = typeface
        activeLineNumberPaint.textSize = textSize * 0.88f
        activeLineNumberPaint.typeface = typeface
        val numberWidth = lineNumberPaint.measureText("9999").toInt()
        lineNumberGutterWidth = maxOf(
            (48 * density).toInt(),
            gutterStartPadding + numberWidth + gutterEndPadding,
        )
        val left = if (showLineNumbers) lineNumberGutterWidth + basePaddingLeft else basePaddingLeft
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

    private fun drawActiveLineHighlight(canvas: Canvas) {
        val l = layout ?: return
        val length = text?.length ?: 0
        val safeOffset = selectionStart.coerceIn(0, length)
        val visualLine = l.getLineForOffset(safeOffset)
        val top = totalPaddingTop + l.getLineTop(visualLine).toFloat()
        val bottom = totalPaddingTop + l.getLineBottom(visualLine).toFloat()
        val left = if (showLineNumbers) lineNumberGutterWidth.toFloat() else 0f
        canvas.drawRect(left, top, width.toFloat(), bottom, activeLineBgPaint)
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val l = layout ?: return
        val editable = text ?: return
        lineNumberPaint.textSize = textSize * 0.88f
        lineNumberPaint.typeface = typeface
        activeLineNumberPaint.textSize = textSize * 0.88f
        activeLineNumberPaint.typeface = typeface

        canvas.getClipBounds(clipBounds)
        // Draw dedicated subtle gutter background
        canvas.drawRect(
            0f,
            clipBounds.top.toFloat(),
            lineNumberGutterWidth.toFloat(),
            clipBounds.bottom.toFloat(),
            gutterBgPaint,
        )

        val dividerX = lineNumberGutterWidth - gutterDividerWidth / 2f
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
                val paint = if (logicalLine == activeLogicalLine) activeLineNumberPaint else lineNumberPaint
                canvas.drawText(
                    logicalLine.toString(),
                    numberRight,
                    textTop + l.getLineBaseline(visualLine).toFloat(),
                    paint,
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

private fun countLogicalLines(text: CharSequence?): Int {
    if (text.isNullOrEmpty()) return 1
    var lines = 1
    for (i in 0 until text.length) {
        if (text[i] == '\n') lines += 1
    }
    return lines
}

private fun resolveExtensionBadge(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").uppercase()
    return if (ext.isNotEmpty() && ext.length <= 6 && ext != fileName.uppercase()) ext else "TXT"
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

        if (filePath == null || remotePath == null || !PreviewIntentGuard.isCachePath(this, filePath)) {
            finish()
            return
        }

        setContent {
            HavenTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val scope = rememberCoroutineScope()
                    val fileName = remember(remotePath) { remotePath.substringAfterLast('/') }
                    val remoteDir = remember(remotePath) {
                        remotePath.substringBeforeLast('/', "/").ifBlank { "/" }
                    }
                    val fileExtBadge = remember(fileName) { resolveExtensionBadge(fileName) }
                    val snackbarHostState = remember { SnackbarHostState() }

                    var isLoading by remember { mutableStateOf(true) }
                    var isUploading by remember { mutableStateOf(false) }
                    var uploadProgress by remember { mutableFloatStateOf(0f) }

                    var savedContent by remember { mutableStateOf("") }
                    var isDirty by remember { mutableStateOf(false) }
                    var showUnsavedDialog by remember { mutableStateOf(false) }
                    var showMoreMenu by remember { mutableStateOf(false) }
                    var showGoToLineDialog by remember { mutableStateOf(false) }
                    var goToLineInput by remember { mutableStateOf("") }

                    var fontSizeSp by remember { mutableFloatStateOf(14f) }
                    var showLineNumbers by remember { mutableStateOf(true) }
                    var cursorLine by remember { mutableIntStateOf(1) }
                    var cursorCol by remember { mutableIntStateOf(1) }
                    var totalLines by remember { mutableIntStateOf(1) }
                    var totalChars by remember { mutableIntStateOf(0) }

                    val editTextRef = remember { mutableStateOf<CursorTrackingEditText?>(null) }
                    val darkTheme = isSystemInDarkTheme()
                    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val editorBackgroundColor = if (darkTheme) Color(0xFF181C26) else Color(0xFFFAFBFD)
                    val gutterBackgroundColor = if (darkTheme) Color(0xFF131720) else Color(0xFFF1F4F8)
                    val activeLineBackgroundColor = if (darkTheme) {
                        Color.White.copy(alpha = 0.055f)
                    } else {
                        primaryColor.copy(alpha = 0.065f)
                    }
                    val lineNumberColor = onSurfaceColor.copy(alpha = 0.42f)
                    val activeLineNumberColor = primaryColor.copy(alpha = 0.92f)
                    val dividerColor = onSurfaceColor.copy(alpha = 0.10f)

                    val searchMatchColor = MaterialTheme.colorScheme.tertiaryContainer
                        .copy(alpha = 0.78f)
                        .toArgb()
                    val currentSearchMatchColor = primaryColor
                        .copy(alpha = 0.36f)
                        .toArgb()
                    val searchFocusRequester = remember { FocusRequester() }
                    var searchActive by remember { mutableStateOf(false) }
                    var searchQuery by remember { mutableStateOf("") }
                    var searchMatches by remember { mutableStateOf<List<IntRange>>(emptyList()) }
                    var currentSearchIndex by remember { mutableIntStateOf(-1) }
                    var textVersion by remember { mutableIntStateOf(0) }

                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current

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

                    fun saveAndUpload(finishAfterSave: Boolean = false) {
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
                                        totalBytes = file.length(),
                                    ) { transferred ->
                                        val total = file.length()
                                        uploadProgress = if (total > 0) transferred.toFloat() / total else 1f
                                        true
                                    }
                                    savedContent = currentText
                                    isDirty = false
                                    isUploading = false
                                    if (finishAfterSave) {
                                        Toast.makeText(
                                            this@TextEditorActivity,
                                            getString(R.string.toast_saved_uploading),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        finish()
                                    } else {
                                        snackbarHostState.showSnackbar(getString(R.string.toast_saved_uploading))
                                    }
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
                    }

                    fun attemptClose() {
                        if (searchActive) {
                            exitTextSearch()
                        } else if (isDirty) {
                            showUnsavedDialog = true
                        } else {
                            finish()
                        }
                    }

                    BackHandler(enabled = searchActive || isDirty) {
                        attemptClose()
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

                    if (showUnsavedDialog) {
                        AlertDialog(
                            onDismissRequest = { showUnsavedDialog = false },
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .widthIn(max = 310.dp),
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                            shape = RoundedCornerShape(20.dp),
                            title = {
                                Text(
                                    text = stringResource(R.string.editor_unsaved_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.editor_unsaved_message, fileName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showUnsavedDialog = false
                                        saveAndUpload(finishAfterSave = true)
                                    },
                                ) {
                                    Text(stringResource(R.string.editor_save_and_exit))
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            showUnsavedDialog = false
                                            finish()
                                        },
                                    ) {
                                        Text(
                                            stringResource(R.string.editor_discard),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    TextButton(onClick = { showUnsavedDialog = false }) {
                                        Text(stringResource(R.string.editor_cancel))
                                    }
                                }
                            },
                        )
                    }

                    if (showGoToLineDialog) {
                        AlertDialog(
                            onDismissRequest = { showGoToLineDialog = false },
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .widthIn(max = 300.dp),
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                            shape = RoundedCornerShape(20.dp),
                            title = {
                                Text(
                                    text = stringResource(R.string.editor_go_to_line),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            text = {
                                OutlinedTextField(
                                    value = goToLineInput,
                                    onValueChange = { input ->
                                        goToLineInput = input.filter { it.isDigit() }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    placeholder = {
                                        Text(stringResource(R.string.editor_go_to_line_hint, totalLines))
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Go,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            val target = goToLineInput.toIntOrNull()
                                            if (target != null) {
                                                editTextRef.value?.scrollToLogicalLine(target.coerceIn(1, totalLines))
                                            }
                                            showGoToLineDialog = false
                                        },
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val target = goToLineInput.toIntOrNull()
                                        if (target != null) {
                                            editTextRef.value?.scrollToLogicalLine(target.coerceIn(1, totalLines))
                                        }
                                        showGoToLineDialog = false
                                    },
                                    enabled = goToLineInput.isNotEmpty(),
                                ) {
                                    Text(stringResource(R.string.editor_jump))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showGoToLineDialog = false }) {
                                    Text(stringResource(R.string.editor_cancel))
                                }
                            },
                        )
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Surface(tonalElevation = 2.dp, shadowElevation = 1.dp) {
                                Column {
                                    TopAppBar(
                                        title = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = fileName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (isDirty) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary),
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = remoteDir,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        },
                                        navigationIcon = {
                                            IconButton(onClick = { attemptClose() }) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = "Back",
                                                )
                                            }
                                        },
                                        actions = {
                                            IconButton(
                                                onClick = {
                                                    if (searchActive) exitTextSearch() else searchActive = true
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = stringResource(R.string.search_text),
                                                    tint = if (searchActive) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                            if (isDirty && !isUploading) {
                                                FilledTonalIconButton(
                                                    onClick = { saveAndUpload() },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    ),
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Save,
                                                        contentDescription = stringResource(R.string.editor_save),
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { saveAndUpload() },
                                                    enabled = !isUploading,
                                                ) {
                                                    if (isUploading) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(20.dp),
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Filled.Save,
                                                            contentDescription = stringResource(R.string.editor_save),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                            Box {
                                                IconButton(onClick = { showMoreMenu = true }) {
                                                    Icon(
                                                        Icons.Filled.MoreVert,
                                                        contentDescription = stringResource(R.string.editor_more),
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showMoreMenu,
                                                    onDismissRequest = { showMoreMenu = false },
                                                ) {
                                                    // Font size stepper row
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.editor_font_size),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                        )
                                                        Spacer(Modifier.width(12.dp))
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(
                                                                onClick = {
                                                                    fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(11f)
                                                                },
                                                                enabled = fontSizeSp > 11f,
                                                                modifier = Modifier.size(32.dp),
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Remove,
                                                                    contentDescription = stringResource(R.string.editor_decrease_font),
                                                                    modifier = Modifier.size(18.dp),
                                                                )
                                                            }
                                                            Text(
                                                                text = "${fontSizeSp.toInt()}sp",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                            )
                                                            IconButton(
                                                                onClick = {
                                                                    fontSizeSp = (fontSizeSp + 1f).coerceAtMost(22f)
                                                                },
                                                                enabled = fontSizeSp < 22f,
                                                                modifier = Modifier.size(32.dp),
                                                            ) {
                                                                Icon(
                                                                    Icons.Filled.Add,
                                                                    contentDescription = stringResource(R.string.editor_increase_font),
                                                                    modifier = Modifier.size(18.dp),
                                                                )
                                                            }
                                                        }
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.editor_go_to_line)) },
                                                        leadingIcon = { Icon(Icons.Filled.Tag, null) },
                                                        onClick = {
                                                            showMoreMenu = false
                                                            goToLineInput = cursorLine.toString()
                                                            showGoToLineDialog = true
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(
                                                                    if (showLineNumbers) R.string.editor_hide_line_numbers
                                                                    else R.string.editor_show_line_numbers
                                                                ),
                                                            )
                                                        },
                                                        leadingIcon = {
                                                            Icon(Icons.Filled.FormatListNumbered, null)
                                                        },
                                                        onClick = {
                                                            showLineNumbers = !showLineNumbers
                                                            showMoreMenu = false
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    )

                                    // Compact single-row search bar
                                    AnimatedVisibility(
                                        visible = searchActive,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically(),
                                    ) {
                                        CompactSearchBar(
                                            query = searchQuery,
                                            onQueryChange = { searchQuery = it },
                                            matchCount = searchMatches.size,
                                            currentIndex = currentSearchIndex,
                                            focusRequester = searchFocusRequester,
                                            onPrev = { moveTextSearch(-1) },
                                            onNext = { moveTextSearch(1) },
                                            onClose = { exitTextSearch() },
                                            onSearchDone = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .imePadding()
                                .background(editorBackgroundColor),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }

                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        val scrollView = NestedScrollView(ctx).apply {
                                            isFillViewport = true
                                            setBackgroundColor(editorBackgroundColor.toArgb())
                                        }

                                        CursorTrackingEditText(ctx).apply {
                                            typeface = android.graphics.Typeface.MONOSPACE
                                            setEditorTextSizeSp(fontSizeSp)
                                            setLineSpacing(0f, 1.35f)
                                            setTextColor(onSurfaceColor.toArgb())
                                            setHintTextColor(onSurfaceColor.copy(alpha = 0.4f).toArgb())
                                            background = null
                                            val dp = resources.displayMetrics.density
                                            setEditorPadding(
                                                (12 * dp).toInt(), (12 * dp).toInt(),
                                                (14 * dp).toInt(), (120 * dp).toInt(),
                                            )
                                            setEditorColors(
                                                lineNumberColor = lineNumberColor.toArgb(),
                                                activeLineNumberColor = activeLineNumberColor.toArgb(),
                                                gutterBgColor = gutterBackgroundColor.toArgb(),
                                                activeLineBgColor = activeLineBackgroundColor.toArgb(),
                                                dividerColor = dividerColor.toArgb(),
                                            )
                                            setShowLineNumbers(showLineNumbers)
                                            isSingleLine = false
                                            gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                            setHorizontallyScrolling(false)
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
                                                    totalLines = countLogicalLines(s)
                                                    totalChars = s?.length ?: 0
                                                    if (!isLoading) {
                                                        isDirty = s?.toString() != savedContent
                                                    }
                                                }
                                            })

                                            onCursorPositionChanged = { line, col ->
                                                cursorLine = line
                                                cursorCol = col
                                            }

                                            onCursorMoved = { lineTop, lineBottom ->
                                                val etTop = top
                                                val absoluteTop = etTop + paddingTop + lineTop
                                                val absoluteBottom = etTop + paddingTop + lineBottom

                                                val svHeight = scrollView.height
                                                val currentScroll = scrollView.scrollY
                                                val margin = (lineBottom - lineTop) * 2

                                                val targetScroll: Int? = when {
                                                    absoluteTop - margin < currentScroll ->
                                                        (absoluteTop - margin).coerceAtLeast(0)
                                                    absoluteBottom + margin > currentScroll + svHeight ->
                                                        absoluteBottom + margin - svHeight
                                                    else -> null
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
                                                    savedContent = text
                                                    setText(text)
                                                    setSelection(0)
                                                    totalLines = countLogicalLines(text)
                                                    totalChars = text.length
                                                    isDirty = false
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        ctx,
                                                        ctx.getString(R.string.toast_failed_load_file),
                                                        Toast.LENGTH_SHORT,
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
                                            lineNumberColor = lineNumberColor.toArgb(),
                                            activeLineNumberColor = activeLineNumberColor.toArgb(),
                                            gutterBgColor = gutterBackgroundColor.toArgb(),
                                            activeLineBgColor = activeLineBackgroundColor.toArgb(),
                                            dividerColor = dividerColor.toArgb(),
                                        )
                                        et?.setShowLineNumbers(showLineNumbers)
                                        et?.setEditorTextSizeSp(fontSizeSp)
                                    },
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

                            // Minimal bottom status bar (sticks above IME when keyboard opens)
                            EditorStatusBar(
                                line = cursorLine,
                                column = cursorCol,
                                totalLines = totalLines,
                                totalChars = totalChars,
                                fileExtBadge = fileExtBadge,
                                onPositionClick = {
                                    goToLineInput = cursorLine.toString()
                                    showGoToLineDialog = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    focusRequester: FocusRequester,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onSearchDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(38.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_in_file),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchDone() }),
                    )
                }
                if (query.isNotEmpty()) {
                    Text(
                        text = if (matchCount == 0) {
                            stringResource(R.string.no_text_matches)
                        } else {
                            "${currentIndex + 1}/$matchCount"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (matchCount == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(2.dp))
        IconButton(
            onClick = onPrev,
            enabled = matchCount > 0,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.previous_match),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = matchCount > 0,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.next_match),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.close_search),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EditorStatusBar(
    line: Int,
    column: Int,
    totalLines: Int,
    totalChars: Int,
    fileExtBadge: String,
    onPositionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.editor_status_position, line, column),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onPositionClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${stringResource(R.string.editor_status_lines, totalLines)} · ${
                            stringResource(R.string.editor_status_chars, totalChars)
                        }",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = fileExtBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
