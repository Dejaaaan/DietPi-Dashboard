package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.data.model.HostInfo
import com.example.data.model.PowerOperationState
import com.example.data.model.SystemStats
import com.example.data.model.TemperatureItem
import com.example.ui.components.*
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun SystemScreen(
    stats: SystemStats,
    hostInfo: HostInfo,
    connectionState: ConnectionState,
    onOpenServerSelector: () -> Unit,
    onRetryConnection: () -> Unit,
    activeServer: ServerEntity? = null,
    onEditServer: () -> Unit = {},
    serverNickname: String = "DietPi Server",
    isLoading: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {},
    powerOperationState: PowerOperationState = PowerOperationState.Idle,
    onReboot: () -> Unit = {},
    onPoweroff: () -> Unit = {},
    onDismissPowerState: () -> Unit = {},
    isInitializing: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (activeServer == null) {
        if (isInitializing) {
            SystemScreenSkeleton(modifier = modifier)
            return
        }
        NoServerSelectedView(
            icon = Icons.Default.Speed,
            title = "No Server Selected",
            description = "Connect to a DietPi node to monitor CPU, temperature, memory, storage, and system telemetry.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier,
            testTag = "system_no_server_view"
        )
        return
    }

    if (isLoading && stats.cpuGlobal == 0f && stats.ramTotal == 0L && hostInfo.hostname.isEmpty()) {
        SystemScreenSkeleton(modifier = modifier)
        return
    }

    var showRebootConfirmDialog by remember { mutableStateOf(false) }
    var showPoweroffConfirmDialog by remember { mutableStateOf(false) }

    var expandAllGraphs by remember { mutableStateOf(false) }
    val expandedGraphs = remember { mutableStateMapOf<String, Boolean>() }

    // Reboot Confirmation Dialog
    if (showRebootConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRebootConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reboot",
                    tint = MetricTempOrange
                )
            },
            title = {
                Text("Reboot DietPi Node?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to reboot '${activeServer.nickname}' (${activeServer.host})?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "All running services and terminal connections will temporarily disconnect. The app will automatically poll and reconnect when the node finishes rebooting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootConfirmDialog = false
                        onReboot()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetricTempOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_reboot_button")
                ) {
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRebootConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_reboot_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Power Off (Shutdown) Confirmation Dialog
    if (showPoweroffConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPoweroffConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Shutdown",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Shut Down DietPi Node?", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Are you sure you want to shut down '${activeServer.nickname}' (${activeServer.host})?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "WARNING: The device will power down completely. You will need physical access to disconnect and reconnect power (or use a hardware switch) to turn it back on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPoweroffConfirmDialog = false
                        onPoweroff()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_poweroff_button")
                ) {
                    Text("Shut Down")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPoweroffConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_poweroff_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Power Operation In-Progress / Notification Card
        if (powerOperationState !is PowerOperationState.Idle) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("power_operation_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = when (powerOperationState) {
                            is PowerOperationState.Failed -> MaterialTheme.colorScheme.errorContainer
                            is PowerOperationState.Completed -> DietPiGreenLight.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            when (powerOperationState) {
                                is PowerOperationState.Failed -> MaterialTheme.colorScheme.error
                                is PowerOperationState.Completed -> DietPiGreenPrimary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (powerOperationState) {
                                    is PowerOperationState.InProgress, is PowerOperationState.Rebooting -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    is PowerOperationState.Completed -> {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = DietPiGreenPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    is PowerOperationState.Failed -> {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "Failed",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    else -> Unit
                                }

                                Text(
                                    text = when (powerOperationState) {
                                        is PowerOperationState.Rebooting -> "System Reboot in Progress"
                                        is PowerOperationState.InProgress -> "Executing Power Action"
                                        is PowerOperationState.Completed -> "Action Completed"
                                        is PowerOperationState.Failed -> "Operation Error"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (powerOperationState) {
                                        is PowerOperationState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                        is PowerOperationState.Completed -> DietPiGreenPrimary
                                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }

                            if (powerOperationState is PowerOperationState.Completed || powerOperationState is PowerOperationState.Failed) {
                                IconButton(
                                    onClick = onDismissPowerState,
                                    modifier = Modifier.size(28.dp).testTag("dismiss_power_state_button")
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        val message = when (powerOperationState) {
                            is PowerOperationState.InProgress -> powerOperationState.message
                            is PowerOperationState.Rebooting -> powerOperationState.message
                            is PowerOperationState.Completed -> powerOperationState.message
                            is PowerOperationState.Failed -> powerOperationState.error
                            else -> ""
                        }

                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (powerOperationState) {
                                is PowerOperationState.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                is PowerOperationState.Completed -> DietPiGreenLight
                                else -> MaterialTheme.colorScheme.onTertiaryContainer
                            }
                        )
                    }
                }
            }
        }
        // Connection & Status Banner
        item {
            when (connectionState) {
                is ConnectionState.Error -> {
                    ConnectionErrorCard(
                        connectionState = connectionState,
                        onRetryConnection = onRetryConnection,
                        onEditServer = onEditServer,
                        isRetrying = isLoading,
                        autoRetryState = autoRetryState,
                        onCancelAutoRetry = onCancelAutoRetry
                    )
                }
                else -> Unit
            }
        }

        // 4 KPI Metric Cards in 2x2 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "CPU LOAD",
                        value = String.format(Locale.US, "%.1f%%", stats.cpuGlobal),
                        subValue = "${stats.cpuCores.size} Cores Active",
                        icon = Icons.Default.Speed,
                        accentColor = MetricCpuGreen,
                        percent = stats.cpuGlobal,
                        modifier = Modifier.weight(1f).testTag("kpi_cpu_card")
                    )

                    StatCard(
                        title = "TEMPERATURE",
                        value = if (stats.cpuTemp != null) String.format(Locale.US, "%.1f°C", stats.cpuTemp) else "N/A",
                        subValue = if (stats.cpuTemp != null && stats.cpuTemp > 65f) "High Thermal" else "Normal Thermal",
                        icon = Icons.Default.Thermostat,
                        accentColor = if (stats.cpuTemp != null && stats.cpuTemp > 65f) StatusFailedRed else MetricTempOrange,
                        percent = if (stats.cpuTemp != null) (stats.cpuTemp / 85f) * 100f else null,
                        modifier = Modifier.weight(1f).testTag("kpi_temp_card")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "MEMORY",
                        value = String.format(Locale.US, "%.1f%%", stats.ramPercent),
                        subValue = "${formatBytes(stats.ramUsed)} / ${formatBytes(stats.ramTotal)}",
                        icon = Icons.Default.Memory,
                        accentColor = MetricRamPurple,
                        percent = stats.ramPercent,
                        modifier = Modifier.weight(1f).testTag("kpi_ram_card")
                    )

                    val mainDisk = stats.disks.firstOrNull()
                    val diskUsedPct = mainDisk?.percent ?: 0f
                    StatCard(
                        title = "STORAGE",
                        value = String.format(Locale.US, "%.1f%%", diskUsedPct),
                        subValue = if (mainDisk != null) "${formatBytes(mainDisk.used)} / ${formatBytes(mainDisk.total)}" else "No disk",
                        icon = Icons.Default.Storage,
                        accentColor = MetricDiskBlue,
                        percent = diskUsedPct,
                        modifier = Modifier.weight(1f).testTag("kpi_disk_card")
                    )
                }
            }
        }

        // Live Real-Time Telemetry Graphs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Real-time Telemetry Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                expandAllGraphs = !expandAllGraphs
                                expandedGraphs["cpu"] = expandAllGraphs
                                expandedGraphs["ram"] = expandAllGraphs
                                expandedGraphs["net"] = expandAllGraphs
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("toggle_expand_all_telemetry")
                        ) {
                            Text(
                                text = if (expandAllGraphs) "Collapse All" else "Expand All",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    TelemetryGraph(
                        title = "CPU Utilization Trend",
                        currentValueText = String.format(Locale.US, "%.1f%%", stats.cpuGlobal),
                        points = stats.historyCpu,
                        lineColor = MetricCpuGreen,
                        maxRange = 100f,
                        unitSuffix = "%",
                        isExpanded = expandedGraphs["cpu"] ?: expandAllGraphs,
                        onToggleExpand = {
                            val cur = expandedGraphs["cpu"] ?: expandAllGraphs
                            expandedGraphs["cpu"] = !cur
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )

                    TelemetryGraph(
                        title = "RAM Consumption Trend",
                        currentValueText = String.format(Locale.US, "%.1f%%", stats.ramPercent),
                        points = stats.historyRam,
                        lineColor = MetricRamPurple,
                        maxRange = 100f,
                        unitSuffix = "%",
                        isExpanded = expandedGraphs["ram"] ?: expandAllGraphs,
                        onToggleExpand = {
                            val cur = expandedGraphs["ram"] ?: expandAllGraphs
                            expandedGraphs["ram"] = !cur
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TX Sent",
                                style = MaterialTheme.typography.labelSmall,
                                color = MetricNetSent
                            )
                            Text(
                                text = formatRate(stats.netSentRate),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RX Received",
                                style = MaterialTheme.typography.labelSmall,
                                color = MetricNetRecv
                            )
                            Text(
                                text = formatRate(stats.netRecvRate),
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TelemetryGraph(
                        title = "Network RX Throughput",
                        currentValueText = formatRate(stats.netRecvRate),
                        points = stats.historyNetRecv,
                        lineColor = MetricNetRecv,
                        maxRange = (stats.historyNetRecv.maxOfOrNull { it.value } ?: 10000f).coerceAtLeast(1000f),
                        unitSuffix = "",
                        isExpanded = expandedGraphs["net"] ?: expandAllGraphs,
                        onToggleExpand = {
                            val cur = expandedGraphs["net"] ?: expandAllGraphs
                            expandedGraphs["net"] = !cur
                        }
                    )
                }
            }
        }

        // Multi-Core CPU Breakdown
        if (stats.cpuCores.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "CPU Cores Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        stats.cpuCores.forEachIndexed { idx, corePct ->
                            GaugeMeterBar(
                                label = "Core #${idx + 1}",
                                valueText = String.format(Locale.US, "%.1f%%", corePct),
                                percent = corePct,
                                barColor = if (corePct > 80f) MetricTempOrange else MetricCpuGreen
                            )
                        }
                    }
                }
            }
        }

        // RAM & Swap Detail
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Memory & Swap Allocation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    GaugeMeterBar(
                        label = "Physical RAM",
                        valueText = "${formatBytes(stats.ramUsed)} / ${formatBytes(stats.ramTotal)}",
                        percent = stats.ramPercent,
                        barColor = MetricRamPurple
                    )

                    GaugeMeterBar(
                        label = "Virtual Swap",
                        valueText = "${formatBytes(stats.swapUsed)} / ${formatBytes(stats.swapTotal)}",
                        percent = stats.swapPercent,
                        barColor = MetricDiskBlue
                    )
                }
            }
        }

        // Storage Partitions & Disks
        if (stats.disks.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Mounted Filesystems & Drives",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    stats.disks.forEach { disk ->
                        DiskCard(disk = disk)
                    }
                }
            }
        }

        // Host System Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Host Specifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(hostInfo.dietPiVersion, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    HostDetailRow("Hostname", hostInfo.hostname)
                    HostDetailRow("OS Distribution", hostInfo.osVersion)
                    HostDetailRow("Linux Kernel", hostInfo.kernel)
                    HostDetailRow("Architecture", hostInfo.arch)
                    HostDetailRow("Active NIC", hostInfo.nic)
                    HostDetailRow("System Uptime", formatUptime(hostInfo.uptimeSeconds))
                    HostDetailRow("Installed Packages", "${hostInfo.packageCount} pkgs")
                }
            }
        }

        // Power Management Card (Reboot & Shutdown)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("power_management_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Power Control",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Power Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = activeServer.nickname,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Remotely restart or safely power off your DietPi host without switching to the terminal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Reboot Button
                        Button(
                            onClick = { showRebootConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetricTempOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("reboot_system_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reboot",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Reboot",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Shutdown / Poweroff Button
                        OutlinedButton(
                            onClick = { showPoweroffConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("poweroff_system_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Shut Down",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Shut Down",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
