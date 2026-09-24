package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import com.example.data.model.ServiceItem
import com.example.data.model.ServiceStatus
import com.example.ui.components.CompactFilterDropdown
import com.example.ui.components.ListSkeleton
import com.example.ui.components.NoServerSelectedView
import com.example.ui.theme.*

@Composable
fun ServicesScreen(
    services: List<ServiceItem>,
    activeServer: ServerEntity? = null,
    onOpenServerSelector: () -> Unit = {},
    isLoading: Boolean = false,
    isInitializing: Boolean = false,
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
                    itemHeight = 60.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            return
        }
        NoServerSelectedView(
            icon = Icons.Default.Settings,
            title = "No Server Selected",
            description = "Connect to a DietPi node to inspect, restart, stop, and manage systemd services.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier,
            testTag = "services_no_server_view"
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<ServiceStatus?>(null) }

    val filteredServices = remember(services, searchQuery, selectedFilter) {
        services.filter { s ->
            val matchesQuery = s.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = selectedFilter == null || s.status == selectedFilter
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter systemd services...") },
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
                .testTag("service_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Consistent Compact Dropdown Filter (Status)
        val activeCount = remember(services) { services.count { it.status == ServiceStatus.Active } }
        val failedCount = remember(services) { services.count { it.status == ServiceStatus.Failed } }
        val inactiveCount = remember(services) { services.count { it.status == ServiceStatus.Inactive } }

        CompactFilterDropdown(
            label = "Status",
            selectedOption = selectedFilter,
            options = listOf(null, ServiceStatus.Active, ServiceStatus.Failed, ServiceStatus.Inactive),
            optionLabel = { filter ->
                when (filter) {
                    ServiceStatus.Active -> "Active ($activeCount)"
                    ServiceStatus.Failed -> "Failed ($failedCount)"
                    ServiceStatus.Inactive -> "Inactive ($inactiveCount)"
                    ServiceStatus.Unknown -> "Unknown"
                    null -> "All (${services.size})"
                }
            },
            onOptionSelected = { selectedFilter = it },
            leadingIcon = Icons.Default.FilterAlt,
            modifier = Modifier.fillMaxWidth(),
            testTagPrefix = "service_status_filter"
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading && services.isEmpty()) {
            ListSkeleton(
                itemCount = 8,
                itemHeight = 60.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (filteredServices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No services found matching filters",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredServices, key = { it.name }) { s ->
                    ServiceCard(service = s)
                }
            }
        }
    }
}

@Composable
fun ServiceCard(
    service: ServiceItem,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (statusColor, statusText) = when (service.status) {
        ServiceStatus.Active -> StatusRunningGreen to "Active (running)"
        ServiceStatus.Inactive -> MaterialTheme.colorScheme.onSurfaceVariant to "Inactive"
        ServiceStatus.Failed -> StatusFailedRed to "Failed"
        ServiceStatus.Unknown -> MaterialTheme.colorScheme.outline to "Unknown"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("service_card_${service.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (service.status == ServiceStatus.Failed) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "${service.name}.service",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = service.startTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded || service.errorLog.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = if (service.errorLog.isNotEmpty()) "Systemd Journal Log:" else "Status Details:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (service.errorLog.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (service.errorLog.isNotEmpty()) service.errorLog else "Unit is loaded and running under systemd init control.",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
