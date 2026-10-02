package dev.youximi.appmapper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.youximi.appmapper.data.PairingParser
import dev.youximi.appmapper.data.PairingRequest
import dev.youximi.appmapper.service.SyncStatus

private enum class MainTab(val label: String, val icon: ImageVector, val inactiveIcon: ImageVector) {
    Home("首页", Icons.Filled.Home, Icons.Outlined.Home),
    Settings("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
}

private enum class RootPage { Main, Logs, Scanner, Licenses }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppScaffold(coordinator: AppCoordinator) {
    val connectionStatus by SyncStatus.text.collectAsStateWithLifecycle()
    val isSyncRunning by SyncStatus.isRunning.collectAsStateWithLifecycle()
    val appState by rememberAppState(coordinator, connectionStatus)
    var rootPage by rememberSaveable { mutableStateOf(RootPage.Main) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var pollingMs by rememberSaveable(appState.pollingMs) { mutableStateOf(appState.pollingMs) }
    var scanError by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }

    fun completePendingAction() {
        val action = pendingAction
        pendingAction = null
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) action?.invoke()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // A foreground service can run even when notification permission is denied.
        completePendingAction()
    }
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            SyncStatus.text.value = "等待连接"
            completePendingAction()
        } else {
            pendingAction = null
            SyncStatus.text.value = SyncStatus.LocalNetworkPermissionRequired
            showPermissionDialog = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
    }
    val pageState = rememberSaveableStateHolder()

    fun requestNotifications(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestLocalNetwork(action: () -> Unit) {
        if (pendingAction != null) return
        if (hasLocalNetworkAccess(context)) {
            action()
        } else {
            pendingAction = action
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    fun pair(request: PairingRequest) {
        requestLocalNetwork { requestNotifications { coordinator.pair(request) } }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要局域网访问权限") },
            text = { Text("连接电脑需要局域网访问权限。可在应用权限设置中允许后，再点击连接；已有配对会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    coordinator.openAppPermissionSettings()
                }) { Text("应用权限设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            },
        )
    }

    BackHandler(enabled = rootPage != RootPage.Main) { rootPage = RootPage.Main }

    Crossfade(targetState = rootPage, animationSpec = tween(220), label = "root page") { page ->
        pageState.SaveableStateProvider(page.name) {
            if (page == RootPage.Logs) {
                LogsScreen(coordinator = coordinator, onBack = { rootPage = RootPage.Main })
            } else if (page == RootPage.Licenses) {
                OpenSourceLicensesScreen(onBack = { rootPage = RootPage.Main })
            } else if (page == RootPage.Scanner) {
                QrScannerScreen(
                    onBack = { rootPage = RootPage.Main },
                    onResult = { contents ->
                        // Crossfade retains the outgoing camera briefly after Back.
                        if (rootPage == RootPage.Scanner) {
                            val request = PairingParser.parseUri(contents)
                            scanError = if (request == null) "二维码无效或版本过旧，请扫描电脑端当前显示的配对二维码。" else ""
                            rootPage = RootPage.Main
                            if (request != null) pair(request)
                        }
                    },
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 600.dp
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                    val tabState = rememberSaveableStateHolder()

                    Row(Modifier.fillMaxSize()) {
                        if (useRail) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                MainTab.entries.forEach { tab ->
                                    NavigationRailItem(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        icon = { TabIcon(tab, selectedTab) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                        // A single Scaffold owns the bars and insets for both main tabs.
                        Scaffold(
                            modifier = Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection),
                            topBar = {
                                TopAppBar(
                                    title = { Text(if (selectedTab == MainTab.Home) "AppMapper" else "设置") },
                                    scrollBehavior = scrollBehavior,
                                )
                            },
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar {
                                        MainTab.entries.forEach { tab ->
                                            NavigationBarItem(
                                                selected = selectedTab == tab,
                                                onClick = { selectedTab = tab },
                                                icon = { TabIcon(tab, selectedTab) },
                                                label = { Text(tab.label) },
                                            )
                                        }
                                    }
                                }
                            },
                        ) { padding ->
                            Box(
                                Modifier.fillMaxSize().padding(padding)
                                    .consumeWindowInsets(padding).imePadding(),
                            ) {
                                Crossfade(
                                    targetState = selectedTab,
                                    animationSpec = tween(220),
                                    label = "main tab",
                                ) { tab ->
                                    tabState.SaveableStateProvider(tab.name) {
                                        when (tab) {
                                            MainTab.Home -> HomeScreen(
                                                pairedComputer = appState.pairedComputer,
                                                connectionStatus = if (appState.hasLocalNetworkAccess) connectionStatus
                                                    else SyncStatus.LocalNetworkPermissionRequired,
                                                isSyncRunning = isSyncRunning,
                                                scanError = scanError,
                                                onScan = {
                                                    requestLocalNetwork {
                                                        scanError = ""
                                                        rootPage = RootPage.Scanner
                                                    }
                                                },
                                                onPair = ::pair,
                                                onStart = {
                                                    requestLocalNetwork { requestNotifications { coordinator.startService() } }
                                                },
                                                onStop = coordinator::stopService,
                                            )
                                            MainTab.Settings -> SettingsScreen(
                                                hasUsageAccess = appState.hasUsageAccess,
                                                pollingMs = pollingMs,
                                                onOpenUsageAccess = coordinator::openUsageAccessSettings,
                                                onPollingSelected = {
                                                    pollingMs = it
                                                    coordinator.savePollingMs(it)
                                                },
                                                onOpenLogs = { rootPage = RootPage.Logs },
                                                onOpenLicenses = { rootPage = RootPage.Licenses },
                                                pairedComputer = appState.pairedComputer,
                                                onForgetComputer = coordinator::forgetComputer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, selectedTab: MainTab) {
    Icon(if (tab == selectedTab) tab.icon else tab.inactiveIcon, contentDescription = null)
}

@Composable
private fun rememberAppState(coordinator: AppCoordinator, status: String): State<AppState> {
    val state = remember { mutableStateOf(coordinator.loadInitialState()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(status) { state.value = coordinator.loadInitialState() }
    DisposableEffect(coordinator, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = coordinator.loadInitialState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
internal fun ScreenContent(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
