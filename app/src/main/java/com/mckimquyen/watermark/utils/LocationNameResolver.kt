package com.mckimquyen.watermark.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.mckimquyen.watermark.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * IDEA-16: đổi toạ độ EXIF GPS → tên địa danh cho token {location}. [lookup] nhận (lat, lon) và
 * trả tên địa danh hoặc `null` (không có mạng / Geocoder không có trên máy / lỗi) — tách thành
 * tham số để unit test được (Geocoder không chạy trên JVM). Gọi đồng bộ, CHỈ từ thread nền
 * (preview: Dispatchers.IO, export: WorkManager).
 */
@Singleton
class LocationNameResolver(private val lookup: (Double, Double) -> String?) {

    @Inject
    constructor(@ApplicationContext context: Context) : this({ lat, lon -> geocode(context, lat, lon) })

    // ponytail: xoá sạch khi đầy thay vì LRU — batch hiếm khi vượt vài trăm địa điểm khác nhau.
    private val cache = ConcurrentHashMap<Pair<Long, Long>, String>()

    /** `null` toạ độ → rỗng, giống token EXIF thiếu. Kết quả rỗng/lỗi KHÔNG cache để lần sau (có mạng) thử lại. */
    fun resolve(latitude: Double?, longitude: Double?): String {
        if (latitude == null || longitude == null) return ""
        val key = roundedKey(latitude, longitude)
        cache[key]?.let { return it }
        val name = try {
            lookup(latitude, longitude)
        } catch (e: Exception) {
            AppLog.d(LOG_TAG, "[LOCATION] lookup lỗi: ${e.message}")
            null
        }
        if (name.isNullOrBlank()) return ""
        if (cache.size >= MAX_CACHE_SIZE) cache.clear()
        cache[key] = name
        return name
    }

    companion object {
        private const val LOG_TAG = "LocationNameResolver"

        /** 3 chữ số thập phân ≈ 110m — các ảnh chụp cùng chỗ chỉ geocode 1 lần. */
        private const val COORDINATE_ROUNDING = 1_000.0
        private const val MAX_CACHE_SIZE = 256
        private const val GEOCODE_TIMEOUT_MS = 5_000L

        /** Resolver không geocode — cho nơi không có Context (test, `ExportNaming()` mặc định). */
        val NONE = LocationNameResolver { _, _ -> null }

        internal fun roundedKey(latitude: Double, longitude: Double): Pair<Long, Long> =
            (latitude * COORDINATE_ROUNDING).roundToLong() to (longitude * COORDINATE_ROUNDING).roundToLong()

        /** "Thành phố, Quốc gia" — ưu tiên locality, thiếu thì lùi dần subAdminArea → adminArea. */
        internal fun formatPlace(locality: String?, subAdminArea: String?, adminArea: String?, countryName: String?): String? =
            listOfNotNull(
                listOf(locality, subAdminArea, adminArea).firstOrNull { !it.isNullOrBlank() },
                countryName?.takeIf { it.isNotBlank() }
            ).joinToString(", ").ifEmpty { null }

        private fun geocode(context: Context, latitude: Double, longitude: Double): String? {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context)
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = AtomicReference<Address?>()
                val latch = CountDownLatch(1)
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            result.set(addresses.firstOrNull())
                            latch.countDown()
                        }

                        override fun onError(errorMessage: String?) {
                            AppLog.d(LOG_TAG, "[LOCATION] geocode lỗi: $errorMessage")
                            latch.countDown()
                        }
                    }
                )
                latch.await(GEOCODE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                result.get()
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
            return address?.let { formatPlace(it.locality, it.subAdminArea, it.adminArea, it.countryName) }
        }
    }
}
