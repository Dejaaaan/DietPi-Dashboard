package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FileBrowserItem
import com.example.ui.screens.saveByteArrayToDownloads
import com.example.ui.theme.DietPiGreenLight
import com.example.ui.theme.DietPiGreenPrimary
import com.example.ui.viewmodel.DietPiViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

fun isImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg")
}

fun isVideoFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "flv", "m4v")
}

fun isAudioFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus")
}

fun isMediaFile(name: String): Boolean = isImageFile(name) || isVideoFile(name) || isAudioFile(name)

/**
 * Fullscreen dialog for previewing Images, Videos, and Audio files fetched from the DietPi server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreviewDialog(
    item: FileBrowserItem,
    viewModel: DietPiViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    fun loadMedia() {
        isLoading = true
        error = null
        fileBytes = null
        viewModel.downloadFile(item) { success, bytes, err ->
            isLoading = false
            if (success && bytes != null) {
                fileBytes = bytes
            } else {
                error = err ?: "Failed to load media file"
            }
        }
    }

    LaunchedEffect(item.path) {
        loadMedia()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = Color(0xFF121214),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when {
                                    isImageFile(item.name) -> Icons.Default.Image
                                    isVideoFile(item.name) -> Icons.Default.Movie
                                    else -> Icons.Default.MusicNote
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = DietPiGreenLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "${item.path} • ${item.formattedSize}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Download button
                        IconButton(
                            onClick = {
                                fileBytes?.let { bytes ->
                                    val savedResult = saveByteArrayToDownloads(context, item.name, bytes)
                                    snackbarMessage = if (savedResult.success) {
                                        com.example.util.DownloadNotificationHelper.showDownloadNotification(
                                            context = context,
                                            fileName = item.name,
                                            fileSizeFormatted = item.formattedSize,
                                            fileUri = savedResult.uri
                                        )
                                        "Saved '${item.name}' to Downloads"
                                    } else {
                                        "Downloaded '${item.name}' (${bytes.size} bytes)"
                                    }
                                }
                            },
                            enabled = fileBytes != null
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download to Device",
                                tint = if (fileBytes != null) DietPiGreenLight else Color.Gray
                            )
                        }

                        // Share button
                        IconButton(
                            onClick = {
                                fileBytes?.let { bytes ->
                                    shareMediaFile(context, item.name, bytes)
                                }
                            },
                            enabled = fileBytes != null
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Media",
                                tint = if (fileBytes != null) Color.White else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E22)
                    )
                )
            },
            snackbarHost = {
                snackbarMessage?.let { msg ->
                    LaunchedEffect(msg) {
                        delay(2500)
                        snackbarMessage = null
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF28282D),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DietPiGreenLight.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = msg,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF121214)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = DietPiGreenLight,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading media preview...",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.formattedSize,
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    error != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = error ?: "Failed to load",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { loadMedia() },
                                colors = ButtonDefaults.buttonColors(containerColor = DietPiGreenPrimary, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                    fileBytes != null -> {
                        val bytes = fileBytes!!
                        when {
                            isImageFile(item.name) -> {
                                ImagePreviewView(bytes = bytes, fileName = item.name)
                            }
                            isVideoFile(item.name) -> {
                                VideoPreviewView(bytes = bytes, fileName = item.name)
                            }
                            isAudioFile(item.name) -> {
                                AudioPreviewView(bytes = bytes, fileName = item.name)
                            }
                            else -> {
                                Text(
                                    text = "Preview not supported for this file format",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive Image viewer with pinch-to-zoom and pan gestures.
 */
@Composable
private fun ImagePreviewView(
    bytes: ByteArray,
    fileName: String
) {
    val bitmap = remember(bytes) {
        try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    if (bitmap == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Unable to decode image", color = Color.White.copy(alpha = 0.7f))
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = fileName,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )

        // Floating Info & Reset bar
        Surface(
            color = Color(0xFF1E1E22).copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38383F)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${bitmap.width} × ${bitmap.height} px",
                    color = DietPiGreenLight,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )

                if (scale != 1f || offsetX != 0f || offsetY != 0f) {
                    TextButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Reset Zoom", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Native VideoView playback component with controls.
 */
@Composable
private fun VideoPreviewView(
    bytes: ByteArray,
    fileName: String
) {
    val context = LocalContext.current
    val tempFile = remember(fileName, bytes) {
        val file = File(context.cacheDir, "preview_${System.currentTimeMillis()}_$fileName")
        file.writeBytes(bytes)
        file
    }

    DisposableEffect(tempFile) {
        onDispose {
            tempFile.delete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoPath(tempFile.absolutePath)
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
        )
    }
}

/**
 * Audio playback player with play/pause and progress timeline.
 */
@Composable
private fun AudioPreviewView(
    bytes: ByteArray,
    fileName: String
) {
    val context = LocalContext.current
    val tempFile = remember(fileName, bytes) {
        val file = File(context.cacheDir, "preview_${System.currentTimeMillis()}_$fileName")
        file.writeBytes(bytes)
        file
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }

    DisposableEffect(tempFile) {
        val player = MediaPlayer().apply {
            setDataSource(tempFile.absolutePath)
            setOnPreparedListener { mp ->
                durationMs = mp.duration.coerceAtLeast(1)
            }
            setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
            }
            prepareAsync()
        }
        mediaPlayer = player

        onDispose {
            player.stop()
            player.release()
            tempFile.delete()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition
                }
            }
            delay(250)
        }
    }

    Surface(
        color = Color(0xFF1E1E22),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28282D)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DietPiGreenPrimary.copy(alpha = 0.15f))
                    .border(1.dp, DietPiGreenLight.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = DietPiGreenLight,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Slider
            Slider(
                value = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    val targetMs = (fraction * durationMs).toInt()
                    currentPositionMs = targetMs
                    mediaPlayer?.seekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = DietPiGreenLight,
                    activeTrackColor = DietPiGreenLight,
                    inactiveTrackColor = Color(0xFF38383F)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentPositionMs),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = formatMs(durationMs),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Play/Pause FAB
            FilledIconButton(
                onClick = {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            player.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = DietPiGreenLight,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun shareMediaFile(context: Context, fileName: String, bytes: ByteArray) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, "Media file from DietPi: $fileName (${bytes.size} bytes)")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share $fileName"))
    } catch (e: Exception) {
        // Fallback
    }
}
