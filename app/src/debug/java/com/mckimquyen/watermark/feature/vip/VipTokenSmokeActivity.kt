package com.mckimquyen.watermark.feature.vip

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.mckimquyen.watermark.BuildConfig
import com.mckimquyen.watermark.databinding.ActivityVipTokenSmokeBinding
import com.roy.sdkadbmob.AdManager

class VipTokenSmokeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVipTokenSmokeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.DEBUG) { "Debug smoke tool is unavailable in release." }
        // Edge-to-edge: Android 15+ ép vẽ dưới thanh hệ thống, nên phải tự đệm theo insets (kể cả cutout).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityVipTokenSmokeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val basePadding = binding.root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.setPadding(basePadding + bars.left, basePadding + bars.top, basePadding + bars.right, basePadding + bars.bottom)
            insets
        }
        binding.tvSmokeStatus.text = when {
            AdManager.isVipTokenResolutionInFlight || AdManager.isVipTokenPending -> "SDK still has a pending VIP token. Wait for resolution before minting another."
            !AdManager.isVipByKeyActive() -> "Ready to mint a one-day, device-bound test token after importing key through the approved app-private pipe."
            else -> "This device already has VIP entitlement. Minting another token may extend it; continue only for the authorized QA run."
        }
        binding.btnMintToken.setOnClickListener { mintToken() }
    }

    private fun mintToken() {
        if (AdManager.isVipTokenResolutionInFlight || AdManager.isVipTokenPending) {
            binding.tvSmokeStatus.text = "Blocked: SDK already has a pending VIP token."
            return
        }
        val harness = VipTokenSmokeHarness(noBackupFilesDir) { privateKey ->
            AdManager.generateVipToken(
                privateKeyBase64 = privateKey,
                days = TOKEN_DAYS,
                bindToCurrentDevice = true,
                bindToApplication = true
            )
        }
        // Throwable (kể cả OutOfMemoryError từ signer): hiện status thay vì crash; key vẫn bị xoá bởi finally của harness.
        val result = try {
            harness.mintToken()
        } catch (_: Throwable) {
            VipTokenSmokeHarness.MintResult.Failed
        }
        val keyNote = if (harness.keyStillOnDisk()) " WARNING: key file could not be removed; delete it manually." else ""
        binding.tvSmokeStatus.text = when (result) {
            VipTokenSmokeHarness.MintResult.Created -> "One-day test token prepared. Close and relaunch app once to test SDK pending path.$keyNote"
            VipTokenSmokeHarness.MintResult.NotArmed -> "No private-key handoff found, or a token is already armed; no new token was made.$keyNote"
            VipTokenSmokeHarness.MintResult.Failed -> "Token generation failed; inspect diagnostics without revealing secrets.$keyNote"
        }
    }

    companion object {
        private const val TOKEN_DAYS = 1
    }
}
