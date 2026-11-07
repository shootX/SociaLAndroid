package com.shoto.socialsave.account

import com.shoto.socialsave.data.Category
import com.shoto.socialsave.data.MetadataStatus
import com.shoto.socialsave.data.SavedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun remote(
        id: String = "x",
        url: String = "https://www.tiktok.com/@a/video/1",
        updatedAt: Long = 2_000,
        note: String? = null,
        favorite: Boolean = false,
        title: String? = null,
        deleted: Boolean = false,
    ) = RemoteSave(
        id = id, url = url, platform = "tiktok", title = title, authorName = null, sourceHost = "tiktok.com",
        thumbnailUrl = null, note = note, isFavorite = favorite, collectionId = null, createdAt = 1_000,
        updatedAt = updatedAt, deleted = deleted,
    )

    private fun local(
        id: String = "x",
        url: String = "https://www.tiktok.com/@a/video/1",
        updatedAt: Long = 1_500,
        dirty: Boolean = false,
        note: String? = null,
        favorite: Boolean = false,
        file: String? = null,
        title: String? = null,
    ) = SavedPost(
        id = id, url = url, canonicalUrl = SyncMerge.canonicalOf(url), platform = "tiktok", title = title,
        thumbnailFile = file, notes = note, isFavorite = favorite, createdAt = 1_000, updatedAt = updatedAt, dirty = dirty,
        metadataStatus = if (file != null) MetadataStatus.LOADED else MetadataStatus.PENDING,
    )

    @Test fun lastWriteWins() {
        assertTrue("clean local loses", SyncMerge.incomingWins(localDirty = false, localUpdatedAt = 9_000, incomingUpdatedAt = 1_000))
        assertFalse("dirty newer local wins", SyncMerge.incomingWins(localDirty = true, localUpdatedAt = 3_000, incomingUpdatedAt = 2_000))
        assertTrue("dirty older local loses", SyncMerge.incomingWins(localDirty = true, localUpdatedAt = 1_000, incomingUpdatedAt = 2_000))
        assertTrue("tie goes to the server", SyncMerge.incomingWins(localDirty = true, localUpdatedAt = 2_000, incomingUpdatedAt = 2_000))
    }

    @Test fun incomingUpdateReplacesCleanLocal() {
        val plan = SyncMerge.planSave(remote(note = "server"), local(), null, local(), now = 5_000)
        val post = (plan as SavePlan.Upsert).post
        assertEquals("server", post.notes)
        assertEquals(2_000, post.updatedAt)
        assertFalse(post.dirty)
        assertNull(plan.replaced)
    }

    @Test fun dirtyNewerLocalIsKept() {
        val mine = local(updatedAt = 3_000, dirty = true, note = "mine")
        assertSame(SavePlan.Skip, SyncMerge.planSave(remote(note = "server"), mine, null, mine, now = 5_000))
    }

    @Test fun incomingKeepsLocalOnlyFieldsAndFetchedTitle() {
        val mine = local(file = "thumb.jpg", title = "Fetched here")
        val post = (SyncMerge.planSave(remote(title = null), mine, null, mine, 5_000) as SavePlan.Upsert).post
        assertEquals("thumb.jpg", post.thumbnailFile)
        assertEquals(MetadataStatus.LOADED, post.metadataStatus)
        assertEquals("Fetched here", post.title)
    }

    @Test fun tombstoneRemovesLocalRow() {
        val mine = local(file = "thumb.jpg")
        val plan = SyncMerge.planSave(remote(deleted = true), mine, null, null, 5_000)
        assertEquals(SavePlan.Remove(mine), plan)
    }

    @Test fun tombstoneLosesToNewerLocalEdit() {
        val mine = local(updatedAt = 3_000, dirty = true)
        assertSame(SavePlan.Skip, SyncMerge.planSave(remote(deleted = true, updatedAt = 2_000), mine, null, null, 5_000))
    }

    @Test fun tombstoneForUnknownRowIsIgnored() {
        assertSame(SavePlan.Skip, SyncMerge.planSave(remote(deleted = true), null, null, null, 5_000))
    }

    @Test fun pendingLocalDeleteBeatsOlderIncomingRow() {
        assertSame(SavePlan.Skip, SyncMerge.planSave(remote(updatedAt = 2_000), null, tombstoneAt = 3_000, clash = null, now = 5_000))
        assertTrue(SyncMerge.planSave(remote(updatedAt = 4_000), null, tombstoneAt = 3_000, clash = null, now = 5_000) is SavePlan.Upsert)
    }

    @Test fun canonicalClashCopiesNoteFavoriteAndThumbnail() {
        val other = local(id = "y", note = "mine", favorite = true, file = "y.jpg")
        val plan = SyncMerge.planSave(remote(id = "x"), null, null, other, now = 5_000) as SavePlan.Upsert
        assertEquals(other, plan.replaced)
        assertEquals("x", plan.post.id)
        assertEquals("mine", plan.post.notes)
        assertTrue(plan.post.isFavorite)
        assertEquals("y.jpg", plan.post.thumbnailFile)
        // The merge changed the row, so it goes back to the server, newer than what it has.
        assertTrue(plan.post.dirty)
        assertEquals(5_000, plan.post.updatedAt)
    }

    @Test fun canonicalClashKeepsIncomingNote() {
        val other = local(id = "y", note = "mine")
        val plan = SyncMerge.planSave(remote(id = "x", note = "server"), null, null, other, 5_000) as SavePlan.Upsert
        assertEquals("server", plan.post.notes)
        assertFalse("nothing to upload", plan.post.dirty)
        assertEquals(2_000, plan.post.updatedAt)
    }

    @Test fun categoryRules() {
        val incoming = RemoteCollection("c", "Cars", "#3F5C6A", 1, 1_000, 2_000, deleted = false)
        val clean = Category(id = "c", name = "Old", colorHex = "#000000", createdAt = 1_000, sortOrder = 0, updatedAt = 1_500, dirty = false)
        assertEquals("Cars", (SyncMerge.planCategory(incoming, clean, null) as CategoryPlan.Upsert).category.name)
        assertSame(CategoryPlan.Skip, SyncMerge.planCategory(incoming, clean.copy(updatedAt = 3_000, dirty = true), null))
        assertEquals(CategoryPlan.Remove("c"), SyncMerge.planCategory(incoming.copy(deleted = true), clean, null))
        assertSame(CategoryPlan.Skip, SyncMerge.planCategory(incoming, null, tombstoneAt = 2_500))
    }

    @Test fun metadataSyncOnlyForUsefulChanges() {
        val before = local(title = null).copy(thumbnailUrl = "https://cdn/a.jpg?sig=1")
        assertTrue(SyncMerge.metadataNeedsSync(before, before.copy(title = "New")))
        assertFalse("signed CDN URL churn", SyncMerge.metadataNeedsSync(before, before.copy(thumbnailUrl = "https://cdn/a.jpg?sig=2")))
        assertTrue(SyncMerge.metadataNeedsSync(before.copy(thumbnailUrl = null), before))
    }
}
