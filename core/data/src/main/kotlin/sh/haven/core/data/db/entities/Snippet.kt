package sh.haven.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
    val autoReturn: Boolean = true,
    val sortOrder: Int = 0
)
