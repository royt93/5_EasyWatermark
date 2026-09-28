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
 * IDEA-12: kiểm chứng [WatermarkProfileDatabase.MIGRATION_2_3] (thêm bảng `watermark_style_history`)
 * KHÔNG đụng dữ liệu `watermark_profile` sẵn có + bảng mới tạo đúng schema — mirror
 * `WatermarkProfileMigration1To2IntegrationTest` (không dùng `MigrationTestHelper` chuẩn vì repo
 * không bật `exportSchema`).
 */
@RunWith(AndroidJUnit4::class)
class WatermarkStyleHistoryMigration2To3IntegrationTest {

    private lateinit var dbFile: File
    private lateinit var db: SupportSQLiteDatabase

    private val v2CreateTableSql = """
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
            `textEffectPillBackground` INTEGER NOT NULL,
            `extraLayersRaw` TEXT
        )
    """.trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath("migration-2-3-test-${System.nanoTime()}.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v2CreateTableSql)
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

    private fun insertV2ProfileRow(name: String): Long {
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
    fun migrate2To3_existingProfileRow_survivesUntouched() {
        val id = insertV2ProfileRow("Instagram")

        WatermarkProfileDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT name FROM watermark_profile WHERE id = $id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Instagram")
        }
    }

    @Test
    fun migrate2To3_createsStyleHistoryTable_acceptingInsertAndRead() {
        WatermarkProfileDatabase.MIGRATION_2_3.migrate(db)

        val values = ContentValues().apply {
            put("timestamp", 5_000L)
            put("textColor", 0xFF00FF)
            put("textStyleKey", 1)
            put("textTypefaceKey", 2)
            put("alpha", 200)
            put("anchor", 3)
            put("markModeValue", 0)
            put("exifFrameStyle", 1)
            put("textEffectStroke", 1)
            put("textEffectShadow", 0)
            put("textEffectPillBackground", 1)
        }
        val id = db.insert("watermark_style_history", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)

        db.query("SELECT textColor, textEffectStroke FROM watermark_style_history WHERE id = $id").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0xFF00FF)
            assertThat(cursor.getInt(1)).isEqualTo(1)
        }
    }
}
