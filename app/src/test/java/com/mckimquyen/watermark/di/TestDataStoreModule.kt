package com.mckimquyen.watermark.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Named
import javax.inject.Singleton

/**
 * BUG-FLAKY-2026-09-30: thay [DataStoreModule] cho MỌI test Robolectric dùng Hilt thật (dựng
 * MainActivity qua Robolectric.buildActivity — bắt buộc khi fragment ép kiểu requireActivity() as
 * MainActivity, ví dụ SaveImageBSDialogFragment). Mỗi lần Hilt build SingletonComponent (mỗi test
 * method, nhờ HiltAndroidRule) nhận 1 file tạm RIÊNG thay vì `context.userDataStore`/
 * `waterMarkDataStore` — 2 property cache theo FILE PATH dùng chung xuyên suốt JVM fork (xem
 * testutil/TestDataStores.kt). Dùng singleton thật từng gây deadlock Mutex vĩnh viễn nếu 1 test
 * khác bị Robolectric huỷ sandbox giữa lúc `edit{}` dở dang — test SAU cùng file bị treo thật khi
 * chạy full suite, tăng deadline không giải quyết được (đã verify thực nghiệm).
 *
 * Cách dùng: test class thêm `@HiltAndroidTest`, `@Config(application = HiltTestApplication::
 * class)` và `@get:Rule val hiltRule = HiltAndroidRule(this)` + gọi `hiltRule.inject()` trước khi
 * dựng Activity (xem SaveImageBSDialogFragmentInvisibleRoboTest).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataStoreModule::class]
)
object TestDataStoreModule {

    @Named("UserPreferences")
    @Singleton
    @Provides
    fun userDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        isolatedDataStore(context, "hilt_test_user")

    @Named("WaterMarkPreferences")
    @Singleton
    @Provides
    fun waterMarkDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        isolatedDataStore(context, "hilt_test_water_mark")

    private fun isolatedDataStore(context: Context, prefix: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                File.createTempFile("${prefix}_${UUID.randomUUID()}", ".preferences_pb", context.cacheDir).apply {
                    deleteOnExit()
                }
            }
        )
}

/**
 * BUG-FLAKY-2026-09-30: cổng truy cập DataStore mà Hilt THẬT SỰ đang bind (qua [TestDataStoreModule]
 * nếu module test có hiệu lực) — dùng trong test để so sánh reference với `context.userDataStore`/
 * `waterMarkDataStore` (singleton sản xuất), chứng minh cô lập bằng cấu trúc (khác instance) thay
 * vì so giá trị (dễ sai lệch theo thứ tự chạy vì singleton là state toàn cục dùng chung).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataStoreTestEntryPoint {
    @Named("UserPreferences")
    fun userDataStore(): DataStore<Preferences>

    @Named("WaterMarkPreferences")
    fun waterMarkDataStore(): DataStore<Preferences>
}
