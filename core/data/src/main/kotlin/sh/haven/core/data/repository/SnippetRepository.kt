package sh.haven.core.data.repository

import kotlinx.coroutines.flow.Flow
import sh.haven.core.data.db.SnippetDao
import sh.haven.core.data.db.entities.Snippet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepository @Inject constructor(
    private val snippetDao: SnippetDao
) {
    fun getAllSnippets(): Flow<List<Snippet>> = snippetDao.getAllSnippets()

    suspend fun addSnippet(snippet: Snippet) {
        snippetDao.insertSnippet(snippet)
    }

    suspend fun updateSnippet(snippet: Snippet) {
        snippetDao.updateSnippet(snippet)
    }

    suspend fun deleteSnippet(snippet: Snippet) {
        snippetDao.deleteSnippet(snippet)
    }
}
