package com.mckimquyen.watermark.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.mckimquyen.watermark.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SignatureModel(
    val file: File,
    val uri: Uri,
    val dateModified: Long
)

@Singleton
class SignatureRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val signatureDir: File
        get() {
            val dir = File(context.filesDir, "signatures")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private fun fileToContentUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }

    suspend fun getAllSignatures(): List<SignatureModel> = withContext(Dispatchers.IO) {
        val files = signatureDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.filter { it.isFile && it.name.endsWith(".webp") }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                SignatureModel(
                    file = file,
                    uri = fileToContentUri(file),
                    dateModified = file.lastModified()
                )
            }
    }

    suspend fun saveSignature(bitmap: Bitmap): SignatureModel? = withContext(Dispatchers.IO) {
        try {
            val fileName = "signature_${System.currentTimeMillis()}.webp"
            val file = File(signatureDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, out)
            }
            return@withContext SignatureModel(
                file = file,
                uri = fileToContentUri(file),
                dateModified = file.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun deleteSignature(model: SignatureModel): Boolean = withContext(Dispatchers.IO) {
        return@withContext if (model.file.exists()) {
            model.file.delete()
        } else {
            false
        }
    }
}
