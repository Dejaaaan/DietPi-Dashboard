package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.data.local.ServerEntity
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import com.example.data.model.SoftwareItem
import com.example.ui.components.CompactFilterDropdown
import com.example.ui.components.ConnectionErrorCard
import com.example.ui.components.ListSkeleton
import com.example.ui.components.NoServerSelectedView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareScreen(
    softwareList: List<SoftwareItem>,
    onToggleSoftware: (id: Int) -> Unit,
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
                    itemHeight = 88.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
            return
        }
        NoServerSelectedView(
            icon = Icons.Default.Apps,
            title = "No Server Selected",
            description = "Connect to a DietPi node to explore, install, and manage DietPi-Software packages.",
            onOpenServerSelector = onOpenServerSelector,
            modifier = modifier,
            testTag = "software_no_server_view"
        )
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableIntStateOf(0) } // 0 = All, 1 = Installed, 2 = Available
    var selectedCategory by remember { mutableStateOf("All") }
    var isGroupedByCategory by remember { mutableStateOf(false) }
    var softwareToConfirm by remember { mutableStateOf<SoftwareItem?>(null) }
    val context = LocalContext.current

    // Extract categories
    val allCategories = remember(softwareList) {
        listOf("All") + softwareList.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // Filter items based on search, status, and category
    val filteredList = remember(softwareList, searchQuery, selectedStatus, selectedCategory) {
        softwareList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.desc.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true) ||
                item.id.toString() == searchQuery.trim()

            val matchesStatus = when (selectedStatus) {
                1 -> item.isInstalled
                2 -> !item.isInstalled
                else -> true
            }

            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)

            matchesSearch && matchesStatus && matchesCategory
        }
    }

    // Group items if grouping is active
    val groupedItems = remember(filteredList, isGroupedByCategory) {
        if (isGroupedByCategory) {
            filteredList.groupBy { it.category }
        } else {
            mapOf("All Software" to filteredList)
        }
    }

    val installedCount = remember(softwareList) { softwareList.count { it.isInstalled } }
    val availableCount = remember(softwareList) { softwareList.count { !it.isInstalled } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

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
            placeholder = { Text("Search DietPi software...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("software_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Filter Dropdowns & Grouping Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Dropdown
            CompactFilterDropdown(
                label = "Status",
                selectedOption = selectedStatus,
                options = listOf(0, 1, 2),
                optionLabel = { status ->
                    when (status) {
                        1 -> "Installed ($installedCount)"
                        2 -> "Available ($availableCount)"
                        else -> "All (${softwareList.size})"
                    }
                },
                onOptionSelected = { selectedStatus = it },
                leadingIcon = Icons.Default.FilterAlt,
                modifier = Modifier.weight(1f),
                testTagPrefix = "software_status_filter"
            )

            // Category Dropdown
            CompactFilterDropdown(
                label = "Category",
                selectedOption = selectedCategory,
                options = allCategories,
                optionLabel = { cat ->
                    val count = if (cat == "All") softwareList.size
                    else softwareList.count { it.category.equals(cat, ignoreCase = true) }
                    "$cat ($count)"
                },
                onOptionSelected = { selectedCategory = it },
                leadingIcon = getCategoryIcon(selectedCategory),
                optionIcon = { cat -> getCategoryIcon(cat) },
                modifier = Modifier.weight(1f),
                testTagPrefix = "software_category_filter"
            )

            // Grouping by Category toggle button with clear tooltip/contentDescription
            IconButton(
                onClick = { isGroupedByCategory = !isGroupedByCategory },
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isGroupedByCategory) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .testTag("software_toggle_grouping")
            ) {
                Icon(
                    imageVector = if (isGroupedByCategory) Icons.Default.ViewAgenda else Icons.Default.ViewHeadline,
                    contentDescription = if (isGroupedByCategory) "Show flat list" else "Group packages by category",
                    tint = if (isGroupedByCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Software List or Empty State
        if (isLoading && softwareList.isEmpty()) {
            ListSkeleton(
                itemCount = 8,
                itemHeight = 88.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No software packages found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Try adjusting your search terms or category filters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                groupedItems.forEach { (categoryName, itemsInCategory) ->
                    if (isGroupedByCategory) {
                        item(key = "header_$categoryName") {
                            CategorySectionHeader(
                                category = categoryName,
                                count = itemsInCategory.size
                            )
                        }
                    }

                    items(itemsInCategory, key = { it.id }) { item ->
                        SoftwareCard(
                            item = item,
                            onToggle = { softwareToConfirm = item },
                            onOpenLink = { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    softwareToConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { softwareToConfirm = null },
            icon = {
                Icon(
                    imageVector = if (item.isInstalled) Icons.Default.Delete else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (item.isInstalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(if (item.isInstalled) "Uninstall Software" else "Install Software")
            },
            text = {
                Column {
                    Text(
                        text = if (item.isInstalled)
                            "Are you sure you want to uninstall ${item.name} (#${item.id}) from this DietPi node?"
                        else
                            "Install ${item.name} (#${item.id}) onto this DietPi system?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (item.deps.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Required dependencies: ${item.deps}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleSoftware(item.id)
                        softwareToConfirm = null
                    },
                    colors = if (item.isInstalled)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_toggle_software_${item.id}")
                ) {
                    Text(if (item.isInstalled) "Uninstall" else "Install")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { softwareToConfirm = null },
                    modifier = Modifier.testTag("cancel_toggle_software")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategorySectionHeader(category: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = getCategoryIcon(category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Text("$count", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SoftwareCard(
    item: SoftwareItem,
    onToggle: () -> Unit,
    onOpenLink: (url: String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("software_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (item.isInstalled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Category icon + Title/ID/Badges + Install/Uninstall Action + Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(item.category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title, category badge & ID
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "#${item.id}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        if (item.isInstalled) {
                            Badge(
                                containerColor = com.example.ui.theme.DietPiGreenContainer,
                                contentColor = com.example.ui.theme.DietPiOnGreenContainer
                            ) {
                                Text("Installed", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Install/Uninstall Action Button
                if (item.isInstalled) {
                    OutlinedButton(
                        onClick = onToggle,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("uninstall_button_${item.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Button(
                        onClick = onToggle,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("install_button_${item.id}")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Install", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Expand/Collapse Chevron Indicator
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable Section: Software image (fit vertically and horizontally) + Description + Links
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // Software Image fitting both vertically and horizontally
                    if (item.imageUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.imageUrl)
                                    .decoderFactory(SvgDecoder.Factory())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${item.name} image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp),
                                loading = {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                error = {
                                    // Fallback to stylized category banner if image cannot load
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = getCategoryIcon(item.category),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Description
                    Text(
                        text = item.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Links row: Homepage and DietPi Docs
                    if (item.homepage.isNotBlank() || item.docs.isNotBlank() || item.deps.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Dependencies if any
                            if (item.deps.isNotBlank()) {
                                Text(
                                    text = "Deps: ${item.deps}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            // Right: External link buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.homepage.isNotBlank()) {
                                    TextButton(
                                        onClick = { onOpenLink(item.homepage) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("homepage_button_${item.id}")
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Homepage", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                if (item.docs.isNotBlank()) {
                                    TextButton(
                                        onClick = { onOpenLink(item.docs) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("docs_button_${item.id}")
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("DietPi Docs", style = MaterialTheme.typography.labelSmall)
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

private fun getCategoryIcon(category: String): ImageVector {
    return when (category.trim().lowercase()) {
        "all" -> Icons.Default.GridView
        "desktops" -> Icons.Default.DesktopWindows
        "remote desktop & access", "remote desktop" -> Icons.Default.SettingsRemote
        "media & audio streaming", "media systems", "audio & music" -> Icons.Default.PlayCircle
        "bittorrent & downloads", "bittorrent & download tools" -> Icons.Default.CloudDownload
        "cloud & backup" -> Icons.Default.CloudSync
        "gaming & emulation" -> Icons.Default.SportsEsports
        "home automation" -> Icons.Default.Home
        "hardware projects & iot", "hardware projects" -> Icons.Default.Memory
        "web servers" -> Icons.Default.Dns
        "file servers" -> Icons.Default.FolderShared
        "dns servers & ad blocking", "dns & network" -> Icons.Default.SecurityUpdateGood
        "advanced networking" -> Icons.Default.Router
        "vpn" -> Icons.Default.VpnKey
        "system security", "system & security" -> Icons.Default.Shield
        "system software" -> Icons.Default.Build
        "system stats & management" -> Icons.Default.Insights
        "ssh servers" -> Icons.Default.Terminal
        "databases & data stores" -> Icons.Default.Storage
        "development & programming", "development" -> Icons.Default.Code
        "camera & surveillance" -> Icons.Default.Videocam
        "printing servers", "printing" -> Icons.Default.Print
        "logging systems" -> Icons.Default.Description
        "social & search" -> Icons.Default.Public
        "distributed projects" -> Icons.Default.Hub
        "general software" -> Icons.Default.Widgets
        else -> Icons.Default.Category
    }
}
