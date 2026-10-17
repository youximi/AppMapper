package dev.youximi.appmapper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import dev.youximi.appmapper.data.PairingParser
import dev.youximi.appmapper.data.PairingRequest
import dev.youximi.appmapper.data.PairingStore
import dev.youximi.appmapper.data.UpdateCheckPhase
import dev.youximi.appmapper.service.ConnectionPhase
import dev.youximi.appmapper.service.ConnectionState
import dev.youximi.appmapper.service.SyncStatus

private enum class MainTab(val label: String, val icon: Int, val inactiveIcon: Int) {
    Home("首页", R.drawable.ic_home_filled, R.drawable.ic_home),
    Settings("设置", R.drawable.ic_settings_filled, R.drawable.ic_settings),
}

private enum class RootPage { Main, Logs, Scanner, Licenses, Devices, About }

@Composable
internal fun AppScaffold(
    coordinator: AppCoordinator,
    dynamicColorEnabled: Boolean,
    themeColor: AppThemeColor,
    onDynamicColorChanged: (Boolean) -> Unit,
    onThemeColorSelected: (AppThemeColor) -> Unit,
) {
    val connection by SyncStatus.connection.collectAsStateWithLifecycle()
    val operationError by SyncStatus.operationError.collectAsStateWithLifecycle()
    val isSyncRunning by SyncStatus.isRunning.collectAsStateWithLifecycle()
    val pairingRevision by PairingStore.changes.collectAsStateWithLifecycle()
    val appState by rememberAppState(coordinator, pairingRevision)
    val updateState by coordinator.updateState.collectAsStateWithLifecycle()
    var includePrereleases by rememberSaveable { mutableStateOf(coordinator.includePrereleases) }
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var updateDownloadError by remember { mutableStateOf<String?>(null) }
    var rootPage by rememberSaveable { mutableStateOf(RootPage.Main) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var pollingMs by rememberSaveable(appState.pollingMs) { mutableLongStateOf(appState.pollingMs) }
    var scanError by rememberSaveable { mutableStateOf("") }
    var scannerReturnPage by rememberSaveable { mutableStateOf(RootPage.Main) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(coordinator) { coordinator.checkUpdatesOnStartup() }
    LaunchedEffect(updateState, resumed, rootPage, selectedTab, pendingAction, showPermissionDialog) {
        val release = updateState.release
        if (resumed && rootPage == RootPage.Main && selectedTab == MainTab.Home &&
            pendingAction == null && !showPermissionDialog &&
            updateState.phase == UpdateCheckPhase.Available && release != null &&
            coordinator.shouldNotifyUpdate(release)) {
            updateDownloadError = null
            showUpdateDialog = true
        }
    }
    if (showUpdateDialog && resumed && !showPermissionDialog && pendingAction == null) {
        updateState.release?.let { release ->
            LaunchedEffect(release.buildNumber) { coordinator.markUpdateNotified(release) }
            UpdateDialog(
                release = release,
                onDismiss = { showUpdateDialog = false; updateDownloadError = null },
                onDownload = {
                    updateDownloadError = coordinator.openRelease(release)
                    if (updateDownloadError == null) showUpdateDialog = false
                },
                downloadError = updateDownloadError,
            )
        }
    }

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
            SyncStatus.connection.value = ConnectionState(message = "等待连接")
            completePendingAction()
        } else {
            pendingAction = null
            SyncStatus.connection.value = ConnectionState(phase = ConnectionPhase.Failed,
                message = SyncStatus.LocalNetworkPermissionRequired)
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

    fun openScanner(returnPage: RootPage) {
        requestLocalNetwork {
            scanError = ""
            SyncStatus.operationError.value = null
            scannerReturnPage = returnPage
            rootPage = RootPage.Scanner
        }
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

    BackHandler(enabled = rootPage != RootPage.Main) {
        rootPage = when (rootPage) {
            RootPage.Scanner -> scannerReturnPage
            RootPage.Licenses -> RootPage.About
            else -> RootPage.Main
        }
    }

    Crossfade(targetState = rootPage, animationSpec = tween(220), label = "root page") { page ->
        pageState.SaveableStateProvider(page.name) {
            if (page == RootPage.Logs) {
                LogsScreen(coordinator = coordinator, onBack = { rootPage = RootPage.Main })
            } else if (page == RootPage.Licenses) {
                OpenSourceLicensesScreen(onBack = { rootPage = RootPage.About })
            } else if (page == RootPage.About) {
                AboutScreen(
                    updateState = updateState,
                    includePrereleases = includePrereleases,
                    onUpdateChannelSelected = {
                        showUpdateDialog = false
                        updateDownloadError = null
                        includePrereleases = it
                        coordinator.setUpdateChannel(it)
                    },
                    onBack = { rootPage = RootPage.Main },
                    onCheckUpdates = coordinator::checkUpdates,
                    onOpenUpdateNotes = {
                        updateDownloadError = null
                        showUpdateDialog = true
                    },
                    onOpenLicenses = { rootPage = RootPage.Licenses },
                )
            } else if (page == RootPage.Devices) {
                DevicesScreen(
                    pairing = appState.pairing,
                    connection = connection,
                    error = scanError.takeIf { it.isNotBlank() } ?: appState.pairingError ?: operationError,
                    onBack = { rootPage = RootPage.Main },
                    onAdd = { openScanner(RootPage.Devices) },
                    onConnect = { serverId ->
                        scanError = ""
                        requestLocalNetwork { requestNotifications { coordinator.connectComputer(serverId) } }
                    },
                    onForget = coordinator::forgetComputer,
                )
            } else if (page == RootPage.Scanner) {
                QrScannerScreen(
                    onBack = { rootPage = scannerReturnPage },
                    onResult = { contents ->
                        // Crossfade retains the outgoing camera briefly after Back.
                        if (rootPage == RootPage.Scanner) {
                            val request = PairingParser.parseUri(contents)
                            scanError = if (request == null) "二维码无效或版本过旧，请扫描电脑端当前显示的配对二维码。" else ""
                            rootPage = scannerReturnPage
                            if (request != null) pair(request)
                        }
                    },
                )
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val useRail = this.maxWidth >= 600.dp
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
                                    title = {
                                        if (selectedTab == MainTab.Home) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Image(
                                                    painterResource(R.drawable.ic_appmapper),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                                Text("AppMapper")
                                            }
                                        } else {
                                            Text("设置")
                                        }
                                    },
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
                                                pairedComputer = appState.pairing.selectedComputer,
                                                savedComputerCount = appState.pairing.computers.size,
                                                connection = if (appState.hasLocalNetworkAccess) connection else
                                                    connection.copy(phase = ConnectionPhase.Failed,
                                                        message = SyncStatus.LocalNetworkPermissionRequired),
                                                isSyncRunning = isSyncRunning,
                                                scanError = scanError,
                                                onScan = { openScanner(RootPage.Main) },
                                                onOpenDevices = {
                                                    scanError = ""
                                                    rootPage = RootPage.Devices
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
                                                dynamicColorEnabled = dynamicColorEnabled,
                                                themeColor = themeColor,
                                                onDynamicColorChanged = onDynamicColorChanged,
                                                onThemeColorSelected = onThemeColorSelected,
                                                onOpenUsageAccess = coordinator::openUsageAccessSettings,
                                                onPollingSelected = {
                                                    pollingMs = it
                                                    coordinator.savePollingMs(it)
                                                },
                                                onOpenLogs = { rootPage = RootPage.Logs },
                                                onOpenAbout = { rootPage = RootPage.About },
                                                savedComputerCount = appState.pairing.computers.size,
                                                onOpenDevices = {
                                                    scanError = ""
                                                    rootPage = RootPage.Devices
                                                },
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
    Icon(painterResource(if (tab == selectedTab) tab.icon else tab.inactiveIcon), contentDescription = null)
}

@Composable
private fun rememberAppState(coordinator: AppCoordinator, pairingRevision: Long): State<AppState> {
    val state = remember { mutableStateOf(coordinator.loadInitialState()) }
    LaunchedEffect(pairingRevision) { state.value = coordinator.loadInitialState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.value = coordinator.loadInitialState()
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
