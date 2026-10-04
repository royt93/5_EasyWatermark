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
 * BUG-58: [WatermarkProfileDatabase.MIGRATION_4_5] thêm 5 cột auto-contrast / EXIF palette / QR động NULLABLE — profile cũ phải
 * sống sót nguyên vẹn (cột mới = NULL). Cùng cách chạy trực tiếp [SupportSQLiteDatabase] như
 * `WatermarkProfileMigration1To2IntegrationTest` (repo chưa bật Room schema export).
 */
@RunWith(AndroidJUnit4::class)
class WatermarkProfileMigration4To5IntegrationTest {

    private lateinit var dbFile: File
    private lateinit var db: SupportSQLiteDatabase

    private val v4CreateTableSql = """
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
            `extraLayersRaw` TEXT,
            `cardFrameEnabled` INTEGER,
            `cardCornerRadiusPercent` REAL,
            `cardShadowPercent` REAL,
            `cardBackgroundColor` INTEGER
        )
    """.trimIndent()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath("migration-4-5-test-${System.nanoTime()}.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v4CreateTableSql)
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

    private fun insertV4Row(name: String): Long {
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
            put("extraLayersRaw", "layers")
        }
        return db.insert("watermark_profile", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    @Test
    fun migrate4To5_existingRow_survivesWithNullNewColumns() {
        val id = insertV4Row("Instagram")

        WatermarkProfileDatabase.MIGRATION_4_5.migrate(db)

        db.query(
            "SELECT name, extraLayersRaw, exifAutoPalette, autoContrastEnabled, qrDynamicEnabled, qrContentTemplate, qrPortfolioLink " +
                "FROM watermark_profile WHERE id = $id"
        ).use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Instagram")
            assertThat(c.getString(1)).isEqualTo("layers") // cột cũ không bị phá
            for (i in 2..6) assertThat(c.isNull(i)).isTrue()
        }
    }

    @Test
    fun migrate4To5_newColumnsAcceptWrites() {
        insertV4Row("Facebook")

        WatermarkProfileDatabase.MIGRATION_4_5.migrate(db)
        db.execSQL(
            "UPDATE watermark_profile SET exifAutoPalette = 1, autoContrastEnabled = 1, qrDynamicEnabled = 1, " +
                "qrContentTemplate = '{hash}', qrPortfolioLink = 'https://x.y' WHERE name = 'Facebook'"
        )

        db.query("SELECT exifAutoPalette, autoContrastEnabled, qrDynamicEnabled, qrContentTemplate, qrPortfolioLink FROM watermark_profile WHERE name = 'Facebook'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
            assertThat(c.getInt(1)).isEqualTo(1)
            assertThat(c.getInt(2)).isEqualTo(1)
            assertThat(c.getString(3)).isEqualTo("{hash}")
            assertThat(c.getString(4)).isEqualTo("https://x.y")
        }
    }
}
