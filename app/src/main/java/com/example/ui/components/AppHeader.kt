package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainTab
import com.example.R
import com.example.data.local.ServerEntity
import com.example.data.model.ConnectionState
import com.example.ui.theme.DietPiGreenPrimary

/**
 * Unified, slim top header component used consistently across all screens of DietPi Companion.
 * Height: 46dp (thinner than standard 64dp TopAppBar for maximized screen real estate).
 *
 * Features:
 * - Hamburger icon button on the left to toggle the navigation sidebar drawer
 * - Interactive server status pill (live connection indicator, nickname, host) opening server picker
 * - Terminal keyboard/accessory toolbar toggle button (when on Terminal tab)
 * - Polling pause/resume toggle button (for REST metric tabs)
 * - Universal refresh button with smooth rotation animation across all tabs
 */
@Composable
fun AppHeader(
    activeServer: ServerEntity?,
    connectionState: ConnectionState,
    currentTab: MainTab,
    isRefreshing: Boolean,
    isPollingActive: Boolean,
    isTerminalToolbarVisible: Boolean,
    onToggleSidebar: () -> Unit,
    onOpenServerSelector: () -> Unit,
    onTogglePolling: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTerminalToolbar: () -> Unit,
    onTerminalRedraw: () -> Unit = {},
    onTerminalReset: () -> Unit = {},
    onTerminalReload: () -> Unit = {},
    isInitializing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Sidebar drawer toggle button
                IconButton(
                    onClick = onToggleSidebar,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("nav_drawer_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open navigation sidebar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center-Left: Active server selector pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenServerSelector() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("active_server_top_bar")
                ) {
                    // Live status indicator dot
                    val dotColor = when (connectionState) {
                        is ConnectionState.Connected -> DietPiGreenPrimary
                        is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
                        is ConnectionState.Error -> MaterialTheme.colorScheme.error
                        ConnectionState.Idle -> MaterialTheme.colorScheme.outline
                    }

                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeServer?.nickname ?: if (isInitializing) "Connecting..." else "DietPi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select server",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = activeServer?.host ?: if (isInitializing) "Locating..." else "Connect",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )

                    // Terminal Tab specific actions: Keyboard toolbar toggle & Reset Shell button
                    if (currentTab == MainTab.TERMINAL) {
                        // Toggle navigation keyboard / accessory toolbar
                        IconButton(
                            onClick = onToggleTerminalToolbar,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("toggle_terminal_toolbar_header")
                        ) {
                            Icon(
                                imageVector = if (isTerminalToolbarVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                                contentDescription = if (isTerminalToolbarVisible) "Hide navigation keyboard" else "Show navigation keyboard",
                                tint = if (isTerminalToolbarVisible) DietPiGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Reset Terminal (Aborts frozen processes, clears buffer & resets shell prompt)
                        IconButton(
                            onClick = onTerminalReset,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("terminal_reset_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Terminal Shell",
                                tint = Color(0xFFF87171), // soft warning red
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // REST Metrics tabs: Polling Pause / Resume
                    when (currentTab) {
                        MainTab.SYSTEM, MainTab.PROCESSES, MainTab.SERVICES, MainTab.SOFTWARE -> {
                            IconButton(
                                onClick = onTogglePolling,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("toggle_polling_button")
                                ) {
                                Icon(
                                    imageVector = if (isPollingActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = if (isPollingActive) "Pause Polling" else "Resume Polling",
                                    tint = if (isPollingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        else -> {}
                    }

                    // Universal Refresh Button for ALL tabs (always at the far right corner)
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = (if (isRefreshing) Modifier.rotate(rotation) else Modifier).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Universal thin refresh progress indicator
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .testTag("app_header_progress_bar"),
                    color = DietPiGreenPrimary,
                    trackColor = Color.Transparent
                )
            } else {
                // Slim separator
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}
