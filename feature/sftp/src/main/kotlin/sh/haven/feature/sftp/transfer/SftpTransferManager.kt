package sh.haven.feature.sftp.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.haven.core.data.db.entities.DownloadRecord
import sh.haven.core.data.repository.DownloadRecordRepository
import sh.haven.core.et.EtSessionManager
import sh.haven.core.mosh.MoshSessionManager
import sh.haven.core.smb.SmbSessionManager
import sh.haven.core.ssh.SshClient
import sh.haven.core.ssh.SshSessionManager
import sh.haven.feature.sftp.SftpEntry
import sh.haven.feature.sftp.TransferState
import sh.haven.feature.sftp.TransferTask
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpTransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SshSessionManager,
    private val moshSessionManager: MoshSessionManager,
    private val etSessionManager: EtSessionManager,
    private val smbSessionManager: SmbSessionManager,
    private val downloadRecordRepository: DownloadRecordRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _transfers = MutableStateFlow<List<TransferTask>>(emptyList())
    val transfers: StateFlow<List<TransferTask>> = _transfers.asStateFlow()
    private val transferJobs = ConcurrentHashMap<String, Job>()
    /** Per-task timestamp of the last progress write to DB, for throttling. */
    private val lastPersistMs = ConcurrentHashMap<String, Long>()

    init {
        // Rebuild interrupted transfers (paused/errored) from DB so the user can
        // resume them after an app restart. Tasks are loaded as PAUSED/ERROR
        // without starting their jobs — the user must tap resume.
        restorePendingTransfers()
    }

    fun addTask(task: TransferTask) {
        _transfers.value = _transfers.value + task
        // Persist a new history row (createdAt is set here; later updates use
        // updateState which preserves it).
        scope.launch { downloadRecordRepository.upsert(task.toRecord()) }
        startTransferJob(task)
    }

    private fun startTransferJob(initialTask: TransferTask) {
        ensureServiceState()
        val job = scope.launch {
            try {
                if (initialTask.isUpload) {
                    performUpload(initialTask)
                } else {
                    performDownload(initialTask)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Log.e("SftpTransferManager", "Transfer failed: ${initialTask.fileName}", e)
                updateTask(initialTask.id) { it.copy(state = TransferState.ERROR, error = e.message) }
            } finally {
                transferJobs.remove(initialTask.id)
                ensureServiceState()
            }
        }
        transferJobs[initialTask.id] = job
    }

    private suspend fun performDownload(task: TransferTask) {
        val entry = task.entry ?: return
        val destUri = task.destinationUri ?: return
        val isAppend = task.transferredBytes > 0
        val mode = if (isAppend) "wa" else "w"

        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(destUri, mode)
                ?: throw IllegalStateException("Cannot open output stream")
            outputStream.use { out ->
                if (task.isSmb) {
                    val client = smbSessionManager.getClientForProfile(task.profileId)
                        ?: throw IllegalStateException("SMB not connected")
                    var current = task.transferredBytes
                    if (isAppend && current > 0) {
                        client.downloadRange(entry.path, current, task.totalBytes - current, out) {
                            current += it
                            updateTask(task.id) { t -> t.copy(transferredBytes = current) }
                            this@withContext.isActive
                        }
                    } else {
                        client.download(entry.path, out) { transferred, _ ->
                            updateTask(task.id) { t -> t.copy(transferredBytes = transferred) }
                            this@withContext.isActive
                        }
                    }
                } else {
                    val channel = openDedicatedSftpChannel(task.profileId)
                        ?: throw IllegalStateException("Not connected")
                    try {
                        val monitor = object : SftpProgressMonitor {
                            // Start from 0: JSch calls monitor.count(skip) once at the
                            // beginning of a RESUME transfer, which advances the counter
                            // to the resume offset. Pre-initialising to transferredBytes
                            // would double-count the offset.
                            private var transferred = 0L
                            override fun init(op: Int, src: String, dest: String, max: Long) {}
                            override fun count(bytes: Long): Boolean {
                                transferred += bytes
                                updateTask(task.id) { t -> t.copy(transferredBytes = transferred) }
                                return this@withContext.isActive
                            }
                            override fun end() {}
                        }
                        if (isAppend) {
                            channel.get(entry.path, out, monitor, ChannelSftp.RESUME, task.transferredBytes)
                        } else {
                            channel.get(entry.path, out, monitor)
                        }
                    } finally {
                        if (channel.isConnected) channel.disconnect()
                    }
                }
            }
        }
        val currentState = _transfers.value.find { it.id == task.id }?.state
        if (currentState == TransferState.DOWNLOADING) {
            updateTask(task.id) { it.copy(state = TransferState.DONE) }
        }
    }

    private suspend fun performUpload(task: TransferTask) {
        val sourceUri = task.sourceUri ?: return
        val destPath = task.destPath ?: return
        
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream")
            inputStream.use { input ->
                if (task.isSmb) {
                    val client = smbSessionManager.getClientForProfile(task.profileId)
                        ?: throw IllegalStateException("SMB not connected")
                    client.upload(input, destPath, task.totalBytes) { transferred, _ ->
                        updateTask(task.id) { t -> t.copy(transferredBytes = transferred) }
                        this@withContext.isActive
                    }
                } else {
                    val channel = openDedicatedSftpChannel(task.profileId)
                        ?: throw IllegalStateException("Not connected")
                    try {
                        val monitor = object : SftpProgressMonitor {
                            private var transferred = 0L
                            override fun init(op: Int, src: String, dest: String, max: Long) {}
                            override fun count(bytes: Long): Boolean {
                                transferred += bytes
                                updateTask(task.id) { t -> t.copy(transferredBytes = transferred) }
                                return this@withContext.isActive
                            }
                            override fun end() {}
                        }
                        channel.put(input, destPath, monitor)
                    } finally {
                        if (channel.isConnected) channel.disconnect()
                    }
                }
            }
        }
        val currentState = _transfers.value.find { it.id == task.id }?.state
        if (currentState == TransferState.DOWNLOADING) {
            updateTask(task.id) { it.copy(state = TransferState.DONE) }
        }
    }

    suspend fun performDirectUpload(
        sourceUri: Uri,
        destPath: String,
        profileId: String,
        isSmb: Boolean,
        totalBytes: Long,
        onProgress: (Long) -> Boolean
    ) {
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream")
            inputStream.use { input ->
                if (isSmb) {
                    val client = smbSessionManager.getClientForProfile(profileId)
                        ?: throw IllegalStateException("SMB not connected")
                    client.upload(input, destPath, totalBytes) { transferred, _ ->
                        onProgress(transferred)
                    }
                } else {
                    val channel = openDedicatedSftpChannel(profileId)
                        ?: throw IllegalStateException("Not connected")
                    try {
                        val monitor = object : SftpProgressMonitor {
                            private var transferred = 0L
                            override fun init(op: Int, src: String, dest: String, max: Long) {}
                            override fun count(bytes: Long): Boolean {
                                transferred += bytes
                                return onProgress(transferred)
                            }
                            override fun end() {}
                        }
                        channel.put(input, destPath, monitor)
                    } finally {
                        if (channel.isConnected) channel.disconnect()
                    }
                }
            }
        }
    }

    private fun updateTask(id: String, update: (TransferTask) -> TransferTask) {
        val previous = _transfers.value.find { it.id == id }
        _transfers.value = _transfers.value.map { if (it.id == id) update(it) else it }
        val current = _transfers.value.find { it.id == id } ?: return
        if (previous?.state != current.state) {
            // State transition — persist immediately.
            persistStateNow(current)
        } else {
            // Progress-only update — throttle DB writes to ~1s.
            maybePersistProgress(current)
        }
    }

    /** Throttled progress persistence: at most one DB write per second per task. */
    private fun maybePersistProgress(task: TransferTask) {
        val now = System.currentTimeMillis()
        val last = lastPersistMs[task.id] ?: 0L
        if (now - last < PROGRESS_PERSIST_INTERVAL_MS) return
        lastPersistMs[task.id] = now
        scope.launch {
            downloadRecordRepository.updateState(
                id = task.id,
                state = task.state.name,
                transferredBytes = task.transferredBytes,
                error = task.error,
            )
        }
    }

    /** Immediate persistence for state transitions (pause/done/error/cancel). */
    private fun persistStateNow(task: TransferTask) {
        lastPersistMs[task.id] = System.currentTimeMillis()
        scope.launch {
            downloadRecordRepository.updateState(
                id = task.id,
                state = task.state.name,
                transferredBytes = task.transferredBytes,
                error = task.error,
            )
        }
    }

    fun pauseTransfer(id: String) {
        transferJobs[id]?.cancel()
        updateTask(id) { it.copy(state = TransferState.PAUSED) }
        ensureServiceState()
    }

    fun resumeTransfer(id: String) {
        val task = _transfers.value.find { it.id == id } ?: return
        val workingTask = if (task.isUpload) {
            task.copy(state = TransferState.DOWNLOADING, error = null, transferredBytes = 0L)
        } else {
            task.copy(state = TransferState.DOWNLOADING, error = null)
        }
        updateTask(id) { workingTask }
        startTransferJob(workingTask)
    }

    fun cancelTransfer(id: String) {
        transferJobs[id]?.cancel()
        val task = _transfers.value.find { it.id == id }
        if (task != null) {
            // Keep the record in history as CANCELLED; just drop from the live list.
            scope.launch {
                downloadRecordRepository.updateState(
                    id = id,
                    state = TransferState.CANCELLED.name,
                    transferredBytes = task.transferredBytes,
                    error = task.error,
                )
            }
        }
        lastPersistMs.remove(id)
        _transfers.value = _transfers.value.filter { it.id != id }
        ensureServiceState()
    }

    fun removeTransfer(id: String) {
        // Only drops from the live (in-memory) list; the history row is retained.
        lastPersistMs.remove(id)
        _transfers.value = _transfers.value.filter { it.id != id }
    }

    /** Delete a history record and any matching live task. */
    fun deleteHistoryRecord(id: String) {
        transferJobs[id]?.cancel()
        lastPersistMs.remove(id)
        _transfers.value = _transfers.value.filter { it.id != id }
        scope.launch { downloadRecordRepository.deleteById(id) }
        ensureServiceState()
    }

    /** Remove all finished/cancelled records from history. */
    fun clearHistory() {
        scope.launch { downloadRecordRepository.deleteCompleted() }
    }

    /**
     * Resume an interrupted transfer that exists only in history (not in the
     * live list). Rebuilds the in-memory task from the DB record, reconciles
     * the download offset with the partial file, then starts the job. If the
     * task is already live, falls back to a normal resume.
     */
    fun resumeFromHistory(id: String) {
        scope.launch {
            if (_transfers.value.any { it.id == id }) {
                resumeTransfer(id)
                return@launch
            }
            val record = downloadRecordRepository.getById(id) ?: return@launch
            // Reconcile the file offset without persisting — we persist the
            // final DOWNLOADING state ourselves below to avoid a PAUSED→DOWNLOADING
            // write race with toRestoredTask's own persistence.
            val task = record.toRestoredTask(persist = false)
                ?.copy(state = TransferState.DOWNLOADING, error = null)
                ?: return@launch
            _transfers.value = _transfers.value + task
            // Update the existing row (preserves createdAt) instead of upserting.
            downloadRecordRepository.updateState(
                id = id,
                state = TransferState.DOWNLOADING.name,
                transferredBytes = task.transferredBytes,
                error = null,
            )
            startTransferJob(task)
        }
    }

    /**
     * Rebuild interrupted transfers (paused/errored) from the DB after app
     * restart. For downloads, the local partial file is checked and the resume
     * offset is reconciled with its actual size. Tasks are added without
     * starting their jobs.
     */
    private fun restorePendingTransfers() {
        scope.launch {
            try {
                val resumable = downloadRecordRepository.getResumable()
                if (resumable.isEmpty()) return@launch
                val restored = resumable.mapNotNull { it.toRestoredTask() }
                if (restored.isEmpty()) return@launch
                _transfers.value = _transfers.value + restored
            } catch (e: Exception) {
                Log.e("SftpTransferManager", "Restore pending transfers failed", e)
            }
        }
    }

    private fun TransferTask.toRecord(): DownloadRecord {
        val now = System.currentTimeMillis()
        return DownloadRecord(
            id = id,
            profileId = profileId,
            fileName = fileName,
            remotePath = entry?.path ?: destPath ?: "",
            localUri = destinationUri?.toString() ?: sourceUri?.toString(),
            totalBytes = totalBytes,
            transferredBytes = transferredBytes,
            isUpload = isUpload,
            isSmb = isSmb,
            state = state.name,
            error = error,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun DownloadRecord.toRestoredTask(persist: Boolean = true): TransferTask? {
        val uri = localUri?.let { Uri.parse(it) }
        var transferred = transferredBytes
        // For downloads, reconcile the resume offset with the actual partial
        // file on disk so we neither skip nor re-download already-fetched bytes.
        if (!isUpload && uri != null && uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            transferred = when {
                !file.exists() -> 0L
                file.length() != transferredBytes -> file.length()
                else -> transferredBytes
            }
        }
        val recordedState = runCatching { TransferState.valueOf(state) }
            .getOrDefault(TransferState.PAUSED)
        // A DOWNLOADING record after a restart was killed mid-flight (there are
        // no live jobs across process death), so surface it as PAUSED with a
        // resume button rather than a stale "in progress" state.
        val restoredState = if (recordedState == TransferState.DOWNLOADING) {
            TransferState.PAUSED
        } else {
            recordedState
        }
        // Persist any corrected offset and/or the DOWNLOADING → PAUSED transition.
        if (persist && (transferred != transferredBytes || restoredState != recordedState)) {
            scope.launch {
                downloadRecordRepository.updateState(
                    id = id,
                    state = restoredState.name,
                    transferredBytes = transferred,
                    error = error,
                )
            }
        }
        val entry = if (!isUpload) {
            SftpEntry(
                name = fileName,
                path = remotePath,
                isDirectory = false,
                size = totalBytes,
                modifiedTime = 0L,
                permissions = "",
            )
        } else null
        return TransferTask(
            id = id,
            fileName = fileName,
            totalBytes = totalBytes,
            transferredBytes = transferred,
            state = restoredState,
            isUpload = isUpload,
            destPath = if (isUpload) remotePath else null,
            sourceUri = if (isUpload) uri else null,
            destinationUri = if (!isUpload) uri else null,
            entry = entry,
            profileId = profileId,
            isSmb = isSmb,
            error = error,
        )
    }

    companion object {
        /** Minimum interval between progress DB writes per task. */
        private const val PROGRESS_PERSIST_INTERVAL_MS = 1000L
    }

    private fun openDedicatedSftpChannel(profileId: String): ChannelSftp? {
        var channel: ChannelSftp? = try {
            val session = sessionManager.sessions.value.values.firstOrNull { 
                it.profileId == profileId && it.status == SshSessionManager.SessionState.Status.CONNECTED 
            }
            session?.client?.openSftpChannel()
        } catch (e: Exception) { null }
        if (channel == null) {
            channel = try {
                val client = moshSessionManager.getSshClientForProfile(profileId) as? SshClient
                client?.openSftpChannel()
            } catch (e: Exception) { null }
        }
        if (channel == null) {
            channel = try {
                val client = etSessionManager.getSshClientForProfile(profileId) as? SshClient
                client?.openSftpChannel()
            } catch (e: Exception) { null }
        }
        return channel
    }

    private fun ensureServiceState() {
        val hasActive = _transfers.value.any { it.state == TransferState.DOWNLOADING }
        val intent = Intent(context, SftpTransferService::class.java)
        if (hasActive) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e("SftpTransferManager", "Failed to start service", e)
            }
        } else {
            context.stopService(intent)
        }
    }
}
