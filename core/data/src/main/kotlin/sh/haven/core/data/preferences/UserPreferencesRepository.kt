package sh.haven.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val biometricEnabledKey = booleanPreferencesKey("biometric_enabled")
    private val terminalFontSizeKey = intPreferencesKey("terminal_font_size")
    private val terminalFontKey = stringPreferencesKey("terminal_font")
    private val themeKey = stringPreferencesKey("theme")
    private val sessionManagerKey = stringPreferencesKey("session_manager")
    private val reticulumRpcKeyKey = stringPreferencesKey("reticulum_rpc_key")
    private val reticulumHostKey = stringPreferencesKey("reticulum_host")
    private val reticulumPortKey = intPreferencesKey("reticulum_port")
    private val terminalColorSchemeKey = stringPreferencesKey("terminal_color_scheme")
    private val toolbarRowsKey = intPreferencesKey("toolbar_rows") // legacy
    private val toolbarRow1Key = stringPreferencesKey("toolbar_row1") // legacy
    private val toolbarRow2Key = stringPreferencesKey("toolbar_row2") // legacy
    private val toolbarLayoutKey = stringPreferencesKey("toolbar_layout")
    private val terminalComboKeysKey = stringPreferencesKey("terminal_combo_keys")
    private val sessionCommandOverrideKey = stringPreferencesKey("session_command_override")
    private val sftpSortModeKey = stringPreferencesKey("sftp_sort_mode")
    private val sftpShowHiddenKey = booleanPreferencesKey("sftp_show_hidden")
    private val sftpLastPathsKey = stringSetPreferencesKey("sftp_last_paths")
    private val sftpFavoriteDirsKey = stringSetPreferencesKey("sftp_favorite_dirs")
    private val lockTimeoutKey = stringPreferencesKey("lock_timeout")
    private val languageModeKey = stringPreferencesKey("language_mode")

    val biometricEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[biometricEnabledKey] ?: false
    }

    val terminalFontSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[terminalFontSizeKey] ?: DEFAULT_FONT_SIZE
    }

    val terminalFont: Flow<TerminalFont> = dataStore.data.map { prefs ->
        TerminalFont.fromString(prefs[terminalFontKey])
    }

    val sessionManager: Flow<SessionManager> = dataStore.data.map { prefs ->
        SessionManager.fromString(prefs[sessionManagerKey])
    }

    suspend fun setSessionManager(manager: SessionManager) {
        dataStore.edit { prefs ->
            prefs[sessionManagerKey] = manager.name
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[biometricEnabledKey] = enabled
        }
    }

    val theme: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromString(prefs[themeKey])
    }

    val languageMode: Flow<LanguageMode> = dataStore.data.map { prefs ->
        LanguageMode.fromString(prefs[languageModeKey])
    }

    suspend fun setTerminalFontSize(sizeSp: Int) {
        dataStore.edit { prefs ->
            prefs[terminalFontSizeKey] = sizeSp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        }
    }

    suspend fun setTerminalFont(font: TerminalFont) {
        dataStore.edit { prefs ->
            prefs[terminalFontKey] = font.name
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        dataStore.edit { prefs ->
            prefs[languageModeKey] = mode.name
        }
    }

    val reticulumRpcKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[reticulumRpcKeyKey]
    }

    val reticulumHost: Flow<String> = dataStore.data.map { prefs ->
        prefs[reticulumHostKey] ?: DEFAULT_RETICULUM_HOST
    }

    val reticulumPort: Flow<Int> = dataStore.data.map { prefs ->
        prefs[reticulumPortKey] ?: DEFAULT_RETICULUM_PORT
    }

    val reticulumConfigured: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[reticulumRpcKeyKey] != null
    }

    suspend fun setReticulumConfig(rpcKey: String, host: String, port: Int) {
        dataStore.edit { prefs ->
            prefs[reticulumRpcKeyKey] = rpcKey
            prefs[reticulumHostKey] = host
            prefs[reticulumPortKey] = port
        }
    }

    suspend fun clearReticulumConfig() {
        dataStore.edit { prefs ->
            prefs.remove(reticulumRpcKeyKey)
        }
    }

    /**
     * Toolbar layout as a [ToolbarLayout]. Migrates from legacy row1/row2
     * comma-separated format on first read if needed.
     */
    val toolbarLayout: Flow<ToolbarLayout> = dataStore.data.map { prefs ->
        val json = prefs[toolbarLayoutKey]
        if (json != null) {
            ToolbarLayout.fromJson(json)
        } else {
            // Migrate from legacy formats
            val row1 = prefs[toolbarRow1Key]
            val row2 = prefs[toolbarRow2Key]
            if (row1 != null || row2 != null) {
                ToolbarLayout.fromLegacy(
                    row1 ?: DEFAULT_TOOLBAR_ROW1,
                    row2 ?: DEFAULT_TOOLBAR_ROW2,
                )
            } else {
                ToolbarLayout.DEFAULT
            }
        }
    }

    val toolbarLayoutJson: Flow<String> = dataStore.data.map { prefs ->
        prefs[toolbarLayoutKey] ?: ToolbarLayout.DEFAULT.toJson()
    }

    suspend fun setToolbarLayout(layout: ToolbarLayout) {
        dataStore.edit { prefs ->
            prefs[toolbarLayoutKey] = layout.toJson()
            // Clear legacy keys
            prefs.remove(toolbarRow1Key)
            prefs.remove(toolbarRow2Key)
            prefs.remove(toolbarRowsKey)
        }
    }

    suspend fun setToolbarLayoutJson(json: String) {
        dataStore.edit { prefs ->
            prefs[toolbarLayoutKey] = json
            prefs.remove(toolbarRow1Key)
            prefs.remove(toolbarRow2Key)
            prefs.remove(toolbarRowsKey)
        }
    }

    val terminalComboKeys: Flow<List<TerminalComboKey>> = dataStore.data.map { prefs ->
        val json = prefs[terminalComboKeysKey]
        if (json == null) TerminalComboKeys.PRESETS else TerminalComboKeys.fromJson(json)
    }

    suspend fun setTerminalComboKeys(keys: List<TerminalComboKey>) {
        dataStore.edit { prefs ->
            prefs[terminalComboKeysKey] = TerminalComboKeys.toJson(keys)
        }
    }

    /**
     * User override for the session manager command template.
     * If non-null, replaces the built-in command. Use {name} for session name.
     */
    val sessionCommandOverride: Flow<String?> = dataStore.data.map { prefs ->
        prefs[sessionCommandOverrideKey]
    }

    suspend fun setSessionCommandOverride(command: String?) {
        dataStore.edit { prefs ->
            if (command.isNullOrBlank()) {
                prefs.remove(sessionCommandOverrideKey)
            } else {
                prefs[sessionCommandOverrideKey] = command
            }
        }
    }

    val sftpSortMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[sftpSortModeKey] ?: "NAME_ASC"
    }

    suspend fun setSftpSortMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[sftpSortModeKey] = mode
        }
    }

    val sftpShowHidden: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[sftpShowHiddenKey] ?: false
    }

    suspend fun setSftpShowHidden(showHidden: Boolean) {
        dataStore.edit { prefs ->
            prefs[sftpShowHiddenKey] = showHidden
        }
    }

    suspend fun getSftpLastPath(profileId: String): String? {
        val entries = dataStore.data.map { it[sftpLastPathsKey].orEmpty() }.first()
        return decodeScopedEntries(entries, profileId).firstOrNull()
    }

    suspend fun setSftpLastPath(profileId: String, path: String) {
        dataStore.edit { prefs ->
            val existing = prefs[sftpLastPathsKey].orEmpty()
            val updated = existing
                .filterNot { decodeScopedEntry(it)?.first == profileId }
                .toMutableSet()
            updated += encodeScopedEntry(profileId, path)
            prefs[sftpLastPathsKey] = updated
        }
    }

    fun sftpFavoriteDirs(profileId: String): Flow<Set<String>> = dataStore.data.map { prefs ->
        decodeScopedEntries(prefs[sftpFavoriteDirsKey].orEmpty(), profileId).toSet()
    }

    suspend fun addSftpFavoriteDir(profileId: String, path: String) {
        dataStore.edit { prefs ->
            val existing = prefs[sftpFavoriteDirsKey].orEmpty().toMutableSet()
            existing += encodeScopedEntry(profileId, path)
            prefs[sftpFavoriteDirsKey] = existing
        }
    }

    suspend fun removeSftpFavoriteDir(profileId: String, path: String) {
        dataStore.edit { prefs ->
            val existing = prefs[sftpFavoriteDirsKey].orEmpty()
            prefs[sftpFavoriteDirsKey] = existing
                .filterNot {
                    val pair = decodeScopedEntry(it)
                    pair?.first == profileId && pair.second == path
                }
                .toSet()
        }
    }

    private fun encodeScopedEntry(profileId: String, value: String): String {
        return "$profileId$SCOPED_ENTRY_SEPARATOR$value"
    }

    private fun decodeScopedEntry(raw: String): Pair<String, String>? {
        val idx = raw.indexOf(SCOPED_ENTRY_SEPARATOR)
        if (idx <= 0 || idx >= raw.length - 1) return null
        return raw.substring(0, idx) to raw.substring(idx + 1)
    }

    private fun decodeScopedEntries(rawEntries: Set<String>, profileId: String): List<String> {
        return rawEntries.mapNotNull { raw ->
            val pair = decodeScopedEntry(raw) ?: return@mapNotNull null
            if (pair.first == profileId) pair.second else null
        }
    }

    val terminalColorScheme: Flow<TerminalColorScheme> = dataStore.data.map { prefs ->
        TerminalColorScheme.fromString(prefs[terminalColorSchemeKey])
    }

    suspend fun setTerminalColorScheme(scheme: TerminalColorScheme) {
        dataStore.edit { prefs ->
            prefs[terminalColorSchemeKey] = scheme.name
        }
    }

    enum class TerminalColorScheme(
        val label: String,
        val background: Long,
        val foreground: Long,
    ) {
        HAVEN("Haven", 0xFF1A1A2E, 0xFF00E676),
        CLASSIC_GREEN("Classic Green", 0xFF000000, 0xFF00FF00),
        LIGHT("Light", 0xFFFFFFFF, 0xFF1A1A1A),
        SOLARIZED_DARK("Solarized Dark", 0xFF002B36, 0xFF839496),
        DRACULA("Dracula", 0xFF282A36, 0xFFF8F8F2),
        MONOKAI("Monokai", 0xFF272822, 0xFFF8F8F2);

        companion object {
            fun fromString(value: String?): TerminalColorScheme =
                entries.find { it.name == value } ?: HAVEN
        }
    }

    enum class TerminalFont(val label: String) {
        SYSTEM("System default"),
        FIRA_CODE("Fira Code"),
        JETBRAINS_MONO("JetBrains Mono");

        companion object {
            fun fromString(value: String?): TerminalFont =
                entries.find { it.name == value } ?: SYSTEM
        }
    }

    val lockTimeout: Flow<LockTimeout> = dataStore.data.map { prefs ->
        LockTimeout.fromString(prefs[lockTimeoutKey])
    }

    suspend fun setLockTimeout(timeout: LockTimeout) {
        dataStore.edit { prefs ->
            prefs[lockTimeoutKey] = timeout.name
        }
    }

    enum class LockTimeout(val label: String, val seconds: Long) {
        IMMEDIATE("Immediately", 0),
        THIRTY_SECONDS("30 seconds", 30),
        ONE_MINUTE("1 minute", 60),
        FIVE_MINUTES("5 minutes", 300),
        NEVER("Never", Long.MAX_VALUE);

        companion object {
            fun fromString(value: String?): LockTimeout =
                entries.find { it.name == value } ?: IMMEDIATE
        }
    }

    enum class ThemeMode(val label: String) {
        SYSTEM("System default"),
        LIGHT("Light"),
        DARK("Dark");

        companion object {
            fun fromString(value: String?): ThemeMode =
                entries.find { it.name == value } ?: SYSTEM
        }
    }

    enum class LanguageMode(val tag: String?) {
        SYSTEM(null),
        ENGLISH("en"),
        CHINESE_SIMPLIFIED("zh-Hans");

        companion object {
            fun fromString(value: String?): LanguageMode =
                entries.find { it.name == value } ?: SYSTEM
        }
    }

    enum class SessionManager(
        val label: String,
        val url: String?,
        val command: ((String) -> String)?,
        val supportsScrollback: Boolean = true,
    ) {
        NONE("None", null, null, supportsScrollback = false),
        TMUX("tmux", "https://github.com/tmux/tmux/wiki", { name -> "tmux new-session -A -s $name \\; set -gq allow-passthrough on \\; set -gq mouse on" }),
        ZELLIJ("zellij", "https://zellij.dev", { name -> "zellij attach $name --create" }),
        SCREEN("screen", "https://www.gnu.org/software/screen/", { name -> "screen -dRR $name" }, supportsScrollback = false),
        BYOBU("byobu", "https://www.byobu.org", { name -> "byobu new-session -A -s $name \\; set -gq mouse on" });

        companion object {
            fun fromString(value: String?): SessionManager =
                entries.find { it.name == value } ?: NONE
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 14
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 32
        const val DEFAULT_TOOLBAR_ROWS = 2 // legacy
        const val DEFAULT_RETICULUM_HOST = "127.0.0.1"
        const val DEFAULT_RETICULUM_PORT = 37428
        const val DEFAULT_TOOLBAR_ROW1 = "keyboard,esc,tab,shift,ctrl,alt" // legacy
        const val DEFAULT_TOOLBAR_ROW2 = "arrow_left,arrow_up,arrow_down,arrow_right,sym_pipe,sym_tilde,sym_slash,sym_backslash,sym_backtick" // legacy
        private const val SCOPED_ENTRY_SEPARATOR = '\u001F'
    }
}
