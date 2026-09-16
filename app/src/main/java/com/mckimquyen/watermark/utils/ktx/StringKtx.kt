package com.mckimquyen.watermark.utils.ktx

// fun String.toMD5(): String {
//    val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
//    return bytes.toHex()
// }

fun ByteArray.toHex(): String {
    return joinToString("") { "%02x".format(it) }
}
