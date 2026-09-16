package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.net.Uri
import com.mckimquyen.watermark.data.backup.BackupRestoreEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Đề xuất F (`doc/feat.md`): backup/restore Template (Room) + Signature (file `.webp`) qua SAF Uri. */
@Singleton
class BackupRestoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val templateRepo: TemplateRepository,
    private val signatureRepo: SignatureRepository
) {

    suspend fun backupTo(destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val templates = templateRepo.getAllTemplate().first()
            val signatureFiles = signatureRepo.getAllSignatures().map { it.file }
            val output = context.contentResolver.openOutputStream(destUri) ?: return@withContext false
            output.use { BackupRestoreEngine.writeBackup(it, templates, signatureFiles) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreFrom(srcUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(srcUri) ?: return@withContext false
            val backup = input.use { BackupRestoreEngine.readBackup(it) }
            backup.templates.forEach { templateRepo.insertTemplate(it) }
            backup.signatureFiles.forEach { (name, bytes) -> signatureRepo.importSignatureBytes(name, bytes) }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
