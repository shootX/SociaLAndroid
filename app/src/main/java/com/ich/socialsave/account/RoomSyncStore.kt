package com.ich.socialsave.account

import androidx.room.withTransaction
import com.ich.socialsave.data.AppDatabase
import com.ich.socialsave.data.Category
import com.ich.socialsave.data.DeletedCategory
import com.ich.socialsave.data.DeletedPost
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.data.ThumbnailStore
import java.util.UUID

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

    /**
     * Another account signs in over an expired one: the server refuses ids owned by the old account (it lists
     * them in `rejected`), so every post and category gets a new id and is uploaded to the new account.
     * Old tombstones belong to the old account and are dropped. Thumbnail files are kept; only ids change.
     */
    suspend fun reassignIds() = db.withTransaction {
        val oldCategories = categories.all()
        val oldPosts = posts.all()
        val newCategoryIds = oldCategories.associate { it.id to UUID.randomUUID().toString() }
        // Delete first: the canonical URL index is unique, so the copies can't sit next to the originals.
        posts.deleteAll()
        categories.deleteAll()
        tombstones.clearPosts()
        tombstones.clearCategories()
        oldCategories.forEach { categories.insert(it.copy(id = newCategoryIds.getValue(it.id), dirty = true)) }
        oldPosts.forEach {
            posts.insert(
                it.copy(id = UUID.randomUUID().toString(), categoryId = it.categoryId?.let(newCategoryIds::get), dirty = true),
            )
        }
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
