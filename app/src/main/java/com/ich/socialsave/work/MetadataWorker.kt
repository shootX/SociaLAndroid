package com.ich.socialsave.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ich.socialsave.account.SyncMerge
import com.ich.socialsave.container
import com.ich.socialsave.core.LinkMetadataFetcher
import com.ich.socialsave.core.TitleSanitizer
import com.ich.socialsave.data.MetadataStatus
import com.ich.socialsave.data.PostDao
import com.ich.socialsave.data.SavedPost
import com.ich.socialsave.data.ThumbnailStore
import com.ich.socialsave.widget.WidgetUpdater
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Fetches titles and preview images for posts that don't have one yet, three at a time. */
class MetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val fetcher = LinkMetadataFetcher(imageConverter = { ThumbnailStore.jpegThumbnail(it) })

    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val dao = container.database.posts()
        val tried = mutableSetOf<String>()
        var syncWorthy = false

        // New links may arrive while we work, so keep going until a pass finds nothing new.
        while (true) {
            val batch = dao.needingMetadata().filter { it.id !in tried }
            if (batch.isEmpty()) break
            tried += batch.map { it.id }
            val gate = Semaphore(MAX_CONCURRENT)
            coroutineScope {
                if (batch.map { post -> async { gate.withPermit { enrich(post) } } }.awaitAll().any { it }) {
                    syncWorthy = true
                }
            }
            WidgetUpdater.update(applicationContext)
        }
        // New titles should reach the user's other phones.
        if (syncWorthy) container.sync.requestSync()
        return Result.success()
    }

    /** Returns true when a field other phones should get (title, author…) changed. */
    private suspend fun enrich(start: SavedPost): Boolean {
        val container = applicationContext.container
        val dao = container.database.posts()
        dao.markLoading(start.id)

        val metadata = runCatching { fetcher.fetch(start.canonicalUrl.ifEmpty { start.url }) }.getOrNull()
        val current = dao.get(start.id) ?: return false
        val now = System.currentTimeMillis()

        if (metadata == null) {
            val attempts = current.metadataAttempts + 1
            dao.saveMetadata(
                current.copy(
                    metadataAttempts = attempts,
                    metadataStatus = if (attempts >= MAX_ATTEMPTS) MetadataStatus.UNAVAILABLE else MetadataStatus.FAILED,
                    metadataUpdatedAt = now,
                ),
            )
            return false
        }

        var updated = current.copy(
            title = TitleSanitizer.cleaned(metadata.title) ?: current.title,
            authorName = current.authorName?.takeIf { it.isNotEmpty() } ?: metadata.authorName,
            thumbnailUrl = metadata.thumbnailUrl?.takeIf { it.isNotEmpty() } ?: current.thumbnailUrl,
            sourceHost = metadata.sourceHost?.takeIf { it.isNotEmpty() } ?: current.sourceHost,
            metadataUpdatedAt = now,
        )

        val savedFile = metadata.thumbnail?.let { jpeg -> container.thumbnails.save(current.id, jpeg) }
        if (savedFile != null) {
            container.thumbnails.delete(current.thumbnailFile)
            updated = updated.copy(thumbnailFile = savedFile)
        }

        updated = if (updated.thumbnailFile != null) {
            updated.copy(metadataAttempts = 0, metadataStatus = MetadataStatus.LOADED)
        } else {
            val attempts = current.metadataAttempts + 1
            updated.copy(
                metadataAttempts = attempts,
                metadataStatus = if (attempts >= MAX_ATTEMPTS) MetadataStatus.UNAVAILABLE else MetadataStatus.FAILED,
            )
        }
        dao.saveMetadata(updated)
        val syncWorthy = SyncMerge.metadataNeedsSync(current, updated)
        if (syncWorthy) dao.markMetadataChanged(current.id)
        // The user deleted the post while we were fetching: don't leave its new thumbnail behind.
        if (savedFile != null && dao.get(current.id) == null) container.thumbnails.delete(savedFile)
        return syncWorthy
    }

    private suspend fun PostDao.saveMetadata(post: SavedPost) = updateMetadata(
        id = post.id,
        title = post.title,
        authorName = post.authorName,
        thumbnailUrl = post.thumbnailUrl,
        sourceHost = post.sourceHost,
        thumbnailFile = post.thumbnailFile,
        status = post.metadataStatus,
        attempts = post.metadataAttempts,
        updatedAt = post.metadataUpdatedAt,
    )

    companion object {
        private const val UNIQUE_NAME = "metadata"
        private const val MAX_CONCURRENT = 3
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MetadataWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
