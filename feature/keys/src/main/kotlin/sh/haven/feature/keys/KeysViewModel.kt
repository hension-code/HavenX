package sh.haven.feature.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.haven.core.data.db.entities.SshKey
import sh.haven.core.data.repository.SshKeyRepository
import sh.haven.core.security.SshKeyGenerator
import sh.haven.core.ssh.SshKeyImporter
import javax.inject.Inject

@HiltViewModel
class KeysViewModel @Inject constructor(
    private val repository: SshKeyRepository,
) : ViewModel() {

    val keys: StateFlow<List<SshKey>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Import flow state
    private val _importResult = MutableStateFlow<SshKeyImporter.ImportedKey?>(null)
    val importResult: StateFlow<SshKeyImporter.ImportedKey?> = _importResult.asStateFlow()

    private val _needsPassphrase = MutableStateFlow(false)
    val needsPassphrase: StateFlow<Boolean> = _needsPassphrase.asStateFlow()

    private var pendingImportBytes: ByteArray? = null

    fun generateKey(label: String, keyType: SshKeyGenerator.KeyType) {
        viewModelScope.launch {
            _generating.value = true
            _error.value = null
            try {
                val generated = withContext(Dispatchers.Default) {
                    SshKeyGenerator.generate(keyType, label)
                }
                val entity = SshKey(
                    label = label,
                    keyType = generated.type.sshName,
                    privateKeyBytes = generated.privateKeyBytes,
                    publicKeyOpenSsh = generated.publicKeyOpenSsh,
                    fingerprintSha256 = generated.fingerprintSha256,
                )
                repository.save(entity)
            } catch (e: Exception) {
                _error.value = e.message ?: "Key generation failed"
            } finally {
                _generating.value = false
            }
        }
    }

    fun startImport(fileBytes: ByteArray) {
        viewModelScope.launch {
            _generating.value = true
            _error.value = null
            try {
                val imported = withContext(Dispatchers.Default) {
                    SshKeyImporter.import(fileBytes)
                }
                _importResult.value = imported
            } catch (_: SshKeyImporter.EncryptedKeyException) {
                pendingImportBytes = fileBytes
                _needsPassphrase.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to read key file"
            } finally {
                _generating.value = false
            }
        }
    }

    fun retryImportWithPassphrase(passphrase: String) {
        val bytes = pendingImportBytes ?: return
        _needsPassphrase.value = false
        viewModelScope.launch {
            _generating.value = true
            _error.value = null
            try {
                val imported = withContext(Dispatchers.Default) {
                    SshKeyImporter.import(bytes, passphrase)
                }
                _importResult.value = imported
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to decrypt key"
            } finally {
                _generating.value = false
            }
        }
    }

    fun saveImportedKey(label: String) {
        val imported = _importResult.value ?: return
        _importResult.value = null
        pendingImportBytes = null
        viewModelScope.launch {
            try {
                val entity = SshKey(
                    label = label,
                    keyType = imported.keyType,
                    privateKeyBytes = imported.privateKeyBytes,
                    publicKeyOpenSsh = imported.publicKeyOpenSsh,
                    fingerprintSha256 = imported.fingerprintSha256,
                )
                repository.save(entity)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save key"
            }
        }
    }

    fun cancelImport() {
        _importResult.value = null
        _needsPassphrase.value = false
        pendingImportBytes = null
    }

    fun deleteKey(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
