package com.mckimquyen.watermark.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** IDEA-16: [LocationNameResolver] với geocoder giả — Geocoder thật chỉ test được trên thiết bị (`LocationTokenIntegrationTest`). */
class LocationNameResolverTest {

    private val hanoiLat = 21.0285
    private val hanoiLon = 105.8542

    @Test
    fun resolve_nullCoordinates_returnsEmpty_withoutLookup() {
        var calls = 0
        val resolver = LocationNameResolver { _, _ -> calls++; "Hà Nội" }

        assertThat(resolver.resolve(null, hanoiLon)).isEmpty()
        assertThat(resolver.resolve(hanoiLat, null)).isEmpty()
        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun resolve_returnsLookupName_andCachesNearbyCoordinates() {
        var calls = 0
        val resolver = LocationNameResolver { _, _ -> calls++; "Hà Nội, Việt Nam" }

        assertThat(resolver.resolve(hanoiLat, hanoiLon)).isEqualTo("Hà Nội, Việt Nam")
        // Lệch < 0.0005° (~50m) → cùng ô làm tròn 3 chữ số, không geocode lại.
        assertThat(resolver.resolve(hanoiLat + 0.0002, hanoiLon - 0.0002)).isEqualTo("Hà Nội, Việt Nam")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun resolve_farCoordinates_lookupAgain() {
        var calls = 0
        val resolver = LocationNameResolver { lat, _ -> calls++; if (lat > 15) "Hà Nội" else "TP. Hồ Chí Minh" }

        assertThat(resolver.resolve(hanoiLat, hanoiLon)).isEqualTo("Hà Nội")
        assertThat(resolver.resolve(10.7769, 106.7009)).isEqualTo("TP. Hồ Chí Minh")
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun resolve_lookupFailsOrEmpty_returnsEmpty_andRetriesNextTime() {
        var calls = 0
        val resolver = LocationNameResolver { _, _ ->
            calls++
            when (calls) {
                1 -> throw java.io.IOException("offline")
                2 -> null
                else -> "Hà Nội"
            }
        }

        assertThat(resolver.resolve(hanoiLat, hanoiLon)).isEmpty()
        assertThat(resolver.resolve(hanoiLat, hanoiLon)).isEmpty()
        assertThat(resolver.resolve(hanoiLat, hanoiLon)).isEqualTo("Hà Nội")
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun none_alwaysEmpty() {
        assertThat(LocationNameResolver.NONE.resolve(hanoiLat, hanoiLon)).isEmpty()
    }

    @Test
    fun roundedKey_groupsWithinThreeDecimals() {
        assertThat(LocationNameResolver.roundedKey(21.02812, 105.85421))
            .isEqualTo(LocationNameResolver.roundedKey(21.0281, 105.8542))
        assertThat(LocationNameResolver.roundedKey(21.029, 105.854))
            .isNotEqualTo(LocationNameResolver.roundedKey(21.028, 105.854))
    }

    @Test
    fun formatPlace_prefersLocality_thenSubAdmin_thenAdmin_withCountry() {
        assertThat(LocationNameResolver.formatPlace("Hoàn Kiếm", "Quận X", "Hà Nội", "Việt Nam")).isEqualTo("Hoàn Kiếm, Việt Nam")
        assertThat(LocationNameResolver.formatPlace(null, "Quận X", "Hà Nội", "Việt Nam")).isEqualTo("Quận X, Việt Nam")
        assertThat(LocationNameResolver.formatPlace(" ", "", "Hà Nội", null)).isEqualTo("Hà Nội")
        assertThat(LocationNameResolver.formatPlace(null, null, null, "Việt Nam")).isEqualTo("Việt Nam")
        assertThat(LocationNameResolver.formatPlace(null, null, null, null)).isNull()
    }
}
