package com.mckimquyen.watermark.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * BUG-55: observer `waterMark` trước đây mỗi lần emit lại `launch` coroutine mới mà KHÔNG huỷ
 * coroutine trước; `resolvePreviewText` chạy `Dispatchers.IO` khi text có token `{...}` nên coroutine
 * cũ có thể hoàn tất SAU coroutine mới và ghi đè preview bằng config cũ → preview ≠ kết quả export.
 * [PreviewConfigUpdater] giữ đúng 1 job, mỗi lần update huỷ job trước.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewConfigUpdaterTest {

    private val image = "img-1"

    @Test
    fun update_staleResolveFinishesAfterNewer_neverOverwritesWithOldConfig() = runTest {
        val applied = mutableListOf<String>()
        val gateOld = CompletableDeferred<String>()
        val gateNew = CompletableDeferred<String>()
        val updater = PreviewConfigUpdater<String>(
            scope = this,
            resolveText = { text, _ -> if (text == "{date} old") gateOld.await() else gateNew.await() },
            apply = { applied.add(it) }
        )

        updater.update("{date} old", image)
        updater.update("{date} new", image)
        // job MỚI hoàn tất trước, job CŨ hoàn tất sau — thứ tự dễ gây ghi đè nhất.
        gateNew.complete("NEW")
        advanceUntilIdle()
        gateOld.complete("OLD")
        advanceUntilIdle()

        assertThat(applied).containsExactly("NEW")
    }

    @Test
    fun update_noSelectedImage_appliesRawTextWithoutResolving() = runTest {
        val applied = mutableListOf<String>()
        var resolveCalls = 0
        val updater = PreviewConfigUpdater<String>(
            scope = this,
            resolveText = { text, _ -> resolveCalls++; text },
            apply = { applied.add(it) }
        )

        updater.update("{date} x", null)
        advanceUntilIdle()

        assertThat(applied).containsExactly("{date} x")
        assertThat(resolveCalls).isEqualTo(0)
    }

    @Test
    fun update_textWithoutToken_stillAppliesLatestOnly() = runTest {
        val applied = mutableListOf<String>()
        val updater = PreviewConfigUpdater<String>(
            scope = this,
            resolveText = { text, _ -> text },
            apply = { applied.add(it) }
        )

        updater.update("a", image)
        updater.update("ab", image)
        advanceUntilIdle()

        assertThat(applied.last()).isEqualTo("ab")
        assertThat(applied).doesNotContain("a")
    }

    @Test
    fun cancel_afterUpdate_dropsPendingApply() = runTest {
        val applied = mutableListOf<String>()
        val gate = CompletableDeferred<String>()
        val updater = PreviewConfigUpdater<String>(
            scope = this,
            resolveText = { _, _ -> gate.await() },
            apply = { applied.add(it) }
        )

        updater.update("{x}", image)
        updater.cancel()
        gate.complete("LATE")
        advanceUntilIdle()

        assertThat(applied).isEmpty()
    }
}
