package com.shoto.socialsave

import android.app.Application
import android.content.Context
import com.shoto.socialsave.ads.NativeAdsController
import com.shoto.socialsave.core.ShortLinkExpander
import com.shoto.socialsave.data.AppDatabase
import com.shoto.socialsave.data.AppSettings
import com.shoto.socialsave.data.LibraryRepository
import com.shoto.socialsave.data.ThumbnailStore
import com.shoto.socialsave.widget.WidgetUpdater
import com.shoto.socialsave.work.MetadataWorker
import com.shoto.socialsave.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SocialSaveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        container.scope.launch {
            container.database.posts().resetInterruptedMetadata()
            MetadataWorker.enqueue(this@SocialSaveApplication)
        }
    }
}

/** Hand-wired dependencies; the app is small enough not to need a DI framework. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: AppDatabase = AppDatabase.create(app)
    val settings = AppSettings(app)
    val thumbnails = ThumbnailStore(app)
    val ads = NativeAdsController(app)

    val repository = LibraryRepository(
        db = database,
        thumbnails = thumbnails,
        expander = ShortLinkExpander(),
        onPostsAdded = { MetadataWorker.enqueue(app) },
        onChanged = { WidgetUpdater.requestUpdate(app, scope) },
    )

    fun rescheduleReminder() {
        scope.launch { ReminderScheduler.reschedule(app, settings) }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as SocialSaveApplication).container
