package com.hector.workouttracker.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Tagged ID + original JSON preserves numeric IDs, nested extensions and array order. */
@Entity(tableName = "records", primaryKeys = ["kind", "idKey"])
data class RecordEntity(
    val kind: String,
    val idKey: String,
    val payload: String,
    val revision: Long = 1,
)

@Entity(tableName = "documents")
data class DocumentEntity(@PrimaryKey val name: String, val payload: String, val revision: Long = 1)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val slot: String = "active",
    val sessionId: String,
    val payload: String,
    val revision: Long = 1,
)

@Dao
interface TrackerDao {
    @Query("SELECT * FROM records ORDER BY kind, idKey") suspend fun records(): List<RecordEntity>

    @Query("SELECT * FROM documents ORDER BY name") suspend fun documents(): List<DocumentEntity>

    @Query(
        "UPDATE records SET payload = :payload, revision = revision + 1 WHERE kind = :kind AND idKey = :idKey AND revision = :expected"
    )
    suspend fun updateRecord(kind: String, idKey: String, expected: Long, payload: String): Int

    @Query("DELETE FROM records WHERE kind = :kind AND idKey = :idKey AND revision = :expected")
    suspend fun deleteRecord(kind: String, idKey: String, expected: Long): Int

    @Query("SELECT * FROM records WHERE kind = :kind ORDER BY idKey")
    fun observeRecords(kind: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE kind = :kind ORDER BY idKey")
    suspend fun recordsOfKind(kind: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE kind = :kind AND idKey = :idKey")
    suspend fun record(kind: String, idKey: String): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(record: RecordEntity)

    @Query("SELECT * FROM drafts WHERE slot = 'active'") suspend fun draft(): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDraft(draft: DraftEntity)

    @Query(
        "UPDATE drafts SET payload = :payload, revision = revision + 1 WHERE slot = 'active' AND sessionId = :id AND revision = :expected"
    )
    suspend fun updateDraft(id: String, expected: Long, payload: String): Int

    @Query("DELETE FROM drafts WHERE slot = 'active' AND sessionId = :id AND revision = :expected")
    suspend fun deleteDraft(id: String, expected: Long): Int

    @Query("SELECT * FROM documents WHERE name = :name")
    suspend fun document(name: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity)

    @Query(
        "UPDATE documents SET payload = :payload, revision = revision + 1 WHERE name = :name AND revision = :expected"
    )
    suspend fun updateDocument(name: String, expected: Long, payload: String): Int
}

@Database(
    entities = [RecordEntity::class, DocumentEntity::class, DraftEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
}
