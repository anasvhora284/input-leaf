package com.inputleaf.android.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class InAppUpdateTest {
    @get:Rule val tmp = TemporaryFolder()

    private val prefix = ReleaseApkPicker.DOWNLOAD_PREFIX + "v1.4.5/"
    private val sha = "ab".repeat(32)

    private fun asset(name: String, url: String = prefix + name, digest: String? = "sha256:$sha") =
        JSONObject().put("name", name).put("browser_download_url", url).put("size", 10L)
            .apply { if (digest != null) put("digest", digest) }

    private fun release(vararg assets: JSONObject) =
        JSONObject().put("assets", org.json.JSONArray(assets.toList()))

    @Test
    fun parseAssets_keepsOnlyApksFromThisRepo() {
        val apks = ReleaseApkPicker.parseAssets(
            release(
                asset("input-leaf_1.4.5_arm64-v8a.apk"),
                asset("input-leaf_1.4.5_x86.apk", digest = null),
                asset("input-leaf_1.4.5_x86_64.apk", digest = "sha256:XYZ"),
                asset("notes.txt"),
                asset("evil.apk", url = "https://example.com/evil.apk"),
            ),
        )
        assertThat(apks.map { it.name }).containsExactly(
            "input-leaf_1.4.5_arm64-v8a.apk", "input-leaf_1.4.5_x86.apk", "input-leaf_1.4.5_x86_64.apk",
        ).inOrder()
        assertThat(apks.map { it.sha256 }).containsExactly(sha, null, null).inOrder()
        assertThat(apks[0].sizeBytes).isEqualTo(10L)
        assertThat(ReleaseApkPicker.parseAssets(JSONObject())).isEmpty()
        assertThat(ReleaseApkPicker.parseAssets(JSONObject().put("assets", org.json.JSONArray().put("x")))).isEmpty()
    }

    @Test
    fun pick_prefersDeviceAbiThenUniversal() {
        val apks = listOf("armeabi-v7a", "arm64-v8a", "universal").map {
            ReleaseApk("input-leaf_1.4.5_$it.apk", prefix, 1, null)
        }
        assertThat(ReleaseApkPicker.pick(apks, "1.4.5", listOf("arm64-v8a", "armeabi-v7a"))?.name)
            .isEqualTo("input-leaf_1.4.5_arm64-v8a.apk")
        assertThat(ReleaseApkPicker.pick(apks, "1.4.5", listOf("x86_64"))?.name)
            .isEqualTo("input-leaf_1.4.5_universal.apk")
        assertThat(ReleaseApkPicker.pick(apks.dropLast(1), "1.4.5", listOf("x86_64"))).isNull()
        assertThat(ReleaseApkPicker.pick(apks, "1.4.6", listOf("arm64-v8a"))).isNull()
    }

    @Test
    fun releaseResult_attachesApkExceptForFdroid() {
        val body = release(asset("input-leaf_1.4.5_universal.apk")).put("tag_name", "v1.4.5").toString()
        val github = parseLatestReleaseResponse(body, "1.4.4", isFdroid = false) as UpdateCheckResult.UpdateAvailable
        assertThat(github.apk?.name).isEqualTo("input-leaf_1.4.5_universal.apk")
        val fdroid = parseLatestReleaseResponse(body, "1.4.4", isFdroid = true) as UpdateCheckResult.UpdateAvailable
        assertThat(fdroid.apk).isNull()
    }

    @Test
    fun verification_rejectsWrongPackageOlderVersionOrSigner() {
        fun problem(pkg: String = "a", code: Long = 2, signers: Set<String>? = setOf("k"), installed: Set<String>? = setOf("k")) =
            UpdateVerification.problem(pkg, code, signers, "a", 1, installed)
        assertThat(problem()).isNull()
        assertThat(problem(signers = null)).isNull()
        assertThat(problem(installed = null)).isNull()
        assertThat(problem(pkg = "b")).contains("not Input Leaf")
        assertThat(problem(code = 1)).contains("not newer")
        assertThat(problem(signers = setOf("other"))).contains("release key")
    }

    private val payload = ByteArray(200_000) { it.toByte() }
    private val payloadSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }

    private fun downloader(code: Int = 200, body: ByteArray = payload, length: Long = body.size.toLong()) =
        ApkDownloader { url -> FakeConnection(url, code, body, length) }

    @Test
    fun download_savesVerifiedFileAndReportsProgress() = runTest {
        val dest = File(tmp.root, "updates/u.apk")
        val progress = mutableListOf<Pair<Long, Long>>()
        val file = downloader().download(ReleaseApk("u.apk", prefix, payload.size.toLong(), payloadSha), dest) { d, t ->
            progress += d to t
        }
        assertThat(file.readBytes()).isEqualTo(payload)
        assertThat(progress.last()).isEqualTo(payload.size.toLong() to payload.size.toLong())
        assertThat(File(tmp.root, "updates/u.apk.part").exists()).isFalse()
    }

    @Test
    fun download_usesContentLengthWhenSizeAndDigestAreUnknown() = runTest {
        var total = 0L
        downloader().download(ReleaseApk("u.apk", prefix, -1, null), File(tmp.root, "u.apk")) { _, t -> total = t }
        assertThat(total).isEqualTo(payload.size.toLong())
    }

    @Test
    fun download_rejectsBadStatusShortFileAndChecksumMismatch() = runTest {
        val dest = File(tmp.root, "u.apk")
        val cases = listOf(
            downloader(code = 404) to ReleaseApk("u.apk", prefix, payload.size.toLong(), payloadSha),
            downloader() to ReleaseApk("u.apk", prefix, payload.size + 1L, payloadSha),
            downloader() to ReleaseApk("u.apk", prefix, payload.size.toLong(), sha),
        )
        for ((d, apk) in cases) {
            val error = runCatching { d.download(apk, dest) { _, _ -> } }.exceptionOrNull()
            assertThat(error).isInstanceOf(UpdateDownloadException::class.java)
            assertThat(dest.exists()).isFalse()
            assertThat(File(tmp.root, "u.apk.part").exists()).isFalse()
        }
    }

    @Test
    fun download_failsWhenFileCannotBeMovedIntoPlace() = runTest {
        val dest = File(tmp.root, "u.apk").apply { mkdirs(); File(this, "child").writeText("x") }
        assertThrows(UpdateDownloadException::class.java) {
            kotlinx.coroutines.runBlocking {
                downloader().download(ReleaseApk("u.apk", prefix, payload.size.toLong(), payloadSha), dest) { _, _ -> }
            }
        }
    }

    @Test
    fun openDownload_setsUserAgentAndTimeoutsWithoutConnecting() {
        val connection = ApkDownloader.openDownload(URL(prefix + "u.apk"))
        assertThat(connection.getRequestProperty("User-Agent")).isEqualTo("InputLeaf-Android")
        assertThat(connection.connectTimeout).isEqualTo(15_000)
        assertThat(connection.readTimeout).isEqualTo(30_000)
    }

    private class FakeConnection(url: URL, private val code: Int, private val body: ByteArray, private val length: Long) :
        HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(body)
        override fun getContentLengthLong() = length
    }
}
