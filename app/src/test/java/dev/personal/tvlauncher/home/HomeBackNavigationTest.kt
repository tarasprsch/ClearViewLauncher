package dev.personal.tvlauncher.home

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import dev.personal.tvlauncher.channels.*
import dev.personal.tvlauncher.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land")
class HomeBackNavigationTest {
    @Test fun backSelectsTheFirstAppAndStaysThereWithoutLeavingHome() = withHome(
        HomeState(preferences = LauncherPreferences(favouriteApps = listOf("app0/.Main", "app1/.Main", "app2/.Main")), preferencesLoaded = true)
    ) { activity, snapshot ->
        focus(activity, 0, 2)
        back(activity)
        assertEquals("card:app0/.Main", activity.window.decorView.findFocus()?.tag)
        activity.onBackPressedDispatcher.onBackPressed(); settle(activity)
        assertEquals("card:app0/.Main", activity.window.decorView.findFocus()?.tag)
        assertFalse(activity.isFinishing)
        display(activity, snapshot, true); settle(activity)
        assertEquals("Back must update the saved selection", "card:app0/.Main", activity.window.decorView.findFocus()?.tag)
    }

    @Test fun twoBackPressesReturnFromAnOffscreenChannelToTheTopSkippingIntermediateRows() = withHome(channelHome()) { activity, snapshot ->
        // Every channel deliberately reuses internal programme IDs. Resolve the actual focused row.
        focus(activity, 5, 2)
        val scroll = views(activity.window.decorView).filterIsInstance<ScrollView>().single()
        assertTrue("The starting channel must be below the viewport", scroll.scrollY > 0)
        back(activity)
        assertEquals("Channel 5 item 0", activity.window.decorView.findFocus()?.contentDescription)
        back(activity)
        assertEquals("Channel 0 item 0", activity.window.decorView.findFocus()?.contentDescription)
        val header = views(activity.window.decorView).filterIsInstance<android.widget.TextView>().single { it.text == "source.app: Channel 0" }
        assertTrue("Back must reveal the first row heading", scroll.scrollY <= header.top)
        display(activity, snapshot, true); settle(activity)
        assertEquals("Channel 0 item 0", activity.window.decorView.findFocus()?.contentDescription)
    }

    @Test fun appsGridUsesEachVisualRowAndMovesOnlyOneStepPerRemoteBackPress() = withHome(
        HomeState(preferences = LauncherPreferences(favouriteApps = (0..12).map { "app$it/.Main" }, appsLayout = AppsLayout.GRID), preferencesLoaded = true)
    ) { activity, _ ->
        val rows = rows(activity)
        assertEquals(listOf(6, 6, 2), rows.map { it.adapter!!.itemCount })
        focus(activity, 2, 1) // All apps, last card in the last grid row.
        back(activity)
        assertEquals("card:app12/.Main", activity.window.decorView.findFocus()?.tag)
        back(activity)
        assertEquals("card:app0/.Main", activity.window.decorView.findFocus()?.tag)
        focus(activity, 1, 0)
        back(activity)
        assertEquals("card:app0/.Main", activity.window.decorView.findFocus()?.tag)
    }

    @Test fun backFromEitherHeaderControlSelectsTheFirstHomePanelRatherThanTheSavedPanel() = withHome(channelHome()) { activity, _ ->
        for (header in listOf("settings", "date")) {
            focus(activity, 4, 2)
            activity.window.decorView.findViewWithTag<View>(header).requestFocus()
            back(activity)
            assertEquals("Back from $header must return to the beginning", "Channel 0 item 0", activity.window.decorView.findFocus()?.contentDescription)
        }
    }

    @Test fun backFromACardSurvivesARefreshBeforeThePostedFocusMove() = withHome(channelHome()) { activity, snapshot ->
        focus(activity, 5, 2)
        dispatchBack(activity)
        display(activity, snapshot, true); settle(activity)
        assertEquals("A refresh must not discard the Back destination", "Channel 5 item 0", activity.window.decorView.findFocus()?.contentDescription)
    }

    @Test fun backFromTheHeaderSurvivesARefreshBeforeThePostedFocusMove() = withHome(channelHome()) { activity, snapshot ->
        focus(activity, 0, 2)
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            settle(activity)
        }
        assertEquals("settings", activity.window.decorView.findFocus()?.tag)
        dispatchBack(activity)
        display(activity, snapshot, true); settle(activity)
        assertEquals("A refresh must not restore the previous header selection", "Channel 0 item 0", activity.window.decorView.findFocus()?.contentDescription)
    }

    @Test fun backRevealsTheFirstAppWhenItHasBeenRecycledOutOfALongRow() = withHome(
        HomeState(preferences = LauncherPreferences(favouriteApps = (0..30).map { "app$it/.Main" }), preferencesLoaded = true)
    ) { activity, _ ->
        focus(activity, 0, 31)
        assertNull("The target must actually be outside the attached row children", rows(activity)[0].findViewHolderForAdapterPosition(0))
        back(activity)
        assertEquals("card:app0/.Main", activity.window.decorView.findFocus()?.tag)
    }

    @Test fun backFromAnEmptyHomeFocusesTheDateAndDoesNotExit() {
        val loads = listOf(ChannelLoadState.Loading, ChannelLoadState.PermissionDenied,
            ChannelLoadState.Ready(emptyList()), ChannelLoadState.Failed(dev.personal.tvlauncher.R.string.error_read_channels))
        for (load in loads) withHome(HomeState(preferences = LauncherPreferences(showApps = false), channelState = load, preferencesLoaded = true)) { activity, _ ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals("settings", activity.window.decorView.findFocus()?.tag)
            back(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            back(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            display(activity, HomeState(preferences = LauncherPreferences(showApps = false), channelState = load, preferencesLoaded = true), true)
            settle(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            assertFalse(activity.isFinishing)
        }
    }

    @Test fun backOnSecondaryPagesStillReturnsToTheirParent() = withHome(HomeState(preferencesLoaded = true)) { activity, _ ->
        activity.window.decorView.findViewWithTag<View>("settings").performClick(); settle(activity)
        for (page in listOf("settings-apps", "settings-lists")) {
            activity.window.decorView.findViewWithTag<View>(page).performClick(); settle(activity)
            back(activity)
            assertNotNull("Back from $page must return to Settings", activity.window.decorView.findViewWithTag<View>("settings-apps"))
        }
        back(activity)
        assertNull(activity.window.decorView.findViewWithTag<View>("page-title"))
        activity.window.decorView.findViewWithTag<View>("card:all-apps").performClick(); settle(activity)
        back(activity)
        assertNull(activity.window.decorView.findViewWithTag<View>("page-title"))
        assertFalse(activity.isFinishing)
    }

    @Test fun backDismissesAnOpenDialogBeforeNavigatingThePage() = withHome(HomeState(preferencesLoaded = true)) { activity, _ ->
        activity.window.decorView.findViewWithTag<View>("settings").performClick(); settle(activity)
        activity.window.decorView.findViewWithTag<View>("background").performClick(); settle(activity)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)); settle(activity)
        assertFalse(dialog.isShowing)
        assertNotNull(activity.window.decorView.findViewWithTag<View>("settings-apps"))
        assertEquals("background", activity.window.decorView.findFocus()?.tag)
    }

    private fun channelHome(): HomeState {
        val empty = ChannelRow("source.app", 99, "empty", "Empty", emptyList())
        val rows = listOf(empty) + (0..5).map { row ->
            ChannelRow("source.app", row.toLong(), "row$row", "Channel $row", (0..2).map { item ->
                ChannelItem((row * 10 + item).toLong(), "Channel $row item $item", null, internalId = "$item")
            })
        }
        return HomeState(preferences = LauncherPreferences(showApps = false,
            lists = rows.map { SavedList(it.key, it.ownerPackage, it.title) }),
            lastRows = rows, channelState = ChannelLoadState.Ready(rows), preferencesLoaded = true)
    }
    private fun withHome(snapshot: HomeState, test: (MainActivity, HomeState) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            // Start the real Back callback and let initial repository emissions settle
            // before supplying a deterministic content snapshot.
            val stateField = MainActivity::class.java.getDeclaredField("state").apply { isAccessible = true }
            val deadline = System.nanoTime() + 5_000_000_000L
            while (((stateField.get(activity) as HomeState).let { !it.preferencesLoaded || it.channelState == ChannelLoadState.Loading }) && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
                Thread.sleep(10)
            }
            assertTrue((stateField.get(activity) as HomeState).let { it.preferencesLoaded && it.channelState != ChannelLoadState.Loading })
            settle(activity)
            display(activity, snapshot); settle(activity)
            test(activity, snapshot)
        }
    }
    private fun focus(activity: MainActivity, row: Int, item: Int) {
        val recycler = rows(activity)[row]
        recycler.scrollToPosition(item); settle(activity)
        assertTrue(recycler.findViewHolderForAdapterPosition(item)!!.itemView.requestFocus())
        settle(activity)
    }
    private fun dispatchBack(activity: MainActivity) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
    }
    private fun back(activity: MainActivity) {
        dispatchBack(activity)
        settle(activity)
    }
    private fun rows(activity: MainActivity) = views(activity.window.decorView).filterIsInstance<RecyclerView>()
    private fun display(activity: MainActivity, state: HomeState, keepFocus: Boolean = false) {
        MainActivity::class.java.getDeclaredField("state").apply { isAccessible = true }.set(activity, state)
        MainActivity::class.java.getDeclaredMethod("render", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, keepFocus)
    }
    private fun settle(activity: MainActivity) {
        repeat(3) {
            activity.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            activity.window.decorView.layout(0, 0, 960, 540)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        }
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
}
