package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import kotlinx.coroutines.delay

@Composable
fun ConnectionErrorCard(
    connectionState: ConnectionState.Error,
    onRetryConnection: () -> Unit,
    onEditServer: () -> Unit,
    modifier: Modifier = Modifier,
    isRetrying: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedRecently by remember { mutableStateOf(false) }

    val isActuallyRetrying = isRetrying || autoRetryState?.isRetryingNow == true

    LaunchedEffect(copiedRecently) {
        if (copiedRecently) {
            delay(2000L)
            copiedRecently = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("connection_error_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Status Icon, Title, and Copy Diagnostics Button (no duplicate refresh button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connection Failed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Copy Diagnostic Button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(connectionState.message))
                        copiedRecently = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("copy_error_button")
                ) {
                    if (copiedRecently) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Diagnostic copied",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy diagnostic info",
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Diagnostic Error Message
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = connectionState.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Auto-Retry Countdown or Paused Banner
            if (autoRetryState != null) {
                if (autoRetryState.secondsRemaining > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-retry in ${autoRetryState.secondsRemaining}s (attempt ${autoRetryState.attempt}/${autoRetryState.maxAttempts})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable(onClick = onCancelAutoRetry)
                                    .testTag("cancel_auto_retry_button")
                            )
                        }
                    }
                } else if (autoRetryState.isPaused) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-retry paused. Tap Retry to reconnect.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Adaptive Buttons: stacks vertically on narrow screens/large font scales to prevent clipping
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 340.dp
                val retryLabel = when {
                    isActuallyRetrying -> "Connecting..."
                    autoRetryState != null && autoRetryState.secondsRemaining > 0 -> "Retry Now (${autoRetryState.secondsRemaining}s)"
                    else -> "Retry Connection"
                }

                if (isNarrow) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RetryButton(
                            onClick = onRetryConnection,
                            isRetrying = isActuallyRetrying,
                            buttonLabel = retryLabel,
                            modifier = Modifier.fillMaxWidth()
                        )
                        EditNodeButton(
                            onClick = onEditServer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RetryButton(
                            onClick = onRetryConnection,
                            isRetrying = isActuallyRetrying,
                            buttonLabel = retryLabel,
                            modifier = Modifier.weight(1f)
                        )
                        EditNodeButton(
                            onClick = onEditServer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryButton(
    onClick: () -> Unit,
    isRetrying: Boolean,
    buttonLabel: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isRetrying,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag("retry_connection_banner_button")
    ) {
        if (isRetrying) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = MaterialTheme.colorScheme.onError,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EditNodeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .heightIn(min = 40.dp)
            .testTag("open_edit_server_button")
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Edit Node",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
