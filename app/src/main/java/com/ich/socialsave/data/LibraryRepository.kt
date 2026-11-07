package com.ich.socialsave.data

import androidx.room.withTransaction
import com.ich.socialsave.core.PlatformDetector
import com.ich.socialsave.core.ShortLinkExpander
import com.ich.socialsave.core.UrlNormalizer
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val postId: String) : SaveResult
    data object AlreadyExists : SaveResult
    data object InvalidUrl : SaveResult
    data object Failure : SaveResult
}

sealed class CategoryError : Exception() {
    data object EmptyName : CategoryError()
    data object DuplicateName : CategoryError()
}

/**
 * Every change to the library goes through here, so side effects (thumbnail cleanup, preview fetching,
 * widget refresh, sync) happen in one place. [onChanged] is called after each write.
 *
 * Each write marks the row dirty and moves its `updatedAt` forward (see the DAOs). Deletes leave a
 * tombstone while [tracksDeletes] is true (an account exists), so the next sync can tell the server.
 */
class LibraryRepository(
    private val db: AppDatabase,
    private val thumbnails: ThumbnailStore,
    private val expander: ShortLinkExpander,
    private val onPostsAdded: () -> Unit,
    private val onChanged: () -> Unit,
    private val tracksDeletes: () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val posts = db.posts()
    private val categories = db.categories()
    private val tombstones = db.tombstones()

    fun observePosts(): Flow<List<SavedPost>> = posts.observeAll()
    fun observePost(id: String): Flow<SavedPost?> = posts.observe(id)
    fun observeCategories(): Flow<List<Category>> = categories.observeAll()

    suspend fun allPosts(): List<SavedPost> = posts.all()

    suspend fun saveLink(raw: String, categoryId: String? = null): SaveResult {
        val canonical = UrlNormalizer.canonicalUrl(raw) ?: return SaveResult.InvalidUrl
        val original = UrlNormalizer.firstHttpUrl(raw) ?: canonical
        if (posts.countCanonical(canonical) > 0) return SaveResult.AlreadyExists

        val expanded = expander.expand(original)
        val finalCanonical = UrlNormalizer.canonicalUrl(expanded) ?: canonical
        val finalUrl = if (UrlNormalizer.canonicalize(expanded) != null) expanded else original
        if (finalCanonical != canonical && posts.countCanonical(finalCanonical) > 0) {
            return SaveResult.AlreadyExists
        }

        val validCategory = categoryId?.takeIf { categories.exists(it) > 0 }
        val now = clock()
        val post = SavedPost(
            createdAt = now,
            updatedAt = now,
            url = finalUrl,
            canonicalUrl = finalCanonical,
            platform = PlatformDetector.detect(finalCanonical).rawValue,
            sourceHost = UrlNormalizer.hostOf(finalCanonical),
            categoryId = validCategory,
        )
        return runCatching {
            if (posts.insert(post) == -1L) {
                SaveResult.AlreadyExists
            } else {
                onPostsAdded()
                onChanged()
                SaveResult.Saved(post.id)
            }
        }.getOrDefault(SaveResult.Failure)
    }

    suspend fun delete(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val list = ids.toList()
        val files = posts.byIds(list).map { it.thumbnailFile }
        val now = clock()
        db.withTransaction {
            posts.delete(list)
            if (tracksDeletes()) tombstones.insertPosts(list.map { DeletedPost(it, now) })
        }
        files.forEach(thumbnails::delete)
        onChanged()
    }

    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) {
        posts.setFavorite(ids.toList(), favorite, clock())
        onChanged()
    }

    suspend fun move(ids: Collection<String>, categoryId: String?) {
        posts.move(ids.toList(), categoryId, clock())
        onChanged()
    }

    suspend fun setNotes(id: String, raw: String) {
        posts.setNotes(id, raw.trim().ifEmpty { null }, clock())
        onChanged()
    }

    suspend fun createCategory(name: String, colorHex: String): Category {
        val trimmed = name.trim()
        val existing = categories.all()
        validate(trimmed, null, existing)
        val now = clock()
        val category = Category(
            name = trimmed,
            colorHex = colorHex,
            createdAt = now,
            updatedAt = now,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        categories.insert(category)
        onChanged()
        return category
    }

    suspend fun updateCategory(category: Category, name: String, colorHex: String) {
        val trimmed = name.trim()
        validate(trimmed, category.id, categories.all())
        categories.update(category.copy(name = trimmed, colorHex = colorHex).touched(clock()))
        onChanged()
    }

    suspend fun deleteCategory(id: String) {
        val now = clock()
        db.withTransaction {
            posts.clearCategory(id, now)
            categories.delete(id)
            if (tracksDeletes()) tombstones.insertCategory(DeletedCategory(id, now))
        }
        onChanged()
    }

    suspend fun reorderCategories(ordered: List<Category>) {
        val now = clock()
        val changed = ordered.mapIndexedNotNull { index, category ->
            if (category.sortOrder == index) null else category.copy(sortOrder = index).touched(now)
        }
        if (changed.isEmpty()) return
        categories.updateAll(changed)
        onChanged()
    }

    private fun Category.touched(now: Long) = copy(updatedAt = maxOf(now, updatedAt + 1), dirty = true)

    private fun validate(name: String, excludingId: String?, existing: List<Category>) {
        if (name.isEmpty()) throw CategoryError.EmptyName
        if (existing.any { it.id != excludingId && it.name.equals(name, ignoreCase = true) }) {
            throw CategoryError.DuplicateName
        }
    }
}
