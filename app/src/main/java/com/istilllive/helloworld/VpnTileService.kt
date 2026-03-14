package com.istilllive.helloworld

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.graphics.drawable.Icon
import android.net.VpnService
import android.widget.Toast
import java.net.URL
import kotlinx.coroutines.*
import android.annotation.SuppressLint

@SuppressLint("NewApi")
class VpnTileService : TileService() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRunning = ProxyVpnService.isServiceRunning
        
        // 动态设置图标（确保它是你刚放进去的矢量图）
        tile.icon = Icon.createWithResource(this, R.drawable.tile_icon)
        
        // 建议：label 保持为 APP 名称，状态交给 state 和 subtitle 处理
        // 很多手机系统（如小米）会自动在 label 后面追加基于 state 的状态文字
        tile.label = getString(R.string.tile_label) 
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        // 如果是 Android 10+，可以使用 subtitle 属性
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) getString(R.string.tile_on) else getString(R.string.tile_off)
        }
        
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = ProxyVpnService.isServiceRunning
        if (isRunning) {
            stopVpn()
        } else {
            // 检查 VPN 是否已授权
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // 未授权，必须引导用户打开 APP 进行授权
                Toast.makeText(this, getString(R.string.toast_grant_permission), Toast.LENGTH_LONG).show()
                val startAppIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this, 0, startAppIntent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("StartActivityAndCollapseDeprecated", "DEPRECATION")
                    startActivityAndCollapse(startAppIntent)
                }
                return
            }
            
            // 已授权，尝试开启
            startVpnWithCheck()
        }
    }

    private fun startVpnWithCheck() {
        scope.launch {
            val file = java.io.File(filesDir, "config.json")
            if (!file.exists()) {
                // 自动补全下载逻辑
                qsTile.subtitle = getString(R.string.tile_syncing)
                qsTile.updateTile()
                val success = withContext(Dispatchers.IO) {
                    try {
                        val content = URL(BuildConfig.CONFIG_URL).readText()
                        file.writeText(content)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (!success) {
                    Toast.makeText(this@VpnTileService, getString(R.string.toast_sync_failed), Toast.LENGTH_SHORT).show()
                    updateTile()
                    return@launch
                }
            }
            
            val intent = Intent(this@VpnTileService, ProxyVpnService::class.java).apply {
                action = "START_VPN"
            }
            androidx.core.content.ContextCompat.startForegroundService(this@VpnTileService, intent)
            
            // 即时反馈
            qsTile.state = Tile.STATE_ACTIVE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                qsTile.subtitle = getString(R.string.tile_turning_on)
            }
            qsTile.updateTile()
        }
    }

    private fun stopVpn() {
        val intent = Intent(this, ProxyVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        startService(intent)
        
        qsTile.state = Tile.STATE_INACTIVE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            qsTile.subtitle = getString(R.string.tile_turning_off)
        }
        qsTile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
