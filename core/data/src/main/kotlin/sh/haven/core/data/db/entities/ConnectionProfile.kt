package sh.haven.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "connection_profiles")
data class ConnectionProfile(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val sshPassword: String? = null,
    val authType: AuthType = AuthType.PASSWORD,
    val keyId: String? = null,
    val colorTag: Int = 0,
    val lastConnected: Long? = null,
    val sortOrder: Int = 0,
    val connectionType: String = "SSH",
    val destinationHash: String? = null,
    val reticulumHost: String = "127.0.0.1",
    val reticulumPort: Int = 37428,
    val jumpProfileId: String? = null,
    val sshOptions: String? = null,
    val vncPort: Int? = null,
    val vncPassword: String? = null,
    val vncSshForward: Boolean = true,
    val sessionManager: String? = null,
    val useMosh: Boolean = false,
    val useEternalTerminal: Boolean = false,
    val etPort: Int = 2022,
    val rdpPort: Int = 3389,
    val rdpUsername: String? = null,
    val rdpDomain: String? = null,
    val rdpPassword: String? = null,
    val rdpSshForward: Boolean = false,
    val rdpSshProfileId: String? = null,
    val smbPort: Int = 445,
    val smbShare: String? = null,
    val smbDomain: String? = null,
    val smbPassword: String? = null,
    val smbSshForward: Boolean = false,
    val smbSshProfileId: String? = null,
    /**
     * Require SMB 3.0+ encryption for direct (non-tunneled) connections.
     * When true, refuse to connect if the server does not support encryption
     * (prevents a silent downgrade to plaintext). Default false for
     * compatibility with SMB 1/2.x servers and SMB 3.x servers with
     * encryption disabled. Ignored when [smbSshForward] is true (the SSH
     * tunnel secures the connection).
     */
    val smbRequireEncryption: Boolean = false,
) {
    enum class AuthType {
        PASSWORD,
        KEY,
    }

    val isSsh: Boolean get() = connectionType == "SSH"
    val isReticulum: Boolean get() = connectionType == "RETICULUM"
    val isMosh: Boolean get() = isSsh && useMosh
    val isEternalTerminal: Boolean get() = isSsh && useEternalTerminal
    val isVnc: Boolean get() = connectionType == "VNC"
    val isRdp: Boolean get() = connectionType == "RDP"
    val isSmb: Boolean get() = connectionType == "SMB"
    val isDesktop: Boolean get() = isVnc || isRdp
    val isTerminal: Boolean get() = !isDesktop && !isSmb
}
