package com.hension.havenx

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        val l = layout ?: return
        val safeOffset = selStart.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(safeOffset)
        onCursorMoved?.invoke(l.getLineTop(line), l.getLineBottom(line))
    }

    fun scrollToCursor() {
        val l = layout ?: return
        val safeOffset = selectionStart.coerceIn(0, text?.length ?: 0)
        val line = l.getLineForOffset(safeOffset)
        val lineTop = l.getLineTop(line)
        val lineBottom = l.getLineBottom(line)
        val margin = (lineBottom - lineTop) * 2
        val rect = android.graphics.Rect(
            0,
            (paddingTop + lineTop - margin).coerceAtLeast(0),
            width,
            paddingTop + lineBottom + margin
        )
        requestRectangleOnScreen(rect, false)
    }
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

                    val imeVisible = WindowInsets.isImeVisible
                    LaunchedEffect(imeVisible) {
                        if (imeVisible) {
                            delay(300)
                            editTextRef.value?.scrollToCursor()
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = { Text(fileName) },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = {
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
                                                        snackbarHostState.showSnackbar(getString(R.string.toast_saved_uploading))
                                                    } catch (e: Exception) {
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
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .imePadding()
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
                                    }

                                    CursorTrackingEditText(ctx).apply {
                                        typeface = android.graphics.Typeface.MONOSPACE
                                        textSize = 14f
                                        setTextColor(onSurfaceColor.toArgb())
                                        setHintTextColor(onSurfaceColor.copy(alpha = 0.4f).toArgb())
                                        background = null
                                        val dp = resources.displayMetrics.density
                                        setPadding(
                                            (16 * dp).toInt(), (12 * dp).toInt(),
                                            (16 * dp).toInt(), (120 * dp).toInt(),
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

                                        onCursorMoved = { lineTop, lineBottom ->
                                            // requestRectangleOnScreen takes a rect in the View's
                                            // own local coordinate space and walks up the entire
                                            // View hierarchy, asking each scrollable parent to
                                            // bring it into view. No manual offset math needed.
                                            val margin = (lineBottom - lineTop) * 2
                                            val rect = android.graphics.Rect(
                                                0,
                                                (paddingTop + lineTop - margin).coerceAtLeast(0),
                                                width,
                                                paddingTop + lineBottom + margin,
                                            )
                                            requestRectangleOnScreen(rect, false)
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
                                    val et = (scrollView as NestedScrollView)
                                        .getChildAt(0) as? CursorTrackingEditText
                                    et?.setTextColor(onSurfaceColor.toArgb())
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