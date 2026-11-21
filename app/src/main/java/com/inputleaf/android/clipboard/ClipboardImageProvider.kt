package com.inputleaf.android.clipboard

import androidx.core.content.FileProvider

/** Grants access only to the clipboard image directory configured in clipboard_paths.xml. */
class ClipboardImageProvider : FileProvider()
