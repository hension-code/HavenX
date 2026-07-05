package sh.haven.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import sh.haven.core.data.db.entities.DownloadRecord

@Dao
interface DownloadRecordDao {

    /** All records, newest first — drives the history list in the UI. */
    @Query("SELECT * FROM download_records ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM download_records WHERE id = :id")
    suspend fun getById(id: String): DownloadRecord?

    /**
     * Records that were interrupted and may be resumable. Includes DOWNLOADING
     * because a transfer left in that state was killed mid-flight by process
     * death (there are no live jobs after a restart), so it is treated as
     * paused on restore.
     */
    @Query(
        "SELECT * FROM download_records WHERE state IN ('DOWNLOADING','PAUSED','ERROR') " +
        "ORDER BY updatedAt DESC"
    )
    suspend fun getResumable(): List<DownloadRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecord)

    @Query(
        "UPDATE download_records SET state = :state, transferredBytes = :transferredBytes, " +
        "error = :error, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateState(
        id: String,
        state: String,
        transferredBytes: Long,
        error: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Remove finished/cancelled records, keeping only interrupted ones. */
    @Query("DELETE FROM download_records WHERE state IN ('DONE','CANCELLED')")
    suspend fun deleteCompleted()

    @Query("DELETE FROM download_records WHERE updatedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
