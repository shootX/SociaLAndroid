package com.ich.socialsave.account

import com.ich.socialsave.data.Category
import com.ich.socialsave.data.DeletedCategory
import com.ich.socialsave.data.DeletedPost
import com.ich.socialsave.data.MetadataStatus
import com.ich.socialsave.data.SavedPost
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncJsonTest {
    private val created = Instant.parse("2026-11-07T10:00:00.000Z").toEpochMilli()
    private val updated = Instant.parse("2026-11-07T10:05:00.123Z").toEpochMilli()

    private val post = SavedPost(
        id = "0b9f7c1e-4a51-4f3e-9d2a-6b1c0e5f7a10",
        url = "https://www.instagram.com/p/ABC123/",
        canonicalUrl = SyncMerge.canonicalOf("https://www.instagram.com/p/ABC123/"),
        platform = "instagram",
        title = "Pasta in 15 minutes",
        authorName = "chef",
        thumbnailUrl = "https://cdn.example.com/a.jpg",
        thumbnailFile = "local-only.jpg",
        sourceHost = "instagram.com",
        metadataStatus = MetadataStatus.LOADED,
        metadataAttempts = 2,
        notes = "for sunday",
        isFavorite = true,
        createdAt = created,
        categoryId = "7d1c2b3a-0000-4000-8000-000000000001",
        updatedAt = updated,
    )

    private val category = Category(
        id = "7d1c2b3a-0000-4000-8000-000000000001",
        name = "Recipes",
        colorHex = "#FF9500",
        createdAt = created,
        sortOrder = 3,
        updatedAt = updated,
    )

    @Test fun timesAreUtcIsoWithMilliseconds() {
        assertEquals("1970-01-01T00:00:00.000Z", SyncJson.formatTime(0))
        assertEquals("1970-01-01T00:00:01.234Z", SyncJson.formatTime(1234))
        assertEquals("2026-11-07T10:05:00.123Z", SyncJson.formatTime(updated))
    }

    @Test fun parsesMicrosecondsAndOffsets() {
        assertEquals(updated, SyncJson.parseTime("2026-11-07T10:05:00.123Z"))
        assertEquals(updated, SyncJson.parseTime("2026-11-07T10:05:00.123456Z"))
        assertEquals(updated, SyncJson.parseTime("2026-11-07T14:05:00.123+04:00"))
    }

    @Test fun saveUsesContractFieldsAndNoLocalOnlyFields() {
        val json = SyncJson.save(post)
        assertEquals(
            setOf(
                "id", "url", "platform", "title", "author_name", "source_host", "thumbnail_url", "note",
                "is_favorite", "collection_id", "created_at", "updated_at", "deleted",
            ),
            json.keys().asSequence().toSet(),
        )
        assertEquals("https://cdn.example.com/a.jpg", json.getString("thumbnail_url"))
        assertEquals("for sunday", json.getString("note"))
        assertEquals("2026-11-07T10:00:00.000Z", json.getString("created_at"))
        assertEquals("2026-11-07T10:05:00.123Z", json.getString("updated_at"))
        assertFalse(json.getBoolean("deleted"))
    }

    @Test fun nullsAreSentAsJsonNull() {
        val json = SyncJson.save(post.copy(title = null, authorName = null, thumbnailUrl = null, notes = null, categoryId = null))
        listOf("title", "author_name", "thumbnail_url", "note", "collection_id").forEach {
            assertTrue("$it present", json.has(it))
            assertTrue("$it null", json.isNull(it))
        }
    }

    @Test fun saveRoundTrip() {
        val parsed = SyncJson.parseSave(JSONObject(SyncJson.save(post).toString()))!!
        assertEquals(post.id, parsed.id)
        assertEquals(post.url, parsed.url)
        assertEquals(post.platform, parsed.platform)
        assertEquals(post.title, parsed.title)
        assertEquals(post.authorName, parsed.authorName)
        assertEquals(post.sourceHost, parsed.sourceHost)
        assertEquals(post.thumbnailUrl, parsed.thumbnailUrl)
        assertEquals(post.notes, parsed.note)
        assertEquals(post.isFavorite, parsed.isFavorite)
        assertEquals(post.categoryId, parsed.collectionId)
        assertEquals(post.createdAt, parsed.createdAt)
        assertEquals(post.updatedAt, parsed.updatedAt)
        assertFalse(parsed.deleted)

        val back = SyncMerge.postFromRemote(parsed, local = post)
        assertEquals(post.copy(dirty = false), back)
    }

    @Test fun collectionRoundTrip() {
        val json = SyncJson.collection(category)
        assertEquals(
            setOf("id", "name", "color_hex", "sort_order", "created_at", "updated_at", "deleted"),
            json.keys().asSequence().toSet(),
        )
        val parsed = SyncJson.parseCollection(JSONObject(json.toString()))!!
        assertEquals(
            RemoteCollection(category.id, "Recipes", "#FF9500", 3, created, updated, deleted = false),
            parsed,
        )
        val plan = SyncMerge.planCategory(parsed, local = null, tombstoneAt = null) as CategoryPlan.Upsert
        assertEquals(category.copy(dirty = false), plan.category)
    }

    @Test fun tombstonesAreMinimalAndGoFirst() {
        val request = SyncJson.request(
            cursor = "abc",
            categories = listOf(category),
            deletedCategories = listOf(DeletedCategory("c-dead", updated)),
            posts = listOf(post),
            deletedPosts = listOf(DeletedPost("p-dead", updated)),
        )
        assertEquals("abc", request.getString("cursor"))
        val saves = request.getJSONArray("saves")
        assertEquals(2, saves.length())
        val tombstone = saves.getJSONObject(0)
        assertEquals(setOf("id", "updated_at", "deleted"), tombstone.keys().asSequence().toSet())
        assertEquals("p-dead", tombstone.getString("id"))
        assertTrue(tombstone.getBoolean("deleted"))
        assertEquals(post.id, saves.getJSONObject(1).getString("id"))
        assertEquals("c-dead", request.getJSONArray("collections").getJSONObject(0).getString("id"))
    }

    @Test fun nullCursorIsSentAsJsonNull() {
        val request = SyncJson.request(null, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(request.has("cursor"))
        assertTrue(request.isNull("cursor"))
    }

    @Test fun parsesResponseLeniently() {
        val data = JSONObject()
            .put("cursor", "next")
            .put("has_more", true)
            .put(
                "collections",
                JSONArray().put(JSONObject().put("id", "c1").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", true)),
            )
            .put(
                "saves",
                JSONArray()
                    .put(JSONObject().put("id", "s1").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", 1))
                    .put(JSONObject().put("id", "no-clock").put("url", "https://x.com/a/status/1"))
                    .put(JSONObject().put("id", "no-url").put("updated_at", "2026-11-07T10:05:00.123Z")),
            )
            .put("rejected", JSONArray().put("c9").put("s9"))
        val response = SyncJson.parseResponse(data)
        assertEquals("next", response.cursor)
        assertTrue(response.hasMore)
        assertTrue(response.collections.single().deleted)
        val save = response.saves.single()
        assertEquals("s1", save.id)
        assertTrue(save.deleted)
        assertEquals(listOf("c9", "s9"), response.rejected)
    }

    @Test fun nullCursorInResponse() {
        assertNull(SyncJson.parseResponse(JSONObject().put("cursor", JSONObject.NULL)).cursor)
    }
}
