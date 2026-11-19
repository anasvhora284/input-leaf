package com.inputleaf.android.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.R
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class DeviceClipboardTest {
    private lateinit var context: Context
    private lateinit var manager: ClipboardManager
    private lateinit var directory: File

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(ClipboardManager::class.java)
        directory = File(context.cacheDir, DeviceClipboard.DIRECTORY)
        directory.deleteRecursively()
        manager.setPrimaryClip(ClipData.newPlainText("previous", "keep me"))
    }

    @After fun tearDown() {
        manager.clearPrimaryClip()
        directory.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test fun `DIB conversion writes an actual PNG with the expected dimensions and colors`() {
        val image = DibImage.decode(ByteBuffer.wrap(ClipboardFixtures.dib()))!!
        val file = DeviceClipboard(context).writePng(image)
        val png = ImageIO.read(file)
        assertThat(png.width).isEqualTo(2)
        assertThat(png.height).isEqualTo(2)
        assertThat(png.getRGB(0, 0, 2, 2, null, 0, 2)).isEqualTo(ClipboardFixtures.colors)
        assertThat(file.parentFile).isEqualTo(directory)
    }

    @Test fun `invalid image preserves the clipboard and text fallback remains available`() = runTest {
        val device = DeviceClipboard(context)
        device.apply(ClipboardContent(bitmap = ByteBuffer.wrap(byteArrayOf(1)))) { true }
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("keep me")
        device.apply(ClipboardContent(text = "fallback", bitmap = ByteBuffer.wrap(byteArrayOf(1)))) { true }
        assertThat(manager.primaryClip!!.getItemAt(0).text.toString()).isEqualTo("fallback")
    }

    @Test fun `provider metadata is private grantable and shares only the image cache`() {
        val info = context.packageManager.resolveContentProvider("${context.packageName}.clipboard", 0)!!
        assertThat(info.exported).isFalse()
        assertThat(info.grantUriPermissions).isTrue()
        val paths = mutableListOf<Pair<String, String>>()
        context.resources.getXml(R.xml.clipboard_paths).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name != "paths") {
                    paths += xml.name to xml.getAttributeValue(null, "path")
                }
            }
        }
        assertThat(paths).containsExactly("cache-path" to "clipboard-images/")
    }
}
