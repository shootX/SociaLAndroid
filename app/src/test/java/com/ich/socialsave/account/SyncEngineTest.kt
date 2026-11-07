package com.ich.socialsave.account

import com.ich.socialsave.data.Category
import com.ich.socialsave.data.DeletedCategory
import com.ich.socialsave.data.DeletedPost
import com.ich.socialsave.data.SavedPost
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** One or more sync rounds against a fake server, with an in-memory store that enforces the unique canonical URL. */
class SyncEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var store: FakeStore
    private lateinit var cursors: FakeCursor
    private lateinit var engine: SyncEngine

    private val t0 = Instant.parse("2026-11-07T10:00:00.000Z").toEpochMilli()
    private val t1 = Instant.parse("2026-11-07T10:05:00.123Z").toEpochMilli()
    private val now = Instant.parse("2026-11-07T12:00:00.000Z").toEpochMilli()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        store = FakeStore()
        cursors = FakeCursor()
        val api = ApiClient(server.url("/api/v1").toString(), OkHttpClient()) { "test-token" }
        engine = SyncEngine(api, store, cursors) { now }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun firstRoundSendsTheContractShapeAndClearsWhatWasSent() = runBlocking {
        val category = Category(id = "c-1", name = "Recipes", colorHex = "#FF9500", createdAt = t0, sortOrder = 0, updatedAt = t1)
        val post = post("p-1", "https://www.youtube.com/watch?v=abc", updatedAt = t1).copy(
            title = null,
            thumbnailUrl = "https://i.ytimg.com/vi/abc/hq.jpg",
            notes = "watch later",
            categoryId = "c-1",
            thumbnailFile = "p-1.jpg",
        )
        store.categories[category.id] = category
        store.posts[post.id] = post
        store.deletedPosts["p-gone"] = t1
        store.deletedCategories["c-gone"] = t1
        server.enqueue(ok(cursor = "cursor-1", saves = JSONArray().put(SyncJson.save(post))))

        val result = engine.run()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/sync", request.path)
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.isNull("cursor"))

        val collections = body.getJSONArray("collections")
        collections.getJSONObject(0).let {
            assertEquals(setOf("id", "updated_at", "deleted"), it.keys().asSequence().toSet())
            assertEquals("c-gone", it.getString("id"))
            assertEquals("2026-11-07T10:05:00.123Z", it.getString("updated_at"))
            assertTrue(it.getBoolean("deleted"))
        }
        collections.getJSONObject(1).let {
            assertEquals("c-1", it.getString("id"))
            assertEquals("Recipes", it.getString("name"))
            assertEquals("#FF9500", it.getString("color_hex"))
            assertEquals(0, it.getInt("sort_order"))
            assertEquals("2026-11-07T10:00:00.000Z", it.getString("created_at"))
            assertEquals("2026-11-07T10:05:00.123Z", it.getString("updated_at"))
            assertFalse(it.getBoolean("deleted"))
        }

        val saves = body.getJSONArray("saves")
        assertEquals(2, saves.length())
        saves.getJSONObject(0).let {
            assertEquals(setOf("id", "updated_at", "deleted"), it.keys().asSequence().toSet())
            assertEquals("p-gone", it.getString("id"))
            assertTrue(it.getBoolean("deleted"))
            assertEquals("2026-11-07T10:05:00.123Z", it.getString("updated_at"))
        }
        saves.getJSONObject(1).let {
            assertEquals("p-1", it.getString("id"))
            assertEquals("https://www.youtube.com/watch?v=abc", it.getString("url"))
            assertEquals("youtube", it.getString("platform"))
            assertTrue(it.has("title") && it.isNull("title"))
            assertEquals("https://i.ytimg.com/vi/abc/hq.jpg", it.getString("thumbnail_url"))
            assertEquals("watch later", it.getString("note"))
            assertEquals("c-1", it.getString("collection_id"))
            assertFalse(it.getBoolean("is_favorite"))
            assertFalse(it.getBoolean("deleted"))
            assertFalse("thumbnail file is local-only", it.toString().contains("p-1.jpg"))
            listOf("created_at", "updated_at").forEach { key ->
                assertTrue(it.getString(key).matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")))
            }
        }

        assertTrue(result.complete)
        assertEquals(1, result.rounds)
        assertEquals("cursor-1", cursors.cursor)
        assertFalse(store.posts.getValue("p-1").dirty)
        assertEquals("p-1.jpg", store.posts.getValue("p-1").thumbnailFile)
        assertFalse(store.categories.getValue("c-1").dirty)
        assertTrue(store.deletedPosts.isEmpty())
        assertTrue(store.deletedCategories.isEmpty())
    }

    @Test fun editMadeDuringTheRequestStaysDirtyAndWins() = runBlocking {
        store.posts["p-1"] = post("p-1", "https://x.com/a/status/1", updatedAt = t0)
        val requests = mutableListOf<JSONObject>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = JSONObject(request.body.readUtf8())
                requests += body
                return if (requests.size == 1) {
                    // The user edits the note while the first request is on the wire.
                    store.posts["p-1"] = store.posts.getValue("p-1").copy(notes = "edited", updatedAt = t1, dirty = true)
                    // The server echoes the version it was sent, with a title fetched elsewhere.
                    val echoed = body.getJSONArray("saves").getJSONObject(0).put("title", "Server title")
                    ok(cursor = "c1", saves = JSONArray().put(echoed))
                } else {
                    ok(cursor = "c2", saves = JSONArray().put(body.getJSONArray("saves").getJSONObject(0)))
                }
            }
        }

        val result = engine.run()

        assertEquals(2, requests.size)
        assertEquals(2, result.rounds)
        assertEquals("c1", requests[1].getString("cursor"))
        val resent = requests[1].getJSONArray("saves").getJSONObject(0)
        assertEquals("edited", resent.getString("note"))
        assertEquals("2026-11-07T10:05:00.123Z", resent.getString("updated_at"))
        val final = store.posts.getValue("p-1")
        assertEquals("edited", final.notes)
        assertFalse(final.dirty)
        assertEquals("c2", cursors.cursor)
    }

    @Test fun appliesRemoteRowsAcrossPages() = runBlocking {
        store.posts["gone"] = post("gone", "https://www.reddit.com/r/a/comments/1/x", updatedAt = t0, dirty = false).copy(thumbnailFile = "gone.jpg")
        store.posts["clean"] = post("clean", "https://www.pinterest.com/pin/1", updatedAt = t0, dirty = false).copy(thumbnailFile = "clean.jpg")
        store.categories["old-cat"] = Category(id = "old-cat", name = "Old", colorHex = "#000000", createdAt = t0, sortOrder = 0, updatedAt = t0, dirty = false)
        store.posts["clean"] = store.posts.getValue("clean").copy(categoryId = "old-cat")

        server.enqueue(
            ok(
                cursor = "page-1",
                hasMore = true,
                collections = JSONArray()
                    .put(JSONObject().put("id", "new-cat").put("name", "Cars").put("color_hex", "#3F5C6A").put("sort_order", 2)
                        .put("created_at", "2026-11-07T10:00:00.000Z").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", false))
                    .put(JSONObject().put("id", "old-cat").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", true)),
                saves = JSONArray()
                    .put(remoteSave("new", "https://www.tiktok.com/@a/video/9", collectionId = "new-cat"))
                    .put(JSONObject().put("id", "gone").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", true)),
            ),
        )
        server.enqueue(ok(cursor = "page-2", saves = JSONArray().put(remoteSave("clean", "https://www.pinterest.com/pin/1", note = "from phone 2"))))

        val result = engine.run()

        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("page-1", JSONObject(server.takeRequest().body.readUtf8()).getString("cursor"))
        assertTrue(result.changedLocally)
        assertTrue("the new post needs a thumbnail", result.needsMetadata)
        assertEquals("page-2", cursors.cursor)

        assertEquals("Cars", store.categories.getValue("new-cat").name)
        assertFalse("old-cat" in store.categories)
        assertFalse("gone" in store.posts)
        assertEquals(listOf("gone.jpg"), store.deletedFiles)
        store.posts.getValue("new").let {
            assertEquals("new-cat", it.categoryId)
            assertEquals("tiktok", it.platform)
            assertNull(it.thumbnailFile)
            assertFalse(it.dirty)
        }
        store.posts.getValue("clean").let {
            assertEquals("from phone 2", it.notes)
            assertEquals("clean.jpg", it.thumbnailFile)
            assertNull("its category was deleted on another phone", it.categoryId)
        }
    }

    @Test fun canonicalClashMergesIntoTheIncomingRowAndUploadsTheMerge() = runBlocking {
        val url = "https://www.instagram.com/p/ABC/"
        store.posts["local"] = post("local", url, updatedAt = t0, dirty = false).copy(notes = "mine", isFavorite = true, thumbnailFile = "local.jpg")
        server.enqueue(ok(cursor = "c1", saves = JSONArray().put(remoteSave("server", url))))
        server.enqueue(ok(cursor = "c2"))

        val result = engine.run()

        assertFalse("local" in store.posts)
        store.posts.getValue("server").let {
            assertEquals("mine", it.notes)
            assertTrue(it.isFavorite)
            assertEquals("local.jpg", it.thumbnailFile)
            assertFalse("uploaded in the second round", it.dirty)
        }
        assertTrue("the thumbnail moved, it wasn't deleted", store.deletedFiles.isEmpty())
        assertEquals(2, result.rounds)
        server.takeRequest()
        val merged = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("saves").getJSONObject(0)
        assertEquals("server", merged.getString("id"))
        assertEquals("mine", merged.getString("note"))
        assertTrue(merged.getBoolean("is_favorite"))
    }

    @Test fun heldCursorStopsTheRun() = runBlocking {
        cursors.cursor = "same"
        server.enqueue(ok(cursor = "same", hasMore = true))

        val result = engine.run()

        assertEquals(1, server.requestCount)
        assertTrue(result.retryLater)
        assertFalse(result.complete)
    }

    @Test fun unauthorizedKeepsLocalChanges() = runBlocking {
        store.posts["p-1"] = post("p-1", "https://x.com/a/status/1", updatedAt = t0)
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"success":false,"message":"Unauthenticated."}"""))

        try {
            engine.run()
            fail("expected 401")
        } catch (error: ApiException) {
            assertTrue(error.unauthorized)
        }
        assertTrue(store.posts.getValue("p-1").dirty)
        assertNull(cursors.cursor)
    }

    private fun post(id: String, url: String, updatedAt: Long, dirty: Boolean = true) = SavedPost(
        id = id,
        url = url,
        canonicalUrl = SyncMerge.canonicalOf(url),
        platform = com.ich.socialsave.core.PlatformDetector.detect(url).rawValue,
        createdAt = t0,
        updatedAt = updatedAt,
        dirty = dirty,
    )

    private fun remoteSave(id: String, url: String, note: String? = null, collectionId: String? = null) = JSONObject()
        .put("id", id).put("url", url).put("platform", com.ich.socialsave.core.PlatformDetector.detect(url).rawValue)
        .put("title", JSONObject.NULL).put("author_name", JSONObject.NULL).put("source_host", JSONObject.NULL)
        .put("thumbnail_url", JSONObject.NULL).put("note", note ?: JSONObject.NULL).put("is_favorite", false)
        .put("collection_id", collectionId ?: JSONObject.NULL)
        .put("created_at", "2026-11-07T10:00:00.000Z").put("updated_at", "2026-11-07T10:05:00.123Z").put("deleted", false)

    private fun ok(
        cursor: String,
        hasMore: Boolean = false,
        collections: JSONArray = JSONArray(),
        saves: JSONArray = JSONArray(),
    ): MockResponse {
        val data = JSONObject().put("cursor", cursor).put("has_more", hasMore)
            .put("collections", collections).put("saves", saves).put("rejected", JSONArray())
        return MockResponse().setHeader("Content-Type", "application/json")
            .setBody(JSONObject().put("success", true).put("data", data).toString())
    }
}

private class FakeCursor : SyncCursorStore {
    override var cursor: String? = null
}

private class FakeStore : SyncLocalStore {
    val posts = linkedMapOf<String, SavedPost>()
    val categories = linkedMapOf<String, Category>()
    val deletedPosts = linkedMapOf<String, Long>()
    val deletedCategories = linkedMapOf<String, Long>()
    val deletedFiles = mutableListOf<String>()

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun dirtyCategories(limit: Int) = categories.values.filter { it.dirty }.sortedBy { it.updatedAt }.take(limit)
    override suspend fun dirtyPosts(limit: Int) = posts.values.filter { it.dirty }.sortedBy { it.updatedAt }.take(limit)
    override suspend fun deletedCategories(limit: Int) = deletedCategories.map { DeletedCategory(it.key, it.value) }.take(limit)
    override suspend fun deletedPosts(limit: Int) = deletedPosts.map { DeletedPost(it.key, it.value) }.take(limit)

    override suspend fun hasPendingChanges() =
        posts.values.any { it.dirty } || categories.values.any { it.dirty } || deletedPosts.isNotEmpty() || deletedCategories.isNotEmpty()

    override suspend fun clearCategoryDirty(id: String, sentUpdatedAt: Long) {
        categories[id]?.takeIf { it.updatedAt == sentUpdatedAt }?.let { categories[id] = it.copy(dirty = false) }
    }

    override suspend fun clearPostDirty(id: String, sentUpdatedAt: Long) {
        posts[id]?.takeIf { it.updatedAt == sentUpdatedAt }?.let { posts[id] = it.copy(dirty = false) }
    }

    override suspend fun dropDeletedCategory(id: String, deletedAt: Long) {
        if (deletedCategories[id] == deletedAt) deletedCategories.remove(id)
    }

    override suspend fun dropDeletedPost(id: String, deletedAt: Long) {
        if (deletedPosts[id] == deletedAt) deletedPosts.remove(id)
    }

    override suspend fun category(id: String) = categories[id]
    override suspend fun categoryDeletedAt(id: String) = deletedCategories[id]
    override suspend fun upsertCategory(category: Category) {
        categories[category.id] = category
    }

    override suspend fun deleteCategory(id: String) {
        categories.remove(id)
        posts.replaceAll { _, post -> if (post.categoryId == id) post.copy(categoryId = null) else post }
    }

    override suspend fun post(id: String) = posts[id]
    override suspend fun postDeletedAt(id: String) = deletedPosts[id]
    override suspend fun postByCanonical(canonicalUrl: String) = posts.values.firstOrNull { it.canonicalUrl == canonicalUrl }

    override suspend fun upsertPost(post: SavedPost) {
        check(posts.values.none { it.id != post.id && it.canonicalUrl == post.canonicalUrl }) {
            "UNIQUE constraint failed: saved_posts.canonicalUrl (${post.canonicalUrl})"
        }
        posts[post.id] = post
    }

    override suspend fun deletePost(id: String) {
        posts.remove(id)
    }

    override fun deleteThumbnail(file: String) {
        deletedFiles += file
    }
}
