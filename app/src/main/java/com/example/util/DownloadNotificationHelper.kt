package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object DownloadNotificationHelper {

    private const val CHANNEL_ID = "dietpi_downloads_channel"
    private const val CHANNEL_NAME = "DietPi Downloads"
    private const val CHANNEL_DESC = "Notifications for downloaded files from DietPi server"
    private var notificationIdCounter = 1000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDownloadNotification(
        context: Context,
        fileName: String,
        fileSizeFormatted: String,
        fileUri: Uri? = null,
        mimeType: String? = null
    ) {
        createNotificationChannel(context)

        val pendingIntent: PendingIntent? = if (fileUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType ?: "*/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            PendingIntent.getActivity(
                context,
                notificationIdCounter,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$fileName ($fileSizeFormatted)")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Successfully downloaded '$fileName' ($fileSizeFormatted) to Downloads folder.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(notificationIdCounter++, builder.build())
            }
        } catch (_: SecurityException) {
            // Handled gracefully if notification permission was revoked
        }
    }
}
