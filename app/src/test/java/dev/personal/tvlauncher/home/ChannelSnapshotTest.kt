package dev.personal.tvlauncher.home

import dev.personal.tvlauncher.channels.*
import org.junit.Assert.*
import org.junit.Test

class ChannelSnapshotTest {
    private val row = ChannelRow("any.provider", 1, "stable", "List", emptyList())

    @Test fun deniedAndFailedReadsRetainLastAuthoritativeSnapshot() {
        val ready = retainSnapshot(emptyList(), ChannelLoadState.Ready(listOf(row)))
        assertEquals(listOf(row), retainSnapshot(ready, ChannelLoadState.PermissionDenied))
        assertEquals(listOf(row), retainSnapshot(ready, ChannelLoadState.Failed(dev.personal.tvlauncher.R.string.error_read_channels)))
        assertEquals(listOf(row), retainSnapshot(ready, ChannelLoadState.Loading))
    }

    @Test fun successfulEmptyRefreshMarksPreviouslyPresentListsUnavailable() {
        assertEquals(emptyList<ChannelRow>(), retainSnapshot(listOf(row), ChannelLoadState.Ready(emptyList())))
    }
}
