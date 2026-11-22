package com.inputleaf.android.clipboard

import android.Manifest
import android.content.ClipData
import android.widget.EditText
import android.view.ViewGroup
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.inputleaf.android.ui.MainActivity
import com.inputleaf.android.service.ConnectionService
import com.inputleaf.android.testutil.ServiceBinding
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

@RunWith(AndroidJUnit4::class)
class DeviceClipboardDeviceTest {
    private val activity = ActivityScenarioRule(MainActivity::class.java)

    // Grant before launch so the notification dialog cannot take clipboard focus.
    private val notificationPermission: TestRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RuleChain.emptyRuleChain()
        }

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(notificationPermission).around(activity)
    private lateinit var context: Context
    private lateinit var manager: ClipboardManager
    private lateinit var directory: File

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        manager = context.getSystemService(ClipboardManager::class.java)
        directory = File(context.cacheDir, DeviceClipboard.DIRECTORY)
        directory.deleteRecursively()
        manager.setPrimaryClip(ClipData.newPlainText("previous", "keep me"))
    }

    @After fun tearDown() {
        manager.clearPrimaryClip()
        directory.deleteRecursively()
    }

    private fun image() = ClipboardContent(bitmap = ByteBuffer.wrap(ClipboardFixtures.dib()))

    @Test fun wireClipboardChunksBecomeAnAndroidImageClipboard() = runBlocking {
        val out = ByteArrayOutputStream()
        val writer = ProtocolWriter(out)
        val events = ClipboardFixtures.events(ClipboardFixtures.blob(2 to ClipboardFixtures.dib()), chunk = 7)
        events.forEach { writer.writeClipboardData(it.id, it.seqNum, it.mark, it.data) }
        val parser = ProtocolParser(ByteArrayInputStream(out.toByteArray()))
        val sync = ClipboardSync()
        val device = DeviceClipboard(context)
        repeat(events.size) { sync.onServerEvent(parser.readNext())?.let { device.apply(it) { true } } }
        val uri = manager.primaryClip!!.getItemAt(0).uri
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(ClipboardFixtures.colors[0])
        assertThat(bitmap.getPixel(1, 1)).isEqualTo(ClipboardFixtures.colors[3])
        bitmap.recycle()
    }

    @Test fun receivedImageBecomesAReadablePNGURIWithTheCorrectColorsAndMIMEType() = runBlocking {
        var applied = 0
        DeviceClipboard(context) { applied++ }.apply(image()) { true }
        val clip = manager.primaryClip!!
        assertThat(clip.description.hasMimeType("image/png")).isTrue()
        assertThat(clip.getItemAt(0).text).isNull()
        val uri = clip.getItemAt(0).uri
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${context.packageName}.clipboard")
        val png = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
        assertThat(png.width).isEqualTo(2)
        assertThat(png.height).isEqualTo(2)
        val colors = IntArray(4)
        png.getPixels(colors, 0, 2, 0, 0, 2, 2)
        assertThat(colors).isEqualTo(ClipboardFixtures.colors)
        png.recycle()
        assertThat(applied).isEqualTo(1)
    }

    @Test fun textAndImageRemainAvailableTogetherAndSubsequentTextStillPastesNormally() = runBlocking {
        val device = DeviceClipboard(context)
        device.apply(image().copy(text = "image caption")) { true }
        val mixed = manager.primaryClip!!
        assertThat(mixed.itemCount).isEqualTo(1)
        assertThat(mixed.description.hasMimeType("text/plain")).isTrue()
        assertThat(mixed.description.hasMimeType("image/png")).isTrue()
        assertThat(mixed.getItemAt(0).text.toString()).isEqualTo("image caption")
        val previousUri = mixed.getItemAt(0).uri
        assertThat(previousUri).isNotNull()
        val png = context.contentResolver.openInputStream(previousUri)!!.use { BitmapFactory.decodeStream(it) }!!
        assertThat(png.getPixel(0, 0)).isEqualTo(ClipboardFixtures.colors[0])
        png.recycle()
        activity.scenario.onActivity { owner ->
            val editor = EditText(owner)
            owner.addContentView(editor, ViewGroup.LayoutParams(200, 100))
            editor.requestFocus()
            assertThat(editor.onTextContextMenuItem(android.R.id.paste)).isTrue()
            assertThat(editor.text.toString()).isEqualTo("image caption")
            (editor.parent as ViewGroup).removeView(editor)
        }
        device.apply(ClipboardContent(text = "later text")) { true }
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("later text")
        // Do not delete a published image when text replaces it or the connection stops.
        assertThat(context.contentResolver.openInputStream(previousUri)!!.use { it.read() }).isEqualTo(137)
    }

    @Test fun invalidImagePreservesTheExistingClipboardAndCanFallBackToValidAccompanyingText() = runBlocking {
        val device = DeviceClipboard(context)
        device.apply(ClipboardContent(bitmap = ByteBuffer.wrap(byteArrayOf(1, 2)))) { true }
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("keep me")
        assertThat(directory.listFiles()?.size ?: 0).isEqualTo(0)
        device.apply(ClipboardContent(text = "fallback", bitmap = ByteBuffer.wrap(byteArrayOf(1)))) { true }
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("fallback")
    }

    @Test fun aClipSupersededDuringConversionIsNotPublishedAndItsTemporaryPNGIsRemoved() = runBlocking {
        var checks = 0
        DeviceClipboard(context).apply(image()) { ++checks == 1 }
        assertThat(checks).isEqualTo(2)
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("keep me")
        assertThat(directory.listFiles()?.size ?: 0).isEqualTo(0)
    }

    @Test fun eachImageGetsItsOwnURIAndTheCacheRetainsOnlyThreePublishedImages() = runBlocking {
        val device = DeviceClipboard(context)
        val uris = mutableSetOf<String>()
        repeat(5) { i ->
            device.apply(image()) { true }
            uris += manager.primaryClip!!.getItemAt(0).uri.toString()
            // Ensure stable ordering even on coarse filesystem timestamps.
            directory.listFiles()!!.forEach { it.setLastModified(1000L * (i + 1)) }
        }
        assertThat(uris).hasSize(5)
        assertThat(directory.listFiles()!!.size).isEqualTo(DeviceClipboard.RETAINED_IMAGES)
        assertThat(context.contentResolver.openInputStream(manager.primaryClip!!.getItemAt(0).uri)!!.use { it.read() })
            .isEqualTo(137)
    }

    @Test fun currentImageSurvivesPruningWhenOlderFilesHaveFutureTimestamps() = runBlocking {
        val device = DeviceClipboard(context)
        repeat(3) { device.apply(image()) { true } }
        directory.listFiles()!!.forEach { it.setLastModified(System.currentTimeMillis() + 86_400_000L) }
        device.apply(image()) { true }
        val uri = manager.primaryClip!!.getItemAt(0).uri
        assertThat(context.contentResolver.openInputStream(uri)!!.use { it.read() }).isEqualTo(137)
        assertThat(directory.listFiles()!!.size).isEqualTo(DeviceClipboard.RETAINED_IMAGES)
    }

    @Test fun explicitBitfieldAlphaSurvivesPNGPublication() = runBlocking {
        val bytes = ClipboardFixtures.dib(depth = 32, headerSize = 124, bitfields = true, alpha = true,
            pixelColors = intArrayOf(0x80ff0000.toInt()))
        DeviceClipboard(context).apply(ClipboardContent(bitmap = ByteBuffer.wrap(bytes))) { true }
        val uri = manager.primaryClip!!.getItemAt(0).uri
        val png = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
        assertThat(png.getPixel(0, 0)).isEqualTo(0x80ff0000.toInt())
        png.recycle()
    }

    @Test fun cleanupPreservesAllReferencedImagesAndRemovesOnlyUnusedPNGs() = runBlocking {
        val device = DeviceClipboard(context)
        device.apply(image()) { true }
        val first = manager.primaryClip!!.getItemAt(0).uri
        device.apply(image()) { true }
        val second = manager.primaryClip!!.getItemAt(0).uri
        device.apply(image()) { true }
        val clip = ClipData.newUri(context.contentResolver, "images", first)
        clip.addItem(ClipData.Item(second))
        manager.setPrimaryClip(clip)
        val unrelated = File(directory, "unrelated.txt").apply { writeText("keep") }
        val folder = File(directory, "clip-folder.png").apply { mkdir() }
        device.clearUnusedImages()
        assertThat(directory.listFiles()!!.count { it.isFile && it.extension == "png" }).isEqualTo(2)
        assertThat(unrelated.exists()).isTrue()
        assertThat(folder.isDirectory).isTrue()
        for (uri in listOf(first, second)) {
            assertThat(context.contentResolver.openInputStream(uri)!!.use { it.read() }).isEqualTo(137)
        }
    }

    @Test fun cleanupAfterTextReplacementRemovesUnusedImages() = runBlocking {
        val device = DeviceClipboard(context)
        device.apply(image()) { true }
        device.apply(ClipboardContent(text = "new text")) { true }
        device.clearUnusedImages()
        assertThat(directory.listFiles()?.size ?: 0).isEqualTo(0)
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("new text")
    }

    @Test fun cleanupAcrossInstancesWaitsForAnInFlightApplyAndPreservesThePublishedImage() = runBlocking {
        val device = DeviceClipboard(context)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        var checks = 0
        val applying = async(Dispatchers.IO) {
            device.apply(image()) {
                if (++checks == 1) {
                    entered.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS)) { "test did not release conversion" }
                }
                true
            }
        }
        try {
            entered.await()
            val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
                DeviceClipboard(context).clearUnusedImages()
            }
            assertThat(cleanup.isCompleted).isFalse()
            release.countDown()
            applying.await()
            cleanup.await()
            assertThat(directory.listFiles()!!.size).isEqualTo(1)
            val uri = manager.primaryClip!!.getItemAt(0).uri
            assertThat(context.contentResolver.openInputStream(uri)!!.use { it.read() }).isEqualTo(137)
        } finally {
            release.countDown()
        }
    }

    @Test fun serviceStartupCleansUnusedImagesWhilePreservingTheCurrentURI() = runBlocking {
        val previousService = ServiceBinding(context, ConnectionService::class.java).use { binding ->
            (binding.awaitBinder() as ConnectionService.LocalBinder).getService()
        }
        // MainViewModel binds on launch. Release that binding so the next bind really starts a service.
        activity.scenario.onActivity { it.viewModelStore.clear() }
        context.stopService(Intent(context, ConnectionService::class.java))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val device = DeviceClipboard(context)
        repeat(3) { device.apply(image()) { true } }
        val uri = manager.primaryClip!!.getItemAt(0).uri
        ServiceBinding(context, ConnectionService::class.java).use { binding ->
            val restarted = (binding.awaitBinder() as ConnectionService.LocalBinder).getService()
            assertThat(restarted).isNotSameInstanceAs(previousService)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (directory.listFiles()!!.size > 1 && System.nanoTime() < deadline) Thread.sleep(25)
            assertThat(directory.listFiles()!!.size).isEqualTo(1)
            assertThat(context.contentResolver.openInputStream(uri)!!.use { it.read() }).isEqualTo(137)
        }
    }

    @Test fun providerAllowsURIGrantsAndExposesOnlyTheClipboardImageCache() {
        val authority = "${context.packageName}.clipboard"
        val info = context.packageManager.resolveContentProvider(authority, 0)!!
        assertThat(info.exported).isFalse()
        assertThat(info.grantUriPermissions).isTrue()
        val privateFile = File(context.filesDir, "private.txt").apply { writeText("private") }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, privateFile)
        }
    }
}
