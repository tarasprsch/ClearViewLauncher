package dev.personal.tvlauncher.settings

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesCodecTest {
    @Test fun roundTripPreservesOpaqueIdentityOrderAndHiddenUnavailableSelections() {
        val input = LauncherPreferences(listOf("b.app/.Tv", "a.app/.Tv"), listOf(
            SavedList("opaque:\"/\n1", "b.app", "Renamed", false),
            SavedList("id:7", "a.app", "Missing", true),
        ))
        assertEquals(input, decodePreferences(encodePreferences(input)))
    }

    @Test fun corruptedOrUnknownFormatDoesNotBecomeAnEmptyPreferenceSnapshot() {
        for (raw in listOf("broken", "{}", "{\"version\":99}")) {
            assertThrows(Exception::class.java) { decodePreferences(raw) }
        }
    }

    @Test fun absentPreferencesStartWithNoPreselectedProvider() {
        assertEquals(LauncherPreferences(), decodePreferences(null))
    }
}
