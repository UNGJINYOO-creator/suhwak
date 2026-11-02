package com.suhwak.app

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.lifecycleScope
import com.suhwak.app.data.Backup
import com.suhwak.app.data.DefaultDecks
import com.suhwak.app.data.ParentAlerts
import com.suhwak.app.data.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.suhwak.app.data.StudySettings
import com.suhwak.app.ui.FishingScreen
import com.suhwak.app.ui.DeckScreen
import com.suhwak.app.ui.HomeScreen
import com.suhwak.app.ui.MatchScreen
import com.suhwak.app.ui.OnboardingScreen
import com.suhwak.app.ui.ReviewScreen
import com.suhwak.app.ui.ScanScreen
import com.suhwak.app.ui.SettingsScreen
import com.suhwak.app.ui.StudyScreen
import com.suhwak.app.ui.theme.WordBookTheme

/** Several photo addresses in one navigation argument, one per line. */
private fun encodeUris(uris: List<Uri>) = Uri.encode(uris.joinToString("\n"))

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("home", "홈", Icons.Default.Home),
    Tab("review", "복습·통계", Icons.Default.Insights),
    Tab("settings", "설정", Icons.Default.Settings),
)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = StudySettings(this)
        lifecycleScope.launch(Dispatchers.IO) {
            com.suhwak.app.data.ProgressReset.finishUndo(applicationContext)
            DefaultDecks.seed(applicationContext)
            // After seeding, so built-in words are matched rather than added twice.
            com.suhwak.app.sync.FamilySync.run(applicationContext)
            // Times set on the other device (reminder, evening report) take effect here too.
            Reminder.schedule(applicationContext)
            com.suhwak.app.data.EveningReport.schedule(applicationContext)
            ParentAlerts.checkStreak(applicationContext)
            Backup.weeklyToTelegram(applicationContext)
        }
        Reminder.schedule(applicationContext)
        com.suhwak.app.data.EveningReport.schedule(applicationContext)
        if (android.os.Build.VERSION.SDK_INT >= 33 && settings.reminderHour >= 0) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            WordBookTheme {
                var onboarding by remember { mutableStateOf(!settings.onboarded) }
                if (onboarding) {
                    OnboardingScreen(onDone = { settings.onboarded = true; onboarding = false })
                } else {
                    AppContent()
                }
                com.suhwak.app.ui.QuietUpdateCheck()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppContent() {
    // 가족 동기화 whenever the app goes to the background, so the other device sees today's progress.
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        var started = false
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            // The first ON_START is the launch itself, already synced after seeding in onCreate.
            val coming = event == androidx.lifecycle.Lifecycle.Event.ON_START && started.also { started = true }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP || coming) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { com.suhwak.app.sync.FamilySync.run(context.applicationContext) }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        // The tab bar only shows on the three top-level screens.
                        if (Tabs.any { it.route == route }) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                Tabs.forEach { tab ->
                                    NavigationBarItem(
                                        selected = route == tab.route,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            // A new build waits in 설정: just a small dot, no window.
                                            if (tab.route == "settings" && com.suhwak.app.net.Updater.available.value != null) {
                                                androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge() }) { Icon(tab.icon, contentDescription = null) }
                                            } else {
                                                Icon(tab.icon, contentDescription = null)
                                            }
                                        },
                                        label = { Text(tab.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { padding ->
                    // On a wide tablet screen the pages keep a readable width in the middle instead of stretching.
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
                    ) {
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        // imePadding: every screen shrinks above the keyboard instead of being covered by it.
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().imePadding(),
                    ) {
                        composable("home") {
                            HomeScreen(
                                onPhoto = { uris -> nav.navigate("scan?uri=${encodeUris(uris)}&deckId=-1") },
                                onOpenDeck = { id -> nav.navigate("deck/$id") },
                                onToday = { nav.navigate("study/-1?today=true") },
                                onStudyDeck = { id -> nav.navigate("study/$id") },
                                onWrongNote = { nav.navigate("study/-1?wrong=true") },
                                onWrongList = { nav.navigate("wrongnote") },
                                onFishing = { giant -> nav.navigate(if (giant) "fishing?giant=true" else "fishing") },
                            )
                        }
                        composable("review") {
                            ReviewScreen(
                                onReview = { nav.navigate("study/-1?review=true") },
                                onStudyAll = { nav.navigate("study/-1?all=true") },
                                onWeekly = { nav.navigate("weekly") },
                            )
                        }
                        composable("wrongnote") {
                            com.suhwak.app.ui.WrongNoteScreen(
                                onBack = { nav.popBackStack() },
                                onStudy = { nav.navigate("study/-1?wrong=true") },
                            )
                        }
                        composable("weekly") {
                            com.suhwak.app.ui.WeeklyReportScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(onAdd = { kind ->
                                // The home screen opens the camera, album or name dialog.
                                com.suhwak.app.ui.AddRequest.kind = kind
                                nav.navigate("home") {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            })
                        }
                        composable(
                            "scan?uri={uri}&deckId={deckId}",
                            arguments = listOf(
                                navArgument("uri") { type = NavType.StringType },
                                navArgument("deckId") { type = NavType.LongType; defaultValue = -1L },
                            ),
                        ) {
                            ScanScreen(
                                onBack = { nav.popBackStack() },
                                onStudy = { deckId ->
                                    nav.navigate("study/$deckId") { popUpTo("home") }
                                },
                                onHome = { nav.popBackStack("home", inclusive = false) },
                            )
                        }
                        composable(
                            "fishing?giant={giant}",
                            arguments = listOf(navArgument("giant") { type = NavType.BoolType; defaultValue = false }),
                        ) { entry ->
                            FishingScreen(
                                giantOnly = entry.arguments?.getBoolean("giant") == true,
                                onBack = { nav.popBackStack() },
                                // 오늘 학습 again: the reviews are done, so it brings the next new words.
                                onMoreWords = { nav.navigate("study/-1?today=true") { popUpTo("home") } },
                            )
                        }
                        composable(
                            "match/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
                        ) {
                            MatchScreen(onBack = { nav.popBackStack() })
                        }
                        composable(
                            "deck/{deckId}",
                            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
                        ) {
                            DeckScreen(
                                onBack = { nav.popBackStack() },
                                onPhoto = { deckId, uris ->
                                    nav.navigate("scan?uri=${encodeUris(uris)}&deckId=$deckId")
                                },
                                onStudy = { deckId, scope, modes, staged ->
                                    val onlyUnknown = scope == com.suhwak.app.data.StudyScope.UNKNOWN
                                    val starred = scope == com.suhwak.app.data.StudyScope.STARRED
                                    nav.navigate("study/$deckId?onlyUnknown=$onlyUnknown&starred=$starred&mode=$modes&staged=$staged")
                                },
                                onTest = { deckId -> nav.navigate("study/$deckId?test=true") },
                                onMatch = { deckId -> nav.navigate("match/$deckId") },
                            )
                        }
                        composable(
                            "study/{deckId}?onlyUnknown={onlyUnknown}&review={review}&mode={mode}&staged={staged}&all={all}&today={today}&wrong={wrong}&test={test}&starred={starred}",
                            arguments = listOf(
                                navArgument("deckId") { type = NavType.LongType },
                                navArgument("onlyUnknown") { type = NavType.BoolType; defaultValue = false },
                                navArgument("review") { type = NavType.BoolType; defaultValue = false },
                                navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                                navArgument("staged") { type = NavType.StringType; defaultValue = "" },
                                navArgument("all") { type = NavType.BoolType; defaultValue = false },
                                navArgument("today") { type = NavType.BoolType; defaultValue = false },
                                navArgument("wrong") { type = NavType.BoolType; defaultValue = false },
                                navArgument("test") { type = NavType.BoolType; defaultValue = false },
                                navArgument("starred") { type = NavType.BoolType; defaultValue = false },
                            ),
                        ) {
                            StudyScreen(onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") })
                        }
                    }
                    }
                }
}
