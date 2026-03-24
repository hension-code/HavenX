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
import sh.haven.core.et.EtSessionManager
import sh.haven.core.mosh.MoshSessionManager
import sh.haven.core.smb.SmbSessionManager
import sh.haven.core.ssh.SshClient
import sh.haven.core.ssh.SshSessionManager
import sh.haven.feature.sftp.SftpEntry
import sh.haven.feature.sftp.TransferState
import sh.haven.feature.sftp.TransferTask
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
    private val smbSessionManager: SmbSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _transfers = MutableStateFlow<List<TransferTask>>(emptyList())
    val transfers: StateFlow<List<TransferTask>> = _transfers.asStateFlow()
    private val transferJobs = ConcurrentHashMap<String, Job>()
    
    fun addTask(task: TransferTask) {
        _transfers.value = _transfers.value + task
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
                            private var transferred = task.transferredBytes
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
        _transfers.value = _transfers.value.map { if (it.id == id) update(it) else it }
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
        _transfers.value = _transfers.value.filter { it.id != id }
        ensureServiceState()
    }

    fun removeTransfer(id: String) {
        _transfers.value = _transfers.value.filter { it.id != id }
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
