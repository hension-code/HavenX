package sh.haven.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import sh.haven.core.data.db.entities.Snippet

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY sortOrder ASC, name ASC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: Snippet)

    @Update
    suspend fun updateSnippet(snippet: Snippet)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)

    @Query("UPDATE snippets SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    /**
     * Persist a new ordering by writing each snippet's list index back as its
     * sortOrder, in a single transaction to avoid intermediate Flow emissions.
     */
    @Transaction
    suspend fun updateSortOrders(snippets: List<Snippet>) {
        snippets.forEachIndexed { index, snippet ->
            updateSortOrder(snippet.id, index)
        }
    }
}
