package com.mckimquyen.watermark.data.model

import androidx.annotation.Keep

@Keep
data class ExifModel(
    val make: String = "",
    val model: String = "",
    val dateTime: String = "",
    val fNumber: String = "",
    val exposureTime: String = "",
    val iso: String = "",
    val focalLength: String = ""
) {
    fun isEmpty(): Boolean {
        return make.isEmpty() && model.isEmpty() && dateTime.isEmpty() && fNumber.isEmpty() && exposureTime.isEmpty() && iso.isEmpty() && focalLength.isEmpty()
    }
    
    fun getFormattedExif(): String {
        val parts = mutableListOf<String>()
        if (focalLength.isNotEmpty()) parts.add(focalLength)
        if (fNumber.isNotEmpty()) parts.add(fNumber)
        if (exposureTime.isNotEmpty()) parts.add(exposureTime)
        if (iso.isNotEmpty()) parts.add("ISO$iso")
        return parts.joinToString("  ")
    }

    fun getCameraName(): String {
        val m = model.trim()
        val c = make.trim()
        if (m.isNotEmpty()) return m
        if (c.isNotEmpty()) return c
        return "Unknown Device"
    }
}
