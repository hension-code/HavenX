package com.hension.havenx

import android.content.Context
import java.io.File

/**
 * Guards the preview Activities (TextEditor / ImagePreview / PlayerPreview) against
 * path-traversal via the `FILE_PATH` and `STREAM_URL` Intent extras.
 *
 * These Activities are `exported=false`, so external apps cannot start them
 * directly today. The guards here are defence-in-depth: should any future code
 * path surface these extras from an untrusted source (PendingIntent, deep link,
 * DocumentsProvider indirection), an attacker must not gain arbitrary file
 * read/write on the device.
 *
 * Legitimate callers always pass a cache file under `context.cacheDir/sftp_preview`
 * (see [sh.haven.feature.sftp.SftpCacheManager]) and a loopback stream URL from
 * [sh.haven.feature.sftp.LocalMediaPreviewServer].
 */
internal object PreviewIntentGuard {

    /**
     * Returns `true` iff [filePath] resolves to a location inside this app's
     * cache directory. Canonicalizes both paths first so `../` escapes are
     * rejected. Returns `false` for null/blank input.
     */
    fun isCachePath(context: Context, filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val target = File(filePath).canonicalPath
            val cacheRoot = context.cacheDir.canonicalPath
            target == cacheRoot || target.startsWith(cacheRoot + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns `true` iff [streamUrl] is an HTTP URL pointing at loopback
     * (`localhost` or `127.0.0.1`). The local media preview server binds only
     * to 127.0.0.1, so any other host is illegitimate. Returns `false` for
     * null/blank input.
     */
    fun isLoopbackUrl(streamUrl: String?): Boolean {
        if (streamUrl.isNullOrBlank()) return false
        if (!streamUrl.startsWith("http://", ignoreCase = true)) return false
        val host = streamUrl.substringAfter("://", "").substringBefore('/', "").substringBefore(':')
        return host == "localhost" || host == "127.0.0.1"
    }
}
