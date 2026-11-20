package com.inputleaf.android.clipboard

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.inputleaf.android.ui.MainActivity
import com.inputleaf.android.protocol.ProtocolParser
import com.inputleaf.android.protocol.ProtocolWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
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

    @Test fun imageTakesPrecedenceOverAccompanyingTextAndSubsequentTextStillPastesNormally() = runBlocking {
        val device = DeviceClipboard(context)
        device.apply(image().copy(text = "image caption")) { true }
        val previousUri = manager.primaryClip!!.getItemAt(0).uri
        assertThat(previousUri).isNotNull()
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
