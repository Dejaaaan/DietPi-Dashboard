package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainTab
import com.example.data.local.ServerEntity
import com.example.ui.theme.DietPiGreenPrimary

/**
 * Floating Action Button for changing sections (like the floating keyboard button).
 * Allows the user to switch sections from anywhere, including the terminal screen.
 */
@Composable
fun FloatingSectionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "floating_section_switcher_button"
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
            .testTag(testTag),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
        contentColor = DietPiGreenPrimary,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = "Change Section",
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Modal Bottom Sheet allowing fast 1-tap switching between all DietPi dashboard sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSectionSwitcherSheet(
    currentTab: MainTab,
    activeServer: ServerEntity?,
    onSelectTab: (MainTab) -> Unit,
    onOpenSidebar: () -> Unit,
    onOpenServerSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("quick_section_switcher_sheet")
        ) {
            // Header: Title & Active Node
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DietPiGreenPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = DietPiGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Change Section",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeServer?.let { "${it.nickname} (${it.host})" } ?: "DietPi Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_section_switcher_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid of all 7 sections
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(MainTab.values()) { tab ->
                    val isSelected = currentTab == tab
                    SectionGridItem(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = {
                            onSelectTab(tab)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick actions: Open Full Sidebar & Switch Server
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSidebar()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_sidebar_from_switcher_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Sidebar", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onOpenServerSelector()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("switch_server_from_switcher_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = DietPiGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Servers",
                        style = MaterialTheme.typography.labelMedium,
                        color = DietPiGreenPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionGridItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        DietPiGreenPrimary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        DietPiGreenPrimary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val iconColor = if (isSelected) {
        DietPiGreenPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textColor = if (isSelected) {
        DietPiGreenPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .testTag("section_item_${tab.tag}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(DietPiGreenPrimary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
