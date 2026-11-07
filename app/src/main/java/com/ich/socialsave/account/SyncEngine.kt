package com.ich.socialsave.account

import com.ich.socialsave.data.Category
import com.ich.socialsave.data.DeletedCategory
import com.ich.socialsave.data.DeletedPost
import com.ich.socialsave.data.SavedPost

/** The local side of a sync: Room in the app, a map in tests. */
interface SyncLocalStore {
    suspend fun <T> transaction(block: suspend () -> T): T

    suspend fun dirtyCategories(limit: Int): List<Category>
    suspend fun dirtyPosts(limit: Int): List<SavedPost>
    suspend fun deletedCategories(limit: Int): List<DeletedCategory>
    suspend fun deletedPosts(limit: Int): List<DeletedPost>

    /** Dirty rows or unsent tombstones are left. */
    suspend fun hasPendingChanges(): Boolean

    /** Clear `dirty` only if `updatedAt` still equals the value that was sent. */
    suspend fun clearCategoryDirty(id: String, sentUpdatedAt: Long)
    suspend fun clearPostDirty(id: String, sentUpdatedAt: Long)
    suspend fun dropDeletedCategory(id: String, deletedAt: Long)
    suspend fun dropDeletedPost(id: String, deletedAt: Long)

    suspend fun category(id: String): Category?
    suspend fun categoryDeletedAt(id: String): Long?
    suspend fun upsertCategory(category: Category)

    /** Removes the category and detaches its posts without marking them dirty. */
    suspend fun deleteCategory(id: String)

    suspend fun post(id: String): SavedPost?
    suspend fun postDeletedAt(id: String): Long?
    suspend fun postByCanonical(canonicalUrl: String): SavedPost?
    suspend fun upsertPost(post: SavedPost)
    suspend fun deletePost(id: String)

    fun deleteThumbnail(file: String)
}

data class SyncRunResult(
    val rounds: Int,
    /** Rows from the server were written locally (refresh the widget). */
    val changedLocally: Boolean,
    /** Some written posts have no thumbnail yet (run the metadata fetch). */
    val needsMetadata: Boolean,
    /** False when the round limit stopped it with work left. */
    val complete: Boolean,
    /** The server held its cursor (has_more with the same cursor): run again ≥3 s later, don't loop. */
    val retryLater: Boolean = false,
)

/**
 * The client algorithm from the contract: push up to 500 dirty items per kind (tombstones included),
 * clear what didn't change meanwhile, apply what came back, store the cursor, and repeat while the server
 * has more or local changes remain — at most 20 rounds per run. When the server answers has_more with the
 * cursor it was sent (more than 500 rows changed in the last 2 s), the run stops so the caller can retry
 * a few seconds later instead of hitting the rate limit.
 */
class SyncEngine(
    private val api: ApiClient,
    private val store: SyncLocalStore,
    private val cursors: SyncCursorStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): SyncRunResult {
        var changed = false
        var needsMetadata = false
        for (round in 1..MAX_ROUNDS) {
            val deletedCategories = store.deletedCategories(BATCH)
            val categories = store.dirtyCategories(BATCH - deletedCategories.size)
            val deletedPosts = store.deletedPosts(BATCH)
            val posts = store.dirtyPosts(BATCH - deletedPosts.size)

            val sentCursor = cursors.cursor
            val request = SyncJson.request(sentCursor, categories, deletedCategories, posts, deletedPosts)
            val response = SyncJson.parseResponse(api.sync(request))

            val filesToDelete = mutableListOf<String>()
            store.transaction {
                categories.forEach { store.clearCategoryDirty(it.id, it.updatedAt) }
                posts.forEach { store.clearPostDirty(it.id, it.updatedAt) }
                deletedCategories.forEach { store.dropDeletedCategory(it.id, it.deletedAt) }
                deletedPosts.forEach { store.dropDeletedPost(it.id, it.deletedAt) }

                response.collections.forEach { if (applyCollection(it)) changed = true }
                response.saves.forEach { incoming ->
                    when (val plan = applySave(incoming, filesToDelete)) {
                        SavePlan.Skip -> Unit
                        is SavePlan.Remove -> changed = true
                        is SavePlan.Upsert -> {
                            changed = true
                            if (plan.post.thumbnailFile == null) needsMetadata = true
                        }
                    }
                }
            }
            // Files go only after the rows are gone for good.
            filesToDelete.forEach(store::deleteThumbnail)
            response.cursor?.let { cursors.cursor = it }

            if (response.hasMore && response.cursor == sentCursor) {
                return SyncRunResult(round, changed, needsMetadata, complete = false, retryLater = true)
            }
            if (!response.hasMore && !store.hasPendingChanges()) {
                return SyncRunResult(round, changed, needsMetadata, complete = true)
            }
        }
        return SyncRunResult(MAX_ROUNDS, changed, needsMetadata, complete = false)
    }

    private suspend fun applyCollection(incoming: RemoteCollection): Boolean {
        val plan = SyncMerge.planCategory(incoming, store.category(incoming.id), store.categoryDeletedAt(incoming.id))
        return when (plan) {
            CategoryPlan.Skip -> false
            is CategoryPlan.Remove -> {
                store.deleteCategory(plan.id)
                true
            }
            is CategoryPlan.Upsert -> {
                store.upsertCategory(plan.category)
                true
            }
        }
    }

    private suspend fun applySave(incoming: RemoteSave, filesToDelete: MutableList<String>): SavePlan {
        val local = store.post(incoming.id)
        val clash = if (incoming.deleted) null else store.postByCanonical(SyncMerge.canonicalOf(incoming.url))
        val plan = SyncMerge.planSave(incoming, local, store.postDeletedAt(incoming.id), clash, clock())
        when (plan) {
            SavePlan.Skip -> Unit
            is SavePlan.Remove -> {
                store.deletePost(plan.local.id)
                plan.local.thumbnailFile?.let(filesToDelete::add)
            }
            is SavePlan.Upsert -> {
                plan.replaced?.let { replaced ->
                    store.deletePost(replaced.id)
                    replaced.thumbnailFile?.takeIf { it != plan.post.thumbnailFile }?.let(filesToDelete::add)
                }
                store.upsertPost(plan.post)
            }
        }
        return plan
    }

    companion object {
        const val BATCH = 500
        const val MAX_ROUNDS = 20
    }
}
