package com.example.medicationreminder.data.photos

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** Copies selected images into app-private files so reminders never depend on external URI access. */
class MedicationImageStore(private val context: Context) {
    private val imageDirectory: File = File(context.filesDir, "medication-images").also { it.mkdirs() }
    private val cameraDirectory: File = File(context.cacheDir, "camera").also { it.mkdirs() }

    fun importFrom(uri: Uri): String {
        val target = File(imageDirectory, "${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: throw IOException("Unable to read selected image")
        } catch (exception: Exception) {
            target.delete()
            throw IOException("Unable to save medication image", exception)
        }
        return target.absolutePath
    }

    fun newCameraCaptureUri(): Uri {
        val photo = File.createTempFile("medication_", ".jpg", cameraDirectory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
    }

    fun delete(path: String?) {
        if (path == null) return
        val candidate = File(path)
        if (candidate.parentFile?.canonicalFile == imageDirectory.canonicalFile) candidate.delete()
    }
}
