package com.ich.socialsave

import android.app.Application
import android.content.Context
import com.ich.socialsave.account.AccountSession
import com.ich.socialsave.account.AccountStore
import com.ich.socialsave.account.ApiClient
import com.ich.socialsave.account.FacebookSignIn
import com.ich.socialsave.account.GoogleSignIn
import com.ich.socialsave.account.RoomSyncStore
import com.ich.socialsave.account.SyncEngine
import com.ich.socialsave.account.SyncScheduler
import com.ich.socialsave.account.deviceName
import com.ich.socialsave.ads.NativeAdsController
import com.ich.socialsave.core.ShortLinkExpander
import com.ich.socialsave.data.AppDatabase
import com.ich.socialsave.data.AppLocale
import com.ich.socialsave.data.AppSettings
import com.ich.socialsave.data.LibraryRepository
import com.ich.socialsave.data.ThumbnailStore
import com.ich.socialsave.widget.WidgetUpdater
import com.ich.socialsave.work.MetadataWorker
import com.ich.socialsave.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SocialSaveApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

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

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val accountStore = AccountStore(app)
    private val api = ApiClient(BuildConfig.API_BASE_URL, http) { accountStore.token }
    private val syncStore = RoomSyncStore(database, thumbnails)

    val sync = SyncScheduler(app, scope) { accountStore.canSync }

    val account = AccountSession(
        api = api,
        store = accountStore,
        engine = SyncEngine(api, syncStore, accountStore),
        local = syncStore,
        scope = scope,
        deviceName = deviceName(),
        onSignedIn = { sync.syncNow() },
        onRemoteChanges = { needsMetadata ->
            WidgetUpdater.requestUpdate(app, scope)
            if (needsMetadata) MetadataWorker.enqueue(app)
        },
        onSignedOut = {
            sync.cancel()
            WidgetUpdater.requestUpdate(app, scope)
            FacebookSignIn.forgetToken()
            scope.launch { GoogleSignIn.clearState(app) }
        },
    )

    val repository = LibraryRepository(
        db = database,
        thumbnails = thumbnails,
        expander = ShortLinkExpander(),
        onPostsAdded = { MetadataWorker.enqueue(app) },
        onChanged = {
            WidgetUpdater.requestUpdate(app, scope)
            sync.scheduleDebounced()
        },
        tracksDeletes = { accountStore.hasAccount },
    )

    fun rescheduleReminder() {
        scope.launch { ReminderScheduler.reschedule(app, settings) }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as SocialSaveApplication).container
