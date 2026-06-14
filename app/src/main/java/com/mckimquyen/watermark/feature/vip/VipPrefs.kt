package com.mckimquyen.watermark.feature.vip

import android.content.Context
import androidx.core.content.edit

class VipPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val sdkPrefs = context.applicationContext.getSharedPreferences(SDK_PREF_NAME, Context.MODE_PRIVATE)

    fun markUserActivatedVip() {
        prefs.edit {
            putBoolean(KEY_USER_ACTIVATED_VIP, true)
            putLong(KEY_LAST_GRANTED_AT_MS, System.currentTimeMillis())
        }
    }

    fun hasUserActivatedVip(): Boolean = prefs.getBoolean(KEY_USER_ACTIVATED_VIP, false)

    fun lastGrantedAtMs(): Long = prefs.getLong(KEY_LAST_GRANTED_AT_MS, 0L)

    /** Xoá timestamp cấp VIP do app lưu (gọi khi user revoke). Giữ cờ user_redeemed để phân biệt grace về sau. */
    fun clearGrantedAtMs() {
        prefs.edit { remove(KEY_LAST_GRANTED_AT_MS) }
    }

    fun isFirstInstallGraceGranted(): Boolean = sdkPrefs.getBoolean(SDK_KEY_FIRST_INSTALL_GRANT, false)

    companion object {
        private const val PREF_NAME = "vip_management"
        private const val KEY_USER_ACTIVATED_VIP = "pref_user_redeemed_key_at_least_once"
        private const val KEY_LAST_GRANTED_AT_MS = "pref_vip_granted_at_ms"

        private const val SDK_PREF_NAME = "loitp_admob"
        private const val SDK_KEY_FIRST_INSTALL_GRANT = "keyAddVIPMemberFirstInitSuccess"
    }
}
