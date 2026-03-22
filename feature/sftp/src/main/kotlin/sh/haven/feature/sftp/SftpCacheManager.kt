package sh.haven.feature.sftp

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object SftpCacheManager {
    private const val DIR_NAME = "sftp_preview"
    private const val MAX_CACHE_BYTES = 500L * 1024 * 1024 // 500MB

    fun getCacheFile(context: Context, remotePath: String): File {
        val dir = File(context.cacheDir, DIR_NAME).also { it.mkdirs() }
        val ext = remotePath.substringAfterLast('.', "")
        val name = remotePath.hashCode().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        return File(dir, name)
    }

    fun toContentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun trimCache(context: Context) {
        val dir = File(context.cacheDir, DIR_NAME)
        if (!dir.exists()) return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= f.length()
            f.delete()
        }
    }
}
