package com.ich.socialsave.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ich.socialsave.core.SocialPlatform
import java.util.UUID

enum class MetadataStatus { PENDING, LOADING, LOADED, UNAVAILABLE, FAILED }

@Entity(
    tableName = "saved_posts",
    indices = [Index(value = ["canonicalUrl"], unique = true), Index("categoryId"), Index("createdAt")],
)
data class SavedPost(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val url: String,
    val canonicalUrl: String,
    val platform: String,
    val title: String? = null,
    val authorName: String? = null,
    val thumbnailUrl: String? = null,
    /** File name inside the app's thumbnails folder. */
    val thumbnailFile: String? = null,
    val sourceHost: String? = null,
    val metadataStatus: MetadataStatus = MetadataStatus.PENDING,
    val metadataAttempts: Int = 0,
    val metadataUpdatedAt: Long? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val categoryId: String? = null,
    /** Last-write-wins clock for sync: the time of the last edit made on any device. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    /** Changed locally since the last successful sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
) {
    val socialPlatform: SocialPlatform get() = SocialPlatform.fromRaw(platform)

    val displayHost: String get() = sourceHost?.takeIf { it.isNotEmpty() } ?: url
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
)

/** A post deleted on this phone that the server has not been told about yet. */
@Entity(tableName = "deleted_posts")
data class DeletedPost(@PrimaryKey val id: String, val deletedAt: Long)

/** A category deleted on this phone that the server has not been told about yet. */
@Entity(tableName = "deleted_categories")
data class DeletedCategory(@PrimaryKey val id: String, val deletedAt: Long)
