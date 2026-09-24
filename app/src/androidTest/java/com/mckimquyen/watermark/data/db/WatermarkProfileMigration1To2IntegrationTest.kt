package com.mckimquyen.watermark.data.db

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FEAT-03: kiểm chứng [WatermarkProfileDatabase.MIGRATION_1_2] (thêm cột `extraLayersRaw`) KHÔNG
 * làm mất dữ liệu profile cũ. Repo này chưa bật Room schema export (`exportSchema = false` mọi
 * `@Database`, không có thư mục `schemas/`) nên không dùng `MigrationTestHelper` chuẩn (cần file
 * JSON schema đã export cho version cũ) — thay vào đó chạy THẲNG [SupportSQLiteDatabase] version 1
 * tự tạo bằng SQL thô (khớp đúng cột gốc của [WatermarkProfileEntity] trước FEAT-03) rồi gọi trực
 * tiếp `MIGRATION_1_2.migrate(db)`, đúng những gì `Room.databaseBuilder(...).addMigrations(...)`
 * sẽ làm khi mở DB cũ thật ngoài đời — không cần bộ máy validate schema/hash của Room, chỉ cần
 * xác nhận câu SQL của migration đúng và không phá dữ liệu.
 */
@RunWith(AndroidJUnit4::class)
class WatermarkProfileMigration1To2IntegrationTest {

    private lateinit var dbFile: File
    private lateinit var db: SupportSQLiteDatabase

    private val v1CreateTableSql = """
        CREATE TABLE IF NOT EXISTS `watermark_profile` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `text` TEXT NOT NULL,
            `textSize` REAL NOT NULL,
            `textColor` INTEGER NOT NULL,
            `textStyleKey` INTEGER NOT NULL,
            `textTypefaceKey` INTEGER NOT NULL,
            `alpha` INTEGER NOT NULL,
            `degree` REAL NOT NULL,
            `hGap` INTEGER NOT NULL,
            `vGap` INTEGER NOT NULL,
            `iconUri` TEXT NOT NULL,
            `markModeValue` INTEGER NOT NULL,
            `enableBounds` INTEGER NOT NULL,
            `enableExif` INTEGER NOT NULL,
            `exifFrameStyle` INTEGER NOT NULL,
            `anchor` INTEGER NOT NULL,
            `marginPercent` REAL NOT NULL,
            `exifBandColor` INTEGER,
            `exifBandThicknessPercent` REAL,
            `exifUseSerifCaption` INTEGER,
            `textEffectStroke` INTEGER NOT NULL,
            `textEffectShadow` INTEGER NOT NULL,
            `textEffectPillBackground` INTEGER NOT NULL
        )
    """.trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath("migration-1-2-test-${System.nanoTime()}.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v1CreateTableSql)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun insertV1Row(name: String): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("createdAt", 1_000L)
            put("text", "hello")
            put("textSize", 14f)
            put("textColor", 0)
            put("textStyleKey", 0)
            put("textTypefaceKey", 0)
            put("alpha", 255)
            put("degree", 0f)
            put("hGap", 0)
            put("vGap", 0)
            put("iconUri", "")
            put("markModeValue", 0)
            put("enableBounds", 0)
            put("enableExif", 0)
            put("exifFrameStyle", 0)
            put("anchor", 4)
            put("marginPercent", 0.05f)
            put("textEffectStroke", 0)
            put("textEffectShadow", 0)
            put("textEffectPillBackground", 0)
        }
        return db.insert("watermark_profile", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    @Test
    fun migrate1To2_existingRow_survivesWithNullNewColumn() {
        val id = insertV1Row("Instagram")

        WatermarkProfileDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT name, extraLayersRaw FROM watermark_profile WHERE id = $id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Instagram")
            assertThat(cursor.isNull(1)).isTrue()
        }
    }

    @Test
    fun migrate1To2_newColumnAcceptsWrite() {
        insertV1Row("Facebook")

        WatermarkProfileDatabase.MIGRATION_1_2.migrate(db)
        db.execSQL("UPDATE watermark_profile SET extraLayersRaw = 'encoded-layer' WHERE name = 'Facebook'")

        db.query("SELECT extraLayersRaw FROM watermark_profile WHERE name = 'Facebook'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("encoded-layer")
        }
    }
}
