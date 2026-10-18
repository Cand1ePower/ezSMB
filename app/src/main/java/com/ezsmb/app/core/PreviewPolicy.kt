package com.ezsmb.app.core

enum class PreviewKind { IMAGE, VIDEO }
fun folderPreviewCandidates(entries: List<Entry>): List<Entry> = entries.filter {
    !it.directory && it.size > 0 && previewKind(it.name) != null &&
        (previewKind(it.name) != PreviewKind.IMAGE || it.size <= 12 * 1024 * 1024)
}.sortedWith(compareBy<Entry> { if (previewKind(it.name) == PreviewKind.IMAGE) 0 else 1 }
    .thenComparator { a, b -> naturalNames.compare(a.name, b.name) }).take(2)
fun previewKind(name: String): PreviewKind? = when (name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
    "jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp" -> PreviewKind.IMAGE
    "mp4", "m4v", "mov", "mkv", "webm", "3gp", "avi" -> PreviewKind.VIDEO
    else -> null
}
fun previewSample(width: Int, height: Int, edge: Int = 384): Int {
    require(width > 0 && height > 0 && edge > 0)
    var sample = 1
    while (maxOf(width, height).toLong() / sample > edge * 2L) sample *= 2
    return sample
}
