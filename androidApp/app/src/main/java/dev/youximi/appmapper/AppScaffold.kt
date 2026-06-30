package dev.youximi.appmapper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private enum class MainTab {
    Home,
    Settings,
}

private enum class RootPage {
    Main,
    Logs,
}

@Composable
internal fun AppScaffold(coordinator: AppCoordinator) {
    val appState by rememberAppState(coordinator)
    var rootPage by rememberSaveable { mutableStateOf(RootPage.Main) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var draftPollingMs by rememberSaveable { mutableStateOf(appState.pollingMs) }

    LaunchedEffect(appState.pollingMs) {
        draftPollingMs = appState.pollingMs
    }

    if (rootPage == RootPage.Logs) {
        BackHandler { rootPage = RootPage.Main }
        LogsScreen(
            coordinator = coordinator,
            onBack = { rootPage = RootPage.Main },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Home,
                    onClick = { selectedTab = MainTab.Home },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("首页") },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Settings,
                    onClick = { selectedTab = MainTab.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                MainTab.Home -> HomeScreen(
                    hasUsageAccess = appState.hasUsageAccess,
                    initialTarget = appState.pairingTarget,
                    onSaveTarget = coordinator::saveTarget,
                    onStart = {
                        coordinator.savePollingMs(draftPollingMs)
                        coordinator.startService()
                    },
                    onStop = coordinator::stopService,
                )

                MainTab.Settings -> SettingsScreen(
                    hasUsageAccess = appState.hasUsageAccess,
                    pollingMs = draftPollingMs,
                    onOpenUsageAccess = coordinator::openUsageAccessSettings,
                    onPollingSelected = { draftPollingMs = it },
                    onOpenLogs = { rootPage = RootPage.Logs },
                )
            }
        }
    }
}

@Composable
private fun rememberAppState(coordinator: AppCoordinator): State<AppState> {
    val state = remember { mutableStateOf(coordinator.loadInitialState()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(coordinator, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = coordinator.loadInitialState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
