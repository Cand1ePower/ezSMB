package com.ezsmb.app.core

/** RFC 1002 node status. Names are display hints only, never authentication identities. */
object NetbiosName {
    fun query(id: Int): ByteArray {
        val out = ByteArray(50)
        out[0] = (id ushr 8).toByte(); out[1] = id.toByte(); out[5] = 1
        out[12] = 32
        val raw = ByteArray(16); raw[0] = 42
        raw.forEachIndexed { i, b -> out[13 + i * 2] = (65 + ((b.toInt() and 255) ushr 4)).toByte(); out[14 + i * 2] = (65 + (b.toInt() and 15)).toByte() }
        out[47] = 33; out[49] = 1
        return out
    }
    fun parse(bytes: ByteArray, id: Int): String? = runCatching {
        fun u16(p: Int): Int = ((bytes[p].toInt() and 255) shl 8) or (bytes[p + 1].toInt() and 255)
        require(bytes.size >= 12 && u16(0) == id && u16(2) and 0x800f == 0x8000)
        fun skipName(start: Int): Int {
            var p = start
            repeat(128) {
                val n = bytes[p].toInt() and 255
                if (n == 0) return p + 1
                if (n and 0xc0 == 0xc0) { require(p + 1 < bytes.size); return p + 2 }
                require(n <= 63); p += n + 1; require(p < bytes.size)
            }
            error("Invalid name")
        }
        var p = 12
        repeat(u16(4).coerceAtMost(32)) { p = skipName(p) + 4 }
        repeat(u16(6).coerceAtMost(32)) {
            p = skipName(p)
            val type = u16(p); val length = u16(p + 8); p += 10
            require(p + length <= bytes.size)
            if (type == 33 && length > 0) {
                val count = bytes[p].toInt() and 255
                require(1 + count * 18 <= length)
                val names = (0 until count).mapNotNull { index ->
                    val at = p + 1 + index * 18
                    val suffix = bytes[at + 15].toInt() and 255
                    val name = String(bytes, at, 15, Charsets.US_ASCII).trim()
                    if (u16(at + 16) and 0x8000 != 0 || suffix !in listOf(0, 32) || name.isBlank() || name.any { c -> c.code !in 32..126 }) null
                    else suffix to name
                }
                return names.maxByOrNull { it.first }?.second
            }
            p += length
        }
        null
    }.getOrNull()
}
