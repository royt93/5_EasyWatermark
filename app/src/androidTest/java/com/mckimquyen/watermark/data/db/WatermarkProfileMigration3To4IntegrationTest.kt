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
 * FEAT-28: [WatermarkProfileDatabase.MIGRATION_3_4] thêm 4 cột khung thẻ NULLABLE — profile cũ phải
 * sống sót nguyên vẹn (cột mới = NULL). Cùng cách chạy trực tiếp [SupportSQLiteDatabase] như
 * `WatermarkProfileMigration1To2IntegrationTest` (repo chưa bật Room schema export).
 */
@RunWith(AndroidJUnit4::class)
class WatermarkProfileMigration3To4IntegrationTest {

    private lateinit var dbFile: File
    private lateinit var db: SupportSQLiteDatabase

    private val v3CreateTableSql = """
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
        dbFile = context.getDatabasePath("migration-3-4-test-${System.nanoTime()}.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(v3CreateTableSql)
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

    private fun insertV3Row(name: String): Long {
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
    fun migrate3To4_existingRow_survivesWithNullCardColumns() {
        val id = insertV3Row("Instagram")

        WatermarkProfileDatabase.MIGRATION_3_4.migrate(db)

        db.query(
            "SELECT name, extraLayersRaw, cardFrameEnabled, cardCornerRadiusPercent, cardShadowPercent, cardBackgroundColor " +
                "FROM watermark_profile WHERE id = $id"
        ).use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("Instagram")
            assertThat(c.getString(1)).isEqualTo("layers") // cột cũ không bị phá
            for (i in 2..5) assertThat(c.isNull(i)).isTrue()
        }
    }

    @Test
    fun migrate3To4_newColumnsAcceptWrites() {
        insertV3Row("Facebook")

        WatermarkProfileDatabase.MIGRATION_3_4.migrate(db)
        db.execSQL(
            "UPDATE watermark_profile SET cardFrameEnabled = 1, cardCornerRadiusPercent = 0.2, " +
                "cardShadowPercent = 0.05, cardBackgroundColor = -1 WHERE name = 'Facebook'"
        )

        db.query("SELECT cardFrameEnabled, cardCornerRadiusPercent, cardShadowPercent, cardBackgroundColor FROM watermark_profile WHERE name = 'Facebook'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getInt(0)).isEqualTo(1)
            assertThat(c.getFloat(1)).isWithin(0.001f).of(0.2f)
            assertThat(c.getFloat(2)).isWithin(0.001f).of(0.05f)
            assertThat(c.getInt(3)).isEqualTo(-1)
        }
    }
}
