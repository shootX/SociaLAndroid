package com.shoto.socialsave.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Local edits set `dirty = 1` and move `updatedAt` forward with MAX(now, updatedAt + 1): the edit always
 * beats the version it was made on, even if this phone's clock is behind the one that wrote that version.
 * Updates skip rows whose value doesn't change, so a bulk action doesn't re-upload untouched posts.
 */

@Dao
interface PostDao {
    @Query("SELECT * FROM saved_posts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPost>>

    @Query("SELECT * FROM saved_posts ORDER BY createdAt DESC")
    suspend fun all(): List<SavedPost>

    @Query("SELECT * FROM saved_posts WHERE id = :id")
    fun observe(id: String): Flow<SavedPost?>

    @Query("SELECT * FROM saved_posts WHERE id = :id")
    suspend fun get(id: String): SavedPost?

    @Query("SELECT COUNT(*) FROM saved_posts WHERE canonicalUrl = :canonical")
    suspend fun countCanonical(canonical: String): Int

    @Query("SELECT * FROM saved_posts WHERE canonicalUrl = :canonical LIMIT 1")
    suspend fun byCanonical(canonical: String): SavedPost?

    @Query(
        "SELECT * FROM saved_posts WHERE thumbnailFile IS NULL AND metadataAttempts < 3 " +
            "AND metadataStatus != 'UNAVAILABLE' ORDER BY createdAt DESC",
    )
    suspend fun needingMetadata(): List<SavedPost>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: SavedPost): Long

    @Upsert
    suspend fun upsert(post: SavedPost)

    @Query("UPDATE saved_posts SET metadataStatus = 'LOADING' WHERE id = :id")
    suspend fun markLoading(id: String)

    /** Touches only the columns the metadata worker owns, so edits made meanwhile (favorite, category, notes) survive. */
    @Query(
        "UPDATE saved_posts SET title = :title, authorName = :authorName, thumbnailUrl = :thumbnailUrl, " +
            "sourceHost = :sourceHost, thumbnailFile = :thumbnailFile, metadataStatus = :status, " +
            "metadataAttempts = :attempts, metadataUpdatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateMetadata(
        id: String,
        title: String?,
        authorName: String?,
        thumbnailUrl: String?,
        sourceHost: String?,
        thumbnailFile: String?,
        status: MetadataStatus,
        attempts: Int,
        updatedAt: Long?,
    )

    /**
     * A fetched title/author should reach other phones, but must not beat a newer edit made elsewhere:
     * one millisecond past the version it was fetched on wins against that version only.
     */
    @Query("UPDATE saved_posts SET dirty = 1, updatedAt = updatedAt + 1 WHERE id = :id")
    suspend fun markMetadataChanged(id: String)

    @Query(
        "UPDATE saved_posts SET isFavorite = :favorite, updatedAt = MAX(:now, updatedAt + 1), dirty = 1 " +
            "WHERE id IN (:ids) AND isFavorite != :favorite",
    )
    suspend fun setFavorite(ids: List<String>, favorite: Boolean, now: Long)

    @Query(
        "UPDATE saved_posts SET categoryId = :categoryId, updatedAt = MAX(:now, updatedAt + 1), dirty = 1 " +
            "WHERE id IN (:ids) AND categoryId IS NOT :categoryId",
    )
    suspend fun move(ids: List<String>, categoryId: String?, now: Long)

    @Query(
        "UPDATE saved_posts SET notes = :notes, updatedAt = MAX(:now, updatedAt + 1), dirty = 1 " +
            "WHERE id = :id AND notes IS NOT :notes",
    )
    suspend fun setNotes(id: String, notes: String?, now: Long)

    /** The user deleted the category: its posts lose it, and that is an edit to sync. */
    @Query(
        "UPDATE saved_posts SET categoryId = NULL, updatedAt = MAX(:now, updatedAt + 1), dirty = 1 " +
            "WHERE categoryId = :categoryId",
    )
    suspend fun clearCategory(categoryId: String, now: Long)

    /** Another device deleted the category; the server already updated these posts. */
    @Query("UPDATE saved_posts SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: String)

    @Query("UPDATE saved_posts SET metadataStatus = 'PENDING' WHERE metadataStatus = 'LOADING'")
    suspend fun resetInterruptedMetadata()

    @Query("SELECT * FROM saved_posts WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SavedPost>

    @Query("DELETE FROM saved_posts WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM saved_posts")
    suspend fun deleteAll()

    @Query("SELECT * FROM saved_posts WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(limit: Int): List<SavedPost>

    /** Clears the flag only if the post wasn't edited again after it was sent. */
    @Query("UPDATE saved_posts SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirty(id: String, updatedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE dirty = 1)")
    suspend fun hasDirty(): Boolean

    @Query("UPDATE saved_posts SET dirty = 1")
    suspend fun markAllDirty()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): Category?

    @Insert
    suspend fun insert(category: Category)

    @Upsert
    suspend fun upsert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun updateAll(categories: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM categories WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(limit: Int): List<Category>

    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun clearDirty(id: String, updatedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE dirty = 1)")
    suspend fun hasDirty(): Boolean

    @Query("UPDATE categories SET dirty = 1")
    suspend fun markAllDirty()
}

@Dao
interface TombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(rows: List<DeletedPost>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(row: DeletedCategory)

    @Query("SELECT * FROM deleted_posts ORDER BY deletedAt LIMIT :limit")
    suspend fun posts(limit: Int): List<DeletedPost>

    @Query("SELECT * FROM deleted_categories ORDER BY deletedAt LIMIT :limit")
    suspend fun categories(limit: Int): List<DeletedCategory>

    @Query("SELECT deletedAt FROM deleted_posts WHERE id = :id")
    suspend fun postDeletedAt(id: String): Long?

    @Query("SELECT deletedAt FROM deleted_categories WHERE id = :id")
    suspend fun categoryDeletedAt(id: String): Long?

    @Query("DELETE FROM deleted_posts WHERE id = :id AND deletedAt = :deletedAt")
    suspend fun dropPost(id: String, deletedAt: Long)

    @Query("DELETE FROM deleted_categories WHERE id = :id AND deletedAt = :deletedAt")
    suspend fun dropCategory(id: String, deletedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_posts) OR EXISTS(SELECT 1 FROM deleted_categories)")
    suspend fun any(): Boolean

    @Query("DELETE FROM deleted_posts")
    suspend fun clearPosts()

    @Query("DELETE FROM deleted_categories")
    suspend fun clearCategories()
}

class Converters {
    @TypeConverter fun fromStatus(status: MetadataStatus): String = status.name

    @TypeConverter fun toStatus(raw: String): MetadataStatus =
        MetadataStatus.entries.firstOrNull { it.name == raw } ?: MetadataStatus.PENDING
}

@Database(
    entities = [SavedPost::class, Category::class, DeletedPost::class, DeletedCategory::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posts(): PostDao
    abstract fun categories(): CategoryDao
    abstract fun tombstones(): TombstoneDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "socialsave.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
