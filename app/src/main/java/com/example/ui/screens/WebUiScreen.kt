package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.ui.components.ConnectionErrorCard

/**
 * Dedicated Web UI screen embedding the official DietPi-Dashboard web interface.
 * Features:
 * - Uses the same global TopAppBar header as the rest of the app (configured in MainActivity)
 * - True element scaling / zoom presets that scale cards, containers, images, and text uniformly
 * - Full horizontal and vertical scrolling freedom without horizontal locking
 * - Slim secondary navigation strip for page back/forward, zoom scaling, and browser opening
 * - Synced authentication cookies
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebUiScreen(
    activeServer: ServerEntity?,
    connectionState: ConnectionState,
    cookies: List<okhttp3.Cookie>,
    onOpenServerSelector: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryConnection: () -> Unit = {},
    onEditServer: () -> Unit = {},
    isRetryingConnection: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {},
    isInitializing: Boolean = false,
    reloadTrigger: Long = 0L
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var lastReloadTrigger by remember { mutableLongStateOf(0L) }

    // Element scaling / zoom controls: 50% default, persisted across tabs and sessions
    val prefs = remember(context) { context.getSharedPreferences("dietpi_ui_prefs", Context.MODE_PRIVATE) }
    var zoomLevel by rememberSaveable { mutableIntStateOf(prefs.getInt("web_ui_zoom_level", 50)) }
    var showZoomMenu by remember { mutableStateOf(false) }

    val zoomOptions = listOf(
        Pair(125, "125%"),
        Pair(100, "100%"),
        Pair(85, "85%"),
        Pair(75, "75%"),
        Pair(65, "65%"),
        Pair(50, "50%")
    )

    // Trigger reload when reloadTrigger changes
    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0 && reloadTrigger != lastReloadTrigger) {
            lastReloadTrigger = reloadTrigger
            webViewInstance?.reload()
        }
    }

    // Reactively update element scale (true zoom for all elements)
    LaunchedEffect(zoomLevel) {
        applyWebUiScale(webViewInstance, zoomLevel)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Slim secondary web toolbar matching app aesthetic
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Navigation controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            webViewInstance?.let { wv ->
                                if (wv.canGoBack()) wv.goBack()
                            }
                        },
                        enabled = canGoBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("web_ui_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    IconButton(
                        onClick = {
                            webViewInstance?.let { wv ->
                                if (wv.canGoForward()) wv.goForward()
                            }
                        },
                        enabled = canGoForward,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("web_ui_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(20.dp),
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }

                // Compact host / page indicator pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "HTTPS/HTTP",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentUrl.isNotEmpty()) currentUrl.removePrefix("http://").removePrefix("https://") else activeServer?.host ?: "Web Dashboard",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Width & Browser controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Page Scale / Zoom preset dropdown
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (zoomLevel != 50) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showZoomMenu = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("web_ui_zoom_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Page Scale",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (zoomLevel != 50) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "$zoomLevel%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (zoomLevel != 50) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showZoomMenu,
                            onDismissRequest = { showZoomMenu = false }
                        ) {
                            Text(
                                text = "Page Scale (Zoom)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            HorizontalDivider()
                            zoomOptions.forEach { (scale, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                fontWeight = if (zoomLevel == scale) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (zoomLevel == scale) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        zoomLevel = scale
                                        prefs.edit().putInt("web_ui_zoom_level", scale).apply()
                                        showZoomMenu = false
                                        applyWebUiScale(webViewInstance, scale)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Open in external browser button (consolidated in web toolbar)
                    IconButton(
                        onClick = {
                            val target = currentUrl.ifEmpty { activeServer?.baseUrl }
                            if (!target.isNullOrEmpty()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("web_ui_external_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in external browser",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Loading indicator
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (connectionState is ConnectionState.Error) {
            ConnectionErrorCard(
                connectionState = connectionState,
                onRetryConnection = onRetryConnection,
                onEditServer = onEditServer,
                isRetrying = isRetryingConnection,
                autoRetryState = autoRetryState,
                onCancelAutoRetry = onCancelAutoRetry,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (activeServer == null) {
            if (isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                            text = "Locating DietPi Web UI...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                com.example.ui.components.NoServerSelectedView(
                    icon = Icons.Default.Language,
                    title = "No Server Selected",
                    description = "Connect to a DietPi node to load the official DietPi-Dashboard web interface.",
                    onOpenServerSelector = onOpenServerSelector,
                    modifier = Modifier.fillMaxSize(),
                    testTag = "web_ui_no_server_view"
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dietpi_web_ui_view"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Software rendering layer to avoid Mesa DRM node warnings
                        try {
                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        } catch (_: Exception) {}

                        // Allow both horizontal and vertical scrolling
                        isHorizontalScrollBarEnabled = true
                        isVerticalScrollBarEnabled = true
                        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            // Viewport configurations to fit content and support responsive zooming
                            useWideViewPort = true
                            loadWithOverviewMode = false
                            textZoom = 100
                            // Disable pinch-to-zoom gestures
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        // Prevent multi-touch pinch-to-zoom gestures directly at the touch level
                        setOnTouchListener { v, event ->
                            if (event.pointerCount > 1) {
                                // Consume multi-touch events so WebView pinch-to-zoom does not trigger
                                true
                            } else {
                                v.onTouchEvent(event)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                currentUrl = url ?: ""
                                applyWebUiScale(view, zoomLevel)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                currentUrl = url ?: ""
                                applyWebUiScale(view, zoomLevel)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress < 100
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (newProgress > 60) {
                                    applyWebUiScale(view, zoomLevel)
                                }
                            }
                        }

                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                    canGoBack = webView.canGoBack()
                    canGoForward = webView.canGoForward()

                    // Ensure uniform element scale
                    applyWebUiScale(webView, zoomLevel)

                    // Sync authentication cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    cookies.forEach { c ->
                        cookieManager.setCookie(activeServer.baseUrl, "${c.name}=${c.value}; Path=/")
                    }
                    cookieManager.flush()

                    val targetUrl = activeServer.baseUrl
                    if (webView.url != targetUrl && (webView.url == null || !webView.url!!.startsWith(targetUrl))) {
                        webView.loadUrl(targetUrl)
                    }
                }
            )
        }
    }
}

/**
 * Scales all DOM elements (layout containers, cards, text, images, tables, charts)
 * uniformly, behaving like browser desktop zoom.
 * Enforces standardized mobile viewports and prevents font-boosting discrepancy across different pages.
 */
private fun applyWebUiScale(webView: WebView?, scalePercent: Int) {
    if (webView == null) return
    try {
        // Reset WebView viewport scale to 0 (default) to avoid compounding scale factors
        webView.setInitialScale(0)
    } catch (_: Exception) {}

    val factor = scalePercent / 100.0
    val script = """
        (function() {
            try {
                var factor = $factor;

                // 1. Ensure uniform viewport meta tag exists on every page with pinch-to-zoom disabled
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    (document.head || document.documentElement).appendChild(meta);
                }
                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                // Prevent gesturestart / pinch zoom in WebKit
                if (!window.__dietpiPinchPreventAttached) {
                    window.__dietpiPinchPreventAttached = true;
                    document.addEventListener('gesturestart', function(e) { e.preventDefault(); }, { passive: false });
                    document.addEventListener('gesturechange', function(e) { e.preventDefault(); }, { passive: false });
                    document.addEventListener('gestureend', function(e) { e.preventDefault(); }, { passive: false });
                }

                // 2. Remove legacy style tags
                var oldStyle = document.getElementById('dietpi-fit-width-style');
                if (oldStyle && oldStyle.parentNode) {
                    oldStyle.parentNode.removeChild(oldStyle);
                }

                // 3. Inject uniform scaling style with font boosting disabled
                var styleId = 'dietpi-page-scale-style';
                var style = document.getElementById(styleId);
                if (!style) {
                    style = document.createElement('style');
                    style.id = styleId;
                    (document.head || document.documentElement).appendChild(style);
                }

                var css = 'html, body { -webkit-text-size-adjust: 100% !important; text-size-adjust: 100% !important; }';
                if (Math.abs(factor - 1.0) > 0.001) {
                    css += ' html { zoom: ' + factor + ' !important; }';
                    document.documentElement.style.zoom = factor;
                } else {
                    document.documentElement.style.zoom = '1';
                }
                style.textContent = css;

                // 4. Ensure Single-Page App router navigations retain zoom
                if (!window.__dietpiZoomObserverAttached) {
                    window.__dietpiZoomObserverAttached = true;
                    var applyZoom = function() {
                        var curFactor = window.__dietpiZoomFactor || factor;
                        if (Math.abs(curFactor - 1.0) > 0.001) {
                            document.documentElement.style.zoom = curFactor;
                        } else {
                            document.documentElement.style.zoom = '1';
                        }
                    };
                    window.addEventListener('popstate', applyZoom);
                    window.addEventListener('hashchange', applyZoom);
                }
                window.__dietpiZoomFactor = factor;
            } catch(e) {}
        })();
    """.trimIndent()

    webView.evaluateJavascript(script, null)
}
