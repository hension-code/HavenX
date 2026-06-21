package sh.haven.core.vnc.protocol

import sh.haven.core.vnc.VncSession
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Performs the VNC handshake: protocol version negotiation, security type
 * negotiation, and authentication.
 */
object Handshaker {

    fun handshake(session: VncSession) {
        negotiateProtocolVersion(session)
        negotiateSecurityType(session)
    }

    private fun negotiateProtocolVersion(session: VncSession) {
        val serverVersion = ProtocolVersion.decode(session.inputStream)
        if (serverVersion.major < 3 || (serverVersion.major == 3 && serverVersion.minor < 3)) {
            throw HandshakingFailedException("Server version too old: $serverVersion")
        }
        val clientVersion = ProtocolVersion(
            major = minOf(serverVersion.major, 3),
            minor = minOf(serverVersion.minor, 8),
        )
        clientVersion.encode(session.outputStream)
        session.protocolVersion = clientVersion
    }

    private fun negotiateSecurityType(session: VncSession) {
        val version = session.protocolVersion!!
        val d = DataInputStream(session.inputStream)

        if (version.atLeast(3, 7)) {
            // 3.7+: server sends list of supported types
            val count = d.readUnsignedByte()
            if (count == 0) {
                val errLen = d.readInt()
                val errBytes = ByteArray(errLen)
                d.readFully(errBytes)
                throw HandshakingFailedException(String(errBytes, Charsets.US_ASCII))
            }
            val types = (0 until count).map { d.readUnsignedByte() }

            when {
                2 in types -> {
                    // VNC authentication
                    session.outputStream.write(2)
                    session.outputStream.flush()
                    authenticateVnc(session)
                }
                1 in types -> {
                    // No authentication — refuse when the caller requires auth
                    // (a password was configured for a direct connection).
                    if (session.config.requireAuth) throw noAuthRefused()
                    session.outputStream.write(1)
                    session.outputStream.flush()
                }
                else -> throw HandshakingFailedException("No supported security types: $types")
            }

            if (version.atLeast(3, 8)) {
                readSecurityResult(session)
            }
        } else {
            // 3.3: server selects a single type
            val type = d.readInt()
            when (type) {
                0 -> {
                    val errLen = d.readInt()
                    val errBytes = ByteArray(errLen)
                    d.readFully(errBytes)
                    throw HandshakingFailedException(String(errBytes, Charsets.US_ASCII))
                }
                1 -> {
                    // No authentication — refuse when the caller requires auth.
                    if (session.config.requireAuth) throw noAuthRefused()
                }
                2 -> authenticateVnc(session)
                else -> throw HandshakingFailedException("Unsupported security type: $type")
            }
        }
    }

    private fun authenticateVnc(session: VncSession) {
        val d = DataInputStream(session.inputStream)
        val challenge = ByteArray(16)
        d.readFully(challenge)

        val password = session.config.passwordSupplier?.invoke()
            ?: throw AuthenticationFailedException("Password required")

        val keyBytes = ByteArray(8)
        val pwBytes = password.toByteArray(Charsets.US_ASCII)
        System.arraycopy(pwBytes, 0, keyBytes, 0, minOf(pwBytes.size, 8))

        // VNC reverses bits in each byte of the key
        for (i in keyBytes.indices) {
            keyBytes[i] = reverseBits(keyBytes[i])
        }

        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "DES"))
        val response = cipher.doFinal(challenge)

        session.outputStream.write(response)
        session.outputStream.flush()

        if (!session.protocolVersion!!.atLeast(3, 8)) {
            readSecurityResult(session)
        }
    }

    private fun readSecurityResult(session: VncSession) {
        val d = DataInputStream(session.inputStream)
        val result = d.readInt()
        if (result != 0) {
            val errMsg = if (session.protocolVersion!!.atLeast(3, 8)) {
                val len = d.readInt()
                val bytes = ByteArray(len)
                d.readFully(bytes)
                String(bytes, Charsets.US_ASCII)
            } else {
                "Authentication failed"
            }
            throw AuthenticationFailedException(errMsg)
        }
    }

    /**
     * Exception raised when the server offers "no authentication" (RFB security
     * type 1) but the caller required auth (a password was configured for a
     * direct, non-tunneled connection). Silently accepting would let a MitM
     * strip authentication or a misconfigured server bypass the user's password.
     */
    private fun noAuthRefused() = HandshakingFailedException(
        "Server offered no authentication (security type 1) but a password was configured. " +
            "Refusing to connect without authentication — this may indicate a downgrade attack " +
            "or a misconfigured server."
    )

    private fun reverseBits(b: Byte): Byte {        var v = b.toInt() and 0xFF
        var result = 0
        for (i in 0 until 8) {
            result = (result shl 1) or (v and 1)
            v = v shr 1
        }
        return result.toByte()
    }
}
