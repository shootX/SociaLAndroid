package com.shoto.socialsave.account

import com.shoto.socialsave.core.PlatformDetector
import com.shoto.socialsave.core.SocialPlatform
import com.shoto.socialsave.core.UrlNormalizer
import com.shoto.socialsave.data.Category
import com.shoto.socialsave.data.MetadataStatus
import com.shoto.socialsave.data.SavedPost

sealed interface CategoryPlan {
    data object Skip : CategoryPlan
    data class Remove(val id: String) : CategoryPlan
    data class Upsert(val category: Category) : CategoryPlan
}

sealed interface SavePlan {
    data object Skip : SavePlan

    /** Delete the local row and its thumbnail file. */
    data class Remove(val local: SavedPost) : SavePlan

    /** Write [post]; first delete [replaced], a different local row with the same canonical URL. */
    data class Upsert(val post: SavedPost, val replaced: SavedPost? = null) : SavePlan
}

/** The client's decisions in step 4 of the sync algorithm, as plain functions. */
object SyncMerge {
    /** An incoming row wins unless the local copy is dirty with a newer `updatedAt`. */
    fun incomingWins(localDirty: Boolean, localUpdatedAt: Long, incomingUpdatedAt: Long): Boolean =
        !(localDirty && localUpdatedAt > incomingUpdatedAt)

    /** The key the unique index uses; the same normalizer the save flow uses. */
    fun canonicalOf(url: String): String = UrlNormalizer.canonicalUrl(url) ?: url

    /**
     * [tombstoneAt]: when this phone deleted the item, if that delete hasn't been sent yet. A pending
     * delete counts as a dirty local copy, so it beats an older incoming row.
     */
    fun planCategory(incoming: RemoteCollection, local: Category?, tombstoneAt: Long?): CategoryPlan {
        if (incoming.deleted) {
            return if (local != null && incomingWins(local.dirty, local.updatedAt, incoming.updatedAt)) {
                CategoryPlan.Remove(local.id)
            } else {
                CategoryPlan.Skip
            }
        }
        if (local != null && !incomingWins(local.dirty, local.updatedAt, incoming.updatedAt)) return CategoryPlan.Skip
        if (local == null && tombstoneAt != null && !incomingWins(true, tombstoneAt, incoming.updatedAt)) {
            return CategoryPlan.Skip
        }
        return CategoryPlan.Upsert(
            Category(
                id = incoming.id,
                name = incoming.name,
                colorHex = incoming.colorHex,
                createdAt = incoming.createdAt,
                sortOrder = incoming.sortOrder,
                updatedAt = incoming.updatedAt,
                dirty = false,
            ),
        )
    }

    /**
     * [clash]: the local row holding the incoming row's canonical URL, if any (it may be [local] itself).
     * [now] stamps a merged row that has to go back to the server.
     */
    fun planSave(
        incoming: RemoteSave,
        local: SavedPost?,
        tombstoneAt: Long?,
        clash: SavedPost?,
        now: Long,
    ): SavePlan {
        if (incoming.deleted) {
            return if (local != null && incomingWins(local.dirty, local.updatedAt, incoming.updatedAt)) {
                SavePlan.Remove(local)
            } else {
                SavePlan.Skip
            }
        }
        if (local != null && !incomingWins(local.dirty, local.updatedAt, incoming.updatedAt)) return SavePlan.Skip
        if (local == null && tombstoneAt != null && !incomingWins(true, tombstoneAt, incoming.updatedAt)) {
            return SavePlan.Skip
        }

        val post = postFromRemote(incoming, local)
        val other = clash?.takeIf { it.id != incoming.id } ?: return SavePlan.Upsert(post)
        return SavePlan.Upsert(mergeClash(post, other, incoming.updatedAt, now), replaced = other)
    }

    /**
     * A canonical clash: the local row goes away after giving its note/favorite to the incoming row when that
     * one has none, and its fetched title/author/thumbnail URL/host where the incoming row's are empty. Its
     * thumbnail is the same picture, so it is kept instead of fetched again. If the merge changed what the
     * server has, the row is marked dirty so the merge is uploaded: a note/favorite as a new edit ([now]),
     * fetched fields alone under the metadata rule (one millisecond past the server's version).
     */
    fun mergeClash(incoming: SavedPost, replaced: SavedPost, incomingUpdatedAt: Long, now: Long): SavedPost {
        var merged = incoming.copy(
            notes = incoming.notes ?: replaced.notes,
            isFavorite = incoming.isFavorite || replaced.isFavorite,
            title = incoming.title.orIfBlank(replaced.title),
            authorName = incoming.authorName.orIfBlank(replaced.authorName),
            thumbnailUrl = incoming.thumbnailUrl.orIfBlank(replaced.thumbnailUrl),
            sourceHost = incoming.sourceHost.orIfBlank(replaced.sourceHost),
        )
        if (merged.thumbnailFile == null && replaced.thumbnailFile != null) {
            merged = merged.copy(
                thumbnailFile = replaced.thumbnailFile,
                metadataStatus = replaced.metadataStatus,
                metadataAttempts = replaced.metadataAttempts,
                metadataUpdatedAt = replaced.metadataUpdatedAt,
            )
        }
        val metadataFilled = merged.title != incoming.title ||
            merged.authorName != incoming.authorName ||
            merged.thumbnailUrl != incoming.thumbnailUrl ||
            merged.sourceHost != incoming.sourceHost
        if (merged.notes != incoming.notes || merged.isFavorite != incoming.isFavorite) {
            merged = merged.copy(dirty = true, updatedAt = maxOf(now, incomingUpdatedAt + 1))
        } else if (metadataFilled) {
            merged = merged.copy(dirty = true, updatedAt = incomingUpdatedAt + 1)
        }
        return merged
    }

    /**
     * The incoming row as a local entity. Local-only fields (thumbnail file, metadata status) are kept.
     * Title/author/thumbnail URL are fetched data, not user edits: an empty incoming value keeps the local one.
     */
    fun postFromRemote(incoming: RemoteSave, local: SavedPost?): SavedPost {
        val canonical = canonicalOf(incoming.url)
        val platform = SocialPlatform.entries.firstOrNull { it.rawValue == incoming.platform }
            ?: PlatformDetector.detect(canonical)
        return SavedPost(
            id = incoming.id,
            url = incoming.url,
            canonicalUrl = canonical,
            platform = platform.rawValue,
            title = incoming.title.blankToNull() ?: local?.title,
            authorName = incoming.authorName.blankToNull() ?: local?.authorName,
            thumbnailUrl = incoming.thumbnailUrl.blankToNull() ?: local?.thumbnailUrl,
            thumbnailFile = local?.thumbnailFile,
            sourceHost = incoming.sourceHost.blankToNull() ?: local?.sourceHost ?: UrlNormalizer.hostOf(canonical),
            metadataStatus = local?.metadataStatus ?: MetadataStatus.PENDING,
            metadataAttempts = local?.metadataAttempts ?: 0,
            metadataUpdatedAt = local?.metadataUpdatedAt,
            notes = incoming.note.blankToNull(),
            isFavorite = incoming.isFavorite,
            createdAt = incoming.createdAt,
            categoryId = incoming.collectionId,
            updatedAt = incoming.updatedAt,
            dirty = false,
        )
    }

    /**
     * Whether a metadata fetch changed something other phones should get. A new thumbnail URL alone doesn't
     * count once there is one: signed CDN links change on every fetch.
     */
    fun metadataNeedsSync(before: SavedPost, after: SavedPost): Boolean =
        before.title != after.title ||
            before.authorName != after.authorName ||
            (before.thumbnailUrl == null && after.thumbnailUrl != null) ||
            (before.sourceHost == null && after.sourceHost != null)

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    /** [other] when this is empty and [other] isn't; otherwise this, unchanged. */
    private fun String?.orIfBlank(other: String?): String? = if (isNullOrBlank() && !other.isNullOrBlank()) other else this
}
