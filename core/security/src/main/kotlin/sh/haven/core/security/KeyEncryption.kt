package sh.haven.core.security

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Encrypts/decrypts SSH private key bytes using Tink AEAD backed by Android Keystore.
 *
 * Keys are encrypted with AES-256-GCM. The master key is stored in Android Keystore
 * (hardware-backed on devices with a secure element). This protects key material
 * at rest — even if the database is extracted, keys cannot be read without the
 * device's Keystore.
 */
object KeyEncryption {

    private const val KEYSET_NAME = "haven_ssh_key_keyset"
    private const val PREFERENCE_FILE = "haven_ssh_key_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://haven_ssh_key_master"

    // Associated data for AEAD — prevents ciphertext from being used in a different context
    private val ASSOCIATED_DATA = "haven-ssh-private-key".toByteArray()

    // Separate associated data for connection passwords — a key ciphertext cannot be
    // decrypted as a password (and vice versa) even though both share the master keyset.
    private val PASSWORD_ASSOCIATED_DATA = "haven-connection-password".toByteArray()

    // Prefix marking a stored string as Tink-encrypted. Legacy plaintext values (stored
    // before password encryption was introduced) lack this prefix and are returned as-is,
    // then re-encrypted on the next save — a lazy migration matching the SshKey approach.
    private const val ENCRYPTED_STRING_PREFIX = "ENC$"

    @Volatile
    private var aead: Aead? = null

    private fun getAead(context: Context): Aead {
        aead?.let { return it }
        synchronized(this) {
            aead?.let { return it }
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREFERENCE_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            return keysetHandle.getPrimitive(Aead::class.java).also { aead = it }
        }
    }

    /** Encrypt private key bytes. Returns ciphertext that can only be decrypted on this device. */
    fun encrypt(context: Context, plaintext: ByteArray): ByteArray {
        return getAead(context).encrypt(plaintext, ASSOCIATED_DATA)
    }

    /** Decrypt private key bytes. Throws GeneralSecurityException if tampered or wrong device. */
    fun decrypt(context: Context, ciphertext: ByteArray): ByteArray {
        return getAead(context).decrypt(ciphertext, ASSOCIATED_DATA)
    }

    /**
     * Check if bytes look like they're already encrypted (Tink ciphertext).
     * Tink AEAD ciphertext starts with a version byte (0x01) followed by a 4-byte key ID.
     * Plain PEM/OpenSSH keys start with '-' (0x2D) or raw DER starts with 0x30.
     */
    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        // Plain key formats start with '-' (PEM) or 0x30 (DER SEQUENCE)
        val first = bytes[0]
        return first != '-'.code.toByte() && first != 0x30.toByte()
    }

    /**
     * Encrypt a connection password string for storage. Returns null for null input.
     * The result is [ENCRYPTED_STRING_PREFIX] + Base64(Tink ciphertext) and should be
     * stored in the password column as-is.
     */
    fun encryptString(context: Context, plaintext: String?): String? {
        if (plaintext == null) return null
        val ciphertext = getAead(context).encrypt(plaintext.toByteArray(Charsets.UTF_8), PASSWORD_ASSOCIATED_DATA)
        return ENCRYPTED_STRING_PREFIX + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypt a stored connection password. Returns null for null input.
     * Values without [ENCRYPTED_STRING_PREFIX] are treated as legacy plaintext and
     * returned unchanged (they get re-encrypted on the next save).
     */
    fun decryptString(context: Context, stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(ENCRYPTED_STRING_PREFIX)) return stored
        val ciphertext = Base64.decode(stored.removePrefix(ENCRYPTED_STRING_PREFIX), Base64.NO_WRAP)
        return String(getAead(context).decrypt(ciphertext, PASSWORD_ASSOCIATED_DATA), Charsets.UTF_8)
    }
}
