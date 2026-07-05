package sh.haven.core.data.repository

import kotlinx.coroutines.flow.Flow
import sh.haven.core.data.db.DownloadRecordDao
import sh.haven.core.data.db.entities.DownloadRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRecordRepository @Inject constructor(
    private val dao: DownloadRecordDao,
) {
    fun observeAll(): Flow<List<DownloadRecord>> = dao.observeAll()

    suspend fun getById(id: String): DownloadRecord? = dao.getById(id)

    suspend fun getResumable(): List<DownloadRecord> = dao.getResumable()

    suspend fun upsert(record: DownloadRecord) = dao.upsert(record)

    suspend fun updateState(
        id: String,
        state: String,
        transferredBytes: Long,
        error: String?,
        updatedAt: Long = System.currentTimeMillis(),
    ) = dao.updateState(id, state, transferredBytes, error, updatedAt)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun deleteCompleted() = dao.deleteCompleted()

    suspend fun deleteOlderThan(olderThan: Long) = dao.deleteOlderThan(olderThan)
}
