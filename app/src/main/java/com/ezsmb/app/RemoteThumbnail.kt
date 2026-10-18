package com.ezsmb.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ezsmb.app.core.*
import com.ezsmb.app.data.Thumbnails

@Composable
fun RemoteThumbnail(loader: Thumbnails, login: Login?, share: String, path: String, entry: Entry, revision: Int = 0) {
    val kind = previewKind(entry.name)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val bitmap by produceState<Bitmap?>(null, login, share, path, entry, lifecycle, revision) {
        value = null
        if (login != null && (entry.directory || kind != null) && lifecycle.isAtLeast(Lifecycle.State.STARTED)) value = loader.load(login, share, path, entry)
    }
    var expanded by remember(login, share, path, entry) { mutableStateOf(false) }
    if (!entry.directory && kind == null) Icon(EzIcons.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
    else Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        val previewModifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
        Box(if (entry.directory) previewModifier else previewModifier.clickable(enabled = bitmap != null) { expanded = true }, contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "预览 ${entry.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (entry.directory) Icon(EzIcons.Folder, null, tint = MaterialTheme.colorScheme.primary)
            else Text(if (kind == PreviewKind.VIDEO) "视频" else "图片", style = MaterialTheme.typography.labelMedium)
            if (bitmap != null && entry.directory) Surface(modifier = Modifier.align(Alignment.BottomEnd), shape = RoundedCornerShape(topStart = 6.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                Icon(EzIcons.Folder, "文件夹内容预览", Modifier.padding(3.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (bitmap != null && !entry.directory && kind == PreviewKind.VIDEO) Surface(modifier = Modifier.align(Alignment.BottomEnd), color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)) { Text("视频", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall) }
        }
    }
    if (expanded && bitmap != null) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(entry.name, maxLines = 2) },
        text = { Column { Image(bitmap!!.asImageBitmap(), "文件缩略预览", Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit)
            if (kind == PreviewKind.VIDEO) Text("视频画面预览", style = MaterialTheme.typography.labelMedium) } },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } })
}
