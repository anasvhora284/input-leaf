package com.inputleaf.android.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/** Converts received DIBs off the main thread and publishes Android image content URIs. */
internal class DeviceClipboard(
    private val context: Context,
    private val onRejected: (String) -> Unit = {},
    private val onApplied: () -> Unit = {},
) {
    private val directory get() = File(context.cacheDir, DIRECTORY)

    suspend fun apply(content: ClipboardContent, isCurrent: () -> Boolean) {
        imageLock.withLock {
            if (!isCurrent()) return
            var imageFile: File? = null
            var published = false
            try {
                val image = content.bitmap?.let { DibImage.decode(it, onRejected) }
                coroutineContext.ensureActive()
                imageFile = image?.let(::writePng)
                val clip = if (imageFile != null) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipboard", imageFile)
                    if (content.text != null) {
                        ClipData(
                            "Input Leaf", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN, "image/png"),
                            ClipData.Item(content.text, null, uri),
                        )
                    } else {
                        ClipData.newUri(context.contentResolver, "Input Leaf image", uri)
                    }
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

    /** A null clip may mean background reads are denied; leave its files intact. */
    suspend fun clearUnusedImages() {
        imageLock.withLock {
            val clip = withContext(Dispatchers.Main) {
                try {
                    context.getSystemService(ClipboardManager::class.java)?.primaryClip
                } catch (_: SecurityException) {
                    null
                }
            } ?: return
            val referenced = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.toSet()
            withContext(Dispatchers.IO) {
                imageFiles().forEach { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.clipboard", file)
                    if (uri !in referenced) file.delete()
                }
            }
        }
    }

    private fun imageFiles(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.name.startsWith("clip-") && it.extension == "png"
    }.orEmpty()

    /** Retain the latest three clips so replacing the clipboard does not immediately break a reader. */
    private fun pruneImages(current: File) {
        imageFiles().filter { it != current }
            .sortedByDescending { it.lastModified() }.drop(RETAINED_IMAGES - 1).forEach { it.delete() }
    }

    companion object {
        // A recreated service can overlap an older instance finishing a conversion.
        private val imageLock = Mutex()
        const val DIRECTORY = "clipboard-images"
        const val RETAINED_IMAGES = 3
    }
}
