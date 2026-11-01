package dev.personal.tvlauncher.fixture

import android.net.Uri
import android.view.KeyEvent
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.personal.tvlauncher.channels.*
import dev.personal.tvlauncher.home.MainActivity
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test

class PreviewIntegrationTest {
    @Test fun independentProviderPublishesMultipleOwnersAndOptionalArtwork() {
        val resolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
        val result = PreviewReader { original ->
            val fixture = Uri.parse("content://dev.personal.tvlauncher.fixture/${original.lastPathSegment}")
            resolver.query(fixture, null, null, null, null)
        }.read(true) as ChannelLoadState.Ready
        assertEquals(2, result.rows.size)
        assertEquals(listOf("Artwork", "Logo only", "No artwork"), result.rows[0].items.map { it.title })
        assertTrue(result.rows[0].items[1].artwork()!!.isLogo)
        assertNull(result.rows[0].items[2].artwork())
        assertEquals("content://missing.authority/image", result.rows[1].items[0].artwork()!!.uri)
    }

    @Test fun remoteCanOpenSettingsAndBackLeavesHomeVisible() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.window.decorView.findViewWithTag<android.view.View>("settings").requestFocus() }
            onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_DPAD_CENTER))
            onView(withText("Set as Home")).check(matches(isDisplayed()))
            onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_BACK))
            onView(withText("Favourite apps")).check(matches(isDisplayed()))
            onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_BACK))
            onView(withText("Favourite apps")).check(matches(isDisplayed()))
        }
    }
}
