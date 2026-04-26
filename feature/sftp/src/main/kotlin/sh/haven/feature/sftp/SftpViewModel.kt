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
import kotlinx.coroutines.isActive
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
private const val SEARCH_MAX_DIRECTORY_DEPTH = 2

private data class SearchIndexKey(
    val profileId: String,
    val rootPath: String,
    val isSmb: Boolean,
    val showHidden: Boolean,
)

// Models have been moved to SftpModels.kt

@HiltViewModel
class SftpViewModel @Inject constructor(
    private val sessionManager: SshSessionManager,
    private val moshSessionManager: MoshSessionManager,
    private val etSessionManager: EtSessionManager,
    private val smbSessionManager: SmbSessionManager,
    private val repository: ConnectionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val appContext: Context,
    private val transferManager: sh.haven.feature.sftp.transfer.SftpTransferManager,
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

    private val _searchResults = MutableStateFlow<List<SftpEntry>>(emptyList())
    val searchResults: StateFlow<List<SftpEntry>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

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

    val transfers: StateFlow<List<TransferTask>> = transferManager.transfers

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
        val profileId: String,
        val isSmb: Boolean,
    )
    private val _lastPreview = MutableStateFlow<PreviewResult?>(null)
    val lastPreview: StateFlow<PreviewResult?> = _lastPreview.asStateFlow()
    fun clearLastPreview() { _lastPreview.value = null }

    private var sftpChannel: ChannelSftp? = null
    private var activeSmbClient: SmbClient? = null
    private val previewReadLock = Any()
    private var favoritesJob: Job? = null
    private var searchJob: Job? = null
    private var searchJobKey: SearchIndexKey? = null
    private var searchIndexKey: SearchIndexKey? = null
    private var searchIndexEntries: List<SftpEntry> = emptyList()
    private var pendingSearchQuery = ""

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
        
        // Observe transfer completions
        viewModelScope.launch {
            var previousTransfers = emptyList<TransferTask>()
            transferManager.transfers.collectLatest { currentTransfers ->
                currentTransfers.forEach { currentTask ->
                    val prevTask = previousTransfers.find { it.id == currentTask.id }
                    if (prevTask?.state == TransferState.DOWNLOADING && currentTask.state == TransferState.DONE) {
                        if (currentTask.isUpload) {
                            if (currentTask.profileId == _activeProfileId.value) {
                                refresh()
                            }
                        } else {
                            if (currentTask.destinationUri != null) {
                                _lastDownload.value = DownloadResult(currentTask.fileName, currentTask.destinationUri)
                            }
                        }
                    }
                }
                previousTransfers = currentTransfers
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
                clearSearch()
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
            clearSearch()
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
            clearSearch()
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
        clearSearch()
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
        clearSearch()
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
        clearSearch()
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
        _searchResults.value = sortEntries(_searchResults.value, mode)
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

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        searchJobKey = null
        searchIndexKey = null
        searchIndexEntries = emptyList()
        pendingSearchQuery = ""
        _searchLoading.value = false
        _searchResults.value = emptyList()
    }

    fun clearSearchResults() {
        pendingSearchQuery = ""
        _searchLoading.value = false
        _searchResults.value = emptyList()
    }

    fun searchCurrentDirectory(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            clearSearchResults()
            return
        }
        val profileId = _activeProfileId.value ?: return
        val key = SearchIndexKey(
            profileId = profileId,
            rootPath = normalizePath(_currentPath.value),
            isSmb = _isSmbProfile.value,
            showHidden = _showHidden.value,
        )
        pendingSearchQuery = normalizedQuery

        if (searchIndexKey == key) {
            publishSearchResults(normalizedQuery)
            return
        }
        if (searchJob?.isActive == true && searchJobKey == key) {
            _searchLoading.value = true
            return
        }

        searchJob?.cancel()
        searchJobKey = key
        searchJob = viewModelScope.launch {
            try {
                _searchLoading.value = true
                val indexedEntries = withContext(Dispatchers.IO) {
                    if (key.isSmb) {
                        val client = activeSmbClient ?: throw IllegalStateException("SMB not connected")
                        buildSmbSearchIndex(client, key.rootPath, key.showHidden)
                    } else {
                        val remoteFindEntries = getSshClientForProfile(profileId)
                            ?.buildRemoteFindSearchIndex(key.rootPath, key.showHidden)
                        if (remoteFindEntries != null) {
                            return@withContext remoteFindEntries
                        }
                        val channel = openDedicatedSftpChannel(profileId)
                            ?: getOrOpenChannel(profileId)
                            ?: throw IllegalStateException("Not connected")
                        val shouldClose = channel !== sftpChannel
                        try {
                            buildSftpSearchIndex(channel, key.rootPath, key.showHidden)
                        } finally {
                            if (shouldClose && channel.isConnected) channel.disconnect()
                        }
                    }
                }
                searchIndexKey = key
                searchIndexEntries = indexedEntries.distinctBy { it.path }
                publishSearchResults(pendingSearchQuery)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _error.value = "Search failed: ${e.message}"
                _searchResults.value = emptyList()
            } finally {
                if (searchJobKey == key) {
                    searchJob = null
                    searchJobKey = null
                    _searchLoading.value = false
                }
            }
        }
    }

    private fun publishSearchResults(query: String) {
        val normalizedQuery = query.trim()
        _searchResults.value = if (normalizedQuery.isEmpty()) {
            emptyList()
        } else {
            sortEntries(
                searchIndexEntries.filter { entry ->
                    entry.name.contains(normalizedQuery, ignoreCase = true)
                },
                _sortMode.value,
            )
        }
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
        val taskId = java.util.UUID.randomUUID().toString()
        val task = TransferTask(
            id = taskId,
            fileName = entry.name,
            totalBytes = entry.size,
            transferredBytes = 0L,
            state = TransferState.DOWNLOADING,
            isUpload = false,
            destinationUri = destinationUri,
            entry = entry,
            profileId = profileId,
            isSmb = _isSmbProfile.value
        )
        transferManager.addTask(task)
    }

    fun uploadFile(fileName: String, sourceUri: Uri) {
        val profileId = _activeProfileId.value ?: return
        val destPath = _currentPath.value.trimEnd('/') + "/" + fileName
        
        viewModelScope.launch {
            val fileSize = appContext.contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            } ?: -1L
            
            val taskId = java.util.UUID.randomUUID().toString()
            val task = TransferTask(
                id = taskId,
                fileName = fileName,
                totalBytes = fileSize,
                transferredBytes = 0L,
                state = TransferState.DOWNLOADING,
                isUpload = true,
                destPath = destPath,
                sourceUri = sourceUri,
                profileId = profileId,
                isSmb = _isSmbProfile.value
            )
            transferManager.addTask(task)
        }
    }

    fun pauseTransfer(id: String) {
        transferManager.pauseTransfer(id)
    }

    fun resumeTransfer(id: String) {
        transferManager.resumeTransfer(id)
    }

    fun cancelTransfer(id: String) {
        transferManager.cancelTransfer(id)
    }

    fun removeTransfer(id: String) {
        transferManager.removeTransfer(id)
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
                        deleteSftpEntry(channel, entry.path, entry.isDirectory)
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

    private fun deleteSftpEntry(channel: ChannelSftp, path: String, isDirectory: Boolean) {
        if (!isDirectory) {
            channel.rm(path)
            return
        }

        val children = mutableListOf<ChannelSftp.LsEntry>()
        channel.ls(path) { lsEntry ->
            val name = lsEntry.filename
            if (name != "." && name != "..") {
                children += lsEntry
            }
            ChannelSftp.LsEntrySelector.CONTINUE
        }

        children.forEach { child ->
            val childPath = path.trimEnd('/') + "/" + child.filename
            if (child.attrs.isDir) {
                deleteSftpEntry(channel, childPath, isDirectory = true)
            } else {
                channel.rm(childPath)
            }
        }
        channel.rmdir(path)
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

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
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
                                            val sshSession = sessionManager.getSessionsForProfile(profileId)
                                                .firstOrNull { it.status == sh.haven.core.ssh.SshSessionManager.SessionState.Status.CONNECTED }
                                            
                                            // Ensure preview channel is localized to avoid UI or task channel freezing
                                            val channel = sshSession?.client?.openSftpChannel()
                                                ?: (moshSessionManager.getSshClientForProfile(profileId) as? sh.haven.core.ssh.SshClient)?.openSftpChannel()
                                                ?: (etSessionManager.getSshClientForProfile(profileId) as? sh.haven.core.ssh.SshClient)?.openSftpChannel()
                                                ?: error("Not connected")
                                                
                                            try {
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
                                            } finally {
                                                if (channel.isConnected) channel.disconnect()
                                            }
                                        }
                                    }
                                },
                            ),
                            entry.name
                        )
                    }
                    _lastPreview.value = PreviewResult(
                        remotePath = entry.path,
                        filePath = null,
                        uri = null,
                        streamUrl = streamUrl,
                        mimeType = mimeType,
                        mediaType = mediaType,
                        profileId = profileId,
                        isSmb = isSmbProfile,
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
                        profileId = profileId,
                        isSmb = _isSmbProfile.value,
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
                    true
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

    private suspend fun SshClient.buildRemoteFindSearchIndex(
        rootPath: String,
        showHidden: Boolean,
    ): List<SftpEntry>? {
        val maxDepth = SEARCH_MAX_DIRECTORY_DEPTH + 1
        val hiddenFilter = if (showHidden) "" else "\\( -name '.*' -prune \\) -o "
        val printFormat = "%y\\t%s\\t%T@\\t%M\\t%p\\0"
        val command = "find ${shellQuote(rootPath)} -mindepth 1 -maxdepth $maxDepth " +
            "$hiddenFilter-printf ${shellQuote(printFormat)}"
        val result = try {
            execCommand(command)
        } catch (e: Exception) {
            Log.d(TAG, "Remote find search index unavailable", e)
            return null
        }
        val entries = parseRemoteFindEntries(result.stdout)
        if (result.exitStatus != 0 && entries.isEmpty()) {
            Log.d(TAG, "Remote find search index failed: ${result.stderr.take(200)}")
            return null
        }
        return entries
    }

    private fun parseRemoteFindEntries(stdout: String): List<SftpEntry> {
        if (stdout.isEmpty()) return emptyList()
        return stdout.split('\u0000').mapNotNull { record ->
            if (record.isEmpty()) return@mapNotNull null
            val parts = record.split('\t', limit = 5)
            if (parts.size != 5) return@mapNotNull null
            val path = normalizePath(parts[4])
            val name = path.trimEnd('/').substringAfterLast('/')
            if (name.isEmpty()) return@mapNotNull null
            SftpEntry(
                name = name,
                path = path,
                isDirectory = parts[0].firstOrNull() == 'd',
                size = parts[1].toLongOrNull() ?: 0L,
                modifiedTime = parts[2].substringBefore('.').toLongOrNull() ?: 0L,
                permissions = parts[3],
            )
        }
    }

    private fun buildSftpSearchIndex(
        channel: ChannelSftp,
        rootPath: String,
        showHidden: Boolean,
    ): List<SftpEntry> {
        val results = mutableListOf<SftpEntry>()
        val visited = mutableSetOf<String>()

        fun visit(path: String, depth: Int) {
            val normalizedPath = normalizePath(path)
            if (!visited.add(normalizedPath)) return

            val children = mutableListOf<ChannelSftp.LsEntry>()
            try {
                channel.ls(normalizedPath) { lsEntry ->
                    val name = lsEntry.filename
                    if (name != "." && name != ".." && (showHidden || !name.startsWith("."))) {
                        children += lsEntry
                    }
                    ChannelSftp.LsEntrySelector.CONTINUE
                }
            } catch (e: Exception) {
                Log.d(TAG, "Skipping unreadable SFTP directory $normalizedPath", e)
                return
            }

            children.forEach { child ->
                val attrs = child.attrs
                val entry = SftpEntry(
                    name = child.filename,
                    path = normalizedPath.trimEnd('/') + "/" + child.filename,
                    isDirectory = attrs.isDir,
                    size = attrs.size,
                    modifiedTime = attrs.mTime.toLong(),
                    permissions = attrs.permissionsString ?: "",
                )
                results += entry
                if (entry.isDirectory && !attrs.isLink && depth < SEARCH_MAX_DIRECTORY_DEPTH) {
                    visit(entry.path, depth + 1)
                }
            }
        }

        visit(rootPath, depth = 0)
        return results
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

    private fun getSshClientForProfile(profileId: String): SshClient? {
        val sshSession = sessionManager.getSessionsForProfile(profileId)
            .firstOrNull { it.status == sh.haven.core.ssh.SshSessionManager.SessionState.Status.CONNECTED }
        if (sshSession != null) return sshSession.client
        return (moshSessionManager.getSshClientForProfile(profileId) as? SshClient)
            ?: (etSessionManager.getSshClientForProfile(profileId) as? SshClient)
    }

    private fun openDedicatedSftpChannel(profileId: String): ChannelSftp? {
        val sshSession = sessionManager.getSessionsForProfile(profileId)
            .firstOrNull { it.status == sh.haven.core.ssh.SshSessionManager.SessionState.Status.CONNECTED }
        if (sshSession != null) {
            return sshSession.client.openSftpChannel()
        }
        val moshClient = moshSessionManager.getSshClientForProfile(profileId) as? SshClient
        if (moshClient != null) {
            return moshClient.openSftpChannel()
        }
        val etClient = etSessionManager.getSshClientForProfile(profileId) as? SshClient
        if (etClient != null) {
            return etClient.openSftpChannel()
        }
        return null
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

    private fun buildSmbSearchIndex(
        client: SmbClient,
        rootPath: String,
        showHidden: Boolean,
    ): List<SftpEntry> {
        val results = mutableListOf<SftpEntry>()
        val visited = mutableSetOf<String>()

        fun visit(path: String, depth: Int) {
            val normalizedPath = normalizePath(path)
            if (!visited.add(normalizedPath)) return

            val children = try {
                client.listDirectory(normalizedPath)
            } catch (e: Exception) {
                Log.d(TAG, "Skipping unreadable SMB directory $normalizedPath", e)
                return
            }

            children.forEach { child ->
                if (!showHidden && child.name.startsWith(".")) return@forEach
                val entry = SftpEntry(
                    name = child.name,
                    path = child.path,
                    isDirectory = child.isDirectory,
                    size = child.size,
                    modifiedTime = child.modifiedTime,
                    permissions = child.permissions,
                )
                results += entry
                if (entry.isDirectory && depth < SEARCH_MAX_DIRECTORY_DEPTH) {
                    visit(entry.path, depth + 1)
                }
            }
        }

        visit(rootPath, depth = 0)
        return results
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
