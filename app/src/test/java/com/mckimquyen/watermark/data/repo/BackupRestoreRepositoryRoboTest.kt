package com.mckimquyen.watermark.data.repo

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mckimquyen.watermark.data.db.AppDatabase
import com.mckimquyen.watermark.data.model.entity.Template
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

/**
 * Đề xuất F (`doc/feat.md`): backup → restore end-to-end qua Room in-memory thật + file thật
 * (URI `file://`) — verify [BackupRestoreRepository] nối đúng [BackupRestoreEngine] +
 * [TemplateRepository]/[SignatureRepository], không chỉ test riêng từng lớp.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreRepositoryRoboTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: BackupRestoreRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BackupRestoreRepository(
            context = context,
            templateRepo = TemplateRepository(db.templateDao()),
            signatureRepo = SignatureRepository(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun backupThenRestore_roundTripsTemplateContentAndSignatureBytes() = runBlocking {
        db.templateDao().insertTemplate(Template(id = 0, content = "© Brand", creationDate = Date(1_000L), lastModifiedDate = Date(2_000L)))
        val signatureBitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        SignatureRepository(context).saveSignature(signatureBitmap)

        val zipFile = tmp.newFile("backup.zip")
        val zipUri = Uri.fromFile(zipFile)

        val backupOk = repo.backupTo(zipUri)
        assertThat(backupOk).isTrue()
        assertThat(zipFile.length()).isGreaterThan(0L)

        // Xoá sạch DB + signature hiện có — mô phỏng restore trên máy MỚI (dữ liệu trống).
        db.templateDao().getAllTemplate().first().forEach { db.templateDao().deleteTemplate(it) }
        SignatureRepository(context).getAllSignatures().forEach { SignatureRepository(context).deleteSignature(it) }

        val restoreOk = repo.restoreFrom(zipUri)
        assertThat(restoreOk).isTrue()

        val restoredTemplates = db.templateDao().getAllTemplate().first()
        assertThat(restoredTemplates.map { it.content }).containsExactly("© Brand")
        // id phải được Room autoGenerate LẠI (không đè lên id=0 gốc lúc backup).
        assertThat(restoredTemplates.single().id).isNotEqualTo(0)

        val restoredSignatures = SignatureRepository(context).getAllSignatures()
        assertThat(restoredSignatures).hasSize(1)
    }

    @Test
    fun restoreFrom_sameBackupTwice_doesNotDuplicateTemplates() {
        // Regression cho bug tìm được ở code review (2026-09-16): Template.id luôn = 0 khi restore
        // (Room autoGenerate PK mới) — không dedup thì restore cùng 1 file backup 2 lần (retry sau
        // lỗi giữa chừng, hoặc máy đích đã có sẵn) sẽ nhân đôi mọi template.
        runBlocking {
            db.templateDao().insertTemplate(Template(id = 0, content = "© Brand", creationDate = Date(1_000L), lastModifiedDate = null))
            val zipFile = tmp.newFile("backup.zip")
            val zipUri = Uri.fromFile(zipFile)
            assertThat(repo.backupTo(zipUri)).isTrue()

            assertThat(repo.restoreFrom(zipUri)).isTrue()
            assertThat(repo.restoreFrom(zipUri)).isTrue()

            val templates = db.templateDao().getAllTemplate().first()
            assertThat(templates.map { it.content }).containsExactly("© Brand")
        }
    }

    @Test
    fun restoreFrom_backupContainsDuplicateContentWithinSameFile_dedupsToOne() {
        // BUG-AUDIT-2026-09-29-JUNIT-TRAP: `= runBlocking { ... }` (expression body) khiến hàm suy
        // luận kiểu trả về theo statement CUỐI trong lambda — nếu đó là `containsExactly(...)` (trả
        // `Ordered`, không phải Unit), JUnit4 `ParentRunner.validate()` ném `InvalidTestClassError`
        // chặn CẢ CLASS (không chỉ test này). Dùng block body `{ runBlocking { ... } }` để hàm luôn
        // suy luận Unit bất kể statement cuối trong lambda trả gì.
        runBlocking {
            // BUG-AUDIT-2026-09-29: trước fix, `existingContents` chỉ tính 1 lần TRƯỚC vòng lặp (dựa
            // trên DB hiện có) — 2 template TRÙNG content NẰM TRONG CÙNG 1 file backup (DB đích trống,
            // vd. máy mới) không dedup lẫn nhau, cả 2 đều lọt qua filter. Insert 2 template trùng
            // content trực tiếp qua Room (không cấm trùng content) để tạo được tình huống này.
            db.templateDao().insertTemplate(Template(id = 0, content = "© Brand", creationDate = Date(1_000L), lastModifiedDate = null))
            db.templateDao().insertTemplate(Template(id = 0, content = "© Brand", creationDate = Date(2_000L), lastModifiedDate = null))
            assertThat(db.templateDao().getAllTemplate().first()).hasSize(2)

            val zipFile = tmp.newFile("dup_backup.zip")
            val zipUri = Uri.fromFile(zipFile)
            assertThat(repo.backupTo(zipUri)).isTrue()

            // Xoá sạch DB — mô phỏng restore trên máy MỚI (đích trống), chỉ còn dedup NỘI BỘ file backup.
            db.templateDao().getAllTemplate().first().forEach { db.templateDao().deleteTemplate(it) }

            assertThat(repo.restoreFrom(zipUri)).isTrue()
            assertThat(db.templateDao().getAllTemplate().first().map { it.content }).containsExactly("© Brand")
        }
    }

    @Test
    fun restoreFrom_invalidZip_doesNotCrashOrInsertGarbage() = runBlocking {
        // ZipInputStream không throw với input không phải zip hợp lệ (đọc được 0 entry) — hành vi
        // đúng cần verify là KHÔNG crash và KHÔNG chèn rác vào DB, không phải giá trị return cụ thể.
        val notAZip = tmp.newFile("not_a_zip.zip").apply { writeText("plain text, not a zip") }

        repo.restoreFrom(Uri.fromFile(notAZip))

        assertThat(db.templateDao().getAllTemplate().first()).isEmpty()
    }
}
