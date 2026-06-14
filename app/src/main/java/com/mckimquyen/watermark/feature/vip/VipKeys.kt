package com.mckimquyen.watermark.feature.vip

import android.util.Base64

object VipKeys {
    private const val KEY_30_DAYS_B64 = "OWZBMHE3ZU4hMjdjTHgwNEAyMTk5M1kydTBJNyNRMA=="
    private const val KEY_3_DAYS_B64 = "ZVE3QDkzTDBmITJZMjcwN3hOMDQwMjE5OTN1MEkjMmFL"

    fun durationDaysFor(input: String): Int? {
        val normalized = input.trim()
        return when (normalized) {
            decode(KEY_30_DAYS_B64) -> 30
            decode(KEY_3_DAYS_B64) -> 3
            else -> null
        }
    }

    private fun decode(value: String): String = String(Base64.decode(value, Base64.NO_WRAP))
}
