package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutoRetryState
import com.example.data.model.ConnectionState
import kotlinx.coroutines.delay

/**
 * Compact, unified connection failure pill matching the Terminal screen aesthetic.
 * Displayed consistently across all tabs (System, Processes, Services, Software, Files, Web UI, Terminal).
 */
@Composable
fun ConnectionErrorPill(
    connectionState: ConnectionState.Error,
    onRetryConnection: () -> Unit,
    onEditServer: () -> Unit,
    modifier: Modifier = Modifier,
    isRetrying: Boolean = false,
    autoRetryState: AutoRetryState? = null,
    onCancelAutoRetry: () -> Unit = {}
) = ConnectionErrorCard(
    connectionState = connectionState,
    onRetryConnection = onRetryConnection,
    onEditServer = onEditServer,
    modifier = modifier,
    isRetrying = isRetrying,
    autoRetryState = autoRetryState,
    onCancelAutoRetry = onCancelAutoRetry
)

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

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 4.dp,
            modifier = Modifier
                .testTag("connection_error_card")
                .clickable(enabled = !isActuallyRetrying) { onRetryConnection() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )

                val statusMsg = when {
                    copiedRecently -> "Diagnostic copied"
                    isActuallyRetrying -> "Connecting..."
                    autoRetryState != null && !autoRetryState.isPaused && autoRetryState.secondsRemaining > 0 ->
                        "Disconnected · Retry in ${autoRetryState.secondsRemaining}s"
                    else -> "Disconnected"
                }

                Text(
                    text = statusMsg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(connectionState.message))
                            copiedRecently = true
                        }
                )

                if (isActuallyRetrying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Text(
                        text = "Retry",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .testTag("retry_connection_banner_button")
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onRetryConnection)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(13.dp)
                        .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f))
                )

                Text(
                    text = "Edit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .testTag("edit_node_banner_button")
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onEditServer)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
