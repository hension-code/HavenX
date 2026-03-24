package com.hension.havenx

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.haven.core.ui.theme.HavenTheme
import sh.haven.feature.sftp.TransferState
import sh.haven.feature.sftp.TransferTask
import sh.haven.feature.sftp.transfer.SftpTransferManager
import java.io.File
import java.util.UUID
import javax.inject.Inject

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
                    var content by remember { mutableStateOf(TextFieldValue("")) }
                    var isLoading by remember { mutableStateOf(true) }
                    val scope = rememberCoroutineScope()
                    val fileName = remotePath.substringAfterLast('/')

                    LaunchedEffect(filePath) {
                        try {
                            val text = withContext(Dispatchers.IO) { File(filePath).readText() }
                            content = TextFieldValue(text = text, selection = TextRange(0))
                        } catch (e: Exception) {
                            Toast.makeText(this@TextEditorActivity, getString(R.string.toast_failed_load_file), Toast.LENGTH_SHORT).show()
                            finish()
                        } finally {
                            isLoading = false
                        }
                    }

                    // Track TextLayoutResult to compute cursor pixel position
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val scrollState = rememberScrollState()
                    val snackbarHostState = remember { SnackbarHostState() }
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    var isUploading by remember { mutableStateOf(false) }
                    var uploadProgress by remember { mutableFloatStateOf(0f) }

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
                                            keyboardController?.hide()
                                            scope.launch {
                                                if (profileId != null) {
                                                    isUploading = true
                                                    uploadProgress = 0f
                                                    try {
                                                        val file = File(filePath)
                                                        withContext(Dispatchers.IO) {
                                                            file.writeText(content.text)
                                                        }
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
                        }
                    ) { innerPadding ->
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val focusRequester = remember { FocusRequester() }
                            val density = androidx.compose.ui.platform.LocalDensity.current

                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .imePadding()
                            ) {
                                val viewportHeight = maxHeight
                                val viewportHeightPx = with(density) { maxHeight.toPx() }

                                // Scroll to cursor when IME appears
                                val imeVisible = WindowInsets.isImeVisible
                                LaunchedEffect(imeVisible) {
                                    if (imeVisible) {
                                        delay(350)
                                        val layout = textLayoutResult ?: return@LaunchedEffect
                                        val cursorOffset = content.selection.start
                                            .coerceIn(0, content.text.length.coerceAtLeast(0))
                                        try {
                                            val cursorRect = layout.getCursorRect(cursorOffset)
                                            val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
                                            // Position cursor in the middle, then move it up by ~6 lines
                                            val targetScroll = (cursorRect.top - (viewportHeightPx / 2f) + (lineHeight * 6f))
                                                .toInt().coerceAtLeast(0)
                                            scrollState.animateScrollTo(
                                                targetScroll,
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness = 200f,
                                                )
                                            )
                                        } catch (_: Exception) { }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) {
                                            focusRequester.requestFocus()
                                        }
                                ) {
                                    BasicTextField(
                                        value = content,
                                        onValueChange = { content = it },
                                        onTextLayout = { textLayoutResult = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = viewportHeight)
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                            .focusRequester(focusRequester),
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(modifier = Modifier.height(viewportHeight))
                                }

                                if (isUploading) {
                                    LinearProgressIndicator(
                                        progress = { uploadProgress },
                                        modifier = Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
