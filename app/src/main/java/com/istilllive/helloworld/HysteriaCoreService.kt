package com.istilllive.helloworld

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import hybridge.Hybridge

/**
 * HysteriaCoreService 运行在独立的 :hysteria 进程中。
 * 这样可以避免 Hysteria 的 Go 运行时与 V2Ray 的 Go 运行时在同一个进程内冲突（导致闪退）。
 */
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HysteriaCoreService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 2 // 使用不同的 ID 避免覆盖主服务
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showNotification()
        try {
            System.loadLibrary("h2jni")
            Log.i("HYSTERIA_CORE", "h2jni loaded in separate process")
        } catch (e: Exception) {
            Log.e("HYSTERIA_CORE", "Failed to load h2jni: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_core))
            .setContentText(getString(R.string.notif_core_running))
            .setSmallIcon(R.drawable.tile_icon)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START") {
            val config = intent.getStringExtra("config") ?: ""
            if (config.isNotEmpty()) {
                val error = Hybridge.start(config)
                if (error.isNotEmpty()) {
                    Log.e("HYSTERIA_CORE", "Start error: $error")
                } else {
                    Log.i("HYSTERIA_CORE", "Hysteria2 core started successfully")
                }
            }
        } else if (action == "STOP") {
            Log.i("HYSTERIA_CORE", "Stopping HysteriaCoreService...")
            Hybridge.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            // 关键：延迟杀死进程，确保 stopSelf 已经告知系统服务已停止。
            serviceScope.launch {
                kotlinx.coroutines.delay(800)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Hybridge.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
