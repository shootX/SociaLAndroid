package com.shoto.socialsave.data

import com.shoto.socialsave.core.PlatformDetector
import com.shoto.socialsave.core.ShortLinkExpander
import com.shoto.socialsave.core.UrlNormalizer
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
 * widget refresh) happen in one place. [onChanged] is called after each write.
 */
class LibraryRepository(
    private val db: AppDatabase,
    private val thumbnails: ThumbnailStore,
    private val expander: ShortLinkExpander,
    private val onPostsAdded: () -> Unit,
    private val onChanged: () -> Unit,
) {
    private val posts = db.posts()
    private val categories = db.categories()

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
        val post = SavedPost(
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
        posts.delete(list)
        files.forEach(thumbnails::delete)
        onChanged()
    }

    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) {
        posts.setFavorite(ids.toList(), favorite)
        onChanged()
    }

    suspend fun move(ids: Collection<String>, categoryId: String?) {
        posts.move(ids.toList(), categoryId)
        onChanged()
    }

    suspend fun setNotes(id: String, raw: String) {
        posts.setNotes(id, raw.trim().ifEmpty { null })
    }

    suspend fun createCategory(name: String, colorHex: String): Category {
        val trimmed = name.trim()
        val existing = categories.all()
        validate(trimmed, null, existing)
        val category = Category(
            name = trimmed,
            colorHex = colorHex,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        )
        categories.insert(category)
        onChanged()
        return category
    }

    suspend fun updateCategory(category: Category, name: String, colorHex: String) {
        val trimmed = name.trim()
        validate(trimmed, category.id, categories.all())
        categories.update(category.copy(name = trimmed, colorHex = colorHex))
        onChanged()
    }

    suspend fun deleteCategory(id: String) {
        posts.clearCategory(id)
        categories.delete(id)
        onChanged()
    }

    suspend fun reorderCategories(ordered: List<Category>) {
        categories.updateAll(ordered.mapIndexed { index, category -> category.copy(sortOrder = index) })
    }

    private fun validate(name: String, excludingId: String?, existing: List<Category>) {
        if (name.isEmpty()) throw CategoryError.EmptyName
        if (existing.any { it.id != excludingId && it.name.equals(name, ignoreCase = true) }) {
            throw CategoryError.DuplicateName
        }
    }
}
