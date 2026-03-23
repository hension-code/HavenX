package sh.haven.feature.connections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import sh.haven.core.data.db.entities.ConnectionProfile
import sh.haven.core.ssh.SshSessionManager

/** Profile group colors — matches TAB_GROUP_COLORS in TerminalScreen. */
private val PROFILE_COLORS = listOf(
    Color(0xFF42A5F5), // blue
    Color(0xFF66BB6A), // green
    Color(0xFFFF7043), // orange
    Color(0xFFAB47BC), // purple
    Color(0xFFFFCA28), // amber
    Color(0xFF26C6DA), // cyan
    Color(0xFFEF5350), // red
    Color(0xFF8D6E63), // brown
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConnectionsScreen(
    onNavigateToTerminal: (profileId: String) -> Unit = {},
    onNavigateToNewSession: (profileId: String) -> Unit = {},
    onNavigateToVnc: (host: String, port: Int, password: String?) -> Unit = { _, _, _ -> },
    onNavigateToRdp: (host: String, port: Int, username: String, password: String, domain: String, sshForward: Boolean, sshProfileId: String?, sshSessionId: String?) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onNavigateToSmb: (profileId: String) -> Unit = {},
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsState()
    val sshKeys by viewModel.sshKeys.collectAsState()
    val profileStatuses by viewModel.profileStatuses.collectAsState()
    val sessions by viewModel.sessions.collectAsState()

    // Derive profile colors matching terminal tab colors (by session registration order)
    val profileColors = remember(sessions) {
        sessions.values
            .filter {
                it.status == SshSessionManager.SessionState.Status.CONNECTED ||
                    it.status == SshSessionManager.SessionState.Status.RECONNECTING
            }
            .map { it.profileId }
            .distinct()
            .withIndex()
            .associate { (i, id) -> id to PROFILE_COLORS[i % PROFILE_COLORS.size] }
    }
    val discoveredDestinations by viewModel.discoveredDestinations.collectAsState()
    val discoveredHosts by viewModel.discoveredHosts.collectAsState()
    val localVmStatus by viewModel.localVmStatus.collectAsState()
    val connectingProfileId by viewModel.connectingProfileId.collectAsState()
    val error by viewModel.error.collectAsState()
    val navigateToTerminal by viewModel.navigateToTerminal.collectAsState()
    val navigateToVnc by viewModel.navigateToVnc.collectAsState()
    val navigateToRdp by viewModel.navigateToRdp.collectAsState()
    val navigateToSmb by viewModel.navigateToSmb.collectAsState()
    val deploySuccess by viewModel.deploySuccess.collectAsState()
    val sessionSelection by viewModel.sessionSelection.collectAsState()
    val passwordFallback by viewModel.passwordFallback.collectAsState()
    val hostKeyPrompt by viewModel.hostKeyPrompt.collectAsState()
    val globalSessionManagerLabel by viewModel.globalSessionManagerLabel.collectAsState()
    val newSessionProfileId by viewModel.newSessionProfileId.collectAsState()
    val subnetScanning by viewModel.subnetScanning.collectAsState()
    val discoveredSmbHosts by viewModel.discoveredSmbHosts.collectAsState()
    val smbSubnetScanning by viewModel.smbSubnetScanning.collectAsState()
    val showMoshSetupGuide by viewModel.showMoshSetupGuide.collectAsState()
    val showMoshClientMissing by viewModel.showMoshClientMissing.collectAsState()


    LaunchedEffect(navigateToTerminal) {
        navigateToTerminal?.let { profileId ->
            onNavigateToTerminal(profileId)
            viewModel.onNavigated()
        }
    }

    LaunchedEffect(navigateToVnc) {
        navigateToVnc?.let { nav ->
            onNavigateToVnc(nav.host, nav.port, nav.password)
            viewModel.onNavigated()
        }
    }

    LaunchedEffect(navigateToRdp) {
        navigateToRdp?.let { nav ->
            onNavigateToRdp(nav.host, nav.port, nav.username, nav.password, nav.domain, nav.sshForward, nav.sshProfileId, nav.sshSessionId)
            viewModel.onNavigated()
        }
    }

    LaunchedEffect(navigateToSmb) {
        navigateToSmb?.let { profileId ->
            onNavigateToSmb(profileId)
            viewModel.onNavigated()
        }
    }

    LaunchedEffect(newSessionProfileId) {
        newSessionProfileId?.let { profileId ->
            onNavigateToNewSession(profileId)
            viewModel.onNavigated()
        }
    }

    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showVmSetup by rememberSaveable { mutableStateOf(false) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingProfile = editingProfileId?.let { id -> connections.firstOrNull { it.id == id } }
    var connectingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var deployingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var portForwardProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var quickConnectText by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val context = LocalContext.current
    val zh = Locale.current.language == "zh"

    val sshKeyDeployedMsg = stringResource(R.string.ssh_key_deployed_successfully)
    LaunchedEffect(deploySuccess) {
        if (deploySuccess) {
            snackbarHostState.showSnackbar(sshKeyDeployedMsg)
            viewModel.dismissDeploySuccess()
        }
    }

    // Request POST_NOTIFICATIONS permission on Android 13+ so the foreground
    // service notification is visible and "Disconnect All" action works.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or denied — either way, foreground service still works */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            )
            if (status != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Probe for Sideband and start collecting announces as soon as the
    // Connections tab is shown. Refreshes every 30s to pick up announces
    // arriving over slow LoRa links. Stops when the screen is disposed.
    DisposableEffect(Unit) {
        viewModel.startPeriodicRefresh()
        viewModel.startNetworkDiscovery()
        onDispose {
            viewModel.stopPeriodicRefresh()
            viewModel.stopNetworkDiscovery()
        }
    }

    if (showAddDialog) {
        ConnectionEditDialog(
            discoveredDestinations = discoveredDestinations,
            discoveredHosts = discoveredHosts,
            discoveredSmbHosts = discoveredSmbHosts,
            sshProfiles = connections,
            sshKeys = sshKeys,
            globalSessionManagerLabel = globalSessionManagerLabel,
            subnetScanning = subnetScanning,
            smbSubnetScanning = smbSubnetScanning,
            onScanSubnet = { viewModel.scanSubnet() },
            onScanSubnetSmb = { viewModel.scanSubnetSmb() },
            onDismiss = { showAddDialog = false },
            onSave = { profile ->
                viewModel.saveConnection(profile)
                showAddDialog = false
            },
        )
    }

    val linuxVmStr = stringResource(R.string.linux_vm)
    if (showVmSetup) {
        LinuxVmSetupDialog(
            vmStatus = localVmStatus,
            onConnectSsh = { port ->
                showVmSetup = false
                val existing = connections.find {
                    it.host in listOf("localhost", "127.0.0.1") && it.port == port && it.username == "droid"
                }
                val profile = existing ?: ConnectionProfile(
                    label = linuxVmStr,
                    host = "localhost",
                    port = port,
                    username = "droid",
                )
                if (existing == null) viewModel.saveConnection(profile)
                connectingProfile = profile
            },
            onConnectVnc = { port ->
                showVmSetup = false
                val sshPort = localVmStatus.sshPort ?: 8022
                val existing = connections.find {
                    it.host in listOf("localhost", "127.0.0.1") && it.port == sshPort && it.username == "droid"
                }
                val profile = (existing?.copy(vncPort = port, vncSshForward = false))
                    ?: ConnectionProfile(
                        label = linuxVmStr,
                        host = "localhost",
                        port = sshPort,
                        username = "droid",
                        vncPort = port,
                        vncSshForward = false,
                    )
                viewModel.saveConnection(profile)
                connectingProfile = profile
            },
            onDismiss = { showVmSetup = false },
        )
    }

    editingProfile?.let { profile ->
        ConnectionEditDialog(
            existing = profile,
            discoveredDestinations = discoveredDestinations,
            discoveredHosts = discoveredHosts,
            discoveredSmbHosts = discoveredSmbHosts,
            sshProfiles = connections,
            sshKeys = sshKeys,
            globalSessionManagerLabel = globalSessionManagerLabel,
            subnetScanning = subnetScanning,
            smbSubnetScanning = smbSubnetScanning,
            onScanSubnet = { viewModel.scanSubnet() },
            onScanSubnetSmb = { viewModel.scanSubnetSmb() },
            onDismiss = { editingProfileId = null },
            onSave = { updated ->
                viewModel.saveConnection(updated)
                editingProfileId = null
            },
        )
    }

    connectingProfile?.let { profile ->
        PasswordDialog(
            profile = profile,
            hasKeys = sshKeys.isNotEmpty(),
            zh = zh,
            onDismiss = { connectingProfile = null },
            onConnect = { password, rememberPassword ->
                viewModel.connect(profile, password, rememberPassword = rememberPassword)
                connectingProfile = null
            },
        )
    }

    passwordFallback?.let { profile ->
        PasswordDialog(
            profile = profile,
            hasKeys = sshKeys.isNotEmpty(),
            zh = zh,
            onDismiss = { viewModel.dismissPasswordFallback() },
            onConnect = { password, rememberPassword ->
                viewModel.connect(profile, password, rememberPassword = rememberPassword)
                viewModel.dismissPasswordFallback()
            },
        )
    }

    hostKeyPrompt?.let { prompt ->
        when (prompt) {
            is ConnectionsViewModel.HostKeyPrompt.NewHost -> {
                NewHostKeyDialog(
                    entry = prompt.entry,
                    zh = zh,
                    onTrust = { viewModel.onHostKeyAccepted() },
                    onCancel = { viewModel.onHostKeyRejected() },
                )
            }
            is ConnectionsViewModel.HostKeyPrompt.KeyChanged -> {
                KeyChangedDialog(
                    oldFingerprint = prompt.oldFingerprint,
                    entry = prompt.entry,
                    zh = zh,
                    onAccept = { viewModel.onHostKeyAccepted() },
                    onDisconnect = { viewModel.onHostKeyRejected() },
                )
            }
        }
    }

    deployingProfile?.let { profile ->
        DeployKeyDialog(
            profile = profile,
            keys = sshKeys,
            onDismiss = { deployingProfile = null },
            onDeploy = { keyId, password ->
                viewModel.deployKey(profile, keyId, password)
                deployingProfile = null
            },
        )
    }

    portForwardProfile?.let { profile ->
        val pfRulesFlow = remember(profile.id) { viewModel.portForwardRules(profile.id) }
        val pfRules by pfRulesFlow.collectAsState()
        val allSessions by viewModel.sessions.collectAsState()
        val activeForwards = allSessions.values
            .filter { it.profileId == profile.id }
            .flatMap { it.activeForwards }

        PortForwardDialog(
            profileLabel = profile.label,
            profileId = profile.id,
            rules = pfRules,
            activeForwards = activeForwards,
            onSave = { rule -> viewModel.savePortForwardRule(rule) },
            onDelete = { ruleId -> viewModel.deletePortForwardRule(ruleId, profile.id) },
            onDismiss = { portForwardProfile = null },
        )
    }

    sessionSelection?.let { selection ->
        SessionPickerDialog(
            managerLabel = selection.managerLabel,
            sessionNames = selection.sessionNames,
            canKill = selection.manager.killCommand != null,
            canRename = selection.manager.renameCommand != null,
            zh = zh,
            onSelect = { name -> viewModel.onSessionSelected(selection.sessionId, name) },
            onKill = { name -> viewModel.killRemoteSession(name) },
            onRename = { old, new -> viewModel.renameRemoteSession(old, new) },
            onNewSession = { viewModel.onSessionSelected(selection.sessionId, null) },
            onDismiss = { viewModel.dismissSessionPicker() },
        )
    }

    if (showMoshSetupGuide) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissMoshSetupGuide() },
            title = { Text(stringResource(R.string.mosh_not_found_on_server)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (zh) {
                            "远程主机未安装 mosh-server。Mosh（Mobile Shell）可以在网络切换和高延迟场景下保持会话不断开。"
                        } else {
                            "The remote host doesn't have mosh-server installed. " +
                                "Mosh (Mobile Shell) keeps your session alive across " +
                                "network changes and high-latency connections."
                        }
                    )
                    Text(stringResource(R.string.install_it_on_the_server))
                    Text(
                        "  Ubuntu/Debian:  sudo apt install mosh\n" +
                            "  Fedora/RHEL:    sudo dnf install mosh\n" +
                            "  Arch:           sudo pacman -S mosh\n" +
                            "  macOS:          brew install mosh",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(stringResource(R.string.udp_port_60001_must_be))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/mobile-shell/mosh")
                }) { Text(stringResource(R.string.mosh_on_github)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMoshSetupGuide() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    if (showMoshClientMissing) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissMoshClientMissing() },
            title = { Text(stringResource(R.string.mosh_client_not_built)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (zh) {
                            "当前构建不包含 mosh-client 二进制。需要使用 Android NDK 进行交叉编译。"
                        } else {
                            "The mosh-client binary is not included in this build. " +
                                "It needs to be cross-compiled for Android using the NDK."
                        }
                    )
                    Text(
                        if (zh) {
                            "请使用 Android NDK r27+ 运行 tools/build-mosh.sh 构建 mosh-client，然后重新编译应用。"
                        } else {
                            "Run tools/build-mosh.sh with Android NDK r27+ to build " +
                                "the mosh-client binary, then rebuild the app."
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    uriHandler.openUri("https://github.com/mobile-shell/mosh")
                }) { Text(stringResource(R.string.mosh_on_github)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMoshClientMissing() }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.connections)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_connection))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val quickConnectError = stringResource(R.string.use_format_user_host_or)
            // Quick connect bar
            OutlinedTextField(
                value = quickConnectText,
                onValueChange = { quickConnectText = it },
                placeholder = { Text(stringResource(R.string.user_host_or_host_port)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            quickConnectAction(
                                quickConnectText, viewModel, sshKeys, quickConnectError,
                                { connectingProfile = it },
                                { quickConnectText = "" },
                            )
                        },
                        enabled = quickConnectText.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Cable, contentDescription = stringResource(R.string.connect))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        quickConnectAction(
                            quickConnectText, viewModel, sshKeys, quickConnectError,
                            { connectingProfile = it },
                            { quickConnectText = "" },
                        )
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Linux VM card — shown when Terminal app is installed
            if (localVmStatus.terminalAppInstalled) {
                LinuxVmCard(
                    vmStatus = localVmStatus,
                    zh = zh,
                    onClick = { showVmSetup = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (connections.isEmpty()) {
                EmptyState(zh = zh)
            } else {
                // Build tree: top-level profiles first, then dependents nested beneath.
                // A profile is a child if it has a jumpProfileId (SSH multihop) or
                // rdpSshProfileId (RDP tunnelled through SSH).
                val profileMap = connections.associateBy { it.id }
                val dependentsByParent = connections
                    .mapNotNull { profile ->
                        val parentId = profile.jumpProfileId ?: profile.rdpSshProfileId
                        if (parentId != null && parentId in profileMap) parentId to profile else null
                    }
                    .groupBy({ it.first }, { it.second })
                val renderedAsChild = dependentsByParent.values.flatten().map { it.id }.toSet()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Top-level: no jump host, or jump host not in saved profiles (orphan)
                    val topLevel = connections.filter { it.id !in renderedAsChild }
                    topLevel.forEach { profile ->
                        item(key = profile.id) {
                            ConnectionTreeItem(
                                profile = profile,
                                indent = 0,
                                isLastChild = false,
                                profileStatuses = profileStatuses,
                                profileColors = profileColors,
                                isConnecting = connectingProfileId == profile.id,
                                hasKeys = sshKeys.isNotEmpty(),
                                hasDependents = profile.id in dependentsByParent,
                                jumpHostLabel = profile.jumpProfileId?.let { profileMap[it]?.label },
                                zh = zh,
                                onTap = { onTapProfile(profile, profileStatuses[profile.id], sshKeys, viewModel) { connectingProfile = profile } },
                                onRename = { newLabel -> viewModel.saveConnection(profile.copy(label = newLabel)) },
                                onEdit = { editingProfileId = profile.id },
                                onDelete = { viewModel.deleteConnection(profile.id) },
                                onDisconnect = { viewModel.disconnect(profile.id) },
                                onDeployKey = { deployingProfile = profile },
                                onConnectWithPassword = { connectingProfile = profile },
                                onPortForwards = { portForwardProfile = profile },
                                onNewSession = { viewModel.openNewSession(profile.id) },
                            )
                        }
                        val deps = dependentsByParent[profile.id].orEmpty()
                        deps.forEachIndexed { index, dep ->
                            item(key = dep.id) {
                                ConnectionTreeItem(
                                    profile = dep,
                                    indent = 1,
                                    isLastChild = index == deps.lastIndex,
                                    profileStatuses = profileStatuses,
                                    profileColors = profileColors,
                                    isConnecting = connectingProfileId == dep.id,
                                    hasKeys = sshKeys.isNotEmpty(),
                                    hasDependents = false,
                                    jumpHostLabel = null,
                                    zh = zh,
                                    onTap = { onTapProfile(dep, profileStatuses[dep.id], sshKeys, viewModel) { connectingProfile = dep } },
                                    onRename = { newLabel -> viewModel.saveConnection(dep.copy(label = newLabel)) },
                                    onEdit = { editingProfileId = dep.id },
                                    onDelete = { viewModel.deleteConnection(dep.id) },
                                    onDisconnect = { viewModel.disconnect(dep.id) },
                                    onDeployKey = { deployingProfile = dep },
                                    onConnectWithPassword = { connectingProfile = dep },
                                    onPortForwards = { portForwardProfile = dep },
                                    onNewSession = { viewModel.openNewSession(dep.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun quickConnectAction(
    input: String,
    viewModel: ConnectionsViewModel,
    sshKeys: List<sh.haven.core.data.db.entities.SshKey>,
    errorMessage: String,
    showPasswordDialog: (ConnectionProfile) -> Unit,
    clearInput: () -> Unit,
) {
    val profile = viewModel.parseQuickConnect(input)
    if (profile == null) {
        viewModel.showError(errorMessage)
        return
    }
    viewModel.saveConnection(profile)
    clearInput()
    if (sshKeys.isNotEmpty()) {
        viewModel.connectWithKey(profile)
    } else {
        showPasswordDialog(profile)
    }
}

private fun onTapProfile(
    profile: ConnectionProfile,
    profileStatus: ProfileStatus?,
    sshKeys: List<sh.haven.core.data.db.entities.SshKey>,
    viewModel: ConnectionsViewModel,
    showPasswordDialog: () -> Unit,
) {
    if (profileStatus == ProfileStatus.CONNECTED) {
        // ensureShellForProfile navigates via _navigateToTerminal when ready
        // (handles jump host sessions that need shell setup or session picker)
        viewModel.ensureShellForProfile(profile.id)
    } else if (profile.isVnc) {
        // VNC: connect directly (password stored in profile)
        viewModel.connect(profile, "")
    } else if (profile.isRdp) {
        val savedPassword = profile.rdpPassword
        if (savedPassword != null) {
            viewModel.connect(profile, savedPassword)
        } else {
            showPasswordDialog()
        }
    } else if (profile.isSmb) {
        val savedPassword = profile.smbPassword
        if (savedPassword != null) {
            viewModel.connect(profile, savedPassword)
        } else {
            showPasswordDialog()
        }
    } else if (profile.isReticulum) {
        viewModel.connect(profile, "")
    } else if (!profile.sshPassword.isNullOrBlank()) {
        viewModel.connect(profile, profile.sshPassword!!)
    } else if (sshKeys.isNotEmpty()) {
        viewModel.connectWithKey(profile)
    } else {
        showPasswordDialog()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionTreeItem(
    profile: ConnectionProfile,
    indent: Int,
    isLastChild: Boolean,
    profileStatuses: Map<String, ProfileStatus>,
    profileColors: Map<String, Color>,
    isConnecting: Boolean,
    hasKeys: Boolean,
    hasDependents: Boolean,
    jumpHostLabel: String?,
    zh: Boolean,
    onTap: () -> Unit,
    onRename: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDisconnect: () -> Unit,
    onDeployKey: () -> Unit,
    onConnectWithPassword: () -> Unit,
    onPortForwards: () -> Unit,
    onNewSession: () -> Unit,
) {
    val profileStatus = profileStatuses[profile.id]
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(androidx.compose.ui.unit.DpOffset.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    if (showRenameDialog) {
        RenameDialog(
            currentLabel = profile.label,
            zh = zh,
            onDismiss = { showRenameDialog = false },
            onRename = { newLabel ->
                onRename(newLabel)
                showRenameDialog = false
            },
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (indent > 0) {
                // Tree connector
                val lineColor = MaterialTheme.colorScheme.outlineVariant
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .width(24.dp)
                        .height(56.dp)
                        .padding(start = 12.dp),
                ) {
                    val midX = size.width / 2
                    val midY = size.height / 2
                    // Vertical line (half or full depending on position)
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(midX, 0f),
                        end = androidx.compose.ui.geometry.Offset(midX, if (isLastChild) midY else size.height),
                        strokeWidth = 2f,
                    )
                    // Horizontal branch
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(midX, midY),
                        end = androidx.compose.ui.geometry.Offset(size.width, midY),
                        strokeWidth = 2f,
                    )
                }
            }

            ListItem(
                headlineContent = { Text(profile.label) },
                supportingContent = {
                    if (profile.isReticulum) {
                        Text(
                            if (zh) {
                                "RNS：${profile.destinationHash?.take(12) ?: ""}...，经由 ${profile.reticulumHost}:${profile.reticulumPort}"
                            } else {
                                "RNS: ${profile.destinationHash?.take(12) ?: ""}... via ${profile.reticulumHost}:${profile.reticulumPort}"
                            }
                        )
                    } else {
                        val suffix = if (jumpHostLabel != null && indent == 0) {
                            stringResource(R.string.via_jumphostlabel, jumpHostLabel)
                        } else ""
                        Text("${profile.username}@${profile.host}:${profile.port}$suffix")
                    }
                },
                leadingContent = {
                    when {
                        isConnecting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        profileStatus == ProfileStatus.RECONNECTING -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        profileStatus == ProfileStatus.CONNECTED -> Icon(
                            Icons.Filled.Circle,
                            contentDescription = stringResource(R.string.connected),
                            tint = profileColors[profile.id] ?: Color(0xFF4CAF50),
                            modifier = Modifier.size(12.dp),
                        )
                        profileStatus == ProfileStatus.ERROR -> Icon(
                            Icons.Filled.Circle,
                            contentDescription = stringResource(R.string.error),
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(12.dp),
                        )
                        else -> Icon(
                            Icons.Filled.Circle,
                            contentDescription = stringResource(R.string.disconnected),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(profile.id) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onLongPress = { offset ->
                                pressOffset = androidx.compose.ui.unit.DpOffset(
                                    x = with(density) { offset.x.toDp() },
                                    y = with(density) { offset.y.toDp() }
                                )
                                showMenu = true
                            }
                        )
                    },
            )
        }

        if (showMenu) {
            val positionProvider = remember(pressOffset) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val touchX = anchorBounds.left + with(density) { pressOffset.x.roundToPx() }
                        val touchY = anchorBounds.top + with(density) { pressOffset.y.roundToPx() }
                        
                        // If touched on the left half, popup appears entirely to the right of the finger
                        // If touched on the right half, popup appears entirely to the left of the finger
                        val isLeftHalf = touchX < windowSize.width / 2
                        val targetX = if (isLeftHalf) touchX else touchX - popupContentSize.width
                        
                        // Add a slight vertical offset (e.g. 24dp) so the user's finger doesn't block the top edge
                        val yOffset = with(density) { 24.dp.roundToPx() }
                        val targetY = touchY + yOffset
                        
                        val x = targetX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                        val y = targetY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .wrapContentWidth()
                        .widthIn(min = 140.dp, max = 340.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                            .padding(vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PopupMenuRow(
                            text = stringResource(R.string.rename),
                            icon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                            onClick = { showMenu = false; showRenameDialog = true },
                        )
                        PopupMenuRow(
                            text = stringResource(R.string.edit),
                            icon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { showMenu = false; onEdit() },
                        )
                        if (profile.isSsh) {
                            PopupMenuRow(
                                text = stringResource(R.string.port_forwards),
                                icon = { Icon(Icons.Filled.SyncAlt, null) },
                                onClick = { showMenu = false; onPortForwards() },
                            )
                        }
                        if (profile.isSsh && profileStatus != ProfileStatus.CONNECTED) {
                            PopupMenuRow(
                                text = stringResource(R.string.connect),
                                icon = { Icon(Icons.Filled.Password, null) },
                                onClick = { showMenu = false; onConnectWithPassword() },
                            )
                        }
                        if (profile.isSsh && profileStatus == ProfileStatus.CONNECTED) {
                            PopupMenuRow(
                                text = stringResource(R.string.new_session),
                                icon = { Icon(Icons.Filled.Add, null) },
                                onClick = { showMenu = false; onNewSession() },
                            )
                        }
                        if (profileStatus == ProfileStatus.CONNECTED) {
                            PopupMenuRow(
                                text = stringResource(R.string.disconnect),
                                icon = { Icon(Icons.Filled.LinkOff, null) },
                                onClick = { showMenu = false; onDisconnect() },
                            )
                        }
                        if (profile.isSsh && hasKeys) {
                            PopupMenuRow(
                                text = stringResource(R.string.deploy_ssh_key),
                                icon = { Icon(Icons.Filled.VpnKey, null) },
                                onClick = { showMenu = false; onDeployKey() },
                            )
                        }
                        PopupMenuRow(
                            text = stringResource(R.string.delete),
                            icon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { showMenu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupMenuRow(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun EmptyState(zh: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Cable,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.no_connections_yet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.tap_to_add_a_server),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SessionPickerDialog(
    managerLabel: String,
    sessionNames: List<String>,
    zh: Boolean,
    canKill: Boolean = false,
    canRename: Boolean = false,
    onSelect: (String) -> Unit,
    onKill: (String) -> Unit = {},
    onRename: (old: String, new: String) -> Unit = { _, _ -> },
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renamingSession by remember { mutableStateOf<String?>(null) }

    renamingSession?.let { name ->
        RenameDialog(
            currentLabel = name,
            zh = zh,
            onDismiss = { renamingSession = null },
            onRename = { newName ->
                onRename(name, newName)
                renamingSession = null
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.managerlabel_sessions, managerLabel)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                sessionNames.forEach { name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = {
                            Row {
                                if (canRename) {
                                    IconButton(onClick = { renamingSession = name }) {
                                        Icon(
                                            Icons.Filled.DriveFileRenameOutline,
                                            contentDescription = stringResource(R.string.rename_session),
                                        )
                                    }
                                }
                                if (canKill) {
                                    IconButton(onClick = { onKill(name) }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.kill_session),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelect(name) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.new_session_1),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable { onNewSession() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RenameDialog(
    currentLabel: String,
    zh: Boolean,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var label by remember { mutableStateOf(currentLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_connection)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(label) },
                enabled = label.isNotBlank(),
            ) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LinuxVmCard(
    vmStatus: LocalVmStatus,
    zh: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasServices = vmStatus.sshPort != null || vmStatus.vncPort != null
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                Icons.Filled.Laptop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.linux_vm), style = MaterialTheme.typography.titleSmall)
                if (hasServices) {
                    val services = buildList {
                        vmStatus.sshPort?.let { add("SSH :$it") }
                        vmStatus.vncPort?.let { add("VNC :$it") }
                    }
                    Text(
                        if (zh) services.joinToString(" · ") + "（localhost）" else services.joinToString(" · ") + " on localhost",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.tap_to_set_up),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (hasServices) {
                Icon(
                    Icons.Filled.Circle,
                    contentDescription = stringResource(R.string.active),
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}
