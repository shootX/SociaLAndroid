package com.ich.socialsave.data

import android.app.Application
import androidx.room.Room
import com.ich.socialsave.account.RoomSyncStore
import com.ich.socialsave.core.ShortLinkExpander
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The library's writes on a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class) // the app's own Application would start ads and WorkManager
class LibraryRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private var hasAccount = true
    private val repository = LibraryRepository(
        db = db,
        thumbnails = ThumbnailStore(context),
        expander = ShortLinkExpander(),
        onPostsAdded = {},
        onChanged = {},
        tracksDeletes = { hasAccount },
        clock = { NOW },
    )

    @After fun close() = db.close()

    private suspend fun save(url: String, categoryId: String? = null): String =
        (repository.saveLink(url, categoryId) as SaveResult.Saved).postId

    @Test fun sameLinkWithTrackingParamsIsSavedOnce() = runBlocking {
        save("https://www.instagram.com/reel/ABC123/")
        assertEquals(SaveResult.AlreadyExists, repository.saveLink("https://www.Instagram.com/reel/ABC123/?igsh=xyz&utm_source=ig"))
        assertEquals(1, db.posts().all().size)
    }

    @Test fun linkLongerThanTheApiAllowsIsRefused() = runBlocking {
        save("https://example.com/" + "a".repeat(2000))
        assertEquals(SaveResult.InvalidUrl, repository.saveLink("https://example.com/" + "b".repeat(2100)))
    }

    @Test fun notesAndCategoryNamesAreCutToTheApiLimits() = runBlocking {
        val id = save("https://example.com/post")
        repository.setNotes(id, "n".repeat(10_500))
        assertEquals(10_000, db.posts().get(id)!!.notes!!.length)
        assertEquals(255, repository.createCategory("c".repeat(300), "#000000").name.length)
    }

    @Test fun renamingKeepsAnOrderSyncedWhileTheEditorWasOpen() = runBlocking {
        val opened = repository.createCategory("Food", "#000000")
        db.categories().upsert(opened.copy(sortOrder = 5, dirty = false))
        repository.updateCategory(opened, "Recipes", "#000000")
        assertEquals(5, db.categories().get(opened.id)!!.sortOrder)
    }

    @Test fun deleteLeavesATombstoneOnlyWithAnAccount() = runBlocking {
        val synced = save("https://example.com/a")
        repository.delete(listOf(synced))
        assertEquals(NOW, db.tombstones().postDeletedAt(synced))

        hasAccount = false
        val local = save("https://example.com/b")
        repository.delete(listOf(local))
        assertNull(db.tombstones().postDeletedAt(local))
    }

    @Test fun failedPreviewWaitsBeforeTheNextTry() = runBlocking {
        val id = save("https://example.com/c")
        db.posts().updateMetadata(id, null, null, null, null, null, MetadataStatus.FAILED, attempts = 1, updatedAt = 0L)
        assertTrue(db.posts().needingMetadata(now = 5 * HOUR).isEmpty())
        assertEquals(listOf(id), db.posts().needingMetadata(now = 6 * HOUR).map { it.id })
    }

    @Test fun anotherAccountGetsNewIdsAndKeepsCategories() = runBlocking {
        val category = repository.createCategory("Food", "#000000")
        val post = save("https://example.com/d", category.id)
        repository.delete(listOf(save("https://example.com/e")))
        db.posts().clearDirty(post, NOW)
        db.categories().clearDirty(category.id, NOW)

        RoomSyncStore(db, ThumbnailStore(context)).reassignIds()

        val newCategory = db.categories().all().single()
        val newPost = db.posts().all().single()
        assertNotEquals(category.id, newCategory.id)
        assertNotEquals(post, newPost.id)
        assertEquals(newCategory.id, newPost.categoryId)
        assertTrue(newPost.dirty && newCategory.dirty)
        assertFalse(db.tombstones().any())
    }

    private companion object {
        const val NOW = 1_000L
        const val HOUR = 3_600_000L
    }
}
