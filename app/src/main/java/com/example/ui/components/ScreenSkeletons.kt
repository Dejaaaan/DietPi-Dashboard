package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer brush modifier effect for skeleton loading placeholders.
 */
@Composable
fun rememberShimmerBrush(
    targetValue: Float = 1000f,
    shimmerColors: List<Color> = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnimation, y = translateAnimation)
    )
}

/**
 * Basic rectangular or rounded bone block for skeletons.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = rememberShimmerBrush()
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Skeleton placeholder for System Screen (telemetry KPI cards & graphs).
 */
@Composable
fun SystemScreenSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("system_skeleton_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 2x2 Metric Cards Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonCard(modifier = Modifier.weight(1f), brush = brush, height = 125.dp)
                SkeletonCard(modifier = Modifier.weight(1f), brush = brush, height = 125.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonCard(modifier = Modifier.weight(1f), brush = brush, height = 125.dp)
                SkeletonCard(modifier = Modifier.weight(1f), brush = brush, height = 125.dp)
            }
        }

        // Telemetry Graph Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(modifier = Modifier.width(180.dp).height(20.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(70.dp).height(18.dp), brush = brush)
                }
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(160.dp), brush = brush)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(3) {
                        SkeletonBox(modifier = Modifier.width(90.dp).height(28.dp), shape = RoundedCornerShape(14.dp), brush = brush)
                    }
                }
            }
        }

        // Host Specifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(modifier = Modifier.width(160.dp).height(20.dp), brush = brush)
                Spacer(modifier = Modifier.height(4.dp))
                repeat(5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBox(modifier = Modifier.width(100.dp).height(16.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(140.dp).height(16.dp), brush = brush)
                    }
                }
            }
        }
    }
}

/**
 * Skeleton placeholder for list items (Processes, Services, Software packages).
 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    itemHeight: Dp = 76.dp,
    testTag: String = "list_skeleton_view"
) {
    val brush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(itemCount) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        brush = brush
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkeletonBox(modifier = Modifier.width(160.dp).height(16.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(220.dp).height(12.dp), brush = brush)
                    }
                    SkeletonBox(
                        modifier = Modifier.width(50.dp).height(24.dp),
                        shape = RoundedCornerShape(6.dp),
                        brush = brush
                    )
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard(
    modifier: Modifier = Modifier,
    brush: Brush,
    height: Dp
) {
    Card(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(modifier = Modifier.width(80.dp).height(14.dp), brush = brush)
                SkeletonBox(modifier = Modifier.size(32.dp), shape = CircleShape, brush = brush)
            }
            SkeletonBox(modifier = Modifier.width(100.dp).height(24.dp), brush = brush)
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(12.dp), brush = brush)
        }
    }
}
