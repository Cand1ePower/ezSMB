package com.ezsmb.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val privacyText = """ezSMB 由 CandlePower 提供，用于手机与您指定的电脑或 NAS 之间的 SMB 文件访问。

设备发现会读取当前局域网的地址和网络状态，并在前台探测有限范围的 SMB 服务。设备名称、IP、共享路径、收藏和传输记录保存在本机；用户名密码仅用于您选择的服务器认证，选择记住时使用 Android Keystore 加密保存。

文件内容在手机和您指定的 SMB 服务器之间直接传输，不经过开发者服务器。图片、视频和文件夹缩略图仅在内存中缓存。下载文件保存到 Download/ezSMB，上传文件由您通过系统文件选择器授权。

应用没有广告、统计、云账户或崩溃上传 SDK。网络权限用于 SMB 连接，通知和前台服务用于显示与维持您发起的传输，唤醒锁用于传输期间避免设备休眠中断。

可以在菜单清除设备、凭据和收藏；卸载会清除应用私有数据。已下载到系统目录的文件需由您在文件管理器中删除。您选择打开官网、GitHub 或分享文件时，将由浏览器、网站或接收应用处理相应访问。

联系开发者：candlepower1112@gmail.com；github.com/Cand1ePower/ezSMB/issues。请勿在公开反馈中提交密码或私人文件。

版本日期：2026-10-18"""

@Composable
fun PrivacyGate(decline: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var accepted by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { accepted = withContext(Dispatchers.IO) { context.getSharedPreferences("privacy", 0).getBoolean("accepted-v1", false) } }
    when (accepted) {
        true -> content()
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> AlertDialog(onDismissRequest = decline, title = { Text("隐私说明") },
            text = { Text(privacyText, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { context.getSharedPreferences("privacy", 0).edit().putBoolean("accepted-v1", true).commit() }
                    if (saved) accepted = true
                }
            }) { Text("同意并开始") } },
            dismissButton = { TextButton(onClick = decline) { Text("退出") } })
    }
}

@Composable
fun PrivacyDialog(dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("隐私政策") },
        text = { Text(privacyText, Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}
