package com.ezsmb.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast

val downloadDirectory: String get() = "${Environment.DIRECTORY_DOWNLOADS}/ezSMB/"

fun openDownloadFolder(context: Context) {
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:${downloadDirectory.trimEnd('/')}")
    val exact = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
    // A directory URI is a navigation hint, not a grant of broad storage access.
    val systemBrowser = context.packageManager.queryIntentActivities(exact, PackageManager.MATCH_DEFAULT_ONLY)
        .firstOrNull { it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
    if (systemBrowser != null && runCatching {
        context.startActivity(exact.setComponent(ComponentName(systemBrowser.activityInfo.packageName, systemBrowser.activityInfo.name)))
    }.isSuccess) return
    val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
    if (runCatching { context.startActivity(picker) }.isSuccess) {
        Toast.makeText(context, "在系统文件中查看 Download/ezSMB；无需选择或授权目录", Toast.LENGTH_LONG).show()
        return
    }
    val opened = runCatching { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }.isSuccess
    Toast.makeText(context, if (opened) "请进入 Download/ezSMB 文件夹" else "请在系统文件管理中打开内部存储/Download/ezSMB", Toast.LENGTH_LONG).show()
}
