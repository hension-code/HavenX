package sh.haven.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sh.haven.core.data.db.ConnectionDao
import sh.haven.core.data.db.entities.ConnectionProfile
import sh.haven.core.security.KeyEncryption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for [ConnectionProfile] that transparently encrypts/decrypts the four
 * password fields (sshPassword, vncPassword, rdpPassword, smbPassword) at the
 * repository boundary.
 *
 * Callers always see plaintext passwords; the database stores Tink-encrypted
 * ciphertext (see [KeyEncryption]). Legacy plaintext values written before this
 * layer was added are returned as-is and re-encrypted on the next save.
 */
@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
    @ApplicationContext private val context: Context,
) {
    fun observeAll(): Flow<List<ConnectionProfile>> =
        connectionDao.observeAll().map { list -> list.map(::decryptProfile) }

    suspend fun getAll(): List<ConnectionProfile> =
        connectionDao.getAll().map(::decryptProfile)

    suspend fun getById(id: String): ConnectionProfile? =
        connectionDao.getById(id)?.let(::decryptProfile)

    /** Save a profile, encrypting password fields at rest. */
    suspend fun save(profile: ConnectionProfile) =
        connectionDao.upsert(encryptProfile(profile))

    suspend fun delete(id: String) = connectionDao.deleteById(id)

    suspend fun markConnected(id: String) = connectionDao.updateLastConnected(id)

    /** Update VNC settings, encrypting the password before it reaches the DB. */
    suspend fun saveVncSettings(id: String, port: Int, password: String?, sshForward: Boolean) =
        connectionDao.updateVncSettings(id, port, KeyEncryption.encryptString(context, password), sshForward)

    private fun encryptProfile(profile: ConnectionProfile): ConnectionProfile = profile.copy(
        sshPassword = KeyEncryption.encryptString(context, profile.sshPassword),
        vncPassword = KeyEncryption.encryptString(context, profile.vncPassword),
        rdpPassword = KeyEncryption.encryptString(context, profile.rdpPassword),
        smbPassword = KeyEncryption.encryptString(context, profile.smbPassword),
    )

    private fun decryptProfile(profile: ConnectionProfile): ConnectionProfile = profile.copy(
        sshPassword = KeyEncryption.decryptString(context, profile.sshPassword),
        vncPassword = KeyEncryption.decryptString(context, profile.vncPassword),
        rdpPassword = KeyEncryption.decryptString(context, profile.rdpPassword),
        smbPassword = KeyEncryption.decryptString(context, profile.smbPassword),
    )
}
