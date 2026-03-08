package sh.haven.feature.terminal

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.connectbot.terminal.SelectionController
import sh.haven.core.data.preferences.ToolbarItem
import sh.haven.core.data.preferences.ToolbarKey
import sh.haven.core.data.preferences.ToolbarLayout

// VT100/xterm escape sequences for special keys
private const val ESC = "\u001b"
private val KEY_ESC = byteArrayOf(0x1b)
private val KEY_TAB = byteArrayOf(0x09)
private val KEY_SHIFT_TAB = "$ESC[Z".toByteArray()
private val KEY_UP = "$ESC[A".toByteArray()
private val KEY_DOWN = "$ESC[B".toByteArray()
private val KEY_RIGHT = "$ESC[C".toByteArray()
private val KEY_LEFT = "$ESC[D".toByteArray()
private val KEY_HOME = "$ESC[H".toByteArray()
private val KEY_END = "$ESC[F".toByteArray()
private val KEY_PGUP = "$ESC[5~".toByteArray()
private val KEY_PGDN = "$ESC[6~".toByteArray()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardToolbar(
    onSendBytes: (ByteArray) -> Unit,
    focusRequester: FocusRequester,
    ctrlActive: Boolean = false,
    altActive: Boolean = false,
    bracketPasteMode: Boolean = false,
    layout: ToolbarLayout = ToolbarLayout.DEFAULT,
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onVncTap: (() -> Unit)? = null,
    selectionController: SelectionController? = null,
    selectionActive: Boolean = false,
    hyperlinkUri: String? = null,
    onPaste: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var shiftActive by remember { mutableStateOf(false) }
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column {
            val rowCount = layout.rows.size
            layout.rows.forEachIndexed { index, row ->
                // When selection is active and there are 2+ rows, replace the
                // last row with selection controls to keep total height constant.
                if (selectionActive && selectionController != null &&
                    index == rowCount - 1 && rowCount > 1
                ) {
                    SelectionToolbarContent(
                        controller = selectionController,
                        hyperlinkUri = hyperlinkUri,
                        bracketPasteMode = bracketPasteMode,
                        onPaste = onPaste,
                    )
                } else if (row.isNotEmpty()) {
                    ToolbarRow(
                        items = row,
                        onSendBytes = onSendBytes,
                        focusRequester = focusRequester,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        shiftActive = shiftActive,
                        imeVisible = imeVisible,
                        view = view,
                        onToggleCtrl = onToggleCtrl,
                        onToggleAlt = onToggleAlt,
                        onToggleShift = { shiftActive = !shiftActive },
                        onShiftUsed = { shiftActive = false },
                        onVncTap = if (index == 0) onVncTap else null,
                    )
                }
            }
            // Single-row layout: show selection as the only row (replaces keyboard)
            if (selectionActive && selectionController != null && rowCount <= 1) {
                SelectionToolbarContent(
                    controller = selectionController,
                    hyperlinkUri = hyperlinkUri,
                    bracketPasteMode = bracketPasteMode,
                    onPaste = onPaste,
                )
            }
        }
    }
}

@Composable
private fun ToolbarRow(
    items: List<ToolbarItem>,
    onSendBytes: (ByteArray) -> Unit,
    focusRequester: FocusRequester,
    ctrlActive: Boolean,
    altActive: Boolean,
    shiftActive: Boolean,
    imeVisible: Boolean,
    view: android.view.View,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleShift: () -> Unit,
    onShiftUsed: () -> Unit,
    onVncTap: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            when (item) {
                is ToolbarItem.BuiltIn -> {
                    BuiltInKey(
                        key = item.key,
                        onSendBytes = onSendBytes,
                        focusRequester = focusRequester,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        shiftActive = shiftActive,
                        imeVisible = imeVisible,
                        view = view,
                        onToggleCtrl = onToggleCtrl,
                        onToggleAlt = onToggleAlt,
                        onToggleShift = onToggleShift,
                        onShiftUsed = onShiftUsed,
                    )
                    // VNC icon right after keyboard toggle
                    if (item.key == ToolbarKey.KEYBOARD && onVncTap != null) {
                        ToolbarIconButton(Icons.Filled.DesktopWindows, "VNC Desktop", onVncTap)
                    }
                }
                is ToolbarItem.Custom -> {
                    SymbolButton(item.label) {
                        val bytes = item.send.toByteArray()
                        if (ctrlActive || altActive) {
                            // Apply modifiers to first byte if it's a single printable char
                            if (item.send.length == 1) {
                                sendChar(item.send[0], ctrlActive, altActive, onSendBytes)
                            } else {
                                onSendBytes(bytes)
                            }
                            if (ctrlActive) onToggleCtrl()
                            if (altActive) onToggleAlt()
                        } else {
                            onSendBytes(bytes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltInKey(
    key: ToolbarKey,
    onSendBytes: (ByteArray) -> Unit,
    focusRequester: FocusRequester,
    ctrlActive: Boolean,
    altActive: Boolean,
    shiftActive: Boolean,
    imeVisible: Boolean,
    view: android.view.View,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleShift: () -> Unit,
    onShiftUsed: () -> Unit,
) {
    when (key) {
        ToolbarKey.KEYBOARD -> {
            ToolbarIconButton(Icons.Filled.Keyboard, "Toggle keyboard") {
                val window = (view.context as? Activity)?.window ?: return@ToolbarIconButton
                val controller = WindowCompat.getInsetsController(window, view)
                if (imeVisible) {
                    controller.hide(WindowInsetsCompat.Type.ime())
                } else {
                    focusRequester.requestFocus()
                    controller.show(WindowInsetsCompat.Type.ime())
                }
            }
        }
        ToolbarKey.ESC_KEY -> ToolbarTextButton("Esc") { onSendBytes(KEY_ESC) }
        ToolbarKey.TAB_KEY -> ToolbarTextButton("Tab") {
            if (shiftActive) {
                onSendBytes(KEY_SHIFT_TAB)
                onShiftUsed()
            } else {
                onSendBytes(KEY_TAB)
            }
        }
        ToolbarKey.SHIFT -> ToolbarToggleButton("Shift", shiftActive, onClick = onToggleShift)
        ToolbarKey.CTRL -> ToolbarToggleButton("Ctrl", ctrlActive, onClick = onToggleCtrl)
        ToolbarKey.ALT -> ToolbarToggleButton("Alt", altActive, onClick = onToggleAlt)
        ToolbarKey.ARROW_LEFT -> ToolbarArrowButton("\u2190") { onSendBytes(KEY_LEFT) }
        ToolbarKey.ARROW_UP -> ToolbarArrowButton("\u2191") { onSendBytes(KEY_UP) }
        ToolbarKey.ARROW_DOWN -> ToolbarArrowButton("\u2193") { onSendBytes(KEY_DOWN) }
        ToolbarKey.ARROW_RIGHT -> ToolbarArrowButton("\u2192") { onSendBytes(KEY_RIGHT) }
        ToolbarKey.HOME -> ToolbarTextButton("Home") { onSendBytes(KEY_HOME) }
        ToolbarKey.END -> ToolbarTextButton("End") { onSendBytes(KEY_END) }
        ToolbarKey.PGUP -> ToolbarTextButton("PgUp") { onSendBytes(KEY_PGUP) }
        ToolbarKey.PGDN -> ToolbarTextButton("PgDn") { onSendBytes(KEY_PGDN) }
        else -> {
            val ch = key.char ?: return
            SymbolButton(key.label) {
                sendChar(ch, ctrlActive, altActive, onSendBytes)
                if (ctrlActive) onToggleCtrl()
                if (altActive) onToggleAlt()
            }
        }
    }
}

private fun sendChar(
    char: Char,
    ctrl: Boolean,
    alt: Boolean,
    onSendBytes: (ByteArray) -> Unit,
) {
    val byte = if (ctrl && char.code in 0x40..0x7F) {
        byteArrayOf((char.code and 0x1F).toByte())
    } else {
        char.toString().toByteArray()
    }

    if (alt) {
        onSendBytes(byteArrayOf(0x1b) + byte)
    } else {
        onSendBytes(byte)
    }
}

@Composable
private fun ToolbarArrowButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            label,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolbarTextButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 11.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun ToolbarToggleButton(label: String, active: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        colors = if (active) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Text(label, fontSize = 11.sp, lineHeight = 11.sp)
    }
}

@Composable
private fun SymbolButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .height(30.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun ToolbarIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}
