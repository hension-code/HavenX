package sh.haven.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.haven.core.data.backup.BackupService
import sh.haven.core.data.preferences.TerminalComboKey
import sh.haven.core.data.preferences.ToolbarLayout
import sh.haven.core.data.preferences.UserPreferencesRepository
import sh.haven.core.security.BiometricAuthenticator
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val authenticator: BiometricAuthenticator,
    private val backupService: BackupService,
) : ViewModel() {

    val biometricAvailable: Boolean =
        authenticator.checkAvailability(appContext) == BiometricAuthenticator.Availability.AVAILABLE

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    sealed interface BackupStatus {
        data object Idle : BackupStatus
        data object InProgress : BackupStatus
        data class Success(val message: String) : BackupStatus
        data class Error(val message: String) : BackupStatus
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress
            try {
                val data = backupService.export(password)
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    ?: throw IllegalStateException("Could not open output stream")
                _backupStatus.value = BackupStatus.Success("Backup exported")
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error(e.message ?: "Export failed")
            }
        }
    }

    fun importBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.InProgress
            try {
                val data = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not open input stream")
                val result = backupService.import(data, password)
                val msg = "Restored ${result.count} items" +
                    if (result.errors.isNotEmpty()) " (${result.errors.size} errors)" else ""
                _backupStatus.value = BackupStatus.Success(msg)
            } catch (e: javax.crypto.AEADBadTagException) {
                _backupStatus.value = BackupStatus.Error("Wrong password")
            } catch (e: Exception) {
                _backupStatus.value = BackupStatus.Error(e.message ?: "Import failed")
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    val biometricEnabled: StateFlow<Boolean> = preferencesRepository.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val terminalFontSize: StateFlow<Int> = preferencesRepository.terminalFontSize
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesRepository.DEFAULT_FONT_SIZE,
        )

    val terminalFont: StateFlow<UserPreferencesRepository.TerminalFont> =
        preferencesRepository.terminalFont
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.TerminalFont.SYSTEM,
            )

    val theme: StateFlow<UserPreferencesRepository.ThemeMode> = preferencesRepository.theme
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UserPreferencesRepository.ThemeMode.SYSTEM,
        )

    val languageMode: StateFlow<UserPreferencesRepository.LanguageMode> =
        preferencesRepository.languageMode
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.LanguageMode.SYSTEM,
            )

    val sessionManager: StateFlow<UserPreferencesRepository.SessionManager> =
        preferencesRepository.sessionManager
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.SessionManager.NONE,
            )

    val lockTimeout: StateFlow<UserPreferencesRepository.LockTimeout> =
        preferencesRepository.lockTimeout
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.LockTimeout.IMMEDIATE,
            )

    fun setLockTimeout(timeout: UserPreferencesRepository.LockTimeout) {
        viewModelScope.launch { preferencesRepository.setLockTimeout(timeout) }
    }

    val sessionCommandOverride: StateFlow<String?> = preferencesRepository.sessionCommandOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSessionCommandOverride(command: String?) {
        viewModelScope.launch {
            preferencesRepository.setSessionCommandOverride(command)
        }
    }

    val terminalColorScheme: StateFlow<UserPreferencesRepository.TerminalColorScheme> =
        preferencesRepository.terminalColorScheme
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                UserPreferencesRepository.TerminalColorScheme.HAVEN,
            )

    val toolbarLayout: StateFlow<ToolbarLayout> = preferencesRepository.toolbarLayout
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ToolbarLayout.DEFAULT,
        )

    val toolbarLayoutJson: StateFlow<String> = preferencesRepository.toolbarLayoutJson
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ToolbarLayout.DEFAULT.toJson(),
        )

    val comboKeys: StateFlow<List<TerminalComboKey>> =
        preferencesRepository.terminalComboKeys
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                sh.haven.core.data.preferences.TerminalComboKeys.PRESETS,
            )

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun setTerminalFontSize(sizeSp: Int) {
        viewModelScope.launch {
            preferencesRepository.setTerminalFontSize(sizeSp)
        }
    }

    fun setTerminalFont(font: UserPreferencesRepository.TerminalFont) {
        viewModelScope.launch {
            preferencesRepository.setTerminalFont(font)
        }
    }

    fun setTheme(mode: UserPreferencesRepository.ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setTheme(mode)
        }
    }

    fun setLanguageMode(mode: UserPreferencesRepository.LanguageMode) {
        viewModelScope.launch {
            preferencesRepository.setLanguageMode(mode)
        }
    }

    fun setSessionManager(manager: UserPreferencesRepository.SessionManager) {
        viewModelScope.launch {
            preferencesRepository.setSessionManager(manager)
        }
    }

    fun setTerminalColorScheme(scheme: UserPreferencesRepository.TerminalColorScheme) {
        viewModelScope.launch {
            preferencesRepository.setTerminalColorScheme(scheme)
        }
    }

    fun setToolbarLayout(layout: ToolbarLayout) {
        viewModelScope.launch {
            preferencesRepository.setToolbarLayout(layout)
        }
    }

    fun setToolbarLayoutJson(json: String) {
        viewModelScope.launch {
            preferencesRepository.setToolbarLayoutJson(json)
        }
    }

    fun setComboKeys(keys: List<TerminalComboKey>) {
        viewModelScope.launch {
            preferencesRepository.setTerminalComboKeys(keys)
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class Available(val versionName: String, val releaseNotes: String, val downloadUrl: String) : UpdateState
        data object UpToDate : UpdateState
        data class Error(val message: String) : UpdateState
    }

    fun checkUpdate(currentVersion: String) {
        if (_updateState.value is UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.github.com/repos/hension-code/HavenX/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    try {
                        val json = org.json.JSONObject(response)
                        val latestVersion = json.optString("tag_name", "").removePrefix("v")
                        val releaseNotes = json.optString("body", "")
                        val assets = json.optJSONArray("assets")
                        var downloadUrl = ""
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                if (asset.optString("name", "").endsWith(".apk")) {
                                    downloadUrl = asset.optString("browser_download_url", "")
                                    break
                                }
                            }
                        }
                        
                        if (latestVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                            val currentClean = currentVersion.removePrefix("v")
                            if (latestVersion != currentClean) {
                                _updateState.value = UpdateState.Available(latestVersion, releaseNotes, downloadUrl)
                            } else {
                                _updateState.value = UpdateState.UpToDate
                            }
                        } else {
                            _updateState.value = UpdateState.Error("No APK found in release")
                        }
                    } catch (e: Exception) {
                        _updateState.value = UpdateState.Error("Invalid response format")
                    }
                } else {
                    _updateState.value = UpdateState.Error("HTTP ${connection.responseCode}")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }
}
