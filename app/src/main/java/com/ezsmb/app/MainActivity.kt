package com.ezsmb.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ezsmb.app.core.*
import com.ezsmb.app.core.Target
import com.ezsmb.app.transfer.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this) }
                else if (dark) darkColorScheme(primary = Color(0xffbdc5ff)) else lightColorScheme(primary = Color(0xff4867d7))
            MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp))) { PrivacyGate({ finish() }) { EzScreen() } }
        }
    }
}
fun bytes(value: Long): String {
    if (value < 0) return "大小未知"
    if (value < 1024) return "$value B"
    val units = listOf("KB", "MB", "GB", "TB")
    var n = value.toDouble() / 1024; var i = 0
    while (n >= 1024 && i < units.lastIndex) { n /= 1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s", n, units[i])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EzScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tasks by vm.app.transfers.tasks.collectAsStateWithLifecycle()
    val conflict by vm.app.transfers.conflict.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var manual by rememberSaveable { mutableStateOf(false) }
    var transferSheet by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var manualShare by rememberSaveable { mutableStateOf(false) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var uploadTarget by remember { mutableStateOf<ScreenState?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun notifyPermission() { if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uploadTarget?.let { vm.upload(uris, it) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) vm.foreground(true) else if (event == Lifecycle.Event.ON_STOP) vm.foreground(false) }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.foreground(true)
        onDispose { lifecycle.removeObserver(observer); vm.foreground(false) }
    }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    val nav = rememberNavController()
    LaunchedEffect(state.page) { if (nav.currentDestination?.route != state.page) nav.navigate(state.page) { popUpTo("home") { inclusive = state.page == "home" }; launchSingleTop = true } }
    Scaffold(
        topBar = {
            TopAppBar(title = { Column { Text(if (state.page == "home") "ezSMB" else state.login?.displayName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.page != "home") Text(if (state.page == "shares") "共享文件夹" else state.share, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                navigationIcon = { if (state.page != "home") IconButton(onClick = vm::back) { Icon(EzIcons.ArrowBack, "返回") } },
                actions = {
                    if (state.page == "browser") {
                        val starred = state.login?.let { login -> state.favorites.any { it.matches(login, state.share, state.path) } } == true
                        IconButton(onClick = vm::toggleFavorite, enabled = !state.busy) {
                            Icon(if (starred) EzIcons.Star else EzIcons.StarOutline, if (starred) "取消收藏当前目录" else "收藏当前目录", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = vm::refresh, enabled = !state.busy) { Icon(EzIcons.Refresh, "刷新") }
                    Box { IconButton(onClick = { menu = true }) { Icon(EzIcons.MoreVert, "更多") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("手动连接") }, onClick = { menu = false; manual = true })
                            if (state.page == "shares") DropdownMenuItem(text = { Text("打开其他共享") }, onClick = { menu = false; manualShare = true })
                            DropdownMenuItem(text = { Text("共享设置教程") }, onClick = { menu = false; help = true })
                            DropdownMenuItem(text = { Text("隐私政策") }, onClick = { menu = false; privacy = true })
                            DropdownMenuItem(text = { Text("清除保存的设备与凭据") }, onClick = { menu = false; clearDialog = true })
                            DropdownMenuItem(text = { Text("打开下载目录") }, onClick = { menu = false; openDownloadFolder(context) })
                            DropdownMenuItem(text = { Text("关于 ezSMB") }, onClick = { menu = false; about = true })
                        }
                    }
                })
        }, snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tasks.isNotEmpty()) Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding().clickable { transferSheet = true }) {
                val active = tasks.firstOrNull { it.active }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(active?.let { "${if (it.upload) "上传" else "下载"} · ${it.name}" } ?: "传输任务 · ${tasks.size} 项", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    if (active != null) { Spacer(Modifier.height(8.dp)); TaskProgress(active) } else Text("查看结果", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        floatingActionButton = {
            if (state.page == "browser") ExtendedFloatingActionButton(onClick = { notifyPermission(); uploadTarget = state; picker.launch(arrayOf("*/*")) }, icon = { Icon(EzIcons.Upload, null) }, text = { Text("上传") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.busy || state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            AnimatedVisibility(state.message.isNotEmpty()) {
                var details by remember(state.message) { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (state.diagnostic.isNotEmpty()) TextButton(onClick = { details = !details }) { Text(if (details) "收起详细信息" else "详细信息") }
                        if (details) Text(state.diagnostic, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") { Home(state, vm::select, { manual = true }, vm::scan, vm::openFavorite, vm::removeFavorite, vm::rebindFavorite) }
                composable("shares") { Shares(state, vm::share); BackHandler { vm.back() } }
                composable("browser") {
                    Column {
                        Text((listOf(state.share) + state.path.split('\\').filter { it.isNotEmpty() }).joinToString("  /  "), Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        PullToRefreshBox(isRefreshing = state.busy, onRefresh = vm::refresh) {
                            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), modifier = Modifier.fillMaxSize()) {
                                if (state.entries.isEmpty() && !state.busy) item { EmptyMessage("文件夹是空的", "使用下方上传按钮添加文件", EzIcons.FolderOpen) }
                                items(state.entries, key = { it.name }) { entry ->
                                    ListItem(modifier = Modifier.clickable(enabled = !state.busy) { if (entry.directory) vm.folder(entry) else { notifyPermission(); vm.download(entry) } },
                                        headlineContent = { Text(entry.name) }, supportingContent = { Text(if (entry.directory) "文件夹" else "${bytes(entry.size)} · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.modified))}") },
                                        leadingContent = { RemoteThumbnail(vm.app.thumbnails, state.login, state.share, state.path, entry, state.previewRevision) },
                                        trailingContent = { Icon(if (entry.directory) EzIcons.ChevronRight else EzIcons.Download, if (entry.directory) "打开文件夹" else "下载") })
                                }
                            }
                        }
                    }
                    BackHandler { vm.back() }
                }
            }
        }
    }
    BackHandler(state.page == "home" && state.busy) { vm.back() }
    if (manual) InputDialog("手动连接", "IP、主机名或 smb://电脑/共享/路径", { manual = false }) { vm.manual(it); manual = false }
    if (manualShare) InputDialog("打开其他共享", "共享名或 SMB 路径", { manualShare = false }) { vm.share(it); manualShare = false }
    if (help) SharingHelp { help = false }
    if (privacy) PrivacyDialog { privacy = false }
    state.credentialTarget?.let { target -> CredentialsSheet(target, state.busy, state.message, vm::dismissCredentials, vm::authenticate, state.anonymousTarget, vm::checkAnonymous, vm::connectAnonymous) }
    if (transferSheet) ModalBottomSheet(onDismissRequest = { transferSheet = false }) {
        Text("传输任务", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(tasks, key = { it.id }) { task ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${stateLabel(task.state)} · ${bytes(task.done)} / ${bytes(task.total)}${if (task.active) " · ${bytes(task.speed)}/s" else ""}", style = MaterialTheme.typography.bodySmall)
                    if (task.active) TaskProgress(task)
                    if (task.error.isNotEmpty()) Text(task.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (task.active && task.state != TransferState.FINALIZING) TextButton(onClick = { vm.app.transfers.cancel(task.id) }) { Text("取消") }
                        if (task.state in setOf(TransferState.FAILED, TransferState.INTERRUPTED, TransferState.CANCELLED)) TextButton(onClick = { notifyPermission(); vm.app.transfers.retry(task, state.login) }) { Text("重新传输") }
                        if (task.state == TransferState.COMPLETED && !task.upload) TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(task.result), context.contentResolver.getType(Uri.parse(task.result)) ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { vm.events.tryEmit("没有可打开此文件的应用，请到 Downloads/ezSMB 查看") }
                        }) { Text("打开文件") }
                        if (task.state == TransferState.COMPLETED && !task.upload) TextButton(onClick = { openDownloadFolder(context) }) { Text("打开目录") }
                        if (task.state == TransferState.COMPLETED && !task.upload) TextButton(onClick = {
                            val uri = Uri.parse(task.result)
                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(context.contentResolver.getType(uri) ?: "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "分享文件")) }.onFailure { vm.events.tryEmit("无法打开系统分享面板") }
                        }) { Text("分享") }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            }
        }
    }
    conflict?.let { c ->
        var applyBatch by remember(c.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { vm.app.transfers.answer(ConflictChoice.SKIP) }, title = { Text("已有同名文件") },
            text = { Column { Text("${c.name}\n${if (c.overwriteAllowed) "覆盖会替换原文件，只有新文件完整传输后才执行。" else "为保护已有下载，保留原文件并自动重命名新文件。"}"); if (c.batch.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(applyBatch, { applyBatch = it }); Text("应用到本批其余冲突") } } },
            confirmButton = { TextButton(onClick = { vm.app.transfers.answer(ConflictChoice.RENAME, applyBatch) }) { Text("自动重命名") } },
            dismissButton = { Row { TextButton(onClick = { vm.app.transfers.answer(ConflictChoice.SKIP, applyBatch) }) { Text("跳过") }; if (c.overwriteAllowed) TextButton(onClick = { vm.app.transfers.answer(ConflictChoice.OVERWRITE, applyBatch) }) { Text("确认覆盖", color = MaterialTheme.colorScheme.error) } } })
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("ezSMB") }, text = { Text("局域网里的文件，轻松传递。\n\nSMB2 / SMB3 · 本地加密凭据\n无云服务、广告或统计\n\n下载位置：Downloads/ezSMB\n进程被结束后，请返回应用重新传输。") }, confirmButton = { TextButton(onClick = { about = false }) { Text("知道了") } })
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("清除保存的信息？") }, text = { Text("删除设备历史、目录收藏和保存的凭据。当前连接与已排队任务仍可继续。") }, confirmButton = { TextButton(onClick = { vm.clearSaved(); clearDialog = false }) { Text("清除") } }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("取消") } })
}

@Composable
fun Home(s: ScreenState, select: (Device) -> Unit, manual: () -> Unit, scan: () -> Unit, openFavorite: (Favorite) -> Unit, removeFavorite: (Favorite) -> Unit, rebindFavorite: (Favorite, Device) -> Unit) {
    var relink by remember { mutableStateOf<Favorite?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("轻松访问附近的共享文件", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp)) }
        if (s.favorites.isNotEmpty()) {
            item { Text("收藏目录", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(s.favorites, key = { "favorite:${it.id}" }) { favorite ->
                var menu by remember { mutableStateOf(false) }
                ListItem(modifier = Modifier.clickable(enabled = !s.busy) { openFavorite(favorite) },
                    headlineContent = { Text(favorite.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${favorite.deviceName} · ${favorite.share}${if (favorite.path.isEmpty()) "" else "/${favorite.path.replace('\\', '/')}"}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(EzIcons.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Box {
                        IconButton(onClick = { menu = true }) { Icon(EzIcons.MoreVert, "收藏选项 ${favorite.title}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("重新关联设备") }, onClick = { menu = false; relink = favorite })
                            DropdownMenuItem(text = { Text("取消收藏") }, onClick = { menu = false; removeFavorite(favorite) })
                        }
                    } })
            }
        }
        listOf(true, false).forEach { saved ->
            val devices = s.devices.filter { it.saved == saved }
            if (devices.isNotEmpty()) {
                item { Text(if (saved) "常用设备" else "附近设备", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(devices, key = { it.identity }) { d ->
                    Surface(onClick = { select(d) }, enabled = !s.busy, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        ListItem(colors = ListItemDefaults.colors(containerColor = Color.Transparent), headlineContent = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(d.name, modifier = Modifier.weight(1f, fill = false)); if (d.saved) Icon(EzIcons.CheckCircle, "曾成功连接", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) } }, supportingContent = { Text(d.host) }, leadingContent = { Icon(EzIcons.Computer, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(EzIcons.ChevronRight, null) })
                    }
                }
            }
        }
        if (s.devices.isEmpty()) item { EmptyMessage(if (s.scanning) "正在查找局域网中的电脑" else "暂时没有找到共享电脑", "请确认电脑已开启共享，并与手机位于可互访的局域网", EzIcons.Devices) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { if (!s.scanning && s.devices.isEmpty()) TextButton(onClick = scan) { Text("重新扫描") }; TextButton(onClick = manual) { Icon(EzIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("手动连接") } } }
    }
    relink?.let { favorite -> AlertDialog(onDismissRequest = { relink = null },
        title = { Text("重新关联设备") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("电脑重启或换网后，可选择现在的设备。验证登录并打开目录后才更新收藏。")
            val devices = s.devices.filter { it.online }
            if (devices.isEmpty()) Text("暂未发现在线设备，请返回首页刷新。", Modifier.padding(top = 16.dp))
            devices.forEach { device -> TextButton(onClick = { relink = null; rebindFavorite(favorite, device) }, enabled = !s.busy) { Text("${device.name}\n${device.host}") } }
        } }, confirmButton = { TextButton(onClick = { relink = null }) { Text("取消") } }) }
}
@Composable
fun Shares(s: ScreenState, open: (String) -> Unit) {
    var manual by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        if (s.enumRestricted) item { Text("已经连接电脑，但无法自动获取共享文件夹。输入共享名即可继续访问。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 20.dp)) }
        if (!s.enumRestricted && s.shares.isEmpty()) item { Text("服务器没有返回磁盘共享，也可以手动输入共享路径。") }
        items(s.shares, key = { it }) { name -> ListItem(modifier = Modifier.clickable(enabled = !s.busy) { open(name) }, headlineContent = { Text(name) }, leadingContent = { Icon(EzIcons.FolderShared, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(EzIcons.ChevronRight, null) }) }
        if (s.enumRestricted || s.shares.isEmpty()) item { TextButton(onClick = { manual = true }, enabled = !s.busy) { Text("手动填写共享名") } }
    }
    if (manual) InputDialog("打开共享", "共享名或 SMB 路径", { manual = false }) { open(it); manual = false }
}
@Composable
fun EmptyMessage(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, null, Modifier.padding(20.dp).size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun InputDialog(title: String, label: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(input, { input = it }, label = { Text(label) }, singleLine = true) }, confirmButton = { TextButton(onClick = { confirm(input) }, enabled = input.isNotBlank()) { Text("连接") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsSheet(target: Target, busy: Boolean, error: String, dismiss: () -> Unit, connect: (Target, Credentials, Boolean) -> Unit, anonymousTarget: Target?, checkAnonymous: (Target?) -> Unit, connectAnonymous: (Target, Boolean) -> Unit) {
    var user by rememberSaveable(target) { mutableStateOf("") }
    var password by remember(target) { mutableStateOf("") }
    var domain by rememberSaveable(target) { mutableStateOf("") }
    var port by rememberSaveable(target) { mutableStateOf(target.port.toString()) }
    var share by rememberSaveable(target) { mutableStateOf(target.share) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var remember by rememberSaveable { mutableStateOf(true) }
    val effectiveTarget = port.toIntOrNull()?.takeIf { it in 1..65535 }?.let { target.copy(port = it, share = share.trim()) }
    LaunchedEffect(effectiveTarget) { checkAnonymous(effectiveTarget) }
    DisposableEffect(target) { onDispose { checkAnonymous(null) } }
    ModalBottomSheet(onDismissRequest = { if (!busy) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("连接 ${target.host}", style = MaterialTheme.typography.headlineSmall)
            Text("使用电脑的 Windows 本地账户或 NAS 账户", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) EzIcons.VisibilityOff else EzIcons.Visibility, if (visible) "隐藏密码" else "显示密码") } }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(remember, { remember = it }); Text("记住此设备和凭据") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起选项" else "更多选项") }
            if (advanced) {
                OutlinedTextField(domain, { domain = it }, label = { Text("域（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it }, label = { Text("端口") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(share, { share = it }, label = { Text("共享名（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (effectiveTarget != null && anonymousTarget == effectiveTarget) {
                TextButton(onClick = { connectAnonymous(effectiveTarget, remember) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("匿名连接") }
            }
            Button(onClick = { effectiveTarget?.let { connect(it, Credentials(user, password, domain), remember) } }, enabled = !busy && user.isNotBlank() && effectiveTarget != null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "正在连接…" else "连接") }
        }
    }
}
@Composable
fun TaskProgress(task: TransferTask) {
    if (task.total <= 0 || task.state == TransferState.PREPARING || task.state == TransferState.FINALIZING) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}
fun stateLabel(state: TransferState) = when (state) {
    TransferState.QUEUED -> "排队中"; TransferState.PREPARING -> "准备中"; TransferState.RUNNING -> "传输中"; TransferState.FINALIZING -> "正在确认完成"; TransferState.COMPLETED -> "已完成"; TransferState.FAILED -> "失败"; TransferState.CANCELLED -> "已取消"; TransferState.INTERRUPTED -> "已中断"
}
