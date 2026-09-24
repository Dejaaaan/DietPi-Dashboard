package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.MainTab
import com.example.ui.theme.DietPiGreenPrimary

/**
 * Quick floating screen switch button that extends upwards to show other navigation buttons,
 * matching the screens available in the sidebar navigation.
 *
 * Positioned on the right side and elevated above bottom elements (keyboard button / toolbar on terminal,
 * and system bars on other screens).
 */
@Composable
fun FloatingScreenSwitcher(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    isTerminalToolbarVisible: Boolean,
    terminalToolbarHeight: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Intercept hardware/system back button when menu is expanded
    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    // Position above bottom elements:
    // On Terminal tab:
    // - If extended accessory keyboard toolbar is shown: sit comfortably above the toolbar (terminalToolbarHeight + 14.dp).
    //   If not yet measured, fall back to 134.dp + 14.dp = 148.dp.
    // - If extended accessory keyboard toolbar is hidden: sit above the 44.dp floating restore button at 72.dp.
    // On other tabs: sit at a comfortable 20.dp above the system navigation bar.
    val targetBottomPadding = when {
        currentTab == MainTab.TERMINAL -> {
            if (isTerminalToolbarVisible) {
                val effectiveToolbarHeight = if (terminalToolbarHeight > 0.dp) terminalToolbarHeight else 134.dp
                effectiveToolbarHeight + 14.dp
            } else {
                72.dp
            }
        }
        else -> 20.dp
    }
    val animatedBottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "floating_switcher_bottom_padding"
    )

    // Full screen overlay for scrim & touch dismiss handling when expanded
    if (isExpanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isExpanded = false }
                .testTag("floating_switcher_scrim")
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(end = 14.dp, bottom = animatedBottomPadding),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Speed dial navigation items extending upwards
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + slideInVertically { it / 3 } + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + slideOutVertically { it / 3 } + scaleOut(targetScale = 0.85f)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                ) {
                    // Display tabs in sidebar order (System down to Web UI)
                    MainTab.values().forEach { tab ->
                        val isSelected = tab == currentTab
                        ScreenSwitchItem(
                            tab = tab,
                            isSelected = isSelected,
                            onClick = {
                                onSelectTab(tab)
                                isExpanded = false
                            }
                        )
                    }
                }
            }

            // Main Floating Action Button (styled identically to floating keyboard button)
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        width = 1.dp,
                        color = if (isExpanded) DietPiGreenPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .testTag("floating_screen_switcher_button"),
                containerColor = if (isExpanded) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
                },
                contentColor = if (isExpanded) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 5.dp,
                    pressedElevation = 8.dp
                )
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 90f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "fab_rotation"
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Layers,
                    contentDescription = if (isExpanded) "Close screen navigation" else "Quick switch screen",
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun ScreenSwitchItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("floating_switch_item_${tab.name.lowercase()}")
    ) {
        // Text pill label
        Surface(
            color = if (isSelected) {
                DietPiGreenPrimary.copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
            },
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 3.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isSelected) DietPiGreenPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.padding(end = 10.dp)
        ) {
            Text(
                text = tab.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Mini Circular Action Button
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = if (isSelected) {
                DietPiGreenPrimary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isSelected) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 3.dp,
                pressedElevation = 6.dp
            ),
            modifier = Modifier
                .size(40.dp)
                .border(
                    width = 1.dp,
                    color = if (isSelected) DietPiGreenPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.title,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
