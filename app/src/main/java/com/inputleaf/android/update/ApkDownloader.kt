package com.inputleaf.android.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The download failed or the file is not what the release published. */
class UpdateDownloadException(message: String) : IOException(message)

/**
 * Downloads a release APK to [dest], reporting progress, and checks it matches the size
 * and SHA-256 that GitHub published for it. A partial or mismatched file is deleted, so
 * nothing unverified is ever left for the installer.
 */
class ApkDownloader(
    private val openConnection: (URL) -> HttpURLConnection = ::openDownload,
) {
    suspend fun download(apk: ReleaseApk, dest: File, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val partial = File(dest.parentFile, dest.name + ".part")
            partial.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            try {
                val connection = openConnection(URL(apk.url))
                connection.useAndDisconnect {
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw UpdateDownloadException("Download failed (HTTP $responseCode)")
                    }
                    val total = if (apk.sizeBytes > 0) apk.sizeBytes else contentLengthLong
                    inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                done += read
                                onProgress(done, total)
                            }
                        }
                    }
                }
                if (apk.sizeBytes > 0 && done != apk.sizeBytes) {
                    throw UpdateDownloadException("Download was incomplete ($done of ${apk.sizeBytes} bytes)")
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (apk.sha256 != null && actual != apk.sha256) {
                    throw UpdateDownloadException("Downloaded file does not match the release checksum")
                }
                dest.delete()
                if (!partial.renameTo(dest)) throw UpdateDownloadException("Could not save the update")
                dest
            } catch (e: Throwable) {
                partial.delete()
                throw e
            }
        }

    internal companion object {
        private const val BUFFER_BYTES = 64 * 1024

        // GitHub answers with a redirect to its file host; HttpURLConnection follows it (both HTTPS).
        fun openDownload(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "InputLeaf-Android")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
    }
}
