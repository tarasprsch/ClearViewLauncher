package dev.personal.tvlauncher.home

import org.junit.Assert.*
import org.junit.Test

class FocusRulesTest {
    @Test fun focusFollowsStableItemWhenProviderOrderChanges() {
        val previous = FocusAnchor("row", "b", 0, 1)
        assertEquals(FocusAnchor("row", "b", 0, 0), restoreFocus(previous, listOf(FocusRow("row", listOf("b", "a")))))
    }

    @Test fun missingItemFallsBackToNearbyItemThenAnotherRow() {
        val previous = FocusAnchor("row", "gone", 1, 8)
        assertEquals(FocusAnchor("row", "last", 1, 1), restoreFocus(previous, listOf(FocusRow("apps", listOf("all")), FocusRow("row", listOf("first", "last")))))
        assertEquals(FocusAnchor("apps", "all", 0, 0), restoreFocus(previous, listOf(FocusRow("apps", listOf("all")), FocusRow("row", emptyList()))))
    }

    @Test fun emptyHomeHasNoCardFocus() {
        assertNull(restoreFocus(FocusAnchor("missing", "x", 0, 0), emptyList()))
    }
}
