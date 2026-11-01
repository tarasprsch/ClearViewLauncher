package dev.personal.tvlauncher.channels

import org.junit.Assert.*
import org.junit.Test

class ChannelRulesTest {
    // Catches identity based on a mutable title or on a provider ID without its owner.
    @Test fun stableIdentitySurvivesRenameAndSeparatesOwners() {
        assertEquals(listIdentity("one.app", "opaque/7", 1), listIdentity("one.app", "opaque/7", 99))
        assertNotEquals(listIdentity("one.app", "7", 1), listIdentity("two.app", "7", 1))
        assertNotEquals(listIdentity("one.app", null, 1), listIdentity("one.app", null, 2))
        assertNotEquals(listIdentity("one.app", "1", 1), listIdentity("one.app", null, 1))
    }

    // Catches accidental local sorting by title or ascending provider weight.
    @Test fun programmesUseDescendingWeightAndNumericIdTies() {
        val items = listOf(item(12, 2), item(9, 7), item(2, 7), item(3, null))
        assertEquals(listOf(2L, 9L, 12L, 3L), orderedItems(items).map { it.providerId })
    }

    @Test fun artworkFallsBackThroughPublishedFieldsAndRejectsUnsafeSchemes() {
        assertEquals("content://p/poster", item(1).copy(posterUri = "content://p/poster", thumbnailUri = "https://p/thumb").artwork()?.uri)
        assertEquals("https://p/thumb", item(1).copy(posterUri = "file:///private", thumbnailUri = "https://p/thumb").artwork()?.uri)
        assertTrue(item(1).copy(logoUri = "android.resource://p/1").artwork()!!.isLogo)
        assertNull(item(1).copy(posterUri = "bad", logoUri = "javascript:bad").artwork())
    }

    @Test fun declaredAspectRatioIsPreserved() {
        assertEquals(2f / 3f, posterAspectRatio(4), 0.001f)
        assertEquals(16f / 9f, posterAspectRatio(null), 0.001f)
        assertEquals(1f / 1.441f, posterAspectRatio(5), 0.001f)
        assertEquals(3f / 4f, posterAspectRatio(6), 0.001f)
    }

    private fun item(id: Long, weight: Int? = null) = ChannelItem(
        providerId = id, title = "Item $id", intentUri = null, weight = weight,
    )
}
