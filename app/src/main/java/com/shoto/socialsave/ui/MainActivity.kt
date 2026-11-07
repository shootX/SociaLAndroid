package com.shoto.socialsave.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.glance.action.ActionParameters
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shoto.socialsave.R
import com.shoto.socialsave.container
import com.shoto.socialsave.ui.collections.CollectionDetailScreen
import com.shoto.socialsave.ui.collections.CollectionKind
import com.shoto.socialsave.ui.collections.CollectionsScreen
import com.shoto.socialsave.ui.detail.PostDetailScreen
import com.shoto.socialsave.ui.library.LibraryScreen
import com.shoto.socialsave.ui.onboarding.OnboardingScreen
import com.shoto.socialsave.ui.settings.OneTapGuideScreen
import com.shoto.socialsave.ui.settings.SettingsScreen
import com.shoto.socialsave.ui.theme.SocialSaveTheme
import com.shoto.socialsave.ui.theme.isDarkTheme
import com.shoto.socialsave.ui.theme.LocalBrand
import com.shoto.socialsave.work.MetadataWorker

class MainActivity : ComponentActivity() {
    private val pendingPostId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = this.container
        if (savedInstanceState == null) pendingPostId.value = intent.postId()
        container.ads.gatherConsent(this)

        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle()
            val dark = isDarkTheme(appearance)
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SocialSaveTheme(appearance) {
                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { pendingPostId.value = it.postId() }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }
                AppRoot(pendingPostId.value) { pendingPostId.value = null }
            }
        }
    }

    private fun Intent.postId(): String? = getStringExtra(EXTRA_POST_ID)

    companion object {
        const val EXTRA_POST_ID = "postId"
        /** Glance puts action parameters into the launch intent's extras under the key's name. */
        val postIdParameter = ActionParameters.Key<String>(EXTRA_POST_ID)
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val SETTINGS = "settings"
    const val POST = "post/{id}"
    const val COLLECTION = "collection/{kind}"
    const val GUIDE = "guide"

    fun post(id: String) = "post/$id"
    fun collection(kind: CollectionKind) = "collection/${android.net.Uri.encode(kind.route)}"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, R.string.tab_home, Icons.Outlined.GridView, Icons.Filled.GridView),
    Tab(Routes.COLLECTIONS, R.string.tab_saved, Icons.Outlined.Folder, Icons.Filled.Folder),
    Tab(Routes.SETTINGS, R.string.tab_bar_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
)

@Composable
private fun AppRoot(pendingPostId: String?, onPendingHandled: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.container
    val nav = rememberNavController()
    val onboardingDone by container.settings.onboardingCompleted.collectAsStateWithLifecycle()
    var selecting by remember { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showTabs = route in tabs.map { it.route } && !selecting
    val brand = LocalBrand.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        MetadataWorker.enqueue(context)
        container.sync.requestSync()
        container.rescheduleReminder()
    }

    LaunchedEffect(pendingPostId, onboardingDone) {
        if (pendingPostId != null && onboardingDone) {
            selecting = false
            nav.navigate(Routes.post(pendingPostId)) { launchSingleTop = true }
            onPendingHandled()
        }
    }

    Box(Modifier.fillMaxSize().background(brand.canvas)) {
        Scaffold(
            containerColor = brand.canvas,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                AnimatedVisibility(showTabs, enter = fadeIn(), exit = fadeOut()) {
                    Column {
                        HorizontalDivider(color = brand.line)
                        NavigationBar(containerColor = brand.surface, tonalElevation = 0.dp) {
                            tabs.forEach { tab ->
                                val selected = route == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { nav.switchTab(tab.route) },
                                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                                    label = { Text(stringResource(tab.label), fontSize = 11.sp, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = brand.ink,
                                        selectedTextColor = brand.ink,
                                        unselectedIconColor = brand.gray,
                                        unselectedTextColor = brand.gray,
                                        indicatorColor = brand.line,
                                    ),
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            ) {
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenPost = { nav.navigate(Routes.post(it)) },
                        onSelectionModeChange = { selecting = it },
                    )
                }
                composable(Routes.COLLECTIONS) {
                    CollectionsScreen(onOpen = { nav.navigate(Routes.collection(it)) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenGuide = { nav.navigate(Routes.GUIDE) },
                        onShowOnboarding = { container.settings.setOnboardingCompleted(false) },
                    )
                }
                composable(Routes.POST) { entry ->
                    PostDetailScreen(entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable(Routes.COLLECTION) { entry ->
                    val kind = CollectionKind.fromRoute(entry.arguments?.getString("kind").orEmpty())
                    CollectionDetailScreen(
                        kind = kind,
                        onBack = { nav.popBackStack() },
                        onOpenPost = { nav.navigate(Routes.post(it)) },
                    )
                }
                composable(Routes.GUIDE) {
                    OneTapGuideScreen(onBack = { nav.popBackStack() })
                }
            }
        }

        if (!onboardingDone) {
            OnboardingScreen(onFinish = { container.settings.setOnboardingCompleted(true) })
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
