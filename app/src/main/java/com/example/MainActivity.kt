package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppDatabase
import com.example.data.local.ServerEntity
import com.example.data.model.ConnectionState
import com.example.data.repository.DietPiRepository
import com.example.ui.components.AppHeader
import com.example.ui.components.AppSidebar
import com.example.ui.components.FloatingScreenSwitcher
import com.example.ui.components.ServerSelectorSheet
import com.example.ui.screens.*
import com.example.ui.theme.DietPiGreenPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DietPiViewModel
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    SYSTEM("System", Icons.Default.Speed, "tab_system"),
    PROCESSES("Processes", Icons.Default.ViewList, "tab_processes"),
    SERVICES("Services", Icons.Default.Settings, "tab_services"),
    SOFTWARE("Software", Icons.Default.Apps, "tab_software"),
    TERMINAL("Terminal", Icons.Default.Terminal, "tab_terminal"),
    FILE_BROWSER("Files", Icons.Default.Folder, "tab_file_browser"),
    WEB_UI("Web UI", Icons.Default.Language, "tab_web_ui")
}

class MainActivity : ComponentActivity() {

    private val viewModel: DietPiViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val repository = DietPiRepository(database.serverDao(), applicationContext)
        DietPiViewModel.provideFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                DietPiApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DietPiApp(viewModel: DietPiViewModel) {
    val servers by viewModel.allServers.collectAsStateWithLifecycle()
    val activeServer by viewModel.activeServer.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val stats by viewModel.systemStats.collectAsStateWithLifecycle()
    val hostInfo by viewModel.hostInfo.collectAsStateWithLifecycle()
    val processes by viewModel.processes.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    val software by viewModel.software.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingInitial by viewModel.isLoadingInitial.collectAsStateWithLifecycle()
    val isServerInitComplete by viewModel.isServerInitComplete.collectAsStateWithLifecycle()
    val isPollingActive by viewModel.isPollingActive.collectAsStateWithLifecycle()
    val isScanningNetwork by viewModel.isScanningNetwork.collectAsStateWithLifecycle()
    val discoveredNodes by viewModel.discoveredNodes.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val powerOperationState by viewModel.powerOperationState.collectAsStateWithLifecycle()

    val isInitializing = !isServerInitComplete && activeServer == null

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("dietpi_ui_prefs", Context.MODE_PRIVATE) }
    val initialTab = remember {
        val savedTabName = prefs.getString("last_active_tab", null)
        if (savedTabName != null) {
            try {
                MainTab.valueOf(savedTabName)
            } catch (_: Exception) {
                MainTab.SYSTEM
            }
        } else {
            MainTab.SYSTEM
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(initialTab) }

    LaunchedEffect(currentTab) {
        prefs.edit().putString("last_active_tab", currentTab.name).apply()
    }

    var showServerSelector by remember { mutableStateOf(false) }
    var serverToEditFromBanner by remember { mutableStateOf<ServerEntity?>(null) }
    var webUiReloadTrigger by remember { mutableLongStateOf(0L) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var isTerminalToolbarVisible by rememberSaveable { mutableStateOf(true) }
    var terminalToolbarHeightDp by remember { mutableStateOf(0.dp) }

    // Edge swipe detection: require gesture to start within edge margin (28.dp) to open drawer,
    // preventing accidental triggers while scrolling content or WebUI
    val density = LocalDensity.current
    val edgeSwipeThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    var isEdgeSwipeActive by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isClosed && currentTab != MainTab.SYSTEM) {
        currentTab = MainTab.SYSTEM
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || (currentTab != MainTab.TERMINAL && isEdgeSwipeActive),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(drawerState.isOpen, currentTab) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    isEdgeSwipeActive = down.position.x <= edgeSwipeThresholdPx
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    isEdgeSwipeActive = false
                }
            },
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                AppSidebar(
                    currentTab = currentTab,
                    onSelectTab = { tab ->
                        currentTab = tab
                        coroutineScope.launch { drawerState.close() }
                    },
                    activeServer = activeServer,
                    connectionState = connectionState,
                    onOpenServerSelector = {
                        coroutineScope.launch { drawerState.close() }
                        showServerSelector = true
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppHeader(
                    activeServer = activeServer,
                    connectionState = connectionState,
                    currentTab = currentTab,
                    isRefreshing = isRefreshing,
                    isPollingActive = isPollingActive,
                    isTerminalToolbarVisible = isTerminalToolbarVisible,
                    onToggleSidebar = {
                        coroutineScope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    },
                    onOpenServerSelector = { showServerSelector = true },
                    onTogglePolling = { viewModel.togglePolling() },
                    onRefresh = {
                        when (currentTab) {
                            MainTab.TERMINAL -> viewModel.refreshTerminalTab()
                            MainTab.FILE_BROWSER -> viewModel.loadDirectory()
                            MainTab.WEB_UI -> webUiReloadTrigger = System.currentTimeMillis()
                            else -> viewModel.refreshAll()
                        }
                    },
                    onToggleTerminalToolbar = {
                        isTerminalToolbarVisible = !isTerminalToolbarVisible
                    },
                    onTerminalRedraw = { viewModel.triggerTerminalRedraw() },
                    onTerminalReset = { viewModel.triggerTerminalReset() },
                    onTerminalReload = { viewModel.triggerTerminalReload() },
                    isInitializing = isInitializing
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                Crossfade(
                    targetState = currentTab,
                    label = "tab_crossfade",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        MainTab.SYSTEM -> {
                            SystemScreen(
                                stats = stats,
                                hostInfo = hostInfo,
                                connectionState = connectionState,
                                onOpenServerSelector = { showServerSelector = true },
                                onRetryConnection = { viewModel.refreshAll() },
                                activeServer = activeServer,
                                onEditServer = {
                                    serverToEditFromBanner = activeServer
                                    showServerSelector = true
                                },
                                serverNickname = activeServer?.nickname ?: "DietPi Server",
                                isLoading = isLoadingInitial || isRefreshing,
                                isInitializing = isInitializing,
                                powerOperationState = powerOperationState,
                                onReboot = { viewModel.rebootHost() },
                                onPoweroff = { viewModel.poweroffHost() },
                                onDismissPowerState = { viewModel.dismissPowerOperationState() },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        MainTab.PROCESSES -> {
                            ProcessesScreen(
                                processes = processes,
                                onSendSignal = { pid, sig -> viewModel.sendProcessSignal(pid, sig) },
                                activeServer = activeServer,
                                onOpenServerSelector = { showServerSelector = true },
                                isLoading = isLoadingInitial || isRefreshing,
                                isInitializing = isInitializing,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        MainTab.SERVICES -> {
                            ServicesScreen(
                                services = services,
                                activeServer = activeServer,
                                onOpenServerSelector = { showServerSelector = true },
                                isLoading = isLoadingInitial || isRefreshing,
                                isInitializing = isInitializing,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        MainTab.SOFTWARE -> {
                            SoftwareScreen(
                                softwareList = software,
                                onToggleSoftware = { id -> viewModel.toggleSoftware(id) },
                                activeServer = activeServer,
                                onOpenServerSelector = { showServerSelector = true },
                                isLoading = isLoadingInitial || isRefreshing,
                                isInitializing = isInitializing,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        MainTab.TERMINAL -> {
                            TerminalScreen(
                                viewModel = viewModel,
                                activeServer = activeServer,
                                onOpenServerSelector = { showServerSelector = true },
                                isToolbarVisible = isTerminalToolbarVisible,
                                onToggleToolbar = { isTerminalToolbarVisible = !isTerminalToolbarVisible },
                                isInitializing = isInitializing,
                                onToolbarHeightChanged = { height -> terminalToolbarHeightDp = height },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        MainTab.FILE_BROWSER -> {
                            FileBrowserScreen(
                                viewModel = viewModel,
                                activeServer = activeServer,
                                onOpenServerSelector = { showServerSelector = true },
                                isInitializing = isInitializing,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                        MainTab.WEB_UI -> {
                            WebUiScreen(
                                activeServer = activeServer,
                                connectionState = connectionState,
                                cookies = viewModel.getActiveServerCookies(),
                                onOpenServerSelector = { showServerSelector = true },
                                reloadTrigger = webUiReloadTrigger,
                                isInitializing = isInitializing,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding())
                            )
                        }
                    }
                }

                // Quick floating screen switch button that extends upwards for rapid navigation
                FloatingScreenSwitcher(
                    currentTab = currentTab,
                    onSelectTab = { selectedTab -> currentTab = selectedTab },
                    isTerminalToolbarVisible = isTerminalToolbarVisible,
                    terminalToolbarHeight = terminalToolbarHeightDp
                )
            }
        }
    }

    if (showServerSelector) {
        ServerSelectorSheet(
            servers = servers,
            activeServerId = activeServer?.id,
            onSelectServer = { s -> viewModel.selectServer(s) },
            onAddServer = { s -> viewModel.addServer(s) },
            onUpdateServer = { s -> viewModel.updateServer(s) },
            onDeleteServer = { s -> viewModel.deleteServer(s) },
            onTestConnection = { s -> viewModel.testServer(s) },
            onDismiss = {
                showServerSelector = false
                serverToEditFromBanner = null
            },
            serverToEditInitially = serverToEditFromBanner,
            isScanningNetwork = isScanningNetwork,
            discoveredNodes = discoveredNodes,
            scanStatus = scanStatus,
            candidateSubnets = viewModel.candidateSubnets,
            onStartScan = { customSubnet -> viewModel.startNetworkDiscovery(customSubnet) },
            onStopScan = { viewModel.stopNetworkDiscovery() }
        )
    }
}
