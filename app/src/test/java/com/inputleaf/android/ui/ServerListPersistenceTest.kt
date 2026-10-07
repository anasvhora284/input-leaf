package com.inputleaf.android.ui

import com.google.common.truth.Truth.assertThat
import com.inputleaf.android.model.ServerInfo
import org.junit.Test

class ServerListPersistenceTest {
    @Test fun `saved servers remain visible after restart or an empty network scan`() {
        assertThat(mergeServerLists(setOf("192.168.1.68", "192.168.1.10"), emptyList()))
            .containsExactly(ServerInfo("192.168.1.10"), ServerInfo("192.168.1.68")).inOrder()
    }

    @Test fun `rediscovering a saved address updates its details without duplicating or dropping other servers`() {
        val discovered = ServerInfo("192.168.1.10", name = "Synergy 1.8")
        val another = ServerInfo("192.168.1.20", name = "Barrier 1.6")
        assertThat(mergeServerLists(setOf("192.168.1.10", "macbook-b.local"), listOf(discovered, another)))
            .containsExactly(discovered, ServerInfo("macbook-b.local"), another).inOrder()
    }
}
