package com.inputleaf.android.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Converts received DIBs off the main thread and publishes Android image content URIs. */
internal class DeviceClipboard(
    private val context: Context,
    private val onApplied: () -> Unit = {},
) {
    private val directory get() = File(context.cacheDir, DIRECTORY)

    suspend fun apply(content: ClipboardContent, isCurrent: () -> Boolean) {
        if (!isCurrent()) return
        var imageFile: File? = null
        var published = false
        try {
            val image = content.bitmap?.let(DibImage::decode)
            coroutineContext.ensureActive()
            imageFile = image?.let(::writePng)
            val clip = if (imageFile != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipboard", imageFile)
                ClipData.newUri(context.contentResolver, "Input Leaf image", uri)
            } else {
                content.text?.let { ClipData.newPlainText("Input Leaf", it) } ?: return
            }
            withContext(Dispatchers.Main) {
                if (isCurrent()) {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    if (manager != null) {
                        manager.setPrimaryClip(clip)
                        published = true
                        onApplied()
                    }
                }
            }
        } catch (error: OutOfMemoryError) {
            throw IOException("Insufficient memory for clipboard image", error)
        } finally {
            if (published && imageFile != null) pruneImages(imageFile) else imageFile?.delete()
        }
    }

    internal fun writePng(image: DibImage): File {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create clipboard cache" }
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        var file: File? = null
        try {
            file = File.createTempFile("clip-", ".png", directory)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file
        } catch (error: Throwable) {
            file?.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    /** Retain the latest three clips so replacing the clipboard does not immediately break a reader. */
    private fun pruneImages(current: File) {
        directory.listFiles()?.filter { it != current && it.isFile && it.name.startsWith("clip-") && it.extension == "png" }
            ?.sortedByDescending { it.lastModified() }?.drop(RETAINED_IMAGES - 1)?.forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY = "clipboard-images"
        const val RETAINED_IMAGES = 3
    }
}
