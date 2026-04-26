package sh.haven.feature.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import sh.haven.core.data.db.entities.Snippet

import androidx.compose.ui.res.stringResource
import sh.haven.feature.terminal.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetsBottomSheet(
    snippets: List<Snippet>,
    onDismiss: () -> Unit,
    onSendSnippet: (Snippet) -> Unit,
    onAddSnippet: (Snippet) -> Unit,
    onUpdateSnippet: (Snippet) -> Unit,
    onDeleteSnippet: (Snippet) -> Unit,
    zh: Boolean // keep param for backward compatibility if needed, but not used below
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingSnippet by remember { mutableStateOf<Snippet?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.presets_snippets),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_snippet))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (snippets.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_saved_snippets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(snippets) { snippet ->
                        ListItem(
                            headlineContent = { Text(snippet.name) },
                            supportingContent = {
                                Text(
                                    text = snippet.command,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingSnippet = snippet }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { onDeleteSnippet(snippet) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            modifier = Modifier.clickable {
                                onSendSnippet(snippet)
                                onDismiss()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showCreateDialog) {
        SnippetEditorDialog(
            snippet = null,
            onDismiss = { showCreateDialog = false },
            onSave = { onAddSnippet(it); showCreateDialog = false },
            zh = zh
        )
    }

    editingSnippet?.let { snippet ->
        SnippetEditorDialog(
            snippet = snippet,
            onDismiss = { editingSnippet = null },
            onSave = { onUpdateSnippet(it); editingSnippet = null },
            zh = zh
        )
    }
}

@Composable
private fun SnippetEditorDialog(
    snippet: Snippet?,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
    zh: Boolean
) {
    var name by remember { mutableStateOf(snippet?.name ?: "") }
    var command by remember { mutableStateOf(snippet?.command ?: "") }
    var autoReturn by remember { mutableStateOf(snippet?.autoReturn ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.edit_snippet_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snippet_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.command_content)) },
                    maxLines = 5,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.auto_append_enter))
                    Switch(
                        checked = autoReturn,
                        onCheckedChange = { autoReturn = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && command.isNotBlank()) {
                        val newSnippet = snippet?.copy(
                            name = name,
                            command = command,
                            autoReturn = autoReturn
                        ) ?: Snippet(
                            name = name,
                            command = command,
                            autoReturn = autoReturn
                        )
                        onSave(newSnippet)
                    }
                },
                enabled = name.isNotBlank() && command.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
