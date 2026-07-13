package com.inputleaf.android.root

object RootCompanionState {
    @Volatile
    var client: ControlChannelClient? = null
}
