package com.mckimquyen.watermark.feature.vip

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class VipTokenSmokeHookTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        clearHandoff()
    }

    @Test
    fun hook_withoutSentinel_doesNotSubmit() {
        clearHandoff()
        var submissions = 0

        val result = VipTokenSmokeHookImpl.consumePendingToken(context) {
            submissions++
            true
        }

        assertThat(result).isEqualTo(SubmitResult.NotArmed)
        assertThat(submissions).isEqualTo(0)
    }

    @Test
    fun hook_deletesOneShotTokenBeforeSubmitAndDoesNotSubmitAgain() {
        val opaqueToken = UUID.randomUUID().toString()
        tokenFile().writeText(opaqueToken)
        sentinelFile().writeText("armed")
        var submissions = 0
        var filesAbsentAtSubmit = false

        val first = VipTokenSmokeHookImpl.consumePendingToken(context) { token ->
            submissions++
            filesAbsentAtSubmit = !tokenFile().exists() && !sentinelFile().exists()
            token == opaqueToken
        }
        val second = VipTokenSmokeHookImpl.consumePendingToken(context) {
            submissions++
            true
        }

        assertThat(first).isEqualTo(SubmitResult.Submitted)
        assertThat(second).isEqualTo(SubmitResult.NotArmed)
        assertThat(submissions).isEqualTo(1)
        assertThat(filesAbsentAtSubmit).isTrue()
    }

    private fun tokenFile() = File(context.noBackupFilesDir.apply { mkdirs() }, "vip-smoke-token")

    private fun sentinelFile() = File(context.noBackupFilesDir.apply { mkdirs() }, "vip-smoke-armed")

    private fun clearHandoff() {
        tokenFile().delete()
        sentinelFile().delete()
    }
}
