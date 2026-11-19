# PC to Android image clipboard

Input Leaf receives images from the server using the existing Input Leap/Deskflow clipboard protocol. On Windows this covers screenshots and applications that place an actual bitmap on the clipboard, such as a browser's **Copy image** command. Copying an image file in Explorer is a file-transfer operation and is outside this feature.

## Using it

Enable clipboard sharing in Deskflow. Copy a screenshot or image, move the pointer onto the Android device, and paste into an application that accepts image clipboard content. Text-only fields cannot paste images. No new Android storage permission is required.

For large screenshots, raise Deskflow's clipboard size limit to **32 MiB**. The current Deskflow source defaults to 3 MiB. Windows bitmap data is uncompressed during transfer, even if the original picture was a small PNG or JPEG. A 3840 x 2160 32-bit screenshot takes about 31.6 MiB before clipboard metadata.

## Supported data and limits

- Incoming protocol bitmap format 2: packed Windows DIB, 24/32-bit BI_RGB. Deskflow's Windows converter normalizes other Windows bitmap formats to this form.
- Positive-height bottom-up and negative-height top-down rows, four-byte row padding, INFO/V2/V3/V4/V5 headers, and optional color tables.
- BI_RGB's reserved fourth byte is treated as opaque. Transparency is not preserved by this path.
- A complete clipboard transfer, including format headers, is limited to 32 MiB. Images are limited to 8,388,608 pixels and 8192 pixels per dimension. Oversize or unsupported data leaves the existing clipboard intact; a valid accompanying text representation can be used as a fallback.
- Incoming images take priority over accompanying text. Existing text synchronization remains available in both directions. Android images and content URIs are never sent back to the server.

## Android integration

Images are converted to PNG in a single background worker, then published through a content URI with MIME type image/png. The provider is not exported and permits Android's temporary URI grants. Its only shared path is the app's clipboard-images cache directory.

Each image gets a unique URI. The most recent three published image files are retained; disconnecting does not immediately remove the current image. Android may reclaim cached files under storage pressure. New transfers and session changes invalidate unfinished older conversions, and pending updates are conflated to the latest completed clipboard.

## Validation

Automated tests cover the wire protocol through image pixels, 4K chunked data, malformed and oversize data, colors/orientation/padding, text coexistence, sequence/session boundaries, and asynchronous replacement. Robolectric tests exercise PNG encoding, text fallback and provider configuration on API 28 and 34. Android instrumentation tests exercise actual clipboard image URIs, MIME type, pixel reads, provider scope, cache retention and superseded updates. FileProvider path resolution is tested on Android rather than a Windows host filesystem.

Physical acceptance must check the actual Windows Deskflow version and Android ROM, including another app reading the image URI. The device-specific APK and acceptance steps are local build artifacts rather than changes to the release package or signing configuration.

## Protocol and platform references

- [Deskflow clipboard format contract](https://github.com/deskflow/deskflow/blob/master/src/lib/deskflow/IClipboard.h)
- [Windows bitmap conversion](https://github.com/deskflow/deskflow/blob/master/src/lib/platform/MSWindowsClipboardBitmapConverter.cpp)
- [Deskflow settings defaults](https://github.com/deskflow/deskflow/blob/master/src/lib/common/Settings.cpp)
- [Android copy/paste](https://developer.android.com/develop/ui/views/touch-and-input/copy-paste)
- [AndroidX FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
