package com.mckimquyen.watermark.testutil

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.util.UUID

/**
 * Robolectric chạy TOÀN BỘ class @Test trong 1 JVM fork dùng chung 1 "SDK Main Thread" — DataStore
 * singleton thật (`context.waterMarkDataStore`/`context.userDataStore`, khai báo bằng
 * `by preferencesDataStore(...)`) bị cache theo FILE PATH chứ không theo Context, nên nhiều test
 * method/class vô tình share chung 1 instance vật lý dù Robolectric tạo Application mới mỗi test.
 * Nếu Robolectric teardown sandbox đúng lúc DataStore đó đang giữ Mutex ghi dở, Mutex kẹt khoá
 * vĩnh viễn — runBlocking { edit {} } ở test SAU treo mãi (deadlock chỉ lộ khi đủ nhiều test tích
 * luỹ trong 1 JVM, ví dụ chạy full suite `testAppReleaseDebugUnitTest` không filter `--tests`).
 * Mọi test PHẢI dùng DataStore CÔ LẬP (file tạm riêng, dọn theo JVM shutdown) qua 2 hàm dưới đây
 * thay vì đụng singleton thật.
 */
fun newTestWaterMarkDataStore(context: Context): DataStore<Preferences> = newIsolatedDataStore(context, "water_mark_test")

fun newTestUserDataStore(context: Context): DataStore<Preferences> = newIsolatedDataStore(context, "user_test")

private fun newIsolatedDataStore(context: Context, prefix: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        produceFile = {
            File.createTempFile("${prefix}_${UUID.randomUUID()}", ".preferences_pb", context.cacheDir).apply {
                deleteOnExit()
            }
        }
    )
