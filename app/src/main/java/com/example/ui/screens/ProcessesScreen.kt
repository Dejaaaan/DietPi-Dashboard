package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.data.model.ProcessItem
import com.example.data.model.ProcessSignal
import com.example.data.model.ProcessStatus
import com.example.ui.components.CompactFilterDropdown
import com.example.ui.components.ConnectionErrorCard
import com.example.ui.components.ListSkeleton
import com.example.ui.components.NoServerSelectedView
import com.example.ui.components.SignalDialog
import com.example.ui.components.formatBytes
import com.example.ui.theme.*
import java.util.Locale

enum class ProcessSort {
    CPU,
    RAM,
    NAME,
    PID
}

@Composable
fun ProcessesScreen(
    processes: List<ProcessItem>,
    onSendSignal: (pid: Int, signal: ProcessSignal) -> Unit,
    activeServer: ServerEntity? = null,
    onOpenServerSelector: () -> Unit = {},
    isLoading: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {},
    isInitializing: Boolean = false,
    connectionState: ConnectionState = ConnectionState.Idle,
    onRetryConnection: () -> Unit = {},
    onEditServer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (activeServer == null) {
        if (isInitializing || isLoading) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ListSkeleton(
                    itemCount = 8,
                    itemHeight = 64.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            return
        }
        NoServerSelectedView(
            icon = Icons.Default.ViewList,
            title = "No Server Selected",
            description = "Connect to a DietPi node to monitor running processes, inspect memory and CPU utilization, and send signals.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier,
            testTag = "processes_no_server_view"
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSort by remember { mutableStateOf(ProcessSort.CPU) }
    var statusFilter by remember { mutableStateOf<ProcessStatus?>(null) }
    var selectedProcessForSignal by remember { mutableStateOf<ProcessItem?>(null) }

    val filteredAndSorted = remember(processes, searchQuery, selectedSort, statusFilter) {
        processes.filter { proc ->
            val matchesQuery = proc.name.contains(searchQuery, ignoreCase = true) ||
                    proc.pid.toString().contains(searchQuery)
            val matchesStatus = statusFilter == null || proc.status == statusFilter
            matchesQuery && matchesStatus
        }.sortedWith { a, b ->
            when (selectedSort) {
                ProcessSort.CPU -> b.cpu.compareTo(a.cpu)
                ProcessSort.RAM -> b.mem.compareTo(a.mem)
                ProcessSort.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                ProcessSort.PID -> a.pid.compareTo(b.pid)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        if (connectionState is ConnectionState.Error) {
            ConnectionErrorCard(
                connectionState = connectionState,
                onRetryConnection = onRetryConnection,
                onEditServer = onEditServer,
                isRetrying = isLoading,
                autoRetryState = autoRetryState,
                onCancelAutoRetry = onCancelAutoRetry,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter by name or PID...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("process_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Consistent Compact Dropdown Filters (Sort & Status)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort Dropdown
            CompactFilterDropdown(
                label = "Sort",
                selectedOption = selectedSort,
                options = listOf(ProcessSort.CPU, ProcessSort.RAM, ProcessSort.NAME, ProcessSort.PID),
                optionLabel = { sort ->
                    when (sort) {
                        ProcessSort.CPU -> "CPU %"
                        ProcessSort.RAM -> "Memory"
                        ProcessSort.NAME -> "Name"
                        ProcessSort.PID -> "PID"
                    }
                },
                onOptionSelected = { selectedSort = it },
                leadingIcon = Icons.Default.Sort,
                modifier = Modifier.weight(1f),
                testTagPrefix = "process_sort_filter"
            )

            // Status Dropdown
            val runningCount = remember(processes) { processes.count { it.status == ProcessStatus.Running } }
            val sleepingCount = remember(processes) { processes.count { it.status == ProcessStatus.Sleeping } }
            CompactFilterDropdown(
                label = "Status",
                selectedOption = statusFilter,
                options = listOf(null, ProcessStatus.Running, ProcessStatus.Sleeping, ProcessStatus.Paused),
                optionLabel = { status ->
                    when (status) {
                        ProcessStatus.Running -> "Running ($runningCount)"
                        ProcessStatus.Sleeping -> "Sleeping ($sleepingCount)"
                        ProcessStatus.Paused -> "Paused"
                        null -> "All (${processes.size})"
                        else -> status.name
                    }
                },
                onOptionSelected = { statusFilter = it },
                leadingIcon = Icons.Default.FilterAlt,
                modifier = Modifier.weight(1f),
                testTagPrefix = "process_status_filter"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Total Active: ${filteredAndSorted.size} processes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && processes.isEmpty()) {
            ListSkeleton(
                itemCount = 8,
                itemHeight = 64.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (filteredAndSorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterListOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No processes match '$searchQuery'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredAndSorted, key = { it.pid }) { proc ->
                    ProcessCard(
                        process = proc,
                        onOpenSignalDialog = { selectedProcessForSignal = proc }
                    )
                }
            }
        }
    }

    selectedProcessForSignal?.let { proc ->
        SignalDialog(
            process = proc,
            onSendSignal = { sig ->
                onSendSignal(proc.pid, sig)
                selectedProcessForSignal = null
            },
            onDismiss = { selectedProcessForSignal = null }
        )
    }
}

@Composable
fun ProcessCard(
    process: ProcessItem,
    onOpenSignalDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenSignalDialog() }
            .testTag("process_card_${process.pid}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // PID Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.widthIn(min = 48.dp)
                ) {
                    Text(
                        text = process.pid.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = process.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CPU: ${String.format(Locale.US, "%.1f%%", process.cpu)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (process.cpu > 10f) MetricTempOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "RAM: ${formatBytes(process.mem)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val (statusColor, statusText) = when (process.status) {
                    ProcessStatus.Running -> StatusRunningGreen to "Running"
                    ProcessStatus.Sleeping -> StatusSleepingBlue to "Sleeping"
                    ProcessStatus.Paused -> StatusPausedAmber to "Paused"
                    ProcessStatus.Other -> MaterialTheme.colorScheme.onSurfaceVariant to "Other"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }

                IconButton(
                    onClick = onOpenSignalDialog,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("signal_button_${process.pid}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Signal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
