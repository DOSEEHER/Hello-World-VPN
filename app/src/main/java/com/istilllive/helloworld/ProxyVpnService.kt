package com.istilllive.helloworld

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

@android.annotation.SuppressLint("VpnService")
class ProxyVpnService : VpnService(), CoreCallbackHandler {

    companion object {
        @Volatile var isServiceRunning = false
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var v2rayController: CoreController? = null
    private val vpnMutex = Mutex()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    @Volatile private var targetState = false
    @Volatile private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_vpn))
            .setContentText(content)
            .setSmallIcon(R.drawable.tile_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i("DUAL_CORE", "onStartCommand action: $action")
        when (action) {
            "START_VPN" -> startVpn()
            "STOP_VPN" -> stopVpn()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        Log.i("DUAL_CORE", "VPN revoked by system")
        stopVpn()
        super.onRevoke()
    }

    @Suppress("SpellCheckingInspection")
    private fun startVpn() {
        targetState = true
        isServiceRunning = true

        serviceScope.launch {
            vpnMutex.withLock {
                if (!targetState) {
                    Log.d("DUAL_CORE", "startVpn skipped (targetState became false)")
                    return@launch
                }
                if (isRunning) return@launch
                
                showNotification(getString(R.string.notif_starting))

                try {
                    go.Seq.setContext(this@ProxyVpnService)

                    // 1. 在独立进程 :hysteria 中启动 Hysteria 2 核心
                    val file = java.io.File(filesDir, "config.json")
                    if (!file.exists()) throw Exception("Config file not found")
                    val configContent = file.readText()

                    Log.d("DUAL_CORE", "Starting Hysteria2 core in isolated process...")
                    val h2Intent = Intent(this@ProxyVpnService, HysteriaCoreService::class.java).apply {
                        this.action = "START"
                        putExtra("config", configContent)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(this@ProxyVpnService, h2Intent)

                    // 等待 Hysteria2 启动并监听 SOCKS5 (1080)
                    kotlinx.coroutines.delay(1500)

                    if (!targetState) return@launch // Double check after delay

                    // 2. 建立 Android VPN 网卡
                    val fd = setupVpnInterface()
                    Log.d("DUAL_CORE", "VPN Interface established, FD: $fd")

                    // 3. 启动 V2Ray 作为网桥 (TUN -> SOCKS5 1080)
                    val bridgeConfig = """
                    {
                      "log": { "loglevel": "warning" },
                      "inbounds": [{
                        "port": 0,
                        "protocol": "tun",
                        "settings": {
                          "mtu": 1500,
                          "sniffing": { "enabled": true, "destOverride": ["http", "tls"] }
                        }
                      }],
                      "outbounds": [{
                        "protocol": "socks",
                        "settings": {
                          "servers": [{ "address": "127.0.0.1", "port": 1080 }]
                        }
                      }]
                    }
                    """.trimIndent()

                    Log.d("DUAL_CORE", "Starting V2Ray bridge...")
                    v2rayController = Libv2ray.newCoreController(this@ProxyVpnService)
                    v2rayController?.startLoop(bridgeConfig, fd) 
                    
                    isRunning = true
                    showNotification(getString(R.string.notif_connected))
                    
                } catch (e: Exception) {
                    Log.e("DUAL_CORE", "Critical error: ${e.message}")
                    stopVpn()
                }
            }
        }
    }

    private fun setupVpnInterface(): Int {
        val builder = Builder()
        builder.setSession("Hello World Bridge")
        builder.setMtu(1500)
        builder.addAddress("172.19.0.1", 30) 
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("8.8.8.8")
        
        val prefs = getSharedPreferences("vpn_prefs", MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        
        if (selectedApps.isNotEmpty()) {
            selectedApps.forEach { pkg ->
                try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
            }
        } else {
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
        }

        val pfd = builder.establish() ?: throw Exception("Failed to establish TUN")
        vpnInterface = pfd
        return pfd.fd
    }

    private fun stopVpn() {
        targetState = false
        isServiceRunning = false
        
        serviceScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                vpnMutex.withLock {
                    if (targetState) {
                        Log.d("DUAL_CORE", "stopVpn skipped (targetState became true)")
                        return@withContext
                    }
                    
                    // Ensures we try to clean everything even if startup failed partially,
                    // but we do want to avoid double-stopping.
                    
                    try {
                        Log.d("DUAL_CORE", "Stopping bridge and core...")
                        v2rayController?.stopLoop()
                        
                        // 停止独立进程中的 Hysteria2
                        val h2Intent = Intent(this@ProxyVpnService, HysteriaCoreService::class.java).apply {
                            this.action = "STOP"
                        }
                        startService(h2Intent)
                        
                        // 给一点时间让停止命令发出
                        kotlinx.coroutines.delay(200)
                        
                    } catch (e: Exception) {
                        Log.e("DUAL_CORE", "Error during stop", e)
                    } finally {
                        v2rayController = null
                        try {
                            vpnInterface?.close()
                        } catch (e: Exception) {
                            Log.e("DUAL_CORE", "Error closing vpnInterface", e)
                        } finally {
                            vpnInterface = null
                        }
                        isRunning = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onEmitStatus(status: Long, msg: String?): Long {
        Log.d("V2RAY_BRIDGE", "Status ($status): $msg")
        return 0
    }

    override fun startup(): Long {
        Log.d("V2RAY_BRIDGE", "V2Ray Core starting...")
        return 0
    }

    override fun shutdown(): Long {
        Log.d("V2RAY_BRIDGE", "V2Ray Core stopping...")
        return 0
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceJob.cancel()
    }
}
