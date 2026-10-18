package com.ezsmb.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingHelp(dismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("共享设置教程", style = MaterialTheme.typography.headlineSmall)
            Text("先让手机和电脑连接同一台路由器。电脑可以使用网线；避免访客 Wi-Fi，并保持电脑开机。")
            HelpSection("Windows · 1 设置网络", "在可信的家庭局域网中，打开 设置 → 网络和 Internet → 当前 Wi-Fi 或以太网，将网络配置为「专用」。\n再进入 高级网络设置 → 高级共享设置，为专用网络开启「网络发现」和「文件和打印机共享」。")
            HelpSection("2 共享一个文件夹", "右键要共享的文件夹 → 显示更多选项 → 授予访问权限 → 特定用户。选择电脑上的账户并添加；只下载选「读取」，需要上传选「读取/写入」，然后点击「共享」。\n也可以在 文件夹属性 → 共享 → 高级共享 中设置共享名与权限。记录共享名，例如 Photos。")
            HelpSection("3 在 ezSMB 连接", "保留密码保护共享。刷新首页，点击电脑，输入被授权的电脑账户和账户密码；Windows Hello PIN 不是账户密码。必要时使用 电脑名\\用户名。\n登录后打开对应共享文件夹。只显示 IP 时也可以连接。")
            HelpSection("找不到电脑或打不开", "• 找不到：确认不是访客网络，路由器未开启客户端隔离；VPN 若不允许局域网访问，需在其设置中允许。\n• 连接超时：检查电脑防火墙是否允许专用网络上的「文件和打印机共享」入站规则（SMB TCP 445）；不要关闭整个防火墙。\n• 登录失败：核对账户密码、电脑名与授权账户；可使用有密码的本地账户。\n• 能登录但打不开：同时检查文件夹「共享」与「安全」页中的账户权限。\n• 无法自动列出共享：在菜单「打开其他共享」输入共享名。也可从首页手动连接 smb://电脑IP/Photos。")
            HelpSection("NAS / Linux Samba", "在设备管理界面启用 SMB2/SMB3 文件服务，创建共享文件夹，并给所用账户读取或读写权限。Linux Samba 还需确认 Samba 账户、共享配置和文件系统权限一致，防火墙允许局域网 TCP 445。\n设置好后回到 ezSMB 刷新。无需安装电脑端配套程序。")
            HelpSection("关于匿名连接", "只有服务器确实允许匿名读取共享目录时，登录弹层才显示「匿名连接」。检测不到时可填写具体共享名后再试。匿名可读取不代表能上传；需要签名或账户权限的共享请正常登录。无需启用 SMB1 或关闭 SMB 签名。")
            HelpSection("IP 经常变化怎么办", "首页会重新发现设备，同一服务器身份的新地址会自动更新到收藏；点击旧地址失败时也会重新查找。\nWindows 重启后服务器身份可能改变。这时从附近设备重新登录，或在收藏菜单选择「重新关联设备」，验证目录可访问后再更新收藏。切换到新网络可能需要重新登录。\n想让电脑长期使用同一 IP，可在路由器的 DHCP / 地址保留中，为电脑网卡设置固定租约。不同路由器菜单名称有所不同；应用不会修改路由器设置。")
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://support.microsoft.com/en-us/windows/experience/connectivity-networking/file-sharing-over-a-network-in-windows"))) }
                    .onFailure { android.widget.Toast.makeText(context, "没有可打开网页的浏览器", android.widget.Toast.LENGTH_SHORT).show() }
            }) { Text("查看微软官方共享说明") }
            TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("知道了") }
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(body, style = MaterialTheme.typography.bodyMedium)
}
