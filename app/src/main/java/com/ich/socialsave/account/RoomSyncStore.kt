package com.ich.socialsave.account

import androidx.room.withTransaction
import com.ich.socialsave.data.AppDatabase
import com.ich.socialsave.data.Category
import com.ich.socialsave.data.DeletedCategory
import com.ich.socialsave.data.DeletedPost
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.data.ThumbnailStore

/** [SyncLocalStore] on the app's Room database, plus the local wipe used by sign-out and account deletion. */
class RoomSyncStore(private val db: AppDatabase, private val thumbnails: ThumbnailStore) : SyncLocalStore {
    private val posts = db.posts()
    private val categories = db.categories()
    private val tombstones = db.tombstones()

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun dirtyCategories(limit: Int): List<Category> = if (limit > 0) categories.dirty(limit) else emptyList()
    override suspend fun dirtyPosts(limit: Int): List<SavedPost> = if (limit > 0) posts.dirty(limit) else emptyList()
    override suspend fun deletedCategories(limit: Int): List<DeletedCategory> = tombstones.categories(limit)
    override suspend fun deletedPosts(limit: Int): List<DeletedPost> = tombstones.posts(limit)

    override suspend fun hasPendingChanges(): Boolean =
        posts.hasDirty() || categories.hasDirty() || tombstones.any()

    override suspend fun clearCategoryDirty(id: String, sentUpdatedAt: Long) = categories.clearDirty(id, sentUpdatedAt)
    override suspend fun clearPostDirty(id: String, sentUpdatedAt: Long) = posts.clearDirty(id, sentUpdatedAt)
    override suspend fun dropDeletedCategory(id: String, deletedAt: Long) = tombstones.dropCategory(id, deletedAt)
    override suspend fun dropDeletedPost(id: String, deletedAt: Long) = tombstones.dropPost(id, deletedAt)

    override suspend fun category(id: String): Category? = categories.get(id)
    override suspend fun categoryDeletedAt(id: String): Long? = tombstones.categoryDeletedAt(id)
    override suspend fun upsertCategory(category: Category) = categories.upsert(category)

    override suspend fun deleteCategory(id: String) {
        posts.detachCategory(id)
        categories.delete(id)
    }

    override suspend fun post(id: String): SavedPost? = posts.get(id)
    override suspend fun postDeletedAt(id: String): Long? = tombstones.postDeletedAt(id)
    override suspend fun postByCanonical(canonicalUrl: String): SavedPost? = posts.byCanonical(canonicalUrl)
    override suspend fun upsertPost(post: SavedPost) = posts.upsert(post)
    override suspend fun deletePost(id: String) = posts.delete(listOf(id))

    override fun deleteThumbnail(file: String) = thumbnails.delete(file)

    /** First sign-in (or another account): everything local is uploaded and merged. */
    suspend fun markAllDirty() = db.withTransaction {
        posts.markAllDirty()
        categories.markAllDirty()
    }

    /** Sign-out and account deletion: posts, categories, tombstones and thumbnail files. */
    suspend fun wipe() {
        db.withTransaction {
            posts.deleteAll()
            categories.deleteAll()
            tombstones.clearPosts()
            tombstones.clearCategories()
        }
        thumbnails.deleteAll()
    }
}
