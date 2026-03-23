package sh.haven.feature.sftp

import android.net.Uri

data class SftpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val permissions: String,
)

enum class SortMode {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC
}

enum class TransferState { DOWNLOADING, PAUSED, CANCELLED, DONE, ERROR }

data class TransferTask(
    val id: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val state: TransferState,
    val isUpload: Boolean = false,
    val destPath: String? = null,
    val sourceUri: Uri? = null,
    val destinationUri: Uri? = null,
    val entry: SftpEntry? = null,
    val profileId: String,
    val isSmb: Boolean,
    val error: String? = null
) {
    val fraction: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

data class TransferProgress(
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}
