package com.shoto.socialsave.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shoto.socialsave.core.SocialPlatform
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
)
