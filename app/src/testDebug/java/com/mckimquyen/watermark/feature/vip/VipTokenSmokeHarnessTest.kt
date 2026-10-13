package com.mckimquyen.watermark.feature.vip

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class VipTokenSmokeHarnessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun consumePendingToken_withoutSentinel_doesNotSubmit() {
        var submissions = 0
        val harness = harness()

        val result = harness.consumePendingToken { submissions++; true }

        assertThat(result).isEqualTo(SubmitResult.NotArmed)
        assertThat(submissions).isEqualTo(0)
    }

    @Test
    fun consumePendingToken_deletesHandoffBeforeSubmitAndSubmitsOnlyOnce() {
        val harness = harness()
        val opaqueToken = UUID.randomUUID().toString()
        harness.tokenFile.writeText(opaqueToken)
        harness.sentinelFile.writeText("armed")
        var handoffDeletedBeforeSubmit = false
        var submittedExpectedToken = false

        val firstResult = harness.consumePendingToken {
            handoffDeletedBeforeSubmit = !harness.tokenFile.exists() && !harness.sentinelFile.exists()
            submittedExpectedToken = it == opaqueToken
            true
        }
        val secondResult = harness.consumePendingToken { error("one-shot handoff submitted twice") }

        assertThat(firstResult).isEqualTo(SubmitResult.Submitted)
        assertThat(secondResult).isEqualTo(SubmitResult.NotArmed)
        assertThat(handoffDeletedBeforeSubmit).isTrue()
        assertThat(submittedExpectedToken).isTrue()
    }

    @Test
    fun mintToken_whenAlreadyArmed_stillDeletesStrayKeyAndNeverSigns() {
        val harness = harness()
        harness.keyFile.writeText(UUID.randomUUID().toString())
        harness.sentinelFile.writeText("armed")

        val result = harness.mintToken()

        assertThat(result).isEqualTo(VipTokenSmokeHarness.MintResult.NotArmed)
        assertThat(harness.keyFile.exists()).isFalse()
    }

    @Test
    fun consumeInitDelayMs_withoutFile_returnsZero() {
        assertThat(harness().consumeInitDelayMs()).isEqualTo(0L)
    }

    @Test
    fun consumeInitDelayMs_readsValueAndDeletesFileSoDelayAppliesOnce() {
        val harness = harness()
        harness.initDelayFile.writeText("35000\n")

        assertThat(harness.consumeInitDelayMs()).isEqualTo(35_000L)
        assertThat(harness.initDelayFile.exists()).isFalse()
        assertThat(harness.consumeInitDelayMs()).isEqualTo(0L)
    }

    @Test
    fun consumeInitDelayMs_clampsToMaximumSoAppCannotStayUninitialized() {
        val harness = harness()
        harness.initDelayFile.writeText("999999999")

        assertThat(harness.consumeInitDelayMs()).isEqualTo(VipTokenSmokeHarness.MAX_INIT_DELAY_MS)
    }

    @Test
    fun consumeInitDelayMs_withGarbageOrNegative_returnsZeroAndStillDeletesFile() {
        val harness = harness()
        listOf("abc", "-5", "").forEach { garbage ->
            harness.initDelayFile.writeText(garbage)

            assertThat(harness.consumeInitDelayMs()).isEqualTo(0L)
            assertThat(harness.initDelayFile.exists()).isFalse()
        }
    }

    @Test
    fun mintToken_whenKeyUnreadable_returnsFailedAndStillDeletesKey() {
        val harness = harness()
        // Thư mục trùng tên key: isFile=false nên không dùng được; dùng key hợp lệ rồi chặn đọc qua quyền file.
        harness.keyFile.writeText(UUID.randomUUID().toString())
        val readable = harness.keyFile.setReadable(false, false)
        org.junit.Assume.assumeTrue("Không chặn được quyền đọc (chạy root?)", readable && !harness.keyFile.canRead())
        var signed = false

        val result = VipTokenSmokeHarness(harness.keyFile.parentFile!!) { signed = true; "token" }.mintToken()

        assertThat(result).isEqualTo(VipTokenSmokeHarness.MintResult.Failed)
        assertThat(signed).isFalse()
        assertThat(harness.keyFile.exists()).isFalse()
    }

    @Test
    fun mintToken_whenSignerThrowsError_stillDeletesKey() {
        val harness = harness { throw OutOfMemoryError("simulated") }
        harness.keyFile.writeText(UUID.randomUUID().toString())

        runCatching { harness.mintToken() }

        assertThat(harness.keyFile.exists()).isFalse()
        assertThat(harness.tokenFile.exists()).isFalse()
        assertThat(harness.sentinelFile.exists()).isFalse()
    }

    @Test
    fun keyStillOnDisk_isFalseAfterEveryMintOutcome() {
        val created = harness().also { it.keyFile.writeText("k") }
        created.mintToken()
        assertThat(created.keyStillOnDisk()).isFalse()

        val failed = harness { error("boom") }.also { it.keyFile.writeText("k") }
        failed.mintToken()
        assertThat(failed.keyStillOnDisk()).isFalse()
    }

    @Test
    fun mintToken_deletesKeyAndStoresOneShotToken() {
        val opaqueKey = UUID.randomUUID().toString()
        val opaqueToken = UUID.randomUUID().toString()
        val harness = harness { key -> if (key == opaqueKey) opaqueToken else error("unexpected key") }
        harness.keyFile.writeText(opaqueKey)

        val result = harness.mintToken()

        assertThat(result).isEqualTo(VipTokenSmokeHarness.MintResult.Created)
        assertThat(harness.keyFile.exists()).isFalse()
        assertThat(harness.tokenFile.readText() == opaqueToken).isTrue()
        assertThat(harness.sentinelFile.exists()).isTrue()
    }

    @Test
    fun mintToken_deletesKeyAndDoesNotArmWhenSigningFails() {
        val harness = harness { error("signing failed") }
        harness.keyFile.writeText(UUID.randomUUID().toString())

        val result = harness.mintToken()

        assertThat(result).isEqualTo(VipTokenSmokeHarness.MintResult.Failed)
        assertThat(harness.keyFile.exists()).isFalse()
        assertThat(harness.tokenFile.exists()).isFalse()
        assertThat(harness.sentinelFile.exists()).isFalse()
    }

    private fun harness(signer: (String) -> String = { UUID.randomUUID().toString() }) =
        VipTokenSmokeHarness(
            filesDir = File(temporaryFolder.root, "noBackupFilesDir").apply { mkdirs() },
            signer = signer
        )
}
