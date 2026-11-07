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
import kotlinx.coroutines.flow.Flow

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

    @Query(
        "SELECT * FROM saved_posts WHERE thumbnailFile IS NULL AND metadataAttempts < 3 " +
            "AND metadataStatus != 'UNAVAILABLE' ORDER BY createdAt DESC",
    )
    suspend fun needingMetadata(): List<SavedPost>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(post: SavedPost): Long

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

    @Query("UPDATE saved_posts SET isFavorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<String>, favorite: Boolean)

    @Query("UPDATE saved_posts SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun move(ids: List<String>, categoryId: String?)

    @Query("UPDATE saved_posts SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: String, notes: String?)

    @Query("UPDATE saved_posts SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: String)

    @Query("UPDATE saved_posts SET metadataStatus = 'PENDING' WHERE metadataStatus = 'LOADING'")
    suspend fun resetInterruptedMetadata()

    @Query("SELECT * FROM saved_posts WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SavedPost>

    @Query("DELETE FROM saved_posts WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, createdAt")
    suspend fun all(): List<Category>

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun exists(id: String): Int

    @Insert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun updateAll(categories: List<Category>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)
}

class Converters {
    @TypeConverter fun fromStatus(status: MetadataStatus): String = status.name

    @TypeConverter fun toStatus(raw: String): MetadataStatus =
        MetadataStatus.entries.firstOrNull { it.name == raw } ?: MetadataStatus.PENDING
}

@Database(entities = [SavedPost::class, Category::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posts(): PostDao
    abstract fun categories(): CategoryDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "socialsave.db").build()
    }
}
