package sh.haven.core.smb

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

private const val TAG = "SmbClient"

data class SmbFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val permissions: String,
)

class SmbClient : Closeable {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null

    val isConnected: Boolean
        get() = share != null && connection?.isConnected == true

    fun connect(
        host: String,
        port: Int,
        shareName: String,
        username: String,
        password: String,
        domain: String,
    ) {
        val smbClient = SMBClient()
        val conn = smbClient.connect(host, port)
        val auth = AuthenticationContext(username, password.toCharArray(), domain)
        val sess = conn.authenticate(auth)
        val diskShare = sess.connectShare(shareName) as DiskShare

        client = smbClient
        connection = conn
        session = sess
        share = diskShare
        Log.d(TAG, "Connected to \\\\$host\\$shareName as $username")
    }

    fun listDirectory(path: String): List<SmbFileEntry> {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(path)
        val entries = diskShare.list(smbPath)
        return entries
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info -> toSmbFileEntry(info, path) }
    }

    fun download(
        remotePath: String,
        output: OutputStream,
        onProgress: (transferred: Long, total: Long) -> Boolean,
    ) {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(remotePath)
        val file = diskShare.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        file.use { f ->
            val size = f.fileInformation.standardInformation.endOfFile
            val inputStream = f.inputStream
            val buffer = ByteArray(65536)
            var transferred = 0L
            onProgress(0, size)
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                transferred += read
                if (!onProgress(transferred, size)) break
            }
            output.flush()
        }
    }

    fun downloadRange(
        remotePath: String,
        start: Long,
        length: Long,
        output: OutputStream,
        onProgress: ((readBytes: Long) -> Boolean)? = null,
    ) {
        if (start < 0 || length < 0) throw IllegalArgumentException("Invalid range")
        if (length == 0L) return
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(remotePath)
        val file = diskShare.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        file.use { f ->
            val buffer = ByteArray(64 * 1024)
            var offset = start
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = f.read(buffer, offset, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                offset += read
                remaining -= read
                if (onProgress != null && !onProgress(read.toLong())) break
            }
            output.flush()
        }
    }

    fun upload(
        input: InputStream,
        remotePath: String,
        size: Long,
        onProgress: (transferred: Long, total: Long) -> Boolean,
    ) {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(remotePath)
        val file = diskShare.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        )
        file.use { f ->
            val outputStream = f.outputStream
            val buffer = ByteArray(65536)
            var transferred = 0L
            onProgress(0, size)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                transferred += read
                if (!onProgress(transferred, size)) break
            }
            outputStream.flush()
        }
    }

    fun delete(path: String, isDirectory: Boolean) {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(path)
        if (isDirectory) {
            diskShare.rmdir(smbPath, false)
        } else {
            diskShare.rm(smbPath)
        }
    }

    fun mkdir(path: String) {
        val diskShare = share ?: throw IllegalStateException("Not connected")
        val smbPath = toSmbPath(path)
        diskShare.mkdir(smbPath)
    }

    override fun close() {
        try { share?.close() } catch (_: Exception) {}
        try { session?.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        share = null
        session = null
        connection = null
        client = null
    }

    private fun toSmbPath(path: String): String {
        // SMB uses backslash paths; our UI uses forward slashes
        // Root is "" in smbj (not "/" or "\")
        val trimmed = path.trim('/')
        return trimmed.replace('/', '\\')
    }

    private fun toSmbFileEntry(
        info: FileIdBothDirectoryInformation,
        parentPath: String,
    ): SmbFileEntry {
        val isDir = info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
        val childPath = parentPath.trimEnd('/') + "/" + info.fileName
        return SmbFileEntry(
            name = info.fileName,
            path = childPath,
            isDirectory = isDir,
            size = info.endOfFile,
            modifiedTime = info.lastWriteTime.toEpochMillis() / 1000,
            permissions = if (isDir) "d" else "-",
        )
    }
}
