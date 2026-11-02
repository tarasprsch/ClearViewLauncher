package dev.personal.tvlauncher.home

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.personal.tvlauncher.LauncherApplication
import dev.personal.tvlauncher.apps.TvApp
import dev.personal.tvlauncher.channels.*
import dev.personal.tvlauncher.settings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land")
class HomeCustomizationTest {
    private val app get() = ApplicationProvider.getApplicationContext<LauncherApplication>()

    @Before fun resetPreferences() = runBlocking {
        val prefs = app.preferences.data.first()
        prefs.favouriteApps.forEach { app.preferences.favourite(it, false) }
        prefs.lists.forEach { app.preferences.forget(it.key) }
        app.preferences.setShowApps(true)
        app.preferences.setAppsLayout(AppsLayout.ROW)
        app.preferences.setBackground(BackgroundTheme.MIDNIGHT)
    }

    @Test fun dateIsAnInertSubtleFocusStopBeforeSettingsAndReturnsToTheSelectedPanel() {
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(preferencesLoaded = true)); settle(activity)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            key(activity, KeyEvent.KEYCODE_DPAD_UP)
            val date = activity.window.decorView.findViewWithTag<TextView>("date")
            assertNotNull("Home must offer the date as a header focus stop", date)
            assertSame(date, activity.window.decorView.findFocus())
            assertEquals(1f, date.scaleX, 0f)
            assertFalse("Date has no click action", date.performClick())
            key(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertSame(date, activity.window.decorView.findFocus())
            assertNull(Shadows.shadowOf(activity).nextStartedActivity)
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals("settings", activity.window.decorView.findFocus()?.tag)
            key(activity, KeyEvent.KEYCODE_DPAD_LEFT)
            assertSame(date, activity.window.decorView.findFocus())
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN); settle(activity)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            activity.window.decorView.findViewWithTag<View>("settings").performClick(); settle(activity)
            assertFalse("The date is a Home-only focus stop", date.isFocusable)
        }
    }

    @Test fun hidingAppsSelectsFirstNonemptyChannelAndRemovesEveryAppsElement() {
        val rows = listOf(
            ChannelRow("source.app", 1, "empty", "Empty", emptyList()),
            ChannelRow("source.app", 2, "filled", "Filled", listOf(ChannelItem(21, "First", null), ChannelItem(22, "Second", null))),
        )
        val prefs = LauncherPreferences(favouriteApps = listOf("source.app/.Main"),
            lists = rows.map { SavedList(it.key, it.ownerPackage, it.title) }, showApps = false)
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(lastRows = rows, channelState = ChannelLoadState.Ready(rows), preferences = prefs, preferencesLoaded = true))
            settle(activity)
            assertNull(activity.window.decorView.findViewWithTag<View>("card:all-apps"))
            assertNull(activity.window.decorView.findViewWithTag<View>("card:source.app/.Main"))
            assertFalse(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Apps" })
            assertEquals("card:id:21", activity.window.decorView.findFocus()?.tag)
            key(activity, KeyEvent.KEYCODE_DPAD_UP)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun hiddenAppsWithNoVisibleChannelItemsDefaultsToDateAcrossAllProviderStates() {
        val empty = ChannelRow("source.app", 1, "empty", "Empty", emptyList())
        val prefs = LauncherPreferences(lists = listOf(SavedList(empty.key, empty.ownerPackage, empty.title)), showApps = false)
        val loads = listOf(ChannelLoadState.Ready(emptyList()), ChannelLoadState.Ready(listOf(empty)),
            ChannelLoadState.PermissionDenied, ChannelLoadState.Loading, ChannelLoadState.Failed(dev.personal.tvlauncher.R.string.error_read_channels))
        for (load in loads) {
            Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
                val activity = controller.get()
                display(activity, HomeState(lastRows = if (load is ChannelLoadState.Ready) load.rows else emptyList(),
                    channelState = load, preferences = prefs, preferencesLoaded = true))
                settle(activity)
                assertEquals("Blank Home ($load) must focus the date", "date", activity.window.decorView.findFocus()?.tag)
                assertTrue(views(activity.window.decorView).filterIsInstance<ContentCard>().isEmpty())
                key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
                assertEquals("date", activity.window.decorView.findFocus()?.tag)
                key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals("settings", activity.window.decorView.findFocus()?.tag)
            }
        }
    }

    @Test fun automaticDateFallbackDoesNotEraseSavedChannelFocusWhileContentLoads() {
        val row = ChannelRow("source.app", 2, "filled", "Filled", listOf(ChannelItem(21, "First", null), ChannelItem(22, "Second", null)))
        val prefs = LauncherPreferences(lists = listOf(SavedList(row.key, row.ownerPackage, row.title)), showApps = false)
        val saved = Bundle().apply {
            putString("focusRow", row.key); putString("focusItem", "id:22")
            putInt("focusRowIndex", 1); putInt("focusItemIndex", 1)
        }
        Robolectric.buildActivity(MainActivity::class.java).create(saved).visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(preferences = prefs, preferencesLoaded = true)); settle(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            display(activity, HomeState(lastRows = listOf(row), channelState = ChannelLoadState.Ready(listOf(row)), preferences = prefs, preferencesLoaded = true))
            settle(activity)
            assertEquals("card:id:22", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun appGridWrapsToViewportWidthWithoutOmittingAppsAndPreservesFocusThroughReflow() {
        val apps = (0..12).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        val initial = HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = apps.map { it.key }, appsLayout = AppsLayout.GRID), preferencesLoaded = true)
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, initial); settle(activity)
            fun rows() = views(activity.window.decorView).filterIsInstance<androidx.recyclerview.widget.RecyclerView>()
            assertEquals("A 960dp viewport fits six compact panels per row", listOf(6, 6, 2), rows().map { it.adapter!!.itemCount })
            assertEquals(1, views(activity.window.decorView).filterIsInstance<TextView>().count { it.text.toString() == "Apps" })
            val expected = apps.map { "card:${it.key}" }.toSet() + "card:all-apps"
            assertEquals(expected, views(activity.window.decorView).filterIsInstance<ContentCard>().map { it.tag }.toSet())
            rows().forEach { row ->
                assertFalse("Grid rows must fit without horizontal scrolling", row.canScrollHorizontally(1))
                for (i in 0 until row.childCount) assertTrue(row.getChildAt(i).right <= row.width - row.paddingRight)
            }
            val selected = "card:${apps[11].key}"
            activity.window.decorView.findViewWithTag<View>(selected).requestFocus()
            settle(activity, 720)
            assertEquals("Narrower screens must wrap into four columns (decor=${activity.window.decorView.width}, root=${activity.window.decorView.findViewWithTag<View>("launcher-root").width}, viewport=${(rows().first().parent.parent as View).width})", listOf(4, 4, 4, 2), rows().map { it.adapter!!.itemCount })
            assertEquals("Reflow must preserve app identity", selected, activity.window.decorView.findFocus()?.tag)
            display(activity, initial.copy(preferences = initial.preferences.copy(appsLayout = AppsLayout.ROW)), keepFocus = true)
            settle(activity, 720)
            assertEquals(1, rows().size)
            assertEquals("Switching back to Row must preserve selection", selected, activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun gridVerticalNavigationKeepsColumnAndClampsThePartialLastRow() {
        val apps = (0..12).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = apps.map { it.key }, appsLayout = AppsLayout.GRID), preferencesLoaded = true))
            settle(activity)
            activity.window.decorView.findViewWithTag<View>("card:${apps[5].key}").requestFocus()
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN); settle(activity)
            assertEquals("card:${apps[11].key}", activity.window.decorView.findFocus()?.tag)
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN); settle(activity)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            key(activity, KeyEvent.KEYCODE_DPAD_UP); settle(activity)
            assertEquals("card:${apps[7].key}", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun appsSettingsPersistVisibilityAndGridWithoutRemovingFavouritePanels() {
        val apps = (0..1).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        runBlocking { apps.forEach { app.preferences.favourite(it.key, true) } }
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            var snapshot = HomeState(apps = apps, preferences = saved(), preferencesLoaded = true)
            display(activity, snapshot); settle(activity)
            click(activity, "settings"); click(activity, "settings-apps"); settle(activity)
            click(activity, "home-apps-visible"); await { !saved().showApps }
            snapshot = snapshot.copy(preferences = saved()); display(activity, snapshot, true); settle(activity)
            assertNotNull("Hiding Home Apps must not hide their editor", activity.window.decorView.findViewWithTag<View>("card:${apps[0].key}"))
            click(activity, "home-apps-layout")
            var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, 1, 1)
            await { saved().appsLayout == AppsLayout.GRID }
            assertEquals(apps.map { it.key }, saved().favouriteApps)
            snapshot = snapshot.copy(preferences = saved()); display(activity, snapshot, true)
            click(activity, "back"); click(activity, "back"); settle(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            assertNull(activity.window.decorView.findViewWithTag<View>("card:all-apps"))
            click(activity, "settings"); click(activity, "settings-apps")
            click(activity, "home-apps-visible"); await { saved().showApps }
            snapshot = snapshot.copy(preferences = saved()); display(activity, snapshot, true)
            click(activity, "back"); click(activity, "back"); settle(activity)
            assertNotNull(activity.window.decorView.findViewWithTag<View>("card:all-apps"))
            assertEquals("card:${apps[0].key}", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun backgroundChooserAppliesAndPersistsGradientAndCancelDoesNotChangeIt() {
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(preferencesLoaded = true)); settle(activity)
            val root = activity.window.decorView.findViewWithTag<View>("launcher-root")
            val original = root.background as android.graphics.drawable.GradientDrawable
            assertArrayEquals(intArrayOf(TvColors.ambient, TvColors.background, TvColors.background), original.colors)
            click(activity, "settings"); click(activity, "background")
            var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(6, dialog.listView.count)
            dialog.listView.performItemClick(null, 2, 2)
            await { saved().background == BackgroundTheme.FOREST }
            display(activity, HomeState(preferences = saved(), preferencesLoaded = true), true); settle(activity)
            assertSame("Applying a theme must keep the root and header", root, activity.window.decorView.findViewWithTag<View>("launcher-root"))
            val current = root.background as android.graphics.drawable.GradientDrawable
            assertEquals(android.graphics.drawable.GradientDrawable.Orientation.TL_BR, current.orientation)
            assertFalse(original.colors!!.contentEquals(current.colors!!))
            assertEquals("background", activity.window.decorView.findFocus()?.tag)
            click(activity, "background")
            dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
            assertEquals(BackgroundTheme.FOREST, saved().background)
            assertArrayEquals(current.colors, (root.background as android.graphics.drawable.GradientDrawable).colors)
        }
    }

    @Test fun savedAppAnchorSurvivesTheFrameBeforePreferencesArrive() {
        val apps = (0..1).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        val saved = Bundle().apply {
            putString("focusRow", "apps"); putString("focusItem", apps[1].key)
            putInt("focusRowIndex", 0); putInt("focusItemIndex", 1)
        }
        Robolectric.buildActivity(MainActivity::class.java).create(saved).visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            settle(activity)
            display(activity, HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = apps.map { it.key }), preferencesLoaded = true))
            settle(activity)
            assertEquals("Loading fallback must not replace an existing app anchor", "card:${apps[1].key}", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun channelFocusStaysInItsOwnListWhenOtherListsReuseAnInternalItemId() {
        val rows = listOf(
            ChannelRow("first.app", 1, "one", "First", listOf(ChannelItem(11, "First source", null, internalId = "42"))),
            ChannelRow("second.app", 2, "two", "Second", listOf(ChannelItem(22, "Second source", null, internalId = "42"))),
        )
        val snapshot = HomeState(lastRows = rows, channelState = ChannelLoadState.Ready(rows),
            preferences = LauncherPreferences(lists = rows.map { SavedList(it.key, it.ownerPackage, it.title) }, showApps = false), preferencesLoaded = true)
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, snapshot); settle(activity)
            fun second() = views(activity.window.decorView).filterIsInstance<ContentCard>().single { it.contentDescription == "Second source" }
            second().requestFocus()
            display(activity, snapshot, true); settle(activity)
            assertEquals("A refresh must retain the source list as well as item identity", "Second source", activity.window.decorView.findFocus()?.contentDescription)
            second().requestFocus()
            activity.window.decorView.findViewWithTag<View>("date").requestFocus()
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN); settle(activity)
            assertEquals("Date Down must restore the selected source list", "Second source", activity.window.decorView.findFocus()?.contentDescription)
        }
    }

    @Test fun cancellingChoosersAfterAContentRefreshRestoresTheirLaunchingControl() {
        for (chooser in listOf("background", "home-apps-layout")) {
            Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
                val activity = controller.get()
                settle(activity)
                click(activity, "settings")
                if (chooser == "home-apps-layout") click(activity, "settings-apps")
                click(activity, chooser)
                val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
                display(activity, HomeState(preferences = saved(), preferencesLoaded = true), true); settle(activity)
                assertTrue(dialog.isShowing)
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
                settle(activity)
                assertEquals("Cancelling $chooser after a refresh must restore its control", chooser, activity.window.decorView.findFocus()?.tag)
            }
        }
    }

    @Test fun homeRowsKeepHorizontalFocusAtTheirEdgesAndOnlyUpEntersTheHeader() {
        val apps = (0..7).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        val channel = ChannelRow("source.app", 1, "one", "Channel", listOf(ChannelItem(11, "Programme", null)))
        for (layout in AppsLayout.entries) {
            Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
                val activity = controller.get()
                display(activity, HomeState(apps = apps, lastRows = listOf(channel), channelState = ChannelLoadState.Ready(listOf(channel)),
                    preferences = LauncherPreferences(favouriteApps = apps.map { it.key }, appsLayout = layout,
                        lists = listOf(SavedList(channel.key, channel.ownerPackage, channel.title))), preferencesLoaded = true))
                settle(activity)
                val rows = views(activity.window.decorView).filterIsInstance<androidx.recyclerview.widget.RecyclerView>()
                rows.forEach { row ->
                    for ((position, code) in listOf(0 to KeyEvent.KEYCODE_DPAD_LEFT, row.adapter!!.itemCount - 1 to KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        row.scrollToPosition(position); settle(activity)
                        val card = row.findViewHolderForAdapterPosition(position)!!.itemView
                        card.requestFocus()
                        repeat(2) {
                            assertTrue("Row edges must consume horizontal keys before platform header focus search",
                                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)))
                            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                        }
                        assertSame("$layout: horizontal focus must stay inside its row", card, activity.window.decorView.findFocus())
                    }
                }
                val first = rows.first().apply { scrollToPosition(0) }
                settle(activity)
                first.findViewHolderForAdapterPosition(0)!!.itemView.requestFocus()
                key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals("Right must still move between cards", "card:${apps[1].key}", activity.window.decorView.findFocus()?.tag)
                key(activity, KeyEvent.KEYCODE_DPAD_UP)
                assertEquals("date", activity.window.decorView.findFocus()?.tag)
                key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                assertEquals("settings", activity.window.decorView.findFocus()?.tag)
            }
        }
    }

    @Test fun appsHomeOptionsComeAfterAvailableAppsAndAreRemotelyReachableInOneRow() {
        val apps = (0..1).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = listOf(apps[0].key)), preferencesLoaded = true))
            settle(activity)
            click(activity, "settings"); click(activity, "settings-apps"); settle(activity)
            val visibility = activity.window.decorView.findViewWithTag<View>("home-apps-visible")
            val layout = activity.window.decorView.findViewWithTag<View>("home-apps-layout")
            assertSame("Home controls should share a row", visibility.parent, layout.parent)
            val options = visibility.parent as ViewGroup
            assertTrue(layout.left < visibility.left)
            assertEquals(visibility.top, layout.top)
            val body = options.parent as ViewGroup
            assertSame("Home options belong at the end", options, body.getChildAt(body.childCount - 1))
            val heading = body.getChildAt(body.childCount - 2) as TextView
            assertEquals("Options", heading.text.toString())
            assertTrue(heading.isAccessibilityHeading)
            val available = activity.window.decorView.findViewWithTag<View>("card:${apps[1].key}")
            available.requestFocus(); key(activity, KeyEvent.KEYCODE_DPAD_DOWN); settle(activity)
            assertSame(layout, activity.window.decorView.findFocus())
            key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
            assertSame(visibility, activity.window.decorView.findFocus())
            display(activity, HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = listOf(apps[0].key)), preferencesLoaded = true), true)
            settle(activity)
            val refreshedVisibility = activity.window.decorView.findViewWithTag<View>("home-apps-visible")
            assertSame("A refresh must keep the right-hand option selected", refreshedVisibility, activity.window.decorView.findFocus())
            key(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertSame("Down on the final controls must stay there", refreshedVisibility, activity.window.decorView.findFocus())
            key(activity, KeyEvent.KEYCODE_DPAD_UP); settle(activity)
            assertEquals(available.tag, activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun homeIntentSelectsFirstAppFromCardsHeaderAndSettingsInRowAndGrid() {
        val apps = (0..7).map { TvApp(ComponentName("source.app$it", "source.app$it.Main"), "App $it", null) }
        val row = ChannelRow("source.app", 1, "one", "Channel", listOf(ChannelItem(11, "Programme", null)))
        for (layout in AppsLayout.entries) {
            Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
                val activity = controller.get()
                val snapshot = HomeState(apps = apps, lastRows = listOf(row), channelState = ChannelLoadState.Ready(listOf(row)),
                    preferences = LauncherPreferences(favouriteApps = apps.map { it.key }, appsLayout = layout,
                        lists = listOf(SavedList(row.key, row.ownerPackage, row.title))), preferencesLoaded = true)
                display(activity, snapshot); settle(activity)
                for (start in listOf("app", "channel", "date", "settings", "editor")) {
                    activity.window.decorView.findViewWithTag<View>("card:${apps[1].key}").requestFocus()
                    when (start) {
                        "channel" -> activity.window.decorView.findViewWithTag<View>("card:id:11").requestFocus()
                        "date", "settings" -> {
                            key(activity, KeyEvent.KEYCODE_DPAD_UP)
                            if (start == "settings") key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                        }
                        "editor" -> { click(activity, "settings"); click(activity, "settings-apps") }
                    }
                    controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                    // A repository emission may arrive before the posted focus move.
                    display(activity, snapshot, true); settle(activity)
                    assertEquals("Home from $start ($layout) must select the first favourite", "card:${apps[0].key}", activity.window.decorView.findFocus()?.tag)
                    assertNull("Home must close secondary pages", activity.window.decorView.findViewWithTag<View>("page-header"))
                }
                activity.window.decorView.findViewWithTag<View>("card:${apps[1].key}").requestFocus()
                display(activity, snapshot, true); settle(activity)
                assertEquals("A normal refresh must still retain the selected app", "card:${apps[1].key}", activity.window.decorView.findFocus()?.tag)
            }
        }
    }

    @Test fun homeIntentUsesStartupFallbackForNoFavouritesHiddenAppsAndEmptyChannels() {
        val empty = ChannelRow("source.app", 1, "empty", "Empty", emptyList())
        val populated = ChannelRow("source.app", 2, "filled", "Filled", listOf(ChannelItem(21, "First", null), ChannelItem(22, "Second", null)))
        val rows = listOf(empty, populated)
        val prefs = LauncherPreferences(lists = rows.map { SavedList(it.key, it.ownerPackage, it.title) }, showApps = false)
        val fixtures = listOf(
            HomeState(lastRows = rows, channelState = ChannelLoadState.Ready(rows), preferences = prefs.copy(showApps = true), preferencesLoaded = true) to "card:all-apps",
            HomeState(lastRows = rows, channelState = ChannelLoadState.Ready(rows), preferences = prefs, preferencesLoaded = true) to "card:id:21",
            HomeState(channelState = ChannelLoadState.Ready(emptyList()), preferences = prefs, preferencesLoaded = true) to "date",
            HomeState(lastRows = listOf(empty), channelState = ChannelLoadState.Ready(listOf(empty)), preferences = prefs, preferencesLoaded = true) to "date",
            HomeState(lastRows = rows, channelState = ChannelLoadState.PermissionDenied, preferences = prefs, preferencesLoaded = true) to "date",
            HomeState(preferences = prefs, preferencesLoaded = true) to "date",
        )
        for ((snapshot, expected) in fixtures) {
            Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
                val activity = controller.get()
                display(activity, snapshot); settle(activity)
                val later = activity.window.decorView.findViewWithTag<View>("card:id:22")
                if (later != null) later.requestFocus() else key(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                display(activity, snapshot, true); settle(activity)
                assertEquals("Home must use the startup selection for ${snapshot.channelState}", expected, activity.window.decorView.findFocus()?.tag)
            }
        }
    }

    @Test fun homeIntentForgetsTheOldChannelAnchorBeforePreferencesArrive() {
        val row = ChannelRow("source.app", 2, "filled", "Filled", listOf(ChannelItem(21, "First", null), ChannelItem(22, "Second", null)))
        val prefs = LauncherPreferences(lists = listOf(SavedList(row.key, row.ownerPackage, row.title)), showApps = false)
        val saved = Bundle().apply {
            putString("focusRow", row.key); putString("focusItem", "id:22")
            putInt("focusRowIndex", 1); putInt("focusItemIndex", 1)
        }
        Robolectric.buildActivity(MainActivity::class.java).create(saved).visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            settle(activity)
            controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            display(activity, HomeState(preferences = prefs, preferencesLoaded = true)); settle(activity)
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
            display(activity, HomeState(lastRows = listOf(row), channelState = ChannelLoadState.Ready(listOf(row)), preferences = prefs, preferencesLoaded = true))
            settle(activity)
            assertEquals("The first channel should be selected after loading, rather than the pre-Home anchor", "card:id:21", activity.window.decorView.findFocus()?.tag)
        }
    }

    private fun saved(): LauncherPreferences {
        val flow = app.preferences.data
        val read = java.util.concurrent.CompletableFuture.supplyAsync { runBlocking { flow.first() } }
        val end = System.nanoTime() + 3_000_000_000L
        try {
            // DataStore may dispatch a UI-originated update back to the paused
            // Robolectric main looper. Keep it moving while awaiting the read.
            while (!read.isDone && System.nanoTime() < end) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
                Thread.sleep(5)
            }
            return read.get(1, TimeUnit.SECONDS)
        } finally { if (!read.isDone) read.cancel(true) }
    }
    private fun click(activity: MainActivity, tag: String) {
        val view = activity.window.decorView.findViewWithTag<View>(tag)
        assertNotNull("Missing setting $tag", view)
        val handled = view.performClick()
        // CompoundButton toggles through its checked-change listener, independently
        // of View's OnClickListener return value. The caller asserts persisted state.
        if (view !is android.widget.CompoundButton) assertTrue(handled)
    }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10)
        }
        assertTrue("Setting was not persisted", condition())
    }

    private fun key(activity: MainActivity, code: Int) {
        val handled = activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        // Activity dispatch does not run ViewRootImpl's directional fallback under
        // Robolectric. Apply the same native focus search for unhandled directions.
        if (!handled) {
            val direction = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                else -> null
            }
            if (direction != null) activity.window.decorView.findFocus()?.focusSearch(direction)?.requestFocus(direction)
        }
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
    }
    private fun display(activity: MainActivity, state: HomeState, keepFocus: Boolean = false) {
        MainActivity::class.java.getDeclaredField("state").apply { isAccessible = true }.set(activity, state)
        MainActivity::class.java.getDeclaredMethod("render", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, keepFocus)
    }
    private fun settle(activity: MainActivity, width: Int = 960) {
        // Robolectric's ViewRoot reapplies its configured 960dp window on idle.
        // Resize the content explicitly so the viewport actually changes.
        val root = activity.window.decorView.findViewWithTag<View>("launcher-root")
        root.layoutParams = root.layoutParams.apply { this.width = width }
        repeat(3) {
            activity.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            activity.window.decorView.layout(0, 0, width, 540)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        }
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
}
