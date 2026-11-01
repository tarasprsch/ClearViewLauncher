package dev.personal.tvlauncher.settings

import dev.personal.tvlauncher.channels.ChannelRow
import org.junit.Assert.*
import org.junit.Test

class ListRulesTest {
    private fun row(id: Long, title: String = "Row $id") = ChannelRow("source.app", id, "stable$id", title, emptyList())
    private fun saved(id: Long, visible: Boolean = true) = SavedList(row(id).key, "source.app", "Row $id", visible)

    @Test fun newSelectionsAppendAndHiddenRowsRetainTheirPlace() {
        val initial = listOf(saved(2), saved(1, false))
        val enabled = selectList(initial, row(3))
        assertEquals(listOf(row(2).key, row(1).key, row(3).key), enabled.map { it.key })
        assertEquals(listOf(row(2).key, row(3).key), enabled.filter { it.visible }.map { it.key })
        assertEquals(3, selectList(enabled, row(1)).size)
        assertTrue(selectList(enabled, row(1))[1].visible)
    }

    @Test fun movementStopsAtBoundariesAndDoesNotInventMissingRows() {
        val rows = listOf(saved(1), saved(2), saved(3))
        assertEquals(rows, moveList(rows, row(1).key, -1))
        assertEquals(rows, moveList(rows, row(3).key, 1))
        assertEquals(rows, moveList(rows, "unknown", 1))
        assertEquals(listOf(row(2).key, row(1).key, row(3).key), moveList(rows, row(2).key, -1).map { it.key })
    }

    @Test fun renamePreservesOrderAndMissingListsStayUnavailable() {
        val saved = listOf(saved(2), saved(1))
        val management = reconcileLists(saved, listOf(row(1, "Renamed")))
        assertEquals(listOf(row(2).key, row(1).key), management.map { it.saved.key })
        assertNull(management[0].row)
        assertEquals("Renamed", management[1].saved.title)
        assertEquals(saved[1].key, management[1].saved.key)
    }

    @Test fun recreatedListWithNoInternalIdentityRequiresReselection() {
        val old = ChannelRow("source.app", 1, null, "News", emptyList())
        val recreated = old.copy(providerId = 2)
        val result = reconcileLists(listOf(SavedList(old.key, old.ownerPackage, old.title)), listOf(recreated))
        assertNull(result.single().row)
        assertNotEquals(result.single().saved.key, recreated.key)
    }

    @Test fun hideDoesNotRemoveSelectionAndUnknownKeysAreIgnored() {
        val rows = listOf(saved(1), saved(2))
        assertFalse(setVisibility(rows, row(1).key, false)[0].visible)
        assertEquals(2, setVisibility(rows, row(1).key, false).size)
        assertEquals(rows, setVisibility(rows, "missing", false))
    }
}
