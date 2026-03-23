package sh.haven.feature.terminal

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.terminal.SelectionController

private const val TAG = "SelectionToolbar"

/**
 * Reflective helper for moving individual selection anchors.
 *
 * The connectbot termlib library marks SelectionManager and SelectionRange as
 * `internal`, so we can't reference them at compile time. The anonymous
 * SelectionController implementation captures the SelectionManager in a field
 * named `$selectionManager`. We use reflection to read the current selection
 * range and call updateSelectionStart / updateSelectionEnd independently.
 */
private class AnchorMover(controller: SelectionController) {
    private val manager: Any? = try {
        val field = controller.javaClass.getDeclaredField("\$selectionManager")
        field.isAccessible = true
        field.get(controller)
    } catch (e: Exception) {
        Log.d(TAG, "Could not extract SelectionManager: ${e.message}")
        null
    }

    val available: Boolean = manager != null

    fun moveStart(dCol: Int, dRow: Int) = moveAnchorInternal("Start", dCol, dRow)
    fun moveEnd(dCol: Int, dRow: Int) = moveAnchorInternal("End", dCol, dRow)

    private fun moveAnchorInternal(which: String, dCol: Int, dRow: Int) {
        val mgr = manager ?: return
        try {
            val rangeGetter = mgr.javaClass.getMethod("getSelectionRange")
            val range = rangeGetter.invoke(mgr) ?: return
            val row = range.javaClass.getMethod("get${which}Row").invoke(range) as Int
            val col = range.javaClass.getMethod("get${which}Col").invoke(range) as Int
            val update = mgr.javaClass.getMethod("updateSelection$which", Int::class.java, Int::class.java)
            update.invoke(mgr, row + dRow, col + dCol)
        } catch (e: Exception) {
            Log.d(TAG, "AnchorMover.move$which failed: ${e.message}")
        }
    }
}

/**
 * Read the current selection range (startRow, startCol, endRow, endCol)
 * from the library-internal SelectionManager via reflection.
 * Returns null if no selection is active or reflection fails.
 */
internal fun getSelectionRange(
    controller: org.connectbot.terminal.SelectionController,
): SelectionPosition? {
    try {
        val mgrField = controller.javaClass.getDeclaredField("\$selectionManager")
        mgrField.isAccessible = true
        val mgr = mgrField.get(controller) ?: return null
        val range = mgr.javaClass.getMethod("getSelectionRange").invoke(mgr) ?: return null
        val startRow = range.javaClass.getMethod("getStartRow").invoke(range) as Int
        val startCol = range.javaClass.getMethod("getStartCol").invoke(range) as Int
        val endRow = range.javaClass.getMethod("getEndRow").invoke(range) as Int
        val endCol = range.javaClass.getMethod("getEndCol").invoke(range) as Int
        return SelectionPosition(startRow, startCol, endRow, endCol)
    } catch (e: Exception) {
        Log.d(TAG, "getSelectionRange: ${e.message}")
        return null
    }
}

internal data class SelectionPosition(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
)

/**
 * Expand a single-character selection to the word (contiguous non-whitespace
 * token) under the cursor. Called immediately after long-press starts selection.
 *
 * Uses reflection to access:
 * - SelectionManager from the SelectionController (library-internal)
 * - TerminalSnapshot from the TerminalEmulator (library-internal getSnapshot$lib)
 */
internal fun expandSelectionToWord(
    controller: SelectionController,
    emulator: org.connectbot.terminal.TerminalEmulator,
) {
    try {
        // Get SelectionManager from controller
        val mgrField = controller.javaClass.getDeclaredField("\$selectionManager")
        mgrField.isAccessible = true
        val mgr = mgrField.get(controller) ?: return

        // Get current selection position
        val range = mgr.javaClass.getMethod("getSelectionRange").invoke(mgr) ?: return
        val row = range.javaClass.getMethod("getStartRow").invoke(range) as Int
        val col = range.javaClass.getMethod("getStartCol").invoke(range) as Int

        // Get line text at selection row
        val lines = getSnapshotLines(emulator) ?: return
        if (row < 0 || row >= lines.size) return
        val text = getLineText(lines[row])
        if (col < 0 || col >= text.length) return

        // Don't expand if long-pressed on whitespace
        if (text[col].isWhitespace()) return

        // Expand to contiguous non-whitespace (selects full tokens: paths, URLs, etc.)
        var startCol = col
        while (startCol > 0 && !text[startCol - 1].isWhitespace()) startCol--
        var endCol = col
        while (endCol < text.length - 1 && !text[endCol + 1].isWhitespace()) endCol++

        // Update selection anchors if expanded
        if (startCol != col || endCol != col) {
            val updateStart = mgr.javaClass.getMethod(
                "updateSelectionStart", Int::class.java, Int::class.java,
            )
            val updateEnd = mgr.javaClass.getMethod(
                "updateSelectionEnd", Int::class.java, Int::class.java,
            )
            updateStart.invoke(mgr, row, startCol)
            updateEnd.invoke(mgr, row, endCol)
        }
    } catch (e: Exception) {
        Log.d(TAG, "expandSelectionToWord: ${e.message}")
    }
}

/**
 * Set the selection end anchor to an absolute cell position.
 * Used by the highlighter-drag gesture during long-press selection.
 */
internal fun updateSelectionEndAbsolute(
    controller: SelectionController,
    row: Int,
    col: Int,
) {
    try {
        val mgrField = controller.javaClass.getDeclaredField("\$selectionManager")
        mgrField.isAccessible = true
        val mgr = mgrField.get(controller) ?: return
        val update = mgr.javaClass.getMethod(
            "updateSelectionEnd", Int::class.java, Int::class.java,
        )
        update.invoke(mgr, row, col)
    } catch (e: Exception) {
        Log.d(TAG, "updateSelectionEndAbsolute: ${e.message}")
    }
}

/**
 * Extract snapshot lines from the terminal emulator via reflection.
 * Returns null if reflection fails.
 */
@Suppress("UNCHECKED_CAST")
private fun getSnapshotLines(
    emulator: org.connectbot.terminal.TerminalEmulator,
): List<Any>? {
    return try {
        val snapshotFlow = emulator.javaClass.getMethod("getSnapshot\$lib").invoke(emulator)
        val snapshot = snapshotFlow.javaClass.getMethod("getValue").invoke(snapshotFlow)
            ?: return null
        snapshot.javaClass.getMethod("getLines").invoke(snapshot) as List<Any>
    } catch (e: Exception) {
        Log.d(TAG, "getSnapshotLines: ${e.message}")
        null
    }
}

/** Get text content of a snapshot line via reflection. */
private fun getLineText(line: Any): String {
    return try {
        line.javaClass.getMethod("getText").invoke(line) as String
    } catch (e: Exception) { "" }
}

/** Get terminal column count via reflection on emulator.dimensions. */
private fun getTerminalColumns(
    emulator: org.connectbot.terminal.TerminalEmulator,
): Int? {
    return try {
        val dims = emulator.javaClass.getMethod("getDimensions").invoke(emulator)
        dims.javaClass.getMethod("getColumns").invoke(dims) as Int
    } catch (e: Exception) {
        Log.d(TAG, "getTerminalColumns: ${e.message}")
        null
    }
}

/** True if the character is a vertical box-drawing border. */
private fun isVerticalBorder(ch: Char): Boolean {
    return ch == '│' || ch == '┃' || ch == '║' || ch == '|' ||
        ch == '┆' || ch == '┇' || ch == '┊' || ch == '┋'
}

/**
 * Find column positions where vertical border characters appear consistently
 * across selected lines, indicating TUI panel boundaries.
 */
private fun findConsistentBorderColumns(lines: List<String>): Set<Int> {
    if (lines.size < 2) return emptySet()

    val nonEmptyLines = lines.count { it.isNotBlank() }
    if (nonEmptyLines < 2) return emptySet()

    val maxLen = lines.maxOf { it.length }
    val borderCounts = IntArray(maxLen)

    for (line in lines) {
        for ((col, ch) in line.withIndex()) {
            if (isVerticalBorder(ch)) {
                borderCounts[col]++
            }
        }
    }

    // A column is a consistent border if it has a vertical border in >=60% of non-empty lines
    val threshold = (nonEmptyLines * 0.6).toInt().coerceAtLeast(2)
    return borderCounts.indices.filter { borderCounts[it] >= threshold }.toSet()
}

/**
 * Extract text from the panel that contains [startCol], bounded by
 * consistent vertical border columns.
 */
private fun extractPanelContent(
    lines: List<String>,
    borderCols: Set<Int>,
    startCol: Int,
): String {
    val sortedBorders = borderCols.sorted()
    val leftBorder = sortedBorders.lastOrNull { it < startCol } ?: -1
    val rightBorder = sortedBorders.firstOrNull { it > startCol }
        ?: (lines.maxOfOrNull { it.length } ?: 0)

    return lines.map { line ->
        val start = (leftBorder + 1).coerceAtLeast(0)
        val end = rightBorder.coerceAtMost(line.length)
        if (start < end) line.substring(start, end).trim() else ""
    }.joinToString("\n").trimEnd()
}

/**
 * Extract the selected portion of each line and unwrap soft-wrapped lines.
 * A line that fills the full terminal width is assumed to be soft-wrapped.
 */
private fun extractWithSoftWrapUnwrap(
    fullTexts: List<String>,
    sel: SelectionPosition,
    columns: Int,
): String {
    // Extract selected portion of each line
    val selectedTexts = fullTexts.mapIndexed { i, text ->
        val row = sel.startRow + i
        val start = if (row == sel.startRow) sel.startCol else 0
        val end = if (row == sel.endRow) (sel.endCol + 1).coerceAtMost(text.length) else text.length
        if (start < end && start < text.length) {
            text.substring(start, end.coerceAtMost(text.length))
        } else ""
    }

    val result = StringBuilder()
    for (i in selectedTexts.indices) {
        result.append(selectedTexts[i])
        if (i < selectedTexts.size - 1) {
            // If the full line fills the terminal width, it was likely soft-wrapped
            if (fullTexts[i].trimEnd().length >= columns) {
                // Soft-wrapped: join without newline
            } else {
                result.append('\n')
            }
        }
    }
    return result.toString()
}

/**
 * Smart copy: extracts text from the terminal selection with two enhancements:
 * 1. TUI border stripping — detects vertical box-drawing borders and extracts
 *    only the panel content where the selection started.
 * 2. Soft-wrap unwrapping — joins lines that were soft-wrapped at the terminal
 *    width boundary, so copied text reads as it would in a wider terminal.
 *
 * Falls back to [SelectionController.copySelection] if reflection fails.
 */
internal fun smartCopy(
    controller: SelectionController,
    emulator: org.connectbot.terminal.TerminalEmulator,
): String? {
    val sel = getSelectionRange(controller) ?: return null
    val columns = getTerminalColumns(emulator) ?: return null
    val snapshotLines = getSnapshotLines(emulator) ?: return null

    val fullTexts = (sel.startRow..sel.endRow).map { row ->
        if (row in snapshotLines.indices) getLineText(snapshotLines[row]) else ""
    }

    val borderCols = findConsistentBorderColumns(fullTexts)

    return if (borderCols.isNotEmpty()) {
        extractPanelContent(fullTexts, borderCols, sel.startCol)
    } else {
        extractWithSoftWrapUnwrap(fullTexts, sel, columns)
    }
}

/**
 * ClipboardManager wrapper that applies smart copy processing (border stripping,
 * soft-wrap unwrapping) to all text written from the terminal. Used via
 * CompositionLocalProvider to intercept both the toolbar copy button and the
 * library's own popup copy action.
 */
class SmartTerminalClipboard(
    private val delegate: androidx.compose.ui.platform.ClipboardManager,
    private val getEmulator: () -> org.connectbot.terminal.TerminalEmulator,
    private val getController: () -> SelectionController?,
) : androidx.compose.ui.platform.ClipboardManager by delegate {

    override fun setText(annotatedString: AnnotatedString) {
        val controller = getController()
        val emulator = getEmulator()
        if (controller != null) {
            val processed = smartCopy(controller, emulator)
            if (processed != null) {
                delegate.setText(AnnotatedString(processed))
                return
            }
        }
        delegate.setText(annotatedString)
    }
}

/** Which selection anchor the d-pad arrows control. */
private enum class AnchorTarget { START, END }

@Composable
fun SelectionToolbar(
    controller: SelectionController,
    hyperlinkUri: String? = null,
    bracketPasteMode: Boolean = false,
    onPaste: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        SelectionToolbarContent(
            controller = controller,
            hyperlinkUri = hyperlinkUri,
            bracketPasteMode = bracketPasteMode,
            onPaste = onPaste,
        )
    }
}

/**
 * Selection toolbar row content without a Surface wrapper.
 * Used by [KeyboardToolbar] to embed selection controls in place of a keyboard
 * row, keeping total toolbar height constant.
 */
@Composable
fun SelectionToolbarContent(
    controller: SelectionController,
    hyperlinkUri: String? = null,
    bracketPasteMode: Boolean = false,
    onPaste: (String) -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val zh = Locale.current.language == "zh"
    val anchorMover = remember(controller) { AnchorMover(controller) }
    var anchorTarget by remember { mutableStateOf(AnchorTarget.END) }

    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val copiedMsg = stringResource(R.string.copied)
        SelectionIconButton(Icons.Filled.ContentCopy, stringResource(R.string.copy)) {
            val text = controller.copySelection()
            if (!text.isNullOrEmpty()) {
                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
            }
        }

        // Paste (wrapped in bracket paste sequences when mode 2004 is active)
        SelectionIconButton(Icons.Filled.ContentPaste, stringResource(R.string.paste)) {
            val text = clipboardManager.getText()?.text
            if (!text.isNullOrEmpty()) {
                controller.clearSelection()
                if (bracketPasteMode) {
                    onPaste("\u001b[200~$text\u001b[201~")
                } else {
                    onPaste(text)
                }
            }
        }

        // Open URL (detected in selection text, or from OSC 8 hyperlink)
        // Try the raw selection first, then with newlines stripped to handle
        // URLs split across lines by the program or terminal wrapping.
        val noUrlMsg = stringResource(R.string.no_url_detected)
        SelectionIconButton(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open)) {
            val raw = controller.copySelection()?.trim()
            val joined = raw?.replace(Regex("\\s*\\n\\s*"), "")
            val url = detectUrl(raw) ?: detectUrl(joined) ?: hyperlinkUri
            if (url != null) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    if (url.contains("://")) url else "https://$url"
                )))
                controller.clearSelection()
            } else {
                Toast.makeText(context, noUrlMsg, Toast.LENGTH_SHORT).show()
            }
        }

        // Anchor target toggle: Start / End
        if (anchorMover.available) {
            SelectionToggleButton(
                label = if (anchorTarget == AnchorTarget.START) {
                    stringResource(R.string.start)
                } else {
                    stringResource(R.string.end)
                },
                active = anchorTarget == AnchorTarget.START,
                onClick = {
                    anchorTarget = if (anchorTarget == AnchorTarget.END)
                        AnchorTarget.START else AnchorTarget.END
                },
            )
        }

        // D-pad arrows
        SelectionIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.up)) {
            moveAnchor(anchorMover, anchorTarget, 0, -1)
        }
        SelectionIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.down)) {
            moveAnchor(anchorMover, anchorTarget, 0, 1)
        }
        SelectionIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.left)) {
            moveAnchor(anchorMover, anchorTarget, -1, 0)
        }
        SelectionIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.right)) {
            moveAnchor(anchorMover, anchorTarget, 1, 0)
        }

        // Dismiss selection
        SelectionIconButton(Icons.Filled.Close, stringResource(R.string.cancel)) {
            controller.clearSelection()
        }
    }
}

private fun moveAnchor(
    mover: AnchorMover,
    target: AnchorTarget,
    dCol: Int,
    dRow: Int,
) {
    if (target == AnchorTarget.START) {
        mover.moveStart(dCol, dRow)
    } else {
        mover.moveEnd(dCol, dRow)
    }
}

@Composable
private fun SelectionToggleButton(label: String, active: Boolean, onClick: () -> Unit) {
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
private fun SelectionIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/**
 * Detect a URL in the selected text. Returns null if no URL found.
 * Auto-adds "https://" if the matched text has no scheme.
 */
internal fun detectUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val matcher = Patterns.WEB_URL.matcher(text)
    if (!matcher.find()) return null
    val url = matcher.group()
    return if (url.contains("://")) url else "https://$url"
}
