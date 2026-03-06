package sh.haven.feature.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle

private const val CHANNEL_ID = "haven_terminal_notifications"
private const val CHANNEL_NAME = "Terminal Notifications"

/**
 * Show a terminal notification. Foreground: Toast. Background: Android notification.
 */
fun showTerminalNotification(context: Context, title: String, body: String, tabLabel: String) {
    val isForeground = ProcessLifecycleOwner.get().lifecycle.currentState
        .isAtLeast(Lifecycle.State.RESUMED)

    if (isForeground) {
        val toastText = if (title.isNotEmpty()) "$title: $body" else body
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
        }
    } else {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            notificationManager.createNotificationChannel(channel)
        }

        val displayTitle = title.ifEmpty { tabLabel }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(displayTitle)
            .setContentText(body)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
