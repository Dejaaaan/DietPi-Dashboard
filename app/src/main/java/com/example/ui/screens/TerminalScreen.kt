package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.data.model.TerminalStatus
import com.example.ui.theme.*
import com.example.ui.viewmodel.DietPiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalBridge(
    private val onInput: (String) -> Unit,
    private val onResize: (cols: Int, rows: Int, force: Boolean) -> Unit,
    private val onReady: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onUserInput(data: String) {
        mainHandler.post { onInput(data) }
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int, force: Boolean) {
        mainHandler.post { onResize(cols, rows, force) }
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        mainHandler.post { onResize(cols, rows, false) }
    }

    @JavascriptInterface
    fun onTerminalReady() {
        mainHandler.post { onReady() }
    }
}

enum class ExpandableDrawer {
    NONE,
    DIETPI,
    F_KEYS
}

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    viewModel: DietPiViewModel,
    activeServer: ServerEntity?,
    onOpenServerSelector: () -> Unit = {},
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    isToolbarVisible: Boolean = true,
    onToggleToolbar: () -> Unit = {},
    isInitializing: Boolean = false,
    onToolbarHeightChanged: (Dp) -> Unit = {},
    connectionState: ConnectionState = ConnectionState.Idle,
    autoRetryState: AutoRetryState? = null,
    onRetryConnection: () -> Unit = {},
    onCancelAutoRetry: () -> Unit = {},
    onEditServer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val terminalStatus by viewModel.terminalStatus.collectAsStateWithLifecycle()
    var hasEverConnected by remember(activeServer?.id) { mutableStateOf(false) }
    var isReconnectingSession by remember { mutableStateOf(false) }
    LaunchedEffect(terminalStatus) {
        if (terminalStatus is TerminalStatus.Connected) {
            hasEverConnected = true
            isReconnectingSession = false
        } else if (terminalStatus is TerminalStatus.Error || terminalStatus is TerminalStatus.Disconnected) {
            isReconnectingSession = false
        }
    }
    LaunchedEffect(isToolbarVisible) {
        if (!isToolbarVisible) {
            onToolbarHeightChanged(0.dp)
        }
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isTerminalReady by remember { mutableStateOf(false) }

    var activeDrawer by remember { mutableStateOf(ExpandableDrawer.NONE) }

    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    val ctrlActiveRef = remember { AtomicBoolean(false) }
    val altActiveRef = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun processInputWithModifiers(data: String): String {
        var toSend = data
        val wasCtrl = ctrlActiveRef.getAndSet(false)
        val wasAlt = altActiveRef.getAndSet(false)

        if (wasCtrl) {
            mainHandler.post { ctrlActive = false }
            // Only apply Ctrl bitmasking to plain single printable characters,
            // NOT to escape sequences (starting with ESC '\u001B') like arrow keys or special keys.
            if (toSend.isNotEmpty() && !toSend.startsWith("\u001B")) {
                val ch = toSend[0]
                val modifiedChar = when (ch) {
                    in 'a'..'z' -> (ch.code - 'a'.code + 1).toChar().toString()
                    in 'A'..'Z' -> (ch.code - 'A'.code + 1).toChar().toString()
                    '@', ' ' -> "\u0000"
                    '[' -> "\u001B"
                    '\\' -> "\u001C"
                    ']' -> "\u001D"
                    '^' -> "\u001E"
                    '_' -> "\u001F"
                    '?' -> "\u007F"
                    else -> ch.toString()
                }
                toSend = modifiedChar + toSend.substring(1)
            }
        }

        if (wasAlt) {
            mainHandler.post { altActive = false }
            toSend = "\u001B$toSend"
        }

        return toSend
    }

        fun runDietPiCommand(cmd: String) {
            if (cmd.endsWith("-") || cmd == "dietpi-") {
                viewModel.sendTerminalInput(cmd)
            } else {
                viewModel.sendTerminalInput("$cmd\r")
            }
        }

        fun sendInputWithModifiers(data: String) {
            val processed = processInputWithModifiers(data)
            viewModel.sendTerminalInput(processed)
        }

    fun sendSpecialKey(keyName: String, fallbackSequence: String) {
        val wasAlt = altActiveRef.getAndSet(false)
        if (wasAlt) mainHandler.post { altActive = false }

        if (webViewRef != null) {
            webViewRef?.evaluateJavascript(
                "window.sendSpecialKey('$keyName', false, $wasAlt, false);",
                null
            )
        } else {
            var seq = fallbackSequence
            if (wasAlt) seq = "\u001B$seq"
            viewModel.sendTerminalInput(seq)
        }
    }

    if (activeServer == null) {
        if (isInitializing) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = com.example.ui.theme.DietPiGreenPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Locating DietPi node...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return
        }
        com.example.ui.components.NoServerSelectedView(
            icon = Icons.Default.Terminal,
            title = "No Server Selected",
            description = "Connect to a DietPi node to start an interactive terminal session.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier.fillMaxSize(),
            testTag = "terminal_no_server_view"
        )
        return
    }

    // Start session when screen enters composition or activeServer changes
    LaunchedEffect(activeServer.id) {
        viewModel.startTerminalSession()
    }

    // Collect terminal output chunks (initial snapshot + live stream) and evaluate in xterm.js
    LaunchedEffect(isTerminalReady, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isTerminalReady) return@LaunchedEffect

        viewModel.openTerminalOutputFlow().collect { chunk ->
            if (chunk.isNotEmpty()) {
                val base64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("window.writeChunkBase64('$base64');", null)
                }
            }
        }
    }

    // Collect global header terminal actions (Redraw, Reset, Reload, Clear)
    LaunchedEffect(isTerminalReady, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isTerminalReady) return@LaunchedEffect

        viewModel.terminalAction.collectLatest { action ->
            withContext(Dispatchers.Main) {
                when (action) {
                    com.example.ui.viewmodel.TerminalAction.REDRAW -> {
                        webView.evaluateJavascript("window.redrawTerminal();", null)
                    }
                    com.example.ui.viewmodel.TerminalAction.RESET -> {
                        webView.evaluateJavascript("window.resetTerminal();", null)
                    }
                    com.example.ui.viewmodel.TerminalAction.RELOAD -> {
                        webView.evaluateJavascript("window.resetTerminal(); window.redrawTerminal();", null)
                    }
                    com.example.ui.viewmodel.TerminalAction.CLEAR -> {
                        webView.evaluateJavascript("window.clearScreen();", null)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("terminal_screen")
    ) {
        // Terminal Box Viewport (Edge-to-edge for 100% full screen width TUI applications)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .background(Color(0xFF0D1117))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))

                        // Explicitly enable hardware acceleration for smooth 60/90/120fps rendering and tear-free scrolling
                        try {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        } catch (_: Exception) {}

                        overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        isHorizontalScrollBarEnabled = false
                        isVerticalScrollBarEnabled = false

                        // Lock outer WebView native scroll position to (0, 0) so only xterm handles touch scrolling
                        setOnScrollChangeListener { _, scrollX, scrollY, _, _ ->
                            if (scrollX != 0 || scrollY != 0) {
                                scrollTo(0, 0)
                            }
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = false
                            loadWithOverviewMode = false
                            builtInZoomControls = false
                            displayZoomControls = false
                            setSupportZoom(false)
                            textZoom = 100
                            allowFileAccess = true
                        }

                        isFocusable = true
                        isFocusableInTouchMode = true

                        val bridge = TerminalBridge(
                            onInput = { data ->
                                val processed = processInputWithModifiers(data)
                                viewModel.sendTerminalInput(processed)
                            },
                            onResize = { cols, rows, force ->
                                viewModel.resizeTerminal(cols, rows, force)
                            },
                            onReady = {
                                isTerminalReady = true
                            }
                        )
                        addJavascriptInterface(bridge, "AndroidTerminal")

                        webViewClient = WebViewClient()

                        loadUrl("file:///android_asset/terminal.html")
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("terminal_webview")
            )

            // Connecting / Initializing Progress Indicator
            if (!isTerminalReady || terminalStatus is TerminalStatus.Connecting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .testTag("terminal_loading_indicator"),
                    color = DietPiGreenPrimary,
                    trackColor = Color.Transparent
                )
            }

            // Status and Reconnect Banners at Top Center
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .zIndex(10f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Node Connection / Unreachable Banner
                if (connectionState is ConnectionState.Error) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier.testTag("terminal_node_offline_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            val statusMsg = when {
                                autoRetryState != null && autoRetryState.isRetryingNow -> "Connecting node..."
                                autoRetryState != null && !autoRetryState.isPaused && autoRetryState.secondsRemaining > 0 ->
                                    "Node offline · Retry in ${autoRetryState.secondsRemaining}s"
                                else -> "Node offline"
                            }
                            Text(
                                text = statusMsg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (autoRetryState != null && autoRetryState.isRetryingNow) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            } else {
                                Text(
                                    text = "Retry",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(onClick = onRetryConnection)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(13.dp)
                                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f))
                            )
                            Text(
                                text = "Edit",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onEditServer)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 2. Terminal Session Disconnected / Error Banner
                if ((hasEverConnected && terminalStatus is TerminalStatus.Disconnected) || terminalStatus is TerminalStatus.Error || isReconnectingSession) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = 4.dp,
                        modifier = Modifier.testTag("terminal_reconnect_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (terminalStatus is TerminalStatus.Connecting || isReconnectingSession) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = DietPiGreenPrimary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Reconnecting session...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            isReconnectingSession = true
                                            viewModel.restartTerminalSession()
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reconnect",
                                        tint = DietPiGreenPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (terminalStatus is TerminalStatus.Disconnected) "Session Ended · Reconnect" else "Connection Error · Retry",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(13.dp)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                )
                                Text(
                                    text = "Reset Shell (^D)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            webViewRef?.evaluateJavascript("window.resetTerminal();", null)
                                            viewModel.resetTerminalShell()
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Quick-Restore Button when navigation keyboard toolbar is toggled off
            if (!isToolbarVisible) {
                FloatingActionButton(
                    onClick = onToggleToolbar,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(14.dp)
                        .size(44.dp)
                        .testTag("restore_terminal_toolbar_button"),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Show Terminal Navigation Keyboard",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Virtual Accessory Toolbar for Mobile Typing
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val isKeyboardOpen = WindowInsets.isImeVisible || imeBottom > 0

        // Smoothly scroll to bottom when keyboard opens to keep active cursor visible
        // without resizing the Linux PTY or triggering destructive SIGWINCH storms.
        LaunchedEffect(isKeyboardOpen) {
            if (isKeyboardOpen) {
                delay(80)
                webViewRef?.evaluateJavascript("window.scrollToBottom();", null)
            }
        }

        // Refined Bottom Accessory Toolbar for Mobile Terminal (User can toggle off)
        if (isToolbarVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .testTag("terminal_accessory_toolbar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            val heightDp = with(density) { size.height.toDp() }
                            onToolbarHeightChanged(heightDp)
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                // EXPANDABLE DRAWER: Opens directly on top of the bottom panel keys
                AnimatedVisibility(
                    visible = activeDrawer != ExpandableDrawer.NONE,
                    enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Drawer Content
                            when (activeDrawer) {
                                ExpandableDrawer.DIETPI -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        val dietpiRows = listOf(
                                            listOf(
                                                Pair("dietpi-launcher", "dietpi-launcher"),
                                                Pair("dietpi-config", "dietpi-config")
                                            ),
                                            listOf(
                                                Pair("dietpi-software", "dietpi-software"),
                                                Pair("dietpi-services", "dietpi-services")
                                            ),
                                            listOf(
                                                Pair("dietpi-drive_manager", "dietpi-drive_manager"),
                                                Pair("dietpi-update", "dietpi-update")
                                            ),
                                            listOf(
                                                Pair("dietpi-explorer", "dietpi-explorer"),
                                                Pair("dietpi-", "dietpi-...")
                                            )
                                        )

                                        dietpiRows.forEach { rowItems ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                rowItems.forEach { (cmd, display) ->
                                                    TerminalKeyButton(
                                                        label = display,
                                                        subLabel = null,
                                                        variant = TerminalKeyVariant.DIETPI_BRAND,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        activeDrawer = ExpandableDrawer.NONE
                                                        runDietPiCommand(cmd)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                ExpandableDrawer.F_KEYS -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val f1to6 = listOf(
                                                Triple("F1", "Help", "\u001BOP"),
                                                Triple("F2", "User", "\u001BOQ"),
                                                Triple("F3", "View", "\u001BOR"),
                                                Triple("F4", "Edit", "\u001BOS"),
                                                Triple("F5", "Copy", "\u001B[15~"),
                                                Triple("F6", "Move", "\u001B[17~")
                                            )
                                            f1to6.forEach { (key, sub, seq) ->
                                                TerminalKeyButton(
                                                    label = key,
                                                    subLabel = sub,
                                                    variant = TerminalKeyVariant.DEFAULT,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    sendInputWithModifiers(seq)
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val f7to12 = listOf(
                                                Triple("F7", "Mkdir", "\u001B[18~"),
                                                Triple("F8", "Delete", "\u001B[19~"),
                                                Triple("F9", "Menu", "\u001B[20~"),
                                                Triple("F10", "Quit", "\u001B[21~"),
                                                Triple("F11", "Full", "\u001B[23~"),
                                                Triple("F12", "Save", "\u001B[24~")
                                            )
                                            f7to12.forEach { (key, sub, seq) ->
                                                val keyVariant = when (key) {
                                                    "F10" -> TerminalKeyVariant.ESCAPE
                                                    "F8" -> TerminalKeyVariant.WARNING
                                                    else -> TerminalKeyVariant.DEFAULT
                                                }
                                                TerminalKeyButton(
                                                    label = key,
                                                    subLabel = sub,
                                                    variant = keyVariant,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    sendInputWithModifiers(seq)
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            val navKeys = listOf(
                                                Triple("Home", "Home", "\u001B[H"),
                                                Triple("End", "End", "\u001B[F"),
                                                Triple("PgUp", "PageUp", "\u001B[5~"),
                                                Triple("PgDn", "PageDown", "\u001B[6~"),
                                                Triple("Del", "Delete", "\u001B[3~")
                                            )
                                            navKeys.forEach { (label, keyName, seq) ->
                                                TerminalKeyButton(
                                                    label = label,
                                                    subLabel = null,
                                                    variant = when (label) {
                                                        "Del" -> TerminalKeyVariant.WARNING
                                                        else -> TerminalKeyVariant.DEFAULT
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    sendSpecialKey(keyName, seq)
                                                }
                                            }
                                        }
                                    }
                                }
                                ExpandableDrawer.NONE -> {}
                            }
                        }
                    }
                }

                // MAIN ACCESSORY BAR (Always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT PANEL: Shortcuts, modifiers, signals, and DietPi / Fn drawer toggles
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Row 1: Expandable drawer toggles (DietPi, Fn) & Hide button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // DietPi Drawer Toggle
                            TerminalKeyButton(
                                label = if (activeDrawer == ExpandableDrawer.DIETPI) "DietPi ▴" else "DietPi ▾",
                                icon = Icons.Default.Terminal,
                                variant = TerminalKeyVariant.DIETPI_BRAND,
                                isActive = activeDrawer == ExpandableDrawer.DIETPI,
                                modifier = Modifier.weight(1.15f)
                            ) {
                                activeDrawer = if (activeDrawer == ExpandableDrawer.DIETPI) ExpandableDrawer.NONE else ExpandableDrawer.DIETPI
                            }

                            // Fn (Function & Navigation Keys) Drawer Toggle
                            TerminalKeyButton(
                                label = if (activeDrawer == ExpandableDrawer.F_KEYS) "Fn ▴" else "Fn ▾",
                                icon = Icons.Default.Keyboard,
                                variant = TerminalKeyVariant.FN_TOGGLE,
                                isActive = activeDrawer == ExpandableDrawer.F_KEYS,
                                modifier = Modifier.weight(1f)
                            ) {
                                activeDrawer = if (activeDrawer == ExpandableDrawer.F_KEYS) ExpandableDrawer.NONE else ExpandableDrawer.F_KEYS
                            }

                            // Direct Hide Keyboard Toolbar button
                            TerminalKeyButton(
                                label = "Hide",
                                icon = Icons.Default.KeyboardHide,
                                contentDescription = "Hide terminal navigation keyboard",
                                variant = TerminalKeyVariant.DEFAULT,
                                modifier = Modifier.weight(0.9f)
                            ) {
                                onToggleToolbar()
                            }
                        }

                        // Row 2: Modifiers (Ctrl, Alt) and Editing (Del, Backspace)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Ctrl latch toggle
                            TerminalKeyButton(
                                label = "Ctrl",
                                contentDescription = "Control key (Ctrl)",
                                variant = TerminalKeyVariant.MODIFIER,
                                isActive = ctrlActive,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val nextState = !ctrlActive
                                    ctrlActive = nextState
                                    ctrlActiveRef.set(nextState)
                                    webViewRef?.requestFocus()
                                }
                            )

                            // Alt latch toggle
                            TerminalKeyButton(
                                label = "Alt",
                                contentDescription = "Alt key (Alt)",
                                variant = TerminalKeyVariant.MODIFIER,
                                isActive = altActive,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val nextState = !altActive
                                    altActive = nextState
                                    altActiveRef.set(nextState)
                                    webViewRef?.requestFocus()
                                }
                            )

                            // Forward Delete key
                            TerminalKeyButton(
                                label = "Del",
                                contentDescription = "Forward delete key (Del)",
                                variant = TerminalKeyVariant.DEFAULT,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendSpecialKey("Delete", "\u001B[3~")
                            }

                            // Backspace key
                            TerminalKeyButton(
                                icon = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace (⌫)",
                                variant = TerminalKeyVariant.DEFAULT,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendInputWithModifiers("\u007F")
                            }
                        }

                        // Row 3: Signals (^Z, ^D), Screen Maintenance (^L Redraw, Clear)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            TerminalKeyButton(
                                label = "^Z",
                                subLabel = "Pause",
                                contentDescription = "Suspend / Pause job (^Z)",
                                variant = TerminalKeyVariant.WARNING,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.sendTerminalInput("\u001A")
                            }

                            TerminalKeyButton(
                                label = "^D",
                                subLabel = "EOF",
                                contentDescription = "Send EOF / Exit Shell (^D)",
                                variant = TerminalKeyVariant.WARNING,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.sendTerminalInput("\u0004")
                            }

                            // Terminal Redraw Keyboard Shortcut (^L)
                            TerminalKeyButton(
                                label = "^L",
                                subLabel = "Redraw",
                                contentDescription = "Redraw TUI screen (^L)",
                                variant = TerminalKeyVariant.DEFAULT,
                                modifier = Modifier.weight(1f)
                            ) {
                                webViewRef?.evaluateJavascript("window.redrawTerminal();", null)
                                viewModel.sendTerminalInput("\u000C")
                            }

                            // Clear Screen button (moved from header to keyboard)
                            TerminalKeyButton(
                                label = "Clear",
                                contentDescription = "Clear terminal screen",
                                variant = TerminalKeyVariant.DEFAULT,
                                modifier = Modifier.weight(1f)
                            ) {
                                webViewRef?.evaluateJavascript("window.clearScreen();", null)
                                viewModel.sendTerminalInput("\u000C")
                            }
                        }
                    }

                    // Subtle Vertical Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(108.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // RIGHT PANEL: Navigation & Control Cluster (Faithful to user sketch)
                    Column(
                        modifier = Modifier.width(140.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Row 1: Esc (top-left), up (top-center), Ctrl+C (top-right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            TerminalKeyButton(
                                label = "Esc",
                                contentDescription = "Escape key (Esc)",
                                variant = TerminalKeyVariant.ESCAPE,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Double ESC ensures whiptail/dialogs (dietpi-config/software) cancel immediately
                                sendInputWithModifiers("\u001B\u001B")
                            }
                            TerminalKeyButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Up Arrow",
                                variant = TerminalKeyVariant.ARROW,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendSpecialKey("ArrowUp", "\u001B[A")
                            }
                            TerminalKeyButton(
                                label = "^C",
                                subLabel = "Intr",
                                contentDescription = "Interrupt / Break (Ctrl+C)",
                                variant = TerminalKeyVariant.WARNING,
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.sendTerminalInput("\u0003")
                            }
                        }

                        // Row 2: left (middle-left), tactile center anchor, right (middle-right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TerminalKeyButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Left Arrow",
                                variant = TerminalKeyVariant.ARROW,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendSpecialKey("ArrowLeft", "\u001B[D")
                            }
                            // D-pad center tactile anchor
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                )
                            }
                            TerminalKeyButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Right Arrow",
                                variant = TerminalKeyVariant.ARROW,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendSpecialKey("ArrowRight", "\u001B[C")
                            }
                        }

                        // Row 3: tab (bottom-left), down (bottom-center), enter (bottom-right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            TerminalKeyButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardTab,
                                contentDescription = "Tab key",
                                variant = TerminalKeyVariant.AUTOCOMPLETE,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendInputWithModifiers("\t")
                            }
                            TerminalKeyButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Down Arrow",
                                variant = TerminalKeyVariant.ARROW,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendSpecialKey("ArrowDown", "\u001B[B")
                            }
                            TerminalKeyButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                                contentDescription = "Enter key",
                                variant = TerminalKeyVariant.PRIMARY_ENTER,
                                modifier = Modifier.weight(1f)
                            ) {
                                sendInputWithModifiers("\r")
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

enum class TerminalKeyVariant {
    DEFAULT,
    ARROW,
    PRIMARY_ENTER,
    AUTOCOMPLETE,
    ESCAPE,
    WARNING,
    DIETPI_BRAND,
    FN_TOGGLE,
    SUPER,
    MODIFIER
}

@Composable
fun TerminalKeyButton(
    label: String? = null,
    subLabel: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    subPainter: Painter? = null,
    contentDescription: String? = label,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isAccent: Boolean = false,
    isWarning: Boolean = false,
    variant: TerminalKeyVariant = when {
        isAccent -> TerminalKeyVariant.DIETPI_BRAND
        isWarning -> TerminalKeyVariant.WARNING
        else -> TerminalKeyVariant.DEFAULT
    },
    onClick: () -> Unit
) {
    val (containerColor, contentColor, borderColor) = when {
        isActive -> Triple(
            DietPiGreenPrimary,
            Color(0xFF003822),
            DietPiGreenLight
        )
        variant == TerminalKeyVariant.PRIMARY_ENTER -> Triple(
            com.example.ui.theme.DietPiGreenContainer,
            com.example.ui.theme.DietPiOnGreenContainer,
            DietPiGreenPrimary.copy(alpha = 0.55f)
        )
        variant == TerminalKeyVariant.AUTOCOMPLETE -> Triple(
            Color(0xFF14293D),
            Color(0xFF7DD3FC),
            Color(0xFF0284C7).copy(alpha = 0.45f)
        )
        variant == TerminalKeyVariant.ESCAPE -> Triple(
            Color(0xFF38220C),
            Color(0xFFFBBF24),
            Color(0xFFD97706).copy(alpha = 0.5f)
        )
        variant == TerminalKeyVariant.WARNING -> Triple(
            Color(0xFF3B1515),
            Color(0xFFFCA5A5),
            Color(0xFFDC2626).copy(alpha = 0.5f)
        )
        variant == TerminalKeyVariant.DIETPI_BRAND -> Triple(
            com.example.ui.theme.DietPiGreenContainer,
            com.example.ui.theme.DietPiOnGreenContainer,
            DietPiGreenPrimary.copy(alpha = 0.4f)
        )
        variant == TerminalKeyVariant.FN_TOGGLE -> Triple(
            Color(0xFF1E293B),
            Color(0xFFCBD5E1),
            Color(0xFF475569).copy(alpha = 0.45f)
        )
        variant == TerminalKeyVariant.MODIFIER -> Triple(
            Color(0xFF1E2638),
            Color(0xFFE2E8F0),
            Color(0xFF475569).copy(alpha = 0.55f)
        )
        variant == TerminalKeyVariant.SUPER -> Triple(
            Color(0xFF1E2638),
            Color(0xFF93C5FD),
            Color(0xFF3B82F6).copy(alpha = 0.45f)
        )
        variant == TerminalKeyVariant.ARROW -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Color(0xFFF3F4F6),
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
        else -> Triple(
            Color(0xFF1E2430),
            Color(0xFFE5E7EB),
            Color(0xFF374151).copy(alpha = 0.55f)
        )
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp,
        modifier = modifier
            .height(34.dp)
            .defaultMinSize(minWidth = 20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            // Upper compartment: Vertically centers the primary key icon/label
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Dual painter icon (e.g. Win + Command icons side-by-side)
                    painter != null && subPainter != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painter,
                                contentDescription = contentDescription,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "/",
                                fontSize = 8.sp,
                                color = contentColor.copy(alpha = 0.45f),
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                painter = subPainter,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    // Icon + Label (e.g. DietPi ▾ or Fn ▾ or Hide)
                    icon != null && label != null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (isActive || variant != TerminalKeyVariant.DEFAULT) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                    // Custom painter asset
                    painter != null -> {
                        Icon(
                            painter = painter,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(if (subLabel != null) 12.dp else 16.dp)
                        )
                    }
                    // Vector icon (Arrow keys, Tab, Enter, Backspace)
                    icon != null -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            modifier = Modifier.size(if (subLabel != null) 13.dp else 17.dp)
                        )
                    }
                    // Primary text label (Esc, Ctrl, Alt, ^C, ^Z, ^D, ^L, Del, etc.)
                    label != null -> {
                        val isEmphasized = isActive || variant != TerminalKeyVariant.DEFAULT || label.equals("esc", ignoreCase = true) || label.startsWith("^")
                        val dynamicFontSize = when {
                            subLabel != null && (label.equals("esc", ignoreCase = true) || label.startsWith("^")) -> 10.sp
                            subLabel != null && label.length > 5 -> 8.5.sp
                            subLabel != null -> 9.5.sp
                            label.equals("esc", ignoreCase = true) -> 10.5.sp
                            label.startsWith("^") -> 10.sp
                            label.length > 15 -> 8.sp
                            label.length > 10 -> 8.5.sp
                            label.length > 5 -> 9.sp
                            label.length > 3 -> 10.5.sp
                            else -> 11.5.sp
                        }
                        Text(
                            text = label,
                            fontSize = dynamicFontSize,
                            fontWeight = if (isEmphasized) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            // Lower compartment: Fixed 10.dp height, strictly uniform baseline for ALL sublabels across all buttons
            if (subLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = subLabel,
                        fontSize = 7.sp,
                        lineHeight = 7.5.sp,
                        color = if (isActive) contentColor.copy(alpha = 0.9f) else contentColor.copy(alpha = 0.65f),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
