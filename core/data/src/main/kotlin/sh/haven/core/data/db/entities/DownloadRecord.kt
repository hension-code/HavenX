package sh.haven.core.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent record of a file transfer (download or upload).
 *
 * The same row is reused across the transfer lifecycle: it is created when a
 * [sh.haven.feature.sftp.TransferTask] is added, updated as progress/state
 * changes, and retained after completion as history. The [id] matches the
 * in-memory `TransferTask.id` (UUID) so the two stay in sync.
 *
 * Only downloads are truly resumable across app restarts: a partial local file
 * survives on disk and [transferredBytes] is persisted, so the engine can
 * resume from the breakpoint (SFTP `RESUME` / SMB `downloadRange`). Uploads
 * are recorded for completeness but restart from scratch on resume.
 */
@Entity(
    tableName = "download_records",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId"), Index("updatedAt")],
)
data class DownloadRecord(
    @PrimaryKey
    val id: String,
    val profileId: String,
    val fileName: String,
    /** Remote-side path: the downloaded file's remote path, or the upload destination path. */
    val remotePath: String,
    /** Local-side URI as a string: destinationUri for downloads, sourceUri for uploads. */
    val localUri: String?,
    val totalBytes: Long,
    val transferredBytes: Long,
    val isUpload: Boolean,
    val isSmb: Boolean,
    /** [sh.haven.feature.sftp.TransferState] name. */
    val state: String,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
