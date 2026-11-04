package com.shoto.socialsave.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shoto.socialsave.container
import com.shoto.socialsave.core.LinkMetadataFetcher
import com.shoto.socialsave.core.TitleSanitizer
import com.shoto.socialsave.data.MetadataStatus
import com.shoto.socialsave.data.SavedPost
import com.shoto.socialsave.data.ThumbnailStore
import com.shoto.socialsave.widget.WidgetUpdater
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

        // New links may arrive while we work, so keep going until a pass finds nothing new.
        while (true) {
            val batch = dao.needingMetadata().filter { it.id !in tried }
            if (batch.isEmpty()) break
            tried += batch.map { it.id }
            val gate = Semaphore(MAX_CONCURRENT)
            coroutineScope {
                batch.map { post -> async { gate.withPermit { enrich(post) } } }.awaitAll()
            }
            WidgetUpdater.update(applicationContext)
        }
        return Result.success()
    }

    private suspend fun enrich(start: SavedPost) {
        val container = applicationContext.container
        val dao = container.database.posts()
        dao.update(start.copy(metadataStatus = MetadataStatus.LOADING))

        val metadata = runCatching { fetcher.fetch(start.canonicalUrl.ifEmpty { start.url }) }.getOrNull()
        val current = dao.get(start.id) ?: return
        val now = System.currentTimeMillis()

        if (metadata == null) {
            val attempts = current.metadataAttempts + 1
            dao.update(
                current.copy(
                    metadataAttempts = attempts,
                    metadataStatus = if (attempts >= MAX_ATTEMPTS) MetadataStatus.UNAVAILABLE else MetadataStatus.FAILED,
                    metadataUpdatedAt = now,
                ),
            )
            return
        }

        var updated = current.copy(
            title = TitleSanitizer.cleaned(metadata.title) ?: current.title,
            authorName = current.authorName?.takeIf { it.isNotEmpty() } ?: metadata.authorName,
            thumbnailUrl = metadata.thumbnailUrl?.takeIf { it.isNotEmpty() } ?: current.thumbnailUrl,
            sourceHost = metadata.sourceHost?.takeIf { it.isNotEmpty() } ?: current.sourceHost,
            metadataUpdatedAt = now,
        )

        metadata.thumbnail?.let { jpeg ->
            container.thumbnails.save(current.id, jpeg)?.let { file ->
                container.thumbnails.delete(current.thumbnailFile)
                updated = updated.copy(thumbnailFile = file)
            }
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
        dao.update(updated)
    }

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
