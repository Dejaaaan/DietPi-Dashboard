package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MetricPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryGraph(
    title: String,
    currentValueText: String,
    points: List<MetricPoint>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    maxRange: Float = 100f,
    unitSuffix: String = "%",
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    // When expanded, show all points (up to 240); when collapsed, show recent 30 points
    val displayPoints = remember(points, isExpanded) {
        if (isExpanded) {
            points
        } else {
            points.takeLast(30)
        }
    }

    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) 150.dp else 72.dp,
        label = "graph_height"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onToggleExpand != null) {
                        Modifier.clickable { onToggleExpand() }
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (onToggleExpand != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("toggle_expand_${title.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Text(
                text = currentValueText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = lineColor
            )
        }

        // Expanded Statistics Bar
        if (isExpanded && displayPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val values = displayPoints.map { it.value }
            val minVal = values.minOrNull() ?: 0f
            val maxVal = values.maxOrNull() ?: 0f
            val avgVal = if (values.isNotEmpty()) values.average().toFloat() else 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIN: ${formatMetricValue(minVal, unitSuffix)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "AVG: ${formatMetricValue(avgVal, unitSuffix)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = lineColor
                )
                Text(
                    text = "MAX: ${formatMetricValue(maxVal, unitSuffix)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${displayPoints.size} samples",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas Area
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(animatedHeight)
        ) {
            val width = size.width
            val height = size.height

            // Horizontal grid lines
            val gridColor = Color.White.copy(alpha = 0.08f)
            val lineCount = if (isExpanded) 4 else 2
            for (i in 0..lineCount) {
                val y = height * (i.toFloat() / lineCount)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Vertical time tick lines if expanded
            if (isExpanded && displayPoints.size >= 2) {
                val vTickCount = 4
                for (i in 0 until vTickCount) {
                    val x = width * (i.toFloat() / (vTickCount - 1))
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            if (displayPoints.size < 2) {
                // Flat line if not enough history
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = Offset(0f, height * 0.8f),
                    end = Offset(width, height * 0.8f),
                    strokeWidth = 2.dp.toPx()
                )
                return@Canvas
            }

            val values = displayPoints.map { it.value }
            val stepX = width / (displayPoints.size - 1).coerceAtLeast(1)
            val effectiveMax = if (maxRange > 0f) maxRange else (values.maxOrNull() ?: 100f).coerceAtLeast(10f)

            val linePath = Path()
            val fillPath = Path()

            values.forEachIndexed { index, value ->
                val normY = (value / effectiveMax).coerceIn(0f, 1f)
                val x = index * stepX
                val y = height - (normY * height)

                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    val prevNormY = (values[index - 1] / effectiveMax).coerceIn(0f, 1f)
                    val prevX = (index - 1) * stepX
                    val prevY = height - (prevNormY * height)

                    val cx1 = prevX + (x - prevX) / 2f
                    val cy1 = prevY
                    val cx2 = prevX + (x - prevX) / 2f
                    val cy2 = y

                    linePath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                    fillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw gradient area underneath
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.35f),
                        lineColor.copy(alpha = 0.02f)
                    )
                )
            )

            // Draw crisp stroke line
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Draw highlight pulse dot at the latest point
            val lastX = width
            val lastNormY = (values.last() / effectiveMax).coerceIn(0f, 1f)
            val lastY = height - (lastNormY * height)
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = Color.White,
                radius = 1.8.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        // X-Axis Time Ticks when Expanded
        if (isExpanded && displayPoints.size >= 2) {
            val startTime = displayPoints.first().timestamp
            val endTime = displayPoints.last().timestamp
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

            val timeTicks = remember(startTime, endTime) {
                val span = (endTime - startTime).coerceAtLeast(1000L)
                listOf(
                    timeFormat.format(Date(startTime)),
                    timeFormat.format(Date(startTime + span / 3)),
                    timeFormat.format(Date(startTime + 2 * span / 3)),
                    timeFormat.format(Date(endTime))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeTicks.forEach { tickText ->
                    Text(
                        text = tickText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            val spanSec = ((endTime - startTime) / 1000).coerceAtLeast(1)
            val spanLabel = if (spanSec >= 60) {
                "${spanSec / 60}m ${spanSec % 60}s span"
            } else {
                "${spanSec}s span"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, end = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Time Axis: $spanLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// Backward-compatible overload for raw List<Float>
@Composable
fun TelemetryGraph(
    title: String,
    currentValueText: String,
    points: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    maxRange: Float = 100f,
    unitSuffix: String = "%"
) {
    val now = remember { System.currentTimeMillis() }
    val mapped = remember(points) {
        points.mapIndexed { idx, v ->
            MetricPoint(timestamp = now - (points.size - 1 - idx) * 2000L, value = v)
        }
    }
    TelemetryGraph(
        title = title,
        currentValueText = currentValueText,
        points = mapped,
        lineColor = lineColor,
        modifier = modifier,
        maxRange = maxRange,
        unitSuffix = unitSuffix
    )
}

private fun formatMetricValue(value: Float, unitSuffix: String): String {
    return if (unitSuffix.isNotEmpty()) {
        String.format(Locale.US, "%.1f%s", value, unitSuffix)
    } else {
        if (value < 1024f) {
            String.format(Locale.US, "%.0f B/s", value)
        } else if (value < 1024f * 1024f) {
            String.format(Locale.US, "%.1f KB/s", value / 1024f)
        } else {
            String.format(Locale.US, "%.2f MB/s", value / (1024f * 1024f))
        }
    }
}
