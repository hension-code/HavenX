package sh.haven.feature.sftp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.haven.core.data.db.entities.ConnectionProfile
import sh.haven.core.data.preferences.UserPreferencesRepository
import sh.haven.core.data.repository.ConnectionRepository
import sh.haven.core.et.EtSessionManager
import sh.haven.core.mosh.MoshSessionManager
import sh.haven.core.smb.SmbClient
import sh.haven.core.smb.SmbSessionManager
import sh.haven.core.ssh.SshClient
import sh.haven.core.ssh.SshSessionManager
import sh.haven.core.ssh.SshSessionManager.SessionState
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

private const val TAG = "SftpViewModel"

data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val permissions: String,
)

enum class SortMode {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC
}

/** Transfer progress for download/upload operations. */
data class TransferProgress(
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val sessionManager: SshSessionManager,
    private val moshSessionManager: MoshSessionManager,
    private val etSessionManager: EtSessionManager,
    private val smbSessionManager: SmbSessionManager,
    private val repository: ConnectionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val backHistory = ArrayDeque<String>()
    private val forwardHistory = ArrayDeque<String>()

    private val _connectedProfiles = MutableStateFlow<List<ConnectionProfile>>(emptyList())
    val connectedProfiles: StateFlow<List<ConnectionProfile>> = _connectedProfiles.asStateFlow()

    private val _activeProfileId = MutableStateFlow<String?>(null)
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _allEntries = MutableStateFlow<List<SftpEntry>>(emptyList())
    private val _entries = MutableStateFlow<List<SftpEntry>>(emptyList())
    val entries: StateFlow<List<SftpEntry>> = _entries.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()
    private val _favoriteDirectories = MutableStateFlow<Set<String>>(emptySet())
    val favoriteDirectories: StateFlow<Set<String>> = _favoriteDirectories.asStateFlow()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _transferProgress = MutableStateFlow<TransferProgress?>(null)
    val transferProgress: StateFlow<TransferProgress?> = _transferProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Emitted after a successful download with the destination URI for "Open" action. */
    data class DownloadResult(val fileName: String, val uri: Uri)
    private val _lastDownload = MutableStateFlow<DownloadResult?>(null)
    val lastDownload: StateFlow<DownloadResult?> = _lastDownload.asStateFlow()
    fun clearLastDownload() { _lastDownload.value = null }

    /** Emitted after preparing a local cached preview file. */
    data class PreviewResult(
        val remotePath: String,
        val filePath: String?,
        val uri: Uri?,
        val streamUrl: String?,
        val mimeType: String,
        val mediaType: MediaTypeResolver.MediaType,
    )
    private val _lastPreview = MutableStateFlow<PreviewResult?>(null)
    val lastPreview: StateFlow<PreviewResult?> = _lastPreview.asStateFlow()
    fun clearLastPreview() { _lastPreview.value = null }

    private var sftpChannel: ChannelSftp? = null
    private var activeSmbClient: SmbClient? = null
    private val previewReadLock = Any()
    private var favoritesJob: Job? = null

    /** Tracks which active profile is SMB (vs SFTP). */
    private val _isSmbProfile = MutableStateFlow(false)

    /** Pending SMB profile to auto-select when navigating to Files tab. */
    private val _pendingSmbProfileId = MutableStateFlow<String?>(null)

    init {
        // Restore persisted sort mode
        viewModelScope.launch {
            val saved = preferencesRepository.sftpSortMode.first()
            _sortMode.value = try {
                SortMode.valueOf(saved)
            } catch (_: IllegalArgumentException) {
                SortMode.NAME_ASC
            }
        }
        // Restore hidden files preference
        viewModelScope.launch {
            preferencesRepository.sftpShowHidden.collectLatest { hidden ->
                _showHidden.value = hidden
                applyFilter()
            }
        }
    }

    fun syncConnectedProfiles() {
        viewModelScope.launch {
            // Collect profile IDs from SSH sessions
            val sshProfileIds = sessionManager.sessions.value.values
                .filter { it.status == SessionState.Status.CONNECTED }
                .map { it.profileId }
                .toSet()

            // Collect profile IDs from mosh sessions that have a live SSH client
            val moshProfileIds = moshSessionManager.sessions.value.values
                .filter {
                    it.status == MoshSessionManager.SessionState.Status.CONNECTED &&
                        it.sshClient != null
                }
                .map { it.profileId }
                .toSet()

            // Collect profile IDs from ET sessions that have a live SSH client
            val etProfileIds = etSessionManager.sessions.value.values
                .filter {
                    it.status == EtSessionManager.SessionState.Status.CONNECTED &&
                        it.sshClient != null
                }
                .map { it.profileId }
                .toSet()

            // Collect profile IDs from SMB sessions
            val smbProfileIds = smbSessionManager.sessions.value.values
                .filter { it.status == SmbSessionManager.SessionState.Status.CONNECTED }
                .map { it.profileId }
                .toSet()

            val connectedProfileIds = sshProfileIds + moshProfileIds + etProfileIds + smbProfileIds

            if (connectedProfileIds.isEmpty()) {
                _connectedProfiles.value = emptyList()
                _activeProfileId.value = null
                sftpChannel = null
                activeSmbClient = null
                _favoriteDirectories.value = emptySet()
                return@launch
            }

            val profiles = withContext(Dispatchers.IO) { repository.getAll() }
            _connectedProfiles.value = profiles.filter { it.id in connectedProfileIds }

            // Handle pending SMB navigation
            val pendingSmb = _pendingSmbProfileId.value
            if (pendingSmb != null && pendingSmb in connectedProfileIds) {
                _pendingSmbProfileId.value = null
                selectProfile(pendingSmb)
                return@launch
            }

            // Auto-select first connected profile if none selected
            if (_activeProfileId.value == null || _activeProfileId.value !in connectedProfileIds) {
                _connectedProfiles.value.firstOrNull()?.let { selectProfile(it.id) }
            }
        }
    }

    fun setPendingSmbProfile(profileId: String) {
        _pendingSmbProfileId.value = profileId
    }

    fun selectProfile(profileId: String) {
        // Check if this is an SMB profile
        val isSmb = smbSessionManager.isProfileConnected(profileId)
        _isSmbProfile.value = isSmb
        observeFavorites(profileId)

        if (isSmb) {
            if (profileId == _activeProfileId.value && activeSmbClient?.isConnected == true) return
            _activeProfileId.value = profileId
            sftpChannel = null
            activeSmbClient = null
            _currentPath.value = "/"
            _allEntries.value = emptyList()
            _entries.value = emptyList()
            clearHistory()
            openSmbAndList(profileId)
        } else {
            if (profileId == _activeProfileId.value && sftpChannel?.isConnected == true) return
            _activeProfileId.value = profileId
            sftpChannel = null
            activeSmbClient = null
            _currentPath.value = "/"
            _allEntries.value = emptyList()
            _entries.value = emptyList()
            clearHistory()
            openSftpAndList(profileId)
        }
    }

    fun navigateTo(path: String) {
        val profileId = _activeProfileId.value ?: return
        val normalized = normalizePath(path)
        val current = _currentPath.value
        if (normalized == current) return
        backHistory.addLast(current)
        if (backHistory.size > 100) backHistory.removeFirst()
        forwardHistory.clear()
        updateHistoryFlags()
        _currentPath.value = normalized
        persistLastPath(profileId, normalized)
        if (_isSmbProfile.value) {
            listSmbDirectory(normalized)
        } else {
            listDirectory(profileId, normalized)
        }
    }

    fun navigateToInput(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val target = if (value.startsWith("/")) {
            value
        } else {
            "${_currentPath.value.trimEnd('/')}/$value"
        }
        val profileId = _activeProfileId.value ?: return
        val normalized = normalizePath(target)
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) {
                if (_isSmbProfile.value) {
                    pathExistsSmb(normalized)
                } else {
                    pathExistsSftp(profileId, normalized)
                }
            }
            if (exists) {
                navigateTo(normalized)
            } else {
                _error.value = "Path not found: $normalized"
            }
        }
    }

    fun goBack() {
        val profileId = _activeProfileId.value ?: return
        if (backHistory.isEmpty()) return
        val current = _currentPath.value
        val target = backHistory.removeLast()
        forwardHistory.addLast(current)
        updateHistoryFlags()
        _currentPath.value = target
        persistLastPath(profileId, target)
        if (_isSmbProfile.value) listSmbDirectory(target) else listDirectory(profileId, target)
    }

    fun goForward() {
        val profileId = _activeProfileId.value ?: return
        if (forwardHistory.isEmpty()) return
        val current = _currentPath.value
        val target = forwardHistory.removeLast()
        backHistory.addLast(current)
        updateHistoryFlags()
        _currentPath.value = target
        persistLastPath(profileId, target)
        if (_isSmbProfile.value) listSmbDirectory(target) else listDirectory(profileId, target)
    }

    fun navigateUp() {
        val current = _currentPath.value
        if (current == "/") return
        val parent = current.trimEnd('/').substringBeforeLast('/', "/")
        navigateTo(if (parent.isEmpty()) "/" else parent)
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        _allEntries.value = sortEntries(_allEntries.value, mode)
        applyFilter()
        // Persist the choice
        viewModelScope.launch {
            preferencesRepository.setSftpSortMode(mode.name)
        }
    }

    fun toggleShowHidden() {
        val next = !_showHidden.value
        _showHidden.value = next
        applyFilter()
        viewModelScope.launch {
            preferencesRepository.setSftpShowHidden(next)
        }
    }

    fun toggleFavoriteDirectory(path: String) {
        val profileId = _activeProfileId.value ?: return
        val normalized = normalizePath(path)
        viewModelScope.launch {
            if (_favoriteDirectories.value.contains(normalized)) {
                preferencesRepository.removeSftpFavoriteDir(profileId, normalized)
            } else {
                preferencesRepository.addSftpFavoriteDir(profileId, normalized)
            }
        }
    }

    fun isFavoriteDirectory(path: String): Boolean {
        return _favoriteDirectories.value.contains(normalizePath(path))
    }

    private fun applyFilter() {
        val all = _allEntries.value
        _entries.value = if (_showHidden.value) all else all.filter { !it.name.startsWith(".") }
    }

    fun refresh() {
        val profileId = _activeProfileId.value ?: return
        if (_isSmbProfile.value) {
            listSmbDirectory(_currentPath.value)
        } else {
            listDirectory(profileId, _currentPath.value)
        }
    }

    fun downloadFile(entry: SftpEntry, destinationUri: Uri) {
        val profileId = _activeProfileId.value ?: return
        viewModelScope.launch {
            try {
                _loading.value = true
                _transferProgress.value = TransferProgress(entry.name, entry.size, 0)
                withContext(Dispatchers.IO) {
                    val outputStream: OutputStream = appContext.contentResolver.openOutputStream(destinationUri)
                        ?: throw IllegalStateException("Cannot open output stream")
                    outputStream.use { out ->
                        if (_isSmbProfile.value) {
                            val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                            client.download(entry.path, out) { transferred, total ->
                                _transferProgress.value = TransferProgress(entry.name, total, transferred)
                            }
                        } else {
                            val channel = getOrOpenChannel(profileId) ?: throw IllegalStateException("Not connected")
                            val monitor = object : SftpProgressMonitor {
                                private var total = 0L
                                private var transferred = 0L

                                override fun init(op: Int, src: String, dest: String, max: Long) {
                                    total = if (max == SftpProgressMonitor.UNKNOWN_SIZE) entry.size else max
                                    transferred = 0
                                    _transferProgress.value = TransferProgress(entry.name, total, 0)
                                }

                                override fun count(bytes: Long): Boolean {
                                    transferred += bytes
                                    _transferProgress.value = TransferProgress(entry.name, total, transferred)
                                    return true
                                }

                                override fun end() {
                                    _transferProgress.value = TransferProgress(entry.name, total, total)
                                }
                            }
                            channel.get(entry.path, out, monitor)
                        }
                    }
                }
                _lastDownload.value = DownloadResult(entry.name, destinationUri)
                _message.value = "Downloaded ${entry.name}"
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _error.value = "Download failed: ${e.message}"
            } finally {
                _loading.value = false
                _transferProgress.value = null
            }
        }
    }

    fun uploadFile(fileName: String, sourceUri: Uri) {
        val profileId = _activeProfileId.value ?: return
        val destPath = _currentPath.value.trimEnd('/') + "/" + fileName
        Log.d(TAG, "Upload: '$fileName' -> '$destPath' (source: $sourceUri)")
        viewModelScope.launch {
            try {
                _loading.value = true
                // Get source file size for progress
                val fileSize = appContext.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
                } ?: -1L
                _transferProgress.value = TransferProgress(fileName, fileSize, 0)
                withContext(Dispatchers.IO) {
                    val inputStream = appContext.contentResolver.openInputStream(sourceUri)
                        ?: throw IllegalStateException("Cannot open input stream")
                    inputStream.use { input ->
                        if (_isSmbProfile.value) {
                            val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                            client.upload(input, destPath, fileSize) { transferred, total ->
                                _transferProgress.value = TransferProgress(fileName, total, transferred)
                            }
                        } else {
                            val channel = getOrOpenChannel(profileId) ?: throw IllegalStateException("Not connected")
                            val monitor = object : SftpProgressMonitor {
                                private var total = 0L
                                private var transferred = 0L

                                override fun init(op: Int, src: String, dest: String, max: Long) {
                                    total = if (max == SftpProgressMonitor.UNKNOWN_SIZE) fileSize else max
                                    transferred = 0
                                    _transferProgress.value = TransferProgress(fileName, total, 0)
                                }

                                override fun count(bytes: Long): Boolean {
                                    transferred += bytes
                                    _transferProgress.value = TransferProgress(fileName, total, transferred)
                                    return true
                                }

                                override fun end() {
                                    _transferProgress.value = TransferProgress(fileName, total, total)
                                }
                            }
                            channel.put(input, destPath, monitor)
                        }
                    }
                    Log.d(TAG, "Upload complete: '$destPath'")
                }
                _message.value = "Uploaded $fileName"
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                _error.value = "Upload failed: ${e.message}"
            } finally {
                _loading.value = false
                _transferProgress.value = null
            }
        }
    }

    fun deleteEntry(entry: SftpEntry) {
        val profileId = _activeProfileId.value ?: return
        viewModelScope.launch {
            try {
                _loading.value = true
                withContext(Dispatchers.IO) {
                    if (_isSmbProfile.value) {
                        val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                        client.delete(entry.path, entry.isDirectory)
                    } else {
                        val channel = getOrOpenChannel(profileId) ?: throw IllegalStateException("Not connected")
                        if (entry.isDirectory) {
                            channel.rmdir(entry.path)
                        } else {
                            channel.rm(entry.path)
                        }
                    }
                }
                _message.value = "Deleted ${entry.name}"
                refresh()
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                _error.value = "Delete failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun clearHistory() {
        backHistory.clear()
        forwardHistory.clear()
        updateHistoryFlags()
    }

    private fun persistLastPath(profileId: String, path: String) {
        viewModelScope.launch {
            preferencesRepository.setSftpLastPath(profileId, normalizePath(path))
        }
    }

    private fun observeFavorites(profileId: String) {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            preferencesRepository.sftpFavoriteDirs(profileId).collectLatest { favorites ->
                _favoriteDirectories.value = favorites.map { normalizePath(it) }.toSet()
            }
        }
    }

    private fun updateHistoryFlags() {
        _canGoBack.value = backHistory.isNotEmpty()
        _canGoForward.value = forwardHistory.isNotEmpty()
    }

    private fun normalizePath(path: String): String {
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (p in parts) {
            if (p == "..") {
                if (stack.isNotEmpty()) stack.removeLast()
            } else {
                stack.addLast(p)
            }
        }
        return if (stack.isEmpty()) "/" else "/" + stack.joinToString("/")
    }

    private fun pathExistsSftp(profileId: String, path: String): Boolean {
        return try {
            val channel = getOrOpenChannel(profileId) ?: return false
            if (path == "/") {
                true
            } else {
                channel.stat(path)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun pathExistsSmb(path: String): Boolean {
        return try {
            val client = activeSmbClient ?: return false
            if (path == "/") return true
            val parent = path.trimEnd('/').substringBeforeLast('/', "/")
            val name = path.trimEnd('/').substringAfterLast('/')
            client.listDirectory(parent).any { it.name == name && it.isDirectory }
        } catch (_: Exception) {
            false
        }
    }

    fun previewMedia(entry: SftpEntry) {
        val profileId = _activeProfileId.value ?: return
        if (entry.isDirectory) return
        viewModelScope.launch {
            try {
                _loading.value = true
                _transferProgress.value = TransferProgress(entry.name, entry.size, 0)
                val mimeType = guessMimeType(entry.name)
                val mediaType = MediaTypeResolver.resolve(entry.path)
                if (mediaType == MediaTypeResolver.MediaType.VIDEO || mediaType == MediaTypeResolver.MediaType.AUDIO) {
                    val isSmbProfile = _isSmbProfile.value
                    val smbClient = if (isSmbProfile) activeSmbClient ?: error("SMB not connected") else null
                    val streamUrl = withContext(Dispatchers.IO) {
                        LocalMediaPreviewServer.register(
                            PreviewByteSource(
                                totalSize = entry.size,
                                mimeType = mimeType,
                                readRange = { start, endInclusive, output ->
                                    val length = endInclusive - start + 1
                                    synchronized(previewReadLock) {
                                        if (isSmbProfile) {
                                            smbClient?.downloadRange(entry.path, start, length, output)
                                                ?: error("SMB not connected")
                                        } else {
                                            val channel = getOrOpenChannel(profileId) ?: error("Not connected")
                                            val monitor = object : SftpProgressMonitor {
                                                override fun init(op: Int, src: String, dest: String, max: Long) = Unit
                                                override fun count(count: Long): Boolean = true
                                                override fun end() = Unit
                                            }
                                            channel.get(entry.path, monitor, start).use { input ->
                                                var remaining = length
                                                val buffer = ByteArray(64 * 1024)
                                                while (remaining > 0) {
                                                    val read = input.read(
                                                        buffer,
                                                        0,
                                                        minOf(buffer.size.toLong(), remaining).toInt(),
                                                    )
                                                    if (read <= 0) break
                                                    output.write(buffer, 0, read)
                                                    remaining -= read
                                                }
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                    }
                    _lastPreview.value = PreviewResult(
                        remotePath = entry.path,
                        filePath = null,
                        uri = null,
                        streamUrl = streamUrl,
                        mimeType = mimeType,
                        mediaType = mediaType,
                    )
                } else {
                    val cacheFile = withContext(Dispatchers.IO) {
                        val file = SftpCacheManager.getCacheFile(appContext, entry.path)
                        if (!file.exists() || file.length() <= 0L) {
                            downloadToFile(profileId, entry, file)
                        }
                        SftpCacheManager.trimCache(appContext)
                        file
                    }
                    val uri = SftpCacheManager.toContentUri(appContext, cacheFile)
                    _lastPreview.value = PreviewResult(
                        remotePath = entry.path,
                        filePath = cacheFile.absolutePath,
                        uri = uri,
                        streamUrl = null,
                        mimeType = mimeType,
                        mediaType = mediaType,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Preview failed", e)
                _error.value = "Preview failed: ${e.message}"
            } finally {
                _loading.value = false
                _transferProgress.value = null
            }
        }
    }

    private fun downloadToFile(profileId: String, entry: SftpEntry, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            if (_isSmbProfile.value) {
                val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                client.download(entry.path, out) { transferred, total ->
                    _transferProgress.value = TransferProgress(entry.name, total, transferred)
                }
            } else {
                val channel = getOrOpenChannel(profileId) ?: throw IllegalStateException("Not connected")
                val monitor = object : SftpProgressMonitor {
                    private var total = 0L
                    private var transferred = 0L

                    override fun init(op: Int, src: String, dest: String, max: Long) {
                        total = if (max == SftpProgressMonitor.UNKNOWN_SIZE) entry.size else max
                        transferred = 0
                        _transferProgress.value = TransferProgress(entry.name, total, 0)
                    }

                    override fun count(bytes: Long): Boolean {
                        transferred += bytes
                        _transferProgress.value = TransferProgress(entry.name, total, transferred)
                        return true
                    }

                    override fun end() {
                        _transferProgress.value = TransferProgress(entry.name, total, total)
                    }
                }
                channel.get(entry.path, out, monitor)
            }
        }
    }

    fun dismissError() { _error.value = null }
    fun dismissMessage() { _message.value = null }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isBlank()) return "*/*"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> "*/*"
        }
    }

    private fun openSftpAndList(profileId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                withContext(Dispatchers.IO) {
                    val channel = sessionManager.openSftpForProfile(profileId)
                        ?: openMoshSftpChannel(profileId)
                        ?: throw IllegalStateException("Session not connected")
                    sftpChannel = channel
                    val home = channel.home
                    val saved = preferencesRepository.getSftpLastPath(profileId)
                    val target = normalizePath(saved ?: home)
                    val actual = try {
                        loadEntries(channel, target)
                        target
                    } catch (_: Exception) {
                        loadEntries(channel, home)
                        normalizePath(home)
                    }
                    _currentPath.value = actual
                    preferencesRepository.setSftpLastPath(profileId, actual)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SFTP open failed", e)
                _error.value = "SFTP failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun listDirectory(profileId: String, path: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                withContext(Dispatchers.IO) {
                    val channel = getOrOpenChannel(profileId) ?: throw IllegalStateException("Not connected")
                    loadEntries(channel, path)
                }
            } catch (e: Exception) {
                Log.e(TAG, "List directory failed", e)
                _error.value = "Failed to list: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadEntries(channel: ChannelSftp, path: String) {
        val results = mutableListOf<SftpEntry>()
        channel.ls(path) { lsEntry ->
            val name = lsEntry.filename
            if (name != "." && name != "..") {
                val attrs = lsEntry.attrs
                results.add(
                    SftpEntry(
                        name = name,
                        path = path.trimEnd('/') + "/" + name,
                        isDirectory = attrs.isDir,
                        size = attrs.size,
                        modifiedTime = attrs.mTime.toLong(),
                        permissions = attrs.permissionsString ?: "",
                    )
                )
            }
            ChannelSftp.LsEntrySelector.CONTINUE
        }
        _allEntries.value = sortEntries(results, _sortMode.value)
        applyFilter()
    }

    private fun getOrOpenChannel(profileId: String): ChannelSftp? {
        sftpChannel?.let { if (it.isConnected) return it }
        // Try SSH session first, then mosh/ET bootstrap SSH client
        val channel = sessionManager.openSftpForProfile(profileId)
            ?: openMoshSftpChannel(profileId)
            ?: openEtSftpChannel(profileId)
            ?: return null
        sftpChannel = channel
        return channel
    }

    private fun openMoshSftpChannel(profileId: String): ChannelSftp? {
        val client = moshSessionManager.getSshClientForProfile(profileId) as? SshClient
            ?: return null
        return try {
            client.openSftpChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SFTP channel via mosh SSH client", e)
            null
        }
    }

    private fun openEtSftpChannel(profileId: String): ChannelSftp? {
        val client = etSessionManager.getSshClientForProfile(profileId) as? SshClient
            ?: return null
        return try {
            client.openSftpChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SFTP channel via ET SSH client", e)
            null
        }
    }

    private fun openSmbAndList(profileId: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                withContext(Dispatchers.IO) {
                    val client = smbSessionManager.getClientForProfile(profileId)
                        ?: throw IllegalStateException("SMB session not connected")
                    activeSmbClient = client
                    val saved = preferencesRepository.getSftpLastPath(profileId)
                    val target = normalizePath(saved ?: "/")
                    val actual = try {
                        loadSmbEntries(client, target)
                        target
                    } catch (_: Exception) {
                        loadSmbEntries(client, "/")
                        "/"
                    }
                    _currentPath.value = actual
                    preferencesRepository.setSftpLastPath(profileId, actual)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMB open failed", e)
                _error.value = "SMB failed: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun listSmbDirectory(path: String) {
        viewModelScope.launch {
            try {
                _loading.value = true
                withContext(Dispatchers.IO) {
                    val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                    loadSmbEntries(client, path)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SMB list directory failed", e)
                _error.value = "Failed to list: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadSmbEntries(client: SmbClient, path: String) {
        val smbEntries = client.listDirectory(path)
        val results = smbEntries.map { entry ->
            SftpEntry(
                name = entry.name,
                path = entry.path,
                isDirectory = entry.isDirectory,
                size = entry.size,
                modifiedTime = entry.modifiedTime,
                permissions = entry.permissions,
            )
        }
        _allEntries.value = sortEntries(results, _sortMode.value)
        applyFilter()
    }

    private fun sortEntries(entries: List<SftpEntry>, mode: SortMode): List<SftpEntry> {
        val dirs = entries.filter { it.isDirectory }
        val files = entries.filter { !it.isDirectory }
        val sortedDirs = when (mode) {
            SortMode.NAME_ASC -> dirs.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> dirs.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_ASC -> dirs.sortedBy { it.name.lowercase() }
            SortMode.SIZE_DESC -> dirs.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> dirs.sortedBy { it.modifiedTime }
            SortMode.DATE_DESC -> dirs.sortedByDescending { it.modifiedTime }
        }
        val sortedFiles = when (mode) {
            SortMode.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_ASC -> files.sortedBy { it.size }
            SortMode.SIZE_DESC -> files.sortedByDescending { it.size }
            SortMode.DATE_ASC -> files.sortedBy { it.modifiedTime }
            SortMode.DATE_DESC -> files.sortedByDescending { it.modifiedTime }
        }
        return sortedDirs + sortedFiles
    }
}
