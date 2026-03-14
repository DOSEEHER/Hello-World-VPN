package com.istilllive.helloworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import java.net.URL
import com.istilllive.helloworld.ui.theme.HelloWorldTheme
import hybridge.Hybridge
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    ProxyScreen()
                }
            }
        }
    }
}

@Composable
fun ProxyScreen() {
    val context = LocalContext.current
    var isProxyActive by remember { mutableStateOf(ProxyVpnService.isServiceRunning) }
    var isLoading by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 定期同步状态，防止 Tile 开关后界面不同步
    LaunchedEffect(Unit) {
        while(true) {
            isProxyActive = ProxyVpnService.isServiceRunning
            delay(500)
        }
    }

    // 注册一个用于请求 VPN 权限的 Launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户同意了 VPN 权限，现在启动我们的 Service
            isProxyActive = true
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = "START_VPN"
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } else {
            // 用户拒绝了，开关回弹
            isProxyActive = false
        }
    }

    fun startVpn() {
        // VpnService.prepare 会检查应用是否有权启动 VPN
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            // 需要用户手动授权，弹出系统的 VPN 连接请求框
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // 已经是授权状态了，直接启动我们的自定义的 ProxyVpnService
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = "START_VPN"
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopVpn() {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        context.startService(intent)
    }

    // 使用 Column 将所有元素居中纵向排列
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 185.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        // 1. Logo 和 标题行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 使用本地的 drawable 图片
            Image(
                painter = painterResource(id = R.drawable.eiai_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // 右侧的两行文字：Hello World 和 iStill.Live
            Column {
                Text(
                    text = "Hello World",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "iStill.Live",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))

        // 2. 超大的 Switch 开关 (包裹在一个设定了缩放的 Box 里)
        Box(modifier = Modifier.scale(2.5f)) {
            Switch(
                checked = if (isLoading) true else isProxyActive,
                onCheckedChange = { isChecked ->
                    if (isLoading) return@Switch // 防止加载中被连击
                    if (isChecked) {
                        isLoading = true
                        coroutineScope.launch {
                            val success = withContext(Dispatchers.IO) {
                                try {
                                    val configStr = URL(BuildConfig.CONFIG_URL).readText()
                                    File(context.filesDir, "config.json").writeText(configStr)
                                    true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    false
                                }
                            }
                            isLoading = false
                            if (success) {
                                isProxyActive = true
                                startVpn()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_connection_failed), Toast.LENGTH_SHORT).show()
                                isProxyActive = false
                            }
                        }
                    } else {
                        isProxyActive = false
                        stopVpn()
                    }
                }
            )

            if (isLoading) {
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))

        // 3. 连接状态提示文字
        Text(
            text = if (isLoading) stringResource(R.string.status_analyzing) else if (isProxyActive) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. 下方小字副标题
        Text(
            text = if (isLoading) stringResource(R.string.subtitle_wait) else if (isProxyActive) stringResource(R.string.subtitle_protected) else stringResource(R.string.subtitle_unprotected),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 5. 分应用代理设置按钮
        Text(
            text = stringResource(R.string.btn_app_picker),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable { showAppPicker = true }
                .padding(horizontal = 32.dp, vertical = 8.dp)
        )

        if (showAppPicker) {
            AppPickerList(
                onDismiss = { showAppPicker = false }
            )
        }
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val isSystem: Boolean
)

@Composable
fun AppPickerList(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
    
    var showSystemApps by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val allApps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { 
                val isSystem = (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val label = it.loadLabel(pm).toString()
                AppInfo(
                    name = label,
                    packageName = it.packageName, 
                    icon = it.loadIcon(pm),
                    isSystem = isSystem
                ) 
            }
            .sortedBy { it.name.lowercase() }
    }

    val filteredApps = remember(showSystemApps) {
        if (showSystemApps) allApps else allApps.filter { !it.isSystem || it.packageName == "com.android.chrome" }
    }

    val selectedApps = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("selected_apps", emptySet()) ?: emptySet())
        }
    }

    // 自动保存逻辑：当 selectedApps 变化时写入 Prefs
    LaunchedEffect(selectedApps.size) {
        prefs.edit().putStringSet("selected_apps", selectedApps.toSet()).apply()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.content_desc_back)
                        )
                    }
                    Text(stringResource(R.string.title_app_picker), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    
                    Box {
                        androidx.compose.material3.IconButton(onClick = { menuExpanded = true }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.content_desc_more)
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear_all)) },
                                onClick = {
                                    selectedApps.clear()
                                    menuExpanded = false
                                }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (showSystemApps) stringResource(R.string.menu_hide_system) else stringResource(R.string.menu_show_system)) },
                                onClick = {
                                    showSystemApps = !showSystemApps
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Text(
                    text = if (selectedApps.isEmpty()) stringResource(R.string.status_global_mode) else stringResource(R.string.status_selected_apps, selectedApps.size),
                    fontSize = 12.sp, 
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedApps.contains(app.packageName)) {
                                        selectedApps.remove(app.packageName)
                                    } else {
                                        selectedApps.add(app.packageName)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (app.isSystem) stringResource(R.string.label_system, app.name) else app.name, 
                                    fontWeight = FontWeight.Medium,
                                    color = if (app.isSystem) Color.Gray else Color.Unspecified
                                )
                                Text(app.packageName, fontSize = 10.sp, color = Color.Gray)
                            }
                            Checkbox(
                                checked = selectedApps.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedApps.contains(app.packageName)) selectedApps.add(app.packageName)
                                    } else {
                                        selectedApps.remove(app.packageName)
                                    }
                                }
                            )
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProxyScreenPreview() {
    HelloWorldTheme {
        ProxyScreen()
    }
}
