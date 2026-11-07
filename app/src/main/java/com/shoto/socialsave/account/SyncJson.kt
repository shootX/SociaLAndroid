package com.shoto.socialsave.account

import com.shoto.socialsave.data.Category
import com.shoto.socialsave.data.DeletedCategory
import com.shoto.socialsave.data.DeletedPost
import com.shoto.socialsave.data.SavedPost
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A collection as the server sends it. Deleted rows may carry only id + updated_at. */
data class RemoteCollection(
    val id: String,
    val name: String,
    val colorHex: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

/** A save as the server sends it. Deleted rows may carry only id + updated_at. */
data class RemoteSave(
    val id: String,
    val url: String,
    val platform: String?,
    val title: String?,
    val authorName: String?,
    val sourceHost: String?,
    val thumbnailUrl: String?,
    val note: String?,
    val isFavorite: Boolean,
    val collectionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
)

data class SyncResponse(
    val cursor: String?,
    val hasMore: Boolean,
    val collections: List<RemoteCollection>,
    val saves: List<RemoteSave>,
    val rejected: List<String>,
)

/** JSON ↔ entities for `POST /sync`, exactly the contract's field names. */
object SyncJson {
    private val isoMillis: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** ISO-8601 UTC with milliseconds, e.g. `2026-11-07T10:00:00.000Z`. */
    fun formatTime(millis: Long): String = isoMillis.format(Instant.ofEpochMilli(millis))

    /** Accepts `Z` or an offset and any number of fraction digits; sub-millisecond digits are dropped. */
    fun parseTime(text: String): Long =
        runCatching { Instant.parse(text) }.getOrElse { OffsetDateTime.parse(text).toInstant() }.toEpochMilli()

    fun request(
        cursor: String?,
        categories: List<Category>,
        deletedCategories: List<DeletedCategory>,
        posts: List<SavedPost>,
        deletedPosts: List<DeletedPost>,
    ): JSONObject {
        // Tombstones go first: deleting a link and saving it again must reach the server in that order,
        // or the new copy would be merged into the old one and then deleted with it.
        val collections = JSONArray()
        deletedCategories.forEach { collections.put(tombstone(it.id, it.deletedAt)) }
        categories.forEach { collections.put(collection(it)) }
        val saves = JSONArray()
        deletedPosts.forEach { saves.put(tombstone(it.id, it.deletedAt)) }
        posts.forEach { saves.put(save(it)) }
        return JSONObject()
            .put("cursor", cursor ?: JSONObject.NULL)
            .put("collections", collections)
            .put("saves", saves)
    }

    fun collection(category: Category): JSONObject = JSONObject()
        .put("id", category.id)
        .put("name", category.name)
        .put("color_hex", category.colorHex)
        .put("sort_order", category.sortOrder)
        .put("created_at", formatTime(category.createdAt))
        .put("updated_at", formatTime(category.updatedAt))
        .put("deleted", false)

    /** Local-only fields (metadata status/attempts, thumbnail file) are never sent. */
    fun save(post: SavedPost): JSONObject = JSONObject()
        .put("id", post.id)
        .put("url", post.url)
        .put("platform", post.platform)
        .put("title", post.title ?: JSONObject.NULL)
        .put("author_name", post.authorName ?: JSONObject.NULL)
        .put("source_host", post.sourceHost ?: JSONObject.NULL)
        .put("thumbnail_url", post.thumbnailUrl ?: JSONObject.NULL)
        .put("note", post.notes ?: JSONObject.NULL)
        .put("is_favorite", post.isFavorite)
        .put("collection_id", post.categoryId ?: JSONObject.NULL)
        .put("created_at", formatTime(post.createdAt))
        .put("updated_at", formatTime(post.updatedAt))
        .put("deleted", false)

    /** A local delete: only the id, the time of the delete and the flag are known. */
    fun tombstone(id: String, deletedAt: Long): JSONObject = JSONObject()
        .put("id", id)
        .put("updated_at", formatTime(deletedAt))
        .put("deleted", true)

    fun parseResponse(data: JSONObject): SyncResponse = SyncResponse(
        cursor = data.stringOrNull("cursor"),
        hasMore = data.optBoolean("has_more", false),
        collections = data.objects("collections").mapNotNull(::parseCollection),
        saves = data.objects("saves").mapNotNull(::parseSave),
        rejected = data.optJSONArray("rejected")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                when (val item = array.opt(index)) {
                    is JSONObject -> item.stringOrNull("id")
                    null, JSONObject.NULL -> null
                    else -> item.toString()
                }
            }
        }.orEmpty(),
    )

    /** Returns null for a row without an id or clock, so one bad row can't stop the sync. */
    fun parseCollection(json: JSONObject): RemoteCollection? {
        val id = json.stringOrNull("id") ?: return null
        val updatedAt = json.timeOrNull("updated_at") ?: return null
        return RemoteCollection(
            id = id,
            name = json.stringOrNull("name").orEmpty(),
            colorHex = json.stringOrNull("color_hex") ?: DEFAULT_COLOR,
            sortOrder = json.optInt("sort_order", 0),
            createdAt = json.timeOrNull("created_at") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = json.flag("deleted"),
        )
    }

    fun parseSave(json: JSONObject): RemoteSave? {
        val id = json.stringOrNull("id") ?: return null
        val updatedAt = json.timeOrNull("updated_at") ?: return null
        val deleted = json.flag("deleted")
        val url = json.stringOrNull("url")
        if (url.isNullOrBlank() && !deleted) return null
        return RemoteSave(
            id = id,
            url = url.orEmpty(),
            platform = json.stringOrNull("platform"),
            title = json.stringOrNull("title"),
            authorName = json.stringOrNull("author_name"),
            sourceHost = json.stringOrNull("source_host"),
            thumbnailUrl = json.stringOrNull("thumbnail_url"),
            note = json.stringOrNull("note"),
            isFavorite = json.flag("is_favorite"),
            collectionId = json.stringOrNull("collection_id"),
            createdAt = json.timeOrNull("created_at") ?: updatedAt,
            updatedAt = updatedAt,
            deleted = deleted,
        )
    }

    private const val DEFAULT_COLOR = "#5E6A72"

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.timeOrNull(key: String): Long? =
        stringOrNull(key)?.let { runCatching { parseTime(it) }.getOrNull() }

    /** Booleans may arrive as true/false or 1/0. */
    private fun JSONObject.flag(key: String): Boolean = when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value == "true" || value == "1"
        else -> false
    }
}
