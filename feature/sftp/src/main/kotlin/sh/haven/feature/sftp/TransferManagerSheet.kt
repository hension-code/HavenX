package sh.haven.feature.sftp

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import sh.haven.core.data.db.entities.DownloadRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TransferTab { ACTIVE, HISTORY }
private enum class TransferDirectionFilter { ALL, DOWNLOAD, UPLOAD }

@Composable
fun TransferManagerSheetContent(
    transfers: List<TransferTask>,
    history: List<DownloadRecord>,
    connectedProfileIds: Set<String>,
    profileNames: Map<String, String>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onResumeHistory: (String) -> Unit,
    onDeleteHistory: (id: String, deleteLocalFile: Boolean) -> Unit,
    onClearHistory: (deleteLocalFiles: Boolean) -> Unit,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onCopyRemotePath: (String) -> Unit,
) {
    val activeTransfers = remember(transfers) {
        transfers.filter { it.state != TransferState.DONE && it.state != TransferState.CANCELLED }
    }
    val activeIds = remember(activeTransfers) { activeTransfers.map { it.id }.toSet() }

    // Orphaned resumable records that exist only in DB history (not currently in memory).
    val orphanedResumable = remember(history, activeIds) {
        history.filter { record ->
            record.id !in activeIds &&
                record.state != TransferState.DONE.name &&
                record.state != TransferState.CANCELLED.name
        }
    }
    val completedHistory = remember(history) {
        history.filter { record ->
            record.state == TransferState.DONE.name || record.state == TransferState.CANCELLED.name
        }
    }

    val totalActiveCount = activeTransfers.size + orphanedResumable.size
    val totalHistoryCount = completedHistory.size

    val initialPage = remember {
        if (totalActiveCount == 0 && totalHistoryCount > 0) TransferTab.HISTORY.ordinal
        else TransferTab.ACTIVE.ordinal
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { TransferTab.entries.size })
    val selectedTab = TransferTab.entries[pagerState.currentPage]
    val coroutineScope = rememberCoroutineScope()
    var directionFilter by remember { mutableStateOf(TransferDirectionFilter.ALL) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var clearLocalFilesChecked by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var deleteLocalFileChecked by remember { mutableStateOf(false) }

    val requestDeleteRecord: (DownloadRecord) -> Unit = { record ->
        if (record.hasLocalDownloadedFile()) {
            deleteLocalFileChecked = false
            recordToDelete = record
        } else {
            onDeleteHistory(record.id, false)
        }
    }

    val filteredActive = remember(activeTransfers, directionFilter) {
        activeTransfers.filter { it.matchesDirection(directionFilter) }
    }
    val filteredOrphaned = remember(orphanedResumable, directionFilter) {
        orphanedResumable.filter { it.matchesDirection(directionFilter) }
    }
    val filteredHistory = remember(completedHistory, directionFilter) {
        completedHistory.filter { it.matchesDirection(directionFilter) }
    }

    val hasFailedRetryable = remember(activeTransfers, connectedProfileIds) {
        activeTransfers.any { it.state == TransferState.ERROR && it.profileId in connectedProfileIds }
    }

    if (showClearConfirm) {
        val hasLocalFilesInHistory = remember(completedHistory) {
            completedHistory.any { it.hasLocalDownloadedFile() }
        }
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 310.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.transfer_clear_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.transfer_clear_history_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (hasLocalFilesInHistory) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { clearLocalFilesChecked = !clearLocalFilesChecked }
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = clearLocalFilesChecked,
                                onCheckedChange = { clearLocalFilesChecked = it },
                            )
                            Text(
                                text = stringResource(R.string.transfer_clear_local_files_checkbox),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteFiles = hasLocalFilesInHistory && clearLocalFilesChecked
                        showClearConfirm = false
                        onClearHistory(deleteFiles)
                    },
                ) {
                    Text(
                        stringResource(R.string.transfer_clear_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .widthIn(max = 310.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.transfer_delete_record_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.transfer_delete_record_message, record.fileName),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { deleteLocalFileChecked = !deleteLocalFileChecked }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteLocalFileChecked,
                            onCheckedChange = { deleteLocalFileChecked = it },
                        )
                        Text(
                            text = stringResource(R.string.transfer_delete_local_file_checkbox),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleteFile = deleteLocalFileChecked
                        recordToDelete = null
                        onDeleteHistory(record.id, deleteFile)
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(stringResource(R.string.cancel_button))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f),
    ) {
        // Top header bar (fixed height so switching tabs never shifts the tab bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transfer_manager_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selectedTab == TransferTab.ACTIVE && hasFailedRetryable) {
                TextButton(onClick = onRetryFailed) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.transfer_retry_failed))
                }
            }
            if (selectedTab == TransferTab.HISTORY && totalHistoryCount > 0) {
                TextButton(
                    onClick = {
                        clearLocalFilesChecked = false
                        showClearConfirm = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.transfer_clear_history))
                }
            }
        }

        // Segmented tabs: Active / History
        TransferSegmentedTabs(
            selectedTab = selectedTab,
            activeCount = totalActiveCount,
            historyCount = totalHistoryCount,
            onSelectTab = { tab ->
                coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Direction filter chips: kept stable across tabs whenever any transfer/history exists
        if (totalActiveCount > 0 || totalHistoryCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = directionFilter == TransferDirectionFilter.ALL,
                    onClick = { directionFilter = TransferDirectionFilter.ALL },
                    label = { Text(stringResource(R.string.transfer_filter_all)) },
                )
                FilterChip(
                    selected = directionFilter == TransferDirectionFilter.DOWNLOAD,
                    onClick = { directionFilter = TransferDirectionFilter.DOWNLOAD },
                    label = { Text(stringResource(R.string.transfer_filter_downloads)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                FilterChip(
                    selected = directionFilter == TransferDirectionFilter.UPLOAD,
                    onClick = { directionFilter = TransferDirectionFilter.UPLOAD },
                    label = { Text(stringResource(R.string.transfer_filter_uploads)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Main list content with horizontal swipe paging
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (TransferTab.entries[page]) {
                TransferTab.ACTIVE -> {
                    if (filteredActive.isEmpty() && filteredOrphaned.isEmpty()) {
                        val isFilteredEmpty = totalActiveCount > 0 && directionFilter != TransferDirectionFilter.ALL
                        TransferEmptyState(
                            icon = Icons.Filled.SwapVert,
                            title = stringResource(
                                if (isFilteredEmpty) R.string.transfer_empty_filter_title
                                else R.string.transfer_empty_active_title
                            ),
                            description = stringResource(
                                if (isFilteredEmpty) R.string.transfer_empty_filter_desc
                                else R.string.transfer_empty_active_desc
                            ),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (activeTransfers.isNotEmpty()) {
                                item(key = "active_summary_banner") {
                                    ActiveSummaryBanner(
                                        activeTransfers = activeTransfers,
                                        connectedProfileIds = connectedProfileIds,
                                        onPauseAll = onPauseAll,
                                        onResumeAll = onResumeAll,
                                    )
                                }
                            }
                            items(filteredActive, key = { it.id }) { task ->
                                ActiveTransferCard(
                                    task = task,
                                    serverName = profileNames[task.profileId]
                                        ?: stringResource(R.string.transfer_unknown_server),
                                    canResume = task.profileId in connectedProfileIds,
                                    onPause = { onPause(task.id) },
                                    onResume = { onResume(task.id) },
                                    onCancel = { onCancel(task.id) },
                                )
                            }
                            items(filteredOrphaned, key = { "orphan_${it.id}" }) { record ->
                                OrphanedResumableCard(
                                    record = record,
                                    serverName = profileNames[record.profileId]
                                        ?: stringResource(R.string.transfer_unknown_server),
                                    canResume = record.profileId in connectedProfileIds,
                                    onResume = { onResumeHistory(record.id) },
                                    onDelete = { requestDeleteRecord(record) },
                                )
                            }
                        }
                    }
                }

                TransferTab.HISTORY -> {
                    if (filteredHistory.isEmpty()) {
                        val isFilteredEmpty = totalHistoryCount > 0 && directionFilter != TransferDirectionFilter.ALL
                        TransferEmptyState(
                            icon = Icons.Filled.History,
                            title = stringResource(
                                if (isFilteredEmpty) R.string.transfer_empty_filter_title
                                else R.string.transfer_empty_history_title
                            ),
                            description = stringResource(
                                if (isFilteredEmpty) R.string.transfer_empty_filter_desc
                                else R.string.transfer_empty_history_desc
                            ),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filteredHistory, key = { it.id }) { record ->
                                HistoryTransferCard(
                                    record = record,
                                    serverName = profileNames[record.profileId]
                                        ?: stringResource(R.string.transfer_unknown_server),
                                    onOpen = { onOpenFile(record.localUri ?: "") },
                                    onShare = { onShareFile(record.localUri ?: "") },
                                    onCopyRemotePath = { onCopyRemotePath(record.remotePath) },
                                    onDelete = { requestDeleteRecord(record) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferSegmentedTabs(
    selectedTab: TransferTab,
    activeCount: Int,
    historyCount: Int,
    onSelectTab: (TransferTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SegmentedTabPill(
                title = stringResource(R.string.transfer_tab_active),
                count = activeCount,
                selected = selectedTab == TransferTab.ACTIVE,
                onClick = { onSelectTab(TransferTab.ACTIVE) },
                modifier = Modifier.weight(1f),
            )
            SegmentedTabPill(
                title = stringResource(R.string.transfer_tab_completed),
                count = historyCount,
                selected = selectedTab == TransferTab.HISTORY,
                onClick = { onSelectTab(TransferTab.HISTORY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentedTabPill(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSummaryBanner(
    activeTransfers: List<TransferTask>,
    connectedProfileIds: Set<String>,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val running = activeTransfers.filter { it.state == TransferState.DOWNLOADING }
    val pausedCount = activeTransfers.count { it.state == TransferState.PAUSED }
    val failedCount = activeTransfers.count { it.state == TransferState.ERROR }
    val canResumeAny = activeTransfers.any {
        (it.state == TransferState.PAUSED || it.state == TransferState.ERROR) &&
            it.profileId in connectedProfileIds
    }

    val downSpeed = running.filter { !it.isUpload }.sumOf { it.speedBytesPerSec }
    val upSpeed = running.filter { it.isUpload }.sumOf { it.speedBytesPerSec }

    val knownTotal = activeTransfers.filter { it.totalBytes > 0L }.sumOf { it.totalBytes }
    val knownTransferred = activeTransfers.filter { it.totalBytes > 0L }.sumOf {
        it.transferredBytes.coerceAtMost(it.totalBytes)
    }
    val rawFraction = if (knownTotal > 0L) (knownTransferred.toFloat() / knownTotal).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(targetValue = rawFraction, label = "summaryProgress")

    val summaryParts = buildList {
        if (running.isNotEmpty()) add(stringResource(R.string.transfer_summary_active, running.size))
        if (pausedCount > 0) add(stringResource(R.string.transfer_summary_paused, pausedCount))
        if (failedCount > 0) add(stringResource(R.string.transfer_summary_failed, failedCount))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summaryParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val speedParts = buildList {
                        if (downSpeed > 0L) add("↓ ${formatSpeed(context, downSpeed)}")
                        if (upSpeed > 0L) add("↑ ${formatSpeed(context, upSpeed)}")
                    }
                    if (speedParts.isNotEmpty() || knownTotal > 0L) {
                        Spacer(Modifier.height(2.dp))
                        val meta = buildList {
                            if (speedParts.isNotEmpty()) add(speedParts.joinToString("  "))
                            if (knownTotal > 0L) add("${(rawFraction * 100f).toInt()}%")
                        }.joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (running.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onPauseAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.transfer_pause_all),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else if (canResumeAny) {
                    FilledTonalButton(
                        onClick = onResumeAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.transfer_resume_all),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (knownTotal > 0L) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ActiveTransferCard(
    task: TransferTask,
    serverName: String,
    canResume: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val animatedFraction by animateFloatAsState(
        targetValue = task.fraction,
        label = "taskProgress_${task.id}",
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileDirectionAvatar(
                    fileName = task.fileName,
                    isUpload = task.isUpload,
                    isError = task.state == TransferState.ERROR,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    ServerPathMetaRow(
                        protocol = if (task.isSmb) "SMB" else "SFTP",
                        serverName = serverName,
                        remotePath = task.remotePath,
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Action buttons with comfortable 40dp touch targets
                when (task.state) {
                    TransferState.DOWNLOADING -> {
                        FilledTonalIconButton(
                            onClick = onPause,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.transfer_action_pause),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.transfer_action_cancel),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    TransferState.PAUSED, TransferState.ERROR -> {
                        FilledIconButton(
                            onClick = onResume,
                            enabled = canResume,
                            modifier = Modifier.size(40.dp),
                            colors = if (task.state == TransferState.ERROR) {
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                IconButtonDefaults.filledIconButtonColors()
                            },
                        ) {
                            Icon(
                                imageVector = if (task.state == TransferState.ERROR) {
                                    Icons.Filled.Refresh
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (task.state == TransferState.ERROR) R.string.transfer_action_retry
                                    else R.string.transfer_action_resume
                                ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.transfer_action_cancel),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    TransferState.DONE, TransferState.CANCELLED -> Unit
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            val progressColor = when (task.state) {
                TransferState.DOWNLOADING -> MaterialTheme.colorScheme.primary
                TransferState.PAUSED -> MaterialTheme.colorScheme.outline
                TransferState.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            if (task.state == TransferState.DOWNLOADING && task.totalBytes <= 0L) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    strokeCap = StrokeCap.Round,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Status & metrics row
            when (task.state) {
                TransferState.DOWNLOADING -> {
                    val transferredStr = Formatter.formatFileSize(context, task.transferredBytes)
                    val sizeText = if (task.totalBytes > 0L) {
                        "$transferredStr / ${Formatter.formatFileSize(context, task.totalBytes)}"
                    } else {
                        transferredStr
                    }
                    val rightMetrics = buildList {
                        if (task.speedBytesPerSec > 0L) add(formatSpeed(context, task.speedBytesPerSec))
                        task.etaSeconds?.takeIf { it > 0L }?.let { add(formatEta(it)) }
                        if (task.totalBytes > 0L) add("${task.progressPercent}%")
                    }.joinToString(" · ")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (rightMetrics.isNotEmpty()) {
                            Text(
                                text = rightMetrics,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                TransferState.PAUSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val pauseDetail = if (task.totalBytes > 0L) {
                            "${stringResource(R.string.transfer_status_paused)} · ${
                                Formatter.formatFileSize(context, task.transferredBytes)
                            } / ${Formatter.formatFileSize(context, task.totalBytes)} (${task.progressPercent}%)"
                        } else {
                            stringResource(R.string.transfer_status_paused)
                        }
                        Text(
                            text = pauseDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!canResume) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.transfer_server_disconnected_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }

                TransferState.ERROR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        val errMsg = task.error?.takeIf { it.isNotBlank() }
                        Text(
                            text = if (errMsg != null) {
                                "${stringResource(R.string.transfer_status_error)}: $errMsg"
                            } else {
                                stringResource(R.string.transfer_status_error)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!canResume) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.transfer_server_disconnected_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                TransferState.DONE, TransferState.CANCELLED -> Unit
            }
        }
    }
}

@Composable
private fun OrphanedResumableCard(
    record: DownloadRecord,
    serverName: String,
    canResume: Boolean,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val state = runCatching { TransferState.valueOf(record.state) }.getOrDefault(TransferState.PAUSED)
    val isError = state == TransferState.ERROR
    val fraction = if (record.totalBytes > 0L) {
        (record.transferredBytes.toFloat() / record.totalBytes).coerceIn(0f, 1f)
    } else 0f
    val pct = (fraction * 100f).toInt().coerceIn(0, 100)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileDirectionAvatar(
                    fileName = record.fileName,
                    isUpload = record.isUpload,
                    isError = isError,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    ServerPathMetaRow(
                        protocol = if (record.isSmb) "SMB" else "SFTP",
                        serverName = serverName,
                        remotePath = record.remotePath,
                    )
                }
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = onResume,
                    enabled = canResume,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (isError) R.string.transfer_action_retry else R.string.transfer_action_resume
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.transfer_action_delete_record),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (record.totalBytes > 0L) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    strokeCap = StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = if (isError) {
                    val msg = record.error?.takeIf { it.isNotBlank() }
                    if (msg != null) "${stringResource(R.string.transfer_status_error)}: $msg"
                    else stringResource(R.string.transfer_status_error)
                } else {
                    "${stringResource(R.string.transfer_status_paused)} · ${
                        Formatter.formatFileSize(context, record.transferredBytes)
                    } / ${Formatter.formatFileSize(context, record.totalBytes)} ($pct%)"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!canResume) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.transfer_server_disconnected_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTransferCard(
    record: DownloadRecord,
    serverName: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopyRemotePath: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val state = runCatching { TransferState.valueOf(record.state) }.getOrDefault(TransferState.DONE)
    val isDone = state == TransferState.DONE
    val canOpen = !record.isUpload && !record.localUri.isNullOrEmpty() && isDone
    var menuExpanded by remember { mutableStateOf(false) }

    val timeText = remember(record.updatedAt) { formatRelativeTimestamp(record.updatedAt) }
    val sizeText = if (record.totalBytes > 0L) {
        Formatter.formatFileSize(context, record.totalBytes)
    } else {
        Formatter.formatFileSize(context, record.transferredBytes)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = canOpen, onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileDirectionAvatar(
                fileName = record.fileName,
                isUpload = record.isUpload,
                isError = false,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                ServerPathMetaRow(
                    protocol = if (record.isSmb) "SMB" else "SFTP",
                    serverName = serverName,
                    remotePath = record.remotePath,
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        label = stringResource(
                            if (isDone) R.string.transfer_status_done else R.string.transfer_status_cancelled
                        ),
                        isPositive = isDone,
                    )
                    Text(
                        text = "$sizeText · $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            if (canOpen) {
                FilledTonalIconButton(
                    onClick = onOpen,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.open),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.transfer_more_options),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (canOpen) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_action_share)) },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                        )
                    }
                    if (record.remotePath.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_action_copy_remote_path)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = {
                                menuExpanded = false
                                onCopyRemotePath()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.transfer_action_delete_record),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FileDirectionAvatar(
    fileName: String,
    isUpload: Boolean,
    isError: Boolean,
) {
    val visual = remember(fileName) { resolveFileVisualByName(fileName) }
    val tint = if (isError) MaterialTheme.colorScheme.error else visual.tintColor(MaterialTheme.colorScheme)
    val badgeBg = when {
        isError -> MaterialTheme.colorScheme.error
        isUpload -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val badgeContent = when {
        isError -> MaterialTheme.colorScheme.onError
        isUpload -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(modifier = Modifier.size(46.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(badgeBg)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isUpload) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = badgeContent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ServerPathMetaRow(
    protocol: String,
    serverName: String,
    remotePath: String,
) {
    val remoteDir = remember(remotePath) {
        if (remotePath.isBlank()) "" else remotePath.substringBeforeLast('/', "/")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = protocol,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val pathSummary = if (remoteDir.isNotBlank()) "$serverName · $remoteDir" else serverName
        Text(
            text = pathSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    isPositive: Boolean,
) {
    val bg = if (isPositive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (isPositive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (isPositive) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TransferEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun TransferTask.matchesDirection(filter: TransferDirectionFilter): Boolean = when (filter) {
    TransferDirectionFilter.ALL -> true
    TransferDirectionFilter.DOWNLOAD -> !isUpload
    TransferDirectionFilter.UPLOAD -> isUpload
}

private fun DownloadRecord.matchesDirection(filter: TransferDirectionFilter): Boolean = when (filter) {
    TransferDirectionFilter.ALL -> true
    TransferDirectionFilter.DOWNLOAD -> !isUpload
    TransferDirectionFilter.UPLOAD -> isUpload
}

private fun DownloadRecord.hasLocalDownloadedFile(): Boolean {
    if (isUpload || localUri.isNullOrBlank()) return false
    return runCatching {
        val uri = Uri.parse(localUri)
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).exists() } == true
            "content" -> true
            else -> false
        }
    }.getOrDefault(false)
}

private fun formatSpeed(context: Context, bytesPerSec: Long): String {
    return "${Formatter.formatShortFileSize(context, bytesPerSec)}/s"
}

@Composable
private fun formatEta(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> stringResource(R.string.transfer_eta_hours, hrs, mins)
        mins > 0 -> stringResource(R.string.transfer_eta_minutes, mins, secs)
        else -> stringResource(R.string.transfer_eta_seconds, secs)
    }
}

private fun formatRelativeTimestamp(timestampMs: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        sameYear -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMs))
}
