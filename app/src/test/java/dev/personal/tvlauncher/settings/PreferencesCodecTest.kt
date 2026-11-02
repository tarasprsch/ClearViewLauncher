package dev.personal.tvlauncher.settings

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreferencesCodecTest {
    @Test fun appearanceAndLayoutFieldsRoundTripWithoutLosingSelections() {
        val raw = """{"version":1,"apps":["app/.Main"],"lists":[],"showApps":false,"appsLayout":"GRID","background":"FOREST"}"""
        val encoded = org.json.JSONObject(encodePreferences(decodePreferences(raw)))
        assertFalse("Hidden Apps must remain hidden", encoded.optBoolean("showApps", true))
        assertEquals("GRID", encoded.optString("appsLayout"))
        assertEquals("FOREST", encoded.optString("background"))
        assertEquals("app/.Main", encoded.getJSONArray("apps").getString(0))
    }

    @Test fun legacyAndUnknownAppearanceValuesUseSafeDefaults() {
        for (extra in listOf("", ",\"appsLayout\":\"FUTURE\",\"background\":\"FUTURE\"")) {
            val value = decodePreferences("{\"version\":1,\"apps\":[\"app/.Main\"],\"lists\":[]$extra}")
            val json = org.json.JSONObject(encodePreferences(value))
            assertTrue(json.optBoolean("showApps", false))
            assertEquals("ROW", json.optString("appsLayout"))
            assertEquals("MIDNIGHT", json.optString("background"))
            assertEquals(listOf("app/.Main"), value.favouriteApps)
        }
    }

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
