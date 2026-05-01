package sh.haven.feature.terminal

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import sh.haven.core.ssh.ConnectionConfig
import sh.haven.core.ssh.HostKeyResult
import sh.haven.core.ssh.HostKeyVerifier
import sh.haven.core.ssh.SessionManager
import sh.haven.core.ssh.SshClient
import sh.haven.core.ssh.SshSessionManager
import sh.haven.core.ssh.SshSessionManager.SessionState
import sh.haven.core.et.EtSessionManager
import sh.haven.core.mosh.MoshSessionManager
import sh.haven.core.reticulum.ReticulumSessionManager
import sh.haven.core.data.preferences.UserPreferencesRepository
import sh.haven.core.data.preferences.TerminalComboKey
import javax.inject.Inject

private const val TAG = "TerminalViewModel"

/** Main-thread handler for posting emulator writes. */
private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

/**
 * Coalesces SSH/RNS data chunks into batched writes on the main thread.
 *
 * Without this, every onDataReceived callback posts a separate message to
 * the main looper. During fast output this floods the queue and delays
 * resize/layout events. This class accumulates bytes in a lock-protected
 * buffer and only keeps one pending main-thread drain in flight at a time.
 */
private class EmulatorWriteBuffer(private val emulator: () -> TerminalEmulator?) {
    private val lock = Any()
    private var buffer = ByteArray(8192)
    private var length = 0
    private var drainScheduled = false

    fun append(data: ByteArray, offset: Int, len: Int) {
        synchronized(lock) {
            if (length + len > buffer.size) {
                buffer = buffer.copyOf(maxOf(buffer.size * 2, length + len))
            }
            System.arraycopy(data, offset, buffer, length, len)
            length += len
            if (!drainScheduled) {
                drainScheduled = true
                mainHandler.post(::drain)
            }
        }
    }

    private fun drain() {
        val copy: ByteArray
        val copyLen: Int
        synchronized(lock) {
            copyLen = length
            copy = buffer.copyOf(copyLen)
            length = 0
            drainScheduled = false
        }
        if (copyLen > 0) {
            emulator()?.writeInput(copy, 0, copyLen)
        }
    }
}

/**
 * Coalesces rapid single-byte inputs into a batch, then deduplicates only
 * the exact IME double-fire pattern (buffer == [X, X]).
 *
 * Android IMEs often fire both commitText and sendKeyEvent for the same
 * keystroke, causing onKeyboardInput to be called twice with the same byte.
 * Both calls happen within a single Handler message, so a posted Runnable
 * flushes after all input from the current message is processed.
 *
 * The IME double-fire signature: exactly 2 identical bytes in the buffer.
 * Paste "aa" also matches this, but that's a rare edge case compared to
 * the constant double-fire on every keystroke. Longer paste sequences
 * (e.g., "aab", "43339") are preserved correctly.
 *
 * Multi-byte inputs (toolbar, escape sequences) bypass coalescing.
 */
private class InputCoalescer(private val sink: (ByteArray) -> Unit) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val buffer = mutableListOf<Byte>()

    private val flushRunnable = Runnable { flush() }

    fun send(data: ByteArray) {
        if (data.size != 1) {
            // Multi-byte input (toolbar key combos, etc.) — flush pending then send directly
            flush()
            sink(data)
            return
        }

        synchronized(buffer) {
            buffer.add(data[0])
        }

        // Post flush to run after the current message completes.
        // Both IME double-fire calls and paste iteration happen within
        // one message, so the flush sees the complete batch.
        handler.removeCallbacks(flushRunnable)
        handler.post(flushRunnable)
    }

    private fun flush() {
        handler.removeCallbacks(flushRunnable)
        val bytes: ByteArray
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            // IME double-fire signature: exactly 2 identical bytes
            if (buffer.size == 2 && buffer[0] == buffer[1]) {
                bytes = byteArrayOf(buffer[0])
            } else {
                bytes = buffer.toByteArray()
            }
            buffer.clear()
        }
        sink(bytes)
    }
}

data class TerminalTab(
    val sessionId: String,
    val profileId: String,
    val label: String,
    val transportType: String,
    val emulator: TerminalEmulator,
    val mouseMode: StateFlow<Boolean>,
    val bracketPasteMode: StateFlow<Boolean>,
    val oscHandler: OscHandler,
    val cwd: StateFlow<String?>,
    val hyperlinkUri: StateFlow<String?>,
    val isReconnecting: StateFlow<Boolean>,
    val sendInput: (ByteArray) -> Unit,
    val resize: (Int, Int) -> Unit,
    val close: () -> Unit,
)

/** VNC connection info for the active terminal's host. */
data class VncInfo(
    val host: String,
    val port: Int,
    val password: String?,
    val sshForward: Boolean,
    val profileId: String,
    val sessionId: String,
    val stored: Boolean,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionManager: SshSessionManager,
    private val reticulumSessionManager: ReticulumSessionManager,
    private val moshSessionManager: MoshSessionManager,
    private val etSessionManager: EtSessionManager,
    private val hostKeyVerifier: HostKeyVerifier,
    private val preferencesRepository: UserPreferencesRepository,
    private val connectionDao: sh.haven.core.data.db.ConnectionDao,
    private val snippetRepository: sh.haven.core.data.repository.SnippetRepository,
) : ViewModel() {

    override fun onCleared() {
        super.onCleared()
        // Detach terminal sessions so they can be re-picked up by a new ViewModel.
        // This happens when the Activity is destroyed but the process stays alive
        // (foreground service keeps SSH connections open).
        for (tab in _tabs.value) {
            when (tab.transportType) {
                "SSH" -> sessionManager.detachTerminalSession(tab.sessionId)
                "RETICULUM" -> reticulumSessionManager.detachTerminalSession(tab.sessionId)
                "MOSH" -> moshSessionManager.detachTerminalSession(tab.sessionId)
                "ET" -> etSessionManager.detachTerminalSession(tab.sessionId)
            }
        }
        trackedSessionIds.clear()
    }

    val terminalColorScheme: StateFlow<UserPreferencesRepository.TerminalColorScheme> =
        preferencesRepository.terminalColorScheme
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.TerminalColorScheme.HAVEN,
            )

    val terminalFont: StateFlow<UserPreferencesRepository.TerminalFont> =
        preferencesRepository.terminalFont
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.TerminalFont.SYSTEM,
            )

    val snippets: StateFlow<List<sh.haven.core.data.db.entities.Snippet>> =
        snippetRepository.getAllSnippets()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList(),
            )

    val comboKeys: StateFlow<List<TerminalComboKey>> =
        preferencesRepository.terminalComboKeys
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                sh.haven.core.data.preferences.TerminalComboKeys.PRESETS,
            )

    fun addSnippet(snippet: sh.haven.core.data.db.entities.Snippet) {
        viewModelScope.launch(Dispatchers.IO) {
            snippetRepository.addSnippet(snippet)
        }
    }

    fun updateSnippet(snippet: sh.haven.core.data.db.entities.Snippet) {
        viewModelScope.launch(Dispatchers.IO) {
            snippetRepository.updateSnippet(snippet)
        }
    }

    fun deleteSnippet(snippet: sh.haven.core.data.db.entities.Snippet) {
        viewModelScope.launch(Dispatchers.IO) {
            snippetRepository.deleteSnippet(snippet)
        }
    }

    private val _tabs = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabs: StateFlow<List<TerminalTab>> = _tabs.asStateFlow()

    /** Emitted once when a session closes and no tabs remain. */
    private val _navigateToConnections = MutableStateFlow(false)
    val navigateToConnections: StateFlow<Boolean> = _navigateToConnections.asStateFlow()

    fun onNavigatedToConnections() {
        _navigateToConnections.value = false
    }

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    // Modifier key state — read by onKeyboardInput callback, toggled by toolbar
    private val _ctrlActive = MutableStateFlow(false)
    val ctrlActive: StateFlow<Boolean> = _ctrlActive.asStateFlow()

    private val _altActive = MutableStateFlow(false)
    val altActive: StateFlow<Boolean> = _altActive.asStateFlow()

    fun toggleCtrl() { _ctrlActive.value = !_ctrlActive.value }
    fun toggleAlt() { _altActive.value = !_altActive.value }

    /** Get VNC connection info for the active terminal tab's SSH host. */
    suspend fun getActiveVncInfo(): VncInfo? {
        val tab = _tabs.value.getOrNull(_activeTabIndex.value) ?: return null
        val profile = connectionDao.getById(tab.profileId)
        // For SSH tabs, use the stored connection config; for mosh/ET, use the profile directly
        val host = sessionManager.getConnectionConfigForProfile(tab.profileId)?.first?.host
            ?: profile?.host
            ?: return null
        return VncInfo(
            host = host,
            port = profile?.vncPort ?: 5900,
            password = profile?.vncPassword,
            sshForward = profile?.vncSshForward ?: true,
            profileId = tab.profileId,
            sessionId = tab.sessionId,
            stored = profile?.vncPort != null,
        )
    }

    /**
     * Send Ctrl+L to the active tab if its profile uses Zellij as session manager.
     * Called by TerminalScreen when the keyboard hides to trigger a full redraw,
     * working around Zellij not reflowing content on alternate screen resize.
     */
    fun sendRedrawIfZellij() {
        val tab = _tabs.value.getOrNull(_activeTabIndex.value) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val profile = connectionDao.getById(tab.profileId)
            val smOverride = profile?.sessionManager
            val isZellij = if (smOverride != null) {
                smOverride.equals("ZELLIJ", ignoreCase = true)
            } else {
                preferencesRepository.sessionManager.first().name
                    .equals("ZELLIJ", ignoreCase = true)
            }
            if (isZellij) {
                // Ctrl+L = 0x0C (form feed) — triggers shell clear/redraw inside Zellij pane
                kotlinx.coroutines.delay(300) // wait for resize debounce + SIGWINCH
                tab.sendInput(byteArrayOf(0x0C))
            }
        }
    }

    /** Save VNC settings for a profile. */
    fun saveVncSettings(profileId: String, port: Int, password: String?, sshForward: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            connectionDao.updateVncSettings(profileId, port, password, sshForward)
        }
    }

    /**
     * Apply Ctrl/Alt modifiers to keyboard input, then reset them (one-shot).
     * Ctrl+letter -> char AND 0x1F (e.g. Ctrl+C = 0x03).
     * Alt+char -> ESC prefix.
     */
    private fun applyModifiers(data: ByteArray): ByteArray {
        val ctrl = _ctrlActive.value
        val alt = _altActive.value
        if (!ctrl && !alt) return data

        _ctrlActive.value = false
        _altActive.value = false

        var result = data
        if (ctrl && result.size == 1) {
            val b = result[0].toInt() and 0xFF
            if (b in 0x40..0x7F) {
                result = byteArrayOf((b and 0x1F).toByte())
            }
        }
        if (alt) {
            result = byteArrayOf(0x1b) + result
        }
        return result
    }

    private val trackedSessionIds = mutableSetOf<String>()

    init {
        // React to session state changes (e.g., "Disconnect All" from notification)
        // even when the TerminalScreen isn't actively composing.
        viewModelScope.launch {
            sessionManager.sessions.collect { syncSessions() }
        }
        viewModelScope.launch {
            reticulumSessionManager.sessions.collect { syncSessions() }
        }
        viewModelScope.launch {
            moshSessionManager.sessions.collect { syncSessions() }
        }
        viewModelScope.launch {
            etSessionManager.sessions.collect { syncSessions() }
        }
    }

    /**
     * Sync tabs with session manager state.
     * Creates emulator + terminal session for new CONNECTED sessions.
     */
    fun syncSessions() {
        val sshSessions = sessionManager.sessions.value
        val rnsSessions = reticulumSessionManager.sessions.value
        val moshSessions = moshSessionManager.sessions.value
        val etSessions = etSessionManager.sessions.value

        // Find SSH sessions that are connected or reconnecting
        val activeSshIds = sshSessions.values
            .filter {
                it.status == SessionState.Status.CONNECTED ||
                    it.status == SessionState.Status.RECONNECTING
            }
            .map { it.sessionId }
            .toSet()

        // Find Reticulum sessions that are connected
        val activeRnsIds = rnsSessions.values
            .filter {
                it.status == ReticulumSessionManager.SessionState.Status.CONNECTED
            }
            .map { it.sessionId }
            .toSet()

        // Find Mosh sessions that are connected
        val activeMoshIds = moshSessions.values
            .filter {
                it.status == MoshSessionManager.SessionState.Status.CONNECTED
            }
            .map { it.sessionId }
            .toSet()

        // Find ET sessions that are connected
        val activeEtIds = etSessions.values
            .filter {
                it.status == EtSessionManager.SessionState.Status.CONNECTED
            }
            .map { it.sessionId }
            .toSet()

        val allActiveIds = activeSshIds + activeRnsIds + activeMoshIds + activeEtIds

        val currentTabs = _tabs.value.toMutableList()

        // Remove tabs for disconnected sessions
        val hadTabs = currentTabs.isNotEmpty()
        val removed = currentTabs.removeAll { tab ->
            when (tab.transportType) {
                "SSH" -> tab.sessionId !in activeSshIds ||
                    sshSessions[tab.sessionId]?.terminalSession == null
                "RETICULUM" -> tab.sessionId !in activeRnsIds ||
                    rnsSessions[tab.sessionId]?.reticulumSession == null
                "MOSH" -> tab.sessionId !in activeMoshIds ||
                    moshSessions[tab.sessionId]?.moshSession == null
                "ET" -> tab.sessionId !in activeEtIds ||
                    etSessions[tab.sessionId]?.etSession == null
                else -> true
            }
        }
        if (removed) {
            trackedSessionIds.retainAll(currentTabs.map { it.sessionId }.toSet())
            if (hadTabs && currentTabs.isEmpty()) {
                _navigateToConnections.value = true
            }
        }

        // Create tabs for new SSH sessions
        for (sessionId in activeSshIds) {
            if (sessionId in trackedSessionIds) continue
            if (!sessionManager.isReadyForTerminal(sessionId)) continue

            val session = sshSessions[sessionId] ?: continue
            val baseLabel = session.label
            val tabLabel = generateTabLabel(baseLabel, session.profileId, currentTabs)

            lateinit var emulator: TerminalEmulator
            val writeBuffer = EmulatorWriteBuffer { emulator }
            val mouseTracker = MouseModeTracker()
            val oscHandler = OscHandler()
            val cwdFlow = MutableStateFlow<String?>(null)
            val hyperlinkFlow = MutableStateFlow<String?>(null)
            oscHandler.onCwdChanged = { cwdFlow.value = it }
            oscHandler.onHyperlink = { uri -> hyperlinkFlow.value = uri }
            val termSession = sessionManager.createTerminalSession(
                sessionId = sessionId,
                onDataReceived = { data, offset, length ->
                    oscHandler.process(data, offset, length)
                    mouseTracker.process(oscHandler.outputBuf, 0, oscHandler.outputLen)
                    val len = oscHandler.outputLen
                    if (len > 0) {
                        writeBuffer.append(oscHandler.outputBuf, 0, len)
                    }
                },
            ) ?: continue

            val coalescer = InputCoalescer { data -> termSession.sendToSsh(data) }
            val scheme = terminalColorScheme.value
            emulator = TerminalEmulatorFactory.create(
                initialRows = 24,
                initialCols = 80,
                defaultForeground = Color(scheme.foreground),
                defaultBackground = Color(scheme.background),
                onKeyboardInput = { data -> coalescer.send(applyModifiers(data)) },
                onResize = { dims ->
                    Log.d(TAG, "SSH onResize: ${dims.columns}x${dims.rows}")
                    // Resize ALL tabs — only the active tab's Terminal composable
                    // fires onResize, so inactive tabs would keep stale PTY sizes
                    // unless we propagate here.
                    for (tab in _tabs.value) {
                        tab.resize(dims.columns, dims.rows)
                    }
                    // Also resize the just-created session (not yet in _tabs)
                    termSession.resize(dims.columns, dims.rows)
                },
            )

            termSession.start()

            val sshSessionId = session.sessionId
            val reconnectingFlow = sessionManager.sessions
                .map { it[sshSessionId]?.status == SessionState.Status.RECONNECTING }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

            currentTabs.add(
                TerminalTab(
                    sessionId = sshSessionId,
                    profileId = session.profileId,
                    label = tabLabel,
                    transportType = "SSH",
                    emulator = emulator,
                    mouseMode = mouseTracker.mouseMode,
                    bracketPasteMode = mouseTracker.bracketPasteMode,
                    oscHandler = oscHandler,
                    cwd = cwdFlow,
                    hyperlinkUri = hyperlinkFlow,
                    isReconnecting = reconnectingFlow,
                    sendInput = { data -> termSession.sendToSsh(data) },
                    resize = { cols, rows -> termSession.resize(cols, rows) },
                    close = { termSession.close() },
                )
            )
            trackedSessionIds.add(session.sessionId)
        }

        // Create tabs for new Reticulum sessions
        for (sessionId in activeRnsIds) {
            if (sessionId in trackedSessionIds) continue
            if (!reticulumSessionManager.isReadyForTerminal(sessionId)) continue

            val session = rnsSessions[sessionId] ?: continue
            val tabLabel = generateTabLabel(session.label, session.profileId, currentTabs)

            lateinit var emulator: TerminalEmulator
            val rnsWriteBuffer = EmulatorWriteBuffer { emulator }
            val rnsMouseTracker = MouseModeTracker()
            val rnsOscHandler = OscHandler()
            val rnsCwdFlow = MutableStateFlow<String?>(null)
            val rnsHyperlinkFlow = MutableStateFlow<String?>(null)
            rnsOscHandler.onCwdChanged = { rnsCwdFlow.value = it }
            rnsOscHandler.onHyperlink = { uri -> rnsHyperlinkFlow.value = uri }
            val rnsSession = reticulumSessionManager.createTerminalSession(
                sessionId = sessionId,
                onDataReceived = { data, offset, length ->
                    rnsOscHandler.process(data, offset, length)
                    rnsMouseTracker.process(rnsOscHandler.outputBuf, 0, rnsOscHandler.outputLen)
                    val len = rnsOscHandler.outputLen
                    if (len > 0) {
                        rnsWriteBuffer.append(rnsOscHandler.outputBuf, 0, len)
                    }
                },
            ) ?: continue

            val rnsCoalescer = InputCoalescer { data -> rnsSession.sendInput(data) }
            val rnsScheme = terminalColorScheme.value
            emulator = TerminalEmulatorFactory.create(
                initialRows = 24,
                initialCols = 80,
                defaultForeground = Color(rnsScheme.foreground),
                defaultBackground = Color(rnsScheme.background),
                onKeyboardInput = { data -> rnsCoalescer.send(applyModifiers(data)) },
                onResize = { dims ->
                    Log.d(TAG, "RNS onResize: ${dims.columns}x${dims.rows}")
                    for (tab in _tabs.value) {
                        tab.resize(dims.columns, dims.rows)
                    }
                    rnsSession.resize(dims.columns, dims.rows)
                },
            )

            rnsSession.start()

            currentTabs.add(
                TerminalTab(
                    sessionId = session.sessionId,
                    profileId = session.profileId,
                    label = tabLabel,
                    transportType = "RETICULUM",
                    emulator = emulator,
                    mouseMode = rnsMouseTracker.mouseMode,
                    bracketPasteMode = rnsMouseTracker.bracketPasteMode,
                    oscHandler = rnsOscHandler,
                    cwd = rnsCwdFlow,
                    hyperlinkUri = rnsHyperlinkFlow,
                    isReconnecting = MutableStateFlow(false),
                    sendInput = { data -> rnsSession.sendInput(data) },
                    resize = { cols, rows -> rnsSession.resize(cols, rows) },
                    close = { rnsSession.close() },
                )
            )
            trackedSessionIds.add(session.sessionId)
        }

        // Create tabs for new Mosh sessions
        for (sessionId in activeMoshIds) {
            if (sessionId in trackedSessionIds) continue
            if (!moshSessionManager.isReadyForTerminal(sessionId)) continue

            val session = moshSessions[sessionId] ?: continue
            val tabLabel = generateTabLabel(session.label, session.profileId, currentTabs)

            lateinit var emulator: TerminalEmulator
            val moshWriteBuffer = EmulatorWriteBuffer { emulator }
            val moshMouseTracker = MouseModeTracker()
            val moshOscHandler = OscHandler()
            val moshCwdFlow = MutableStateFlow<String?>(null)
            val moshHyperlinkFlow = MutableStateFlow<String?>(null)
            moshOscHandler.onCwdChanged = { moshCwdFlow.value = it }
            moshOscHandler.onHyperlink = { uri -> moshHyperlinkFlow.value = uri }

            // Defer initial command until first output arrives (shell ready)
            val moshInitialCmd = session.initialCommand
            val moshShellReady = java.util.concurrent.atomic.AtomicBoolean(false)
            // Holder so lambda can reference session before it's assigned
            val moshSessionRef = arrayOfNulls<sh.haven.core.mosh.MoshSession>(1)

            val moshSession = moshSessionManager.createTerminalSession(
                sessionId = sessionId,
                onDataReceived = { data, offset, length ->
                    moshOscHandler.process(data, offset, length)
                    moshMouseTracker.process(moshOscHandler.outputBuf, 0, moshOscHandler.outputLen)
                    val len = moshOscHandler.outputLen
                    if (len > 0) {
                        moshWriteBuffer.append(moshOscHandler.outputBuf, 0, len)
                    }
                    // Send session manager command once shell produces first output
                    if (moshInitialCmd != null && moshShellReady.compareAndSet(false, true)) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(100) // brief settle after first prompt
                            moshSessionRef[0]?.sendInput((moshInitialCmd + "\n").toByteArray())
                        }
                    }
                },
            ) ?: continue
            moshSessionRef[0] = moshSession

            val moshCoalescer = InputCoalescer { data -> moshSession.sendInput(data) }
            val moshScheme = terminalColorScheme.value
            emulator = TerminalEmulatorFactory.create(
                initialRows = 24,
                initialCols = 80,
                defaultForeground = Color(moshScheme.foreground),
                defaultBackground = Color(moshScheme.background),
                onKeyboardInput = { data -> moshCoalescer.send(applyModifiers(data)) },
                onResize = { dims ->
                    Log.d(TAG, "MOSH onResize: ${dims.columns}x${dims.rows}")
                    for (tab in _tabs.value) {
                        tab.resize(dims.columns, dims.rows)
                    }
                    moshSession.resize(dims.columns, dims.rows)
                },
            )

            moshSession.start()

            currentTabs.add(
                TerminalTab(
                    sessionId = session.sessionId,
                    profileId = session.profileId,
                    label = tabLabel,
                    transportType = "MOSH",
                    emulator = emulator,
                    mouseMode = moshMouseTracker.mouseMode,
                    bracketPasteMode = moshMouseTracker.bracketPasteMode,
                    oscHandler = moshOscHandler,
                    cwd = moshCwdFlow,
                    hyperlinkUri = moshHyperlinkFlow,
                    isReconnecting = MutableStateFlow(false),
                    sendInput = { data -> moshSession.sendInput(data) },
                    resize = { cols, rows -> moshSession.resize(cols, rows) },
                    close = { moshSession.close() },
                )
            )
            trackedSessionIds.add(session.sessionId)
        }

        // Create tabs for new ET sessions
        for (sessionId in activeEtIds) {
            if (sessionId in trackedSessionIds) continue
            if (!etSessionManager.isReadyForTerminal(sessionId)) continue

            val session = etSessions[sessionId] ?: continue
            val tabLabel = generateTabLabel(session.label, session.profileId, currentTabs)

            lateinit var emulator: TerminalEmulator
            val etWriteBuffer = EmulatorWriteBuffer { emulator }
            val etMouseTracker = MouseModeTracker()
            val etOscHandler = OscHandler()
            val etCwdFlow = MutableStateFlow<String?>(null)
            val etHyperlinkFlow = MutableStateFlow<String?>(null)
            etOscHandler.onCwdChanged = { etCwdFlow.value = it }
            etOscHandler.onHyperlink = { uri -> etHyperlinkFlow.value = uri }

            // Defer initial command until first output arrives (shell ready)
            val etInitialCmd = session.initialCommand
            val etShellReady = java.util.concurrent.atomic.AtomicBoolean(false)
            val etSessionRef = arrayOfNulls<sh.haven.core.et.EtSession>(1)

            val etSession = etSessionManager.createTerminalSession(
                sessionId = sessionId,
                onDataReceived = { data, offset, length ->
                    etOscHandler.process(data, offset, length)
                    etMouseTracker.process(etOscHandler.outputBuf, 0, etOscHandler.outputLen)
                    val len = etOscHandler.outputLen
                    if (len > 0) {
                        etWriteBuffer.append(etOscHandler.outputBuf, 0, len)
                    }
                    // Send session manager command once shell produces first output
                    if (etInitialCmd != null && etShellReady.compareAndSet(false, true)) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(100) // brief settle after first prompt
                            etSessionRef[0]?.sendInput((etInitialCmd + "\n").toByteArray())
                        }
                    }
                },
            ) ?: continue
            etSessionRef[0] = etSession

            val etCoalescer = InputCoalescer { data -> etSession.sendInput(data) }
            val etScheme = terminalColorScheme.value
            emulator = TerminalEmulatorFactory.create(
                initialRows = 24,
                initialCols = 80,
                defaultForeground = Color(etScheme.foreground),
                defaultBackground = Color(etScheme.background),
                onKeyboardInput = { data -> etCoalescer.send(applyModifiers(data)) },
                onResize = { dims ->
                    Log.d(TAG, "ET onResize: ${dims.columns}x${dims.rows}")
                    for (tab in _tabs.value) {
                        tab.resize(dims.columns, dims.rows)
                    }
                    etSession.resize(dims.columns, dims.rows)
                },
            )

            etSession.start()

            currentTabs.add(
                TerminalTab(
                    sessionId = session.sessionId,
                    profileId = session.profileId,
                    label = tabLabel,
                    transportType = "ET",
                    emulator = emulator,
                    mouseMode = etMouseTracker.mouseMode,
                    bracketPasteMode = etMouseTracker.bracketPasteMode,
                    oscHandler = etOscHandler,
                    cwd = etCwdFlow,
                    hyperlinkUri = etHyperlinkFlow,
                    isReconnecting = MutableStateFlow(false),
                    sendInput = { data -> etSession.sendInput(data) },
                    resize = { cols, rows -> etSession.resize(cols, rows) },
                    close = { etSession.close() },
                )
            )
            trackedSessionIds.add(session.sessionId)
        }

        _tabs.value = currentTabs

        // Clamp active index
        if (_activeTabIndex.value >= currentTabs.size && currentTabs.isNotEmpty()) {
            _activeTabIndex.value = currentTabs.size - 1
        }
    }

    /**
     * Generate a tab label, appending a number suffix when multiple tabs
     * share the same profile (e.g., "myserver", "myserver (2)").
     */
    private fun generateTabLabel(
        baseLabel: String,
        profileId: String,
        existingTabs: List<TerminalTab>,
    ): String {
        val sameProfileCount = existingTabs.count { it.profileId == profileId }
        return if (sameProfileCount == 0) baseLabel
        else "$baseLabel (${sameProfileCount + 1})"
    }

    fun selectTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }

    fun selectTabByProfileId(profileId: String) {
        val index = _tabs.value.indexOfFirst { it.profileId == profileId }
        if (index >= 0) {
            _activeTabIndex.value = index
        } else {
            // Tab not yet created by syncSessions — wait for it
            viewModelScope.launch {
                try {
                    withTimeout(5000) {
                        _tabs.first { tabs -> tabs.any { it.profileId == profileId } }
                    }
                    val newIndex = _tabs.value.indexOfFirst { it.profileId == profileId }
                    if (newIndex >= 0) {
                        _activeTabIndex.value = newIndex
                    }
                } catch (_: TimeoutCancellationException) {
                    Log.w(TAG, "selectTabByProfileId: tab for $profileId not created within 5s")
                }
            }
        }
    }

    fun selectTabBySessionId(sessionId: String) {
        val index = _tabs.value.indexOfFirst { it.sessionId == sessionId }
        if (index >= 0) {
            _activeTabIndex.value = index
        }
    }

    fun closeTab(sessionId: String) {
        // Check all four managers
        if (sessionManager.sessions.value.containsKey(sessionId)) {
            sessionManager.removeSession(sessionId)
        } else if (moshSessionManager.sessions.value.containsKey(sessionId)) {
            moshSessionManager.removeSession(sessionId)
        } else if (etSessionManager.sessions.value.containsKey(sessionId)) {
            etSessionManager.removeSession(sessionId)
        } else {
            reticulumSessionManager.removeSession(sessionId)
        }
        trackedSessionIds.remove(sessionId)
        syncSessions()
    }

    /** Close all sessions for a profile (called from connections disconnect). */
    fun closeSession(profileId: String) {
        sessionManager.removeAllSessionsForProfile(profileId)
        reticulumSessionManager.removeAllSessionsForProfile(profileId)
        moshSessionManager.removeAllSessionsForProfile(profileId)
        etSessionManager.removeAllSessionsForProfile(profileId)
        trackedSessionIds.removeAll(
            _tabs.value.filter { it.profileId == profileId }.map { it.sessionId }.toSet()
        )
        syncSessions()
    }

    /** When non-null, the UI should show a session picker for a new tab. */
    data class NewTabSessionSelection(
        val profileId: String,
        val managerLabel: String,
        val sessionNames: List<String>,
        val sessionId: String,
        val manager: SessionManager = SessionManager.NONE,
        val error: String? = null,
    )

    private val _newTabSessionPicker = MutableStateFlow<NewTabSessionSelection?>(null)
    val newTabSessionPicker: StateFlow<NewTabSessionSelection?> = _newTabSessionPicker.asStateFlow()

    private val _newTabLoading = MutableStateFlow(false)
    val newTabLoading: StateFlow<Boolean> = _newTabLoading.asStateFlow()

    /**
     * Add a new tab by creating a fresh connection to the same server as the current tab.
     */
    fun addTab() {
        val activeTab = _tabs.value.getOrNull(_activeTabIndex.value)
        if (activeTab == null) {
            Log.w(TAG, "addTab: no active tab (index=${_activeTabIndex.value}, tabs=${_tabs.value.size})")
            return
        }

        if (activeTab.transportType == "RETICULUM") {
            addReticulumTab(activeTab)
            return
        }

        if (activeTab.transportType == "MOSH") {
            // Mosh doesn't support multiple tabs to same server from one bootstrap
            Log.w(TAG, "addTab: mosh sessions don't support new tabs (use SSH for multi-tab)")
            return
        }

        if (activeTab.transportType == "ET") {
            // ET doesn't support multiple tabs to same server from one bootstrap
            Log.w(TAG, "addTab: ET sessions don't support new tabs (use SSH for multi-tab)")
            return
        }

        addSshTabForProfile(activeTab.profileId)
    }

    /**
     * Add a new SSH tab for a profile by creating a fresh connection.
     * Called from [addTab] (clone current tab) and from the Connections screen
     * "New Session" context menu item.
     */
    fun addSshTabForProfile(profileId: String) {
        val configPair = sessionManager.getConnectionConfigForProfile(profileId)
        if (configPair == null) {
            Log.w(TAG, "addSshTabForProfile: no connection config for profile $profileId")
            return
        }
        val (config, sshSessionMgr) = configPair

        // Derive label from an existing tab or the profile's config host
        val existingTab = _tabs.value.firstOrNull { it.profileId == profileId }
        val label = existingTab?.label?.replace(Regex(" \\(\\d+\\)$"), "")
            ?: config.host

        viewModelScope.launch {
            _newTabLoading.value = true
            val client = SshClient()
            val sessionId = sessionManager.registerSession(profileId, label, client)
            try {
                val hostKeyEntry = withContext(Dispatchers.IO) {
                    client.connect(config)
                }

                // Silent TOFU: auto-accept new hosts, reject key changes
                when (val hkResult = hostKeyVerifier.verify(hostKeyEntry)) {
                    is HostKeyResult.Trusted -> { /* matches — continue */ }
                    is HostKeyResult.NewHost -> {
                        hostKeyVerifier.accept(hkResult.entry)
                    }
                    is HostKeyResult.KeyChanged -> {
                        client.disconnect()
                        sessionManager.removeSession(sessionId)
                        Log.w(TAG, "Host key changed for ${config.host}:${config.port} — aborting new tab")
                        _newTabLoading.value = false
                        return@launch
                    }
                }

                sessionManager.storeConnectionConfig(sessionId, config, sshSessionMgr)

                val listCmd = sshSessionMgr.listCommand
                if (listCmd != null) {
                    val existingSessions = withContext(Dispatchers.IO) {
                        try {
                            val result = client.execCommand(listCmd)
                            if (result.exitStatus == 0) {
                                SessionManager.parseSessionList(sshSessionMgr, result.stdout)
                            } else emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    if (existingSessions.isNotEmpty()) {
                        _newTabSessionPicker.value = NewTabSessionSelection(
                            profileId = profileId,
                            managerLabel = sshSessionMgr.label,
                            sessionNames = existingSessions,
                            sessionId = sessionId,
                            manager = sshSessionMgr,
                        )
                        _newTabLoading.value = false
                        return@launch
                    }
                }

                finishNewSshTab(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "addSshTabForProfile failed", e)
                sessionManager.removeSession(sessionId)
            } finally {
                _newTabLoading.value = false
            }
        }
    }

    /**
     * Add a new Reticulum tab to the same destination as the current tab.
     */
    private fun addReticulumTab(activeTab: TerminalTab) {
        val profileId = activeTab.profileId
        val rnsSession = reticulumSessionManager.sessions.value.values
            .firstOrNull { it.profileId == profileId }
            ?: return
        val label = activeTab.label.replace(Regex(" \\(\\d+\\)$"), "")

        viewModelScope.launch {
            _newTabLoading.value = true
            try {
                val sessionId = reticulumSessionManager.registerSession(
                    profileId = profileId,
                    label = label,
                    destinationHash = rnsSession.destinationHash,
                )
                withContext(Dispatchers.IO) {
                    reticulumSessionManager.connectSession(
                        sessionId = sessionId,
                        configDir = "", // Already initialised
                        host = "",
                        port = 0,
                    )
                }
                syncSessions()
                selectTabBySessionId(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "addReticulumTab failed", e)
            } finally {
                _newTabLoading.value = false
            }
        }
    }

    fun onNewTabSessionSelected(sessionId: String, sessionName: String?) {
        val remoteNames = _newTabSessionPicker.value?.sessionNames ?: emptyList()
        _newTabSessionPicker.value = null
        viewModelScope.launch {
            _newTabLoading.value = true
            try {
                val effectiveName = sessionName ?: generateUniqueSessionName(
                    sessionManager.getSession(sessionId)?.label ?: sessionId.take(8),
                    remoteNames,
                )
                sessionManager.setChosenSessionName(sessionId, effectiveName)
                finishNewSshTab(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "onNewTabSessionSelected failed", e)
                sessionManager.removeSession(sessionId)
            } finally {
                _newTabLoading.value = false
            }
        }
    }

    private fun generateUniqueSessionName(label: String, remoteNames: List<String>): String {
        val base = label.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val existing = remoteNames.toSet()
        if (base !in existing) return base
        var i = 2
        while ("$base-$i" in existing) i++
        return "$base-$i"
    }

    fun killRemoteSession(sessionName: String) {
        val sel = _newTabSessionPicker.value ?: return
        val killCmd = sel.manager.killCommand?.invoke(sessionName) ?: return
        val session = sessionManager.getSession(sel.sessionId) ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    session.client.execCommand(killCmd)
                }
                val listCmd = sel.manager.listCommand ?: return@launch
                val updated = withContext(Dispatchers.IO) {
                    try {
                        val result = session.client.execCommand(listCmd)
                        if (result.exitStatus == 0) {
                            SessionManager.parseSessionList(sel.manager, result.stdout)
                        } else emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                if (updated.isNotEmpty()) {
                    _newTabSessionPicker.value = sel.copy(sessionNames = updated)
                } else {
                    _newTabSessionPicker.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "killRemoteSession failed", e)
            }
        }
    }

    fun renameRemoteSession(oldName: String, newName: String) {
        val sel = _newTabSessionPicker.value ?: return
        val renameCmd = sel.manager.renameCommand?.invoke(oldName, newName) ?: return
        val session = sessionManager.getSession(sel.sessionId) ?: return

        viewModelScope.launch {
            try {
                val renameResult = withContext(Dispatchers.IO) {
                    session.client.execCommand(renameCmd)
                }
                val renameError = if (renameResult.exitStatus != 0) {
                    Log.w(TAG, "renameRemoteSession failed: exit=${renameResult.exitStatus} stderr='${renameResult.stderr}'")

                    renameResult.stderr.ifBlank { "Rename failed (exit ${renameResult.exitStatus})" }
                } else null
                // Give the session manager a moment to propagate the rename
                kotlinx.coroutines.delay(500)
                val listCmd = sel.manager.listCommand ?: return@launch
                val updated = withContext(Dispatchers.IO) {
                    try {
                        val result = session.client.execCommand(listCmd)
                        if (result.exitStatus == 0) {
                            SessionManager.parseSessionList(sel.manager, result.stdout)
                        } else emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                if (updated.isNotEmpty()) {
                    _newTabSessionPicker.value = sel.copy(sessionNames = updated, error = renameError)
                } else {
                    _newTabSessionPicker.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "renameRemoteSession failed", e)
            }
        }
    }

    fun dismissNewTabSessionPicker() {
        val sel = _newTabSessionPicker.value ?: return
        _newTabSessionPicker.value = null
        sessionManager.removeSession(sel.sessionId)
    }

    private suspend fun finishNewSshTab(sessionId: String) {
        withContext(Dispatchers.IO) {
            sessionManager.openShellForSession(sessionId)
        }
        sessionManager.updateStatus(sessionId, SessionState.Status.CONNECTED)
        syncSessions()
        selectTabBySessionId(sessionId)
    }
}
