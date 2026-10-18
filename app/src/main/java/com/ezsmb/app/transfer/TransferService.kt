package com.ezsmb.app.transfer

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.ezsmb.app.EzApp
import com.ezsmb.app.MainActivity
import com.ezsmb.app.R
import kotlinx.coroutines.*

class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private val queue get() = (application as EzApp).transfers
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("transfers", "文件传输", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(text: String) = NotificationCompat.Builder(this, "transfers")
        .setSmallIcon(R.drawable.ic_launcher).setContentTitle("ezSMB · 文件传输").setContentText(text).setOnlyAlertOnce(true).setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startForeground(17, notification("正在准备传输"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
        catch (_: Exception) { queue.interruptAll("系统暂不允许后台传输，请回到应用后重试"); stopSelf(); return START_NOT_STICKY }
        if (worker?.isActive != true) worker = scope.launch {
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ezSMB:transfer").apply {
                setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L)
            }
            val updater = launch {
                var lastNotification = 0L
                queue.tasks.collect { list ->
                    list.firstOrNull { it.active }?.let { task ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotification >= 1000 && (android.os.Build.VERSION.SDK_INT < 33 ||
                            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)) {
                            getSystemService(NotificationManager::class.java).notify(17, notification("${task.name} · ${task.done / 1024} KB")); lastNotification = now
                        }
                    }
                }
            }
            try { withContext(Dispatchers.IO) { queue.runQueue() } }
            finally { updater.cancel(); if (wake.isHeld) wake.release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { queue.interruptAll("系统后台传输时限已到，请回到应用重试"); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { if (queue.tasks.value.any { it.active }) queue.interruptAll("后台服务已停止，请重新传输"); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
