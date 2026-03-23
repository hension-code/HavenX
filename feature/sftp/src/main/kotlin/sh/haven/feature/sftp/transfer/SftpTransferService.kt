package sh.haven.feature.sftp.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sh.haven.feature.sftp.TransferState
import javax.inject.Inject

@AndroidEntryPoint
class SftpTransferService : Service() {

    @Inject
    lateinit var transferManager: SftpTransferManager

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            transferManager.transfers.collectLatest { transfers ->
                val downloading = transfers.filter { it.state == TransferState.DOWNLOADING }
                if (downloading.isEmpty()) {
                    if (isForeground) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } else {
                                @Suppress("DEPRECATION")
                                stopForeground(true)
                            }
                        } catch (e: Exception) {}
                        stopSelf()
                        isForeground = false
                    }
                } else {
                    val progressText = if (downloading.size == 1) {
                        val t = downloading.first()
                        val percent = if (t.totalBytes > 0) ((t.transferredBytes.toFloat() / t.totalBytes) * 100).toInt() else 0
                        "${t.fileName}: $percent%"
                    } else {
                        "${downloading.size} files transferring"
                    }
                    
                    val notification = NotificationCompat.Builder(this@SftpTransferService, CHANNEL_ID)
                        .setContentTitle("Haven Background Transfer")
                        .setContentText(progressText)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setProgress(100, if (downloading.size == 1) (downloading.first().fraction * 100).toInt() else 0, downloading.size > 1)
                        .build()
                        
                    if (!isForeground) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                            } else {
                                startForeground(NOTIFICATION_ID, notification)
                            }
                            isForeground = true
                        } catch (e: Exception) {
                            // Ignored
                        }
                    } else {
                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        manager.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for ongoing file transfers"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "sftp_transfers"
        const val NOTIFICATION_ID = 4001
    }
}
