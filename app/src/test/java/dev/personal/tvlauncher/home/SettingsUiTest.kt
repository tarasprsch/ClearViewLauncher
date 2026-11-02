package dev.personal.tvlauncher.home

import android.content.ComponentName
import android.provider.Settings
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
class SettingsUiTest {
    @Test fun startupSelectsFirstFavouriteAfterPreferencesLoadAndPreservesLaterSelection() {
        val apps = listOf("one", "two").map { TvApp(ComponentName("source.$it", "source.$it.Main"), it, null) }
        val favourites = listOf(apps[1].key, apps[0].key)
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            // The first frame has no saved preferences yet, so only All apps exists.
            layout(activity)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            val loaded = HomeState(apps = apps, preferences = LauncherPreferences(favouriteApps = favourites), preferencesLoaded = true)
            display(activity, loaded); layout(activity)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            assertEquals("Startup must select the first app in saved favourite order", "card:${apps[1].key}", activity.window.decorView.findFocus()?.tag)
            activity.window.decorView.findViewWithTag<View>("card:all-apps").requestFocus()
            display(activity, loaded); layout(activity)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            assertEquals("A later user selection must survive refreshes", "card:all-apps", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun returningUpRevealsEveryChannelRowHeadingAlongWithItsCards() {
        val rows = (1..8).map { id -> ChannelRow("source.app", id.toLong(), "row$id", "List $id",
            listOf(ChannelItem(id.toLong(), "Item $id", null))) }
        val prefs = LauncherPreferences(lists = rows.map { SavedList(it.key, it.ownerPackage, it.title, true) })
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = prefs))
            layout(activity)
            activity.window.decorView.findViewWithTag<View>("card:id:8").requestFocus()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
            for (id in 7 downTo 1) {
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
                layout(activity)
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                assertEquals("card:id:$id", activity.window.decorView.findFocus()?.tag)
                val heading = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString().endsWith(": List $id") }
                val visible = android.graphics.Rect()
                assertTrue("Channel list $id heading must be visible when its cards regain focus", heading.getGlobalVisibleRect(visible))
                assertTrue("Channel list $id heading must not be clipped", visible.height() >= heading.height)
            }
        }
    }

    @Test fun homeSelectionDisablesActionOnlyWhileHomeRoleIsHeld() {
        val roles = Shadows.shadowOf(app.getSystemService(android.app.role.RoleManager::class.java))
        roles.addAvailableRole(android.app.role.RoleManager.ROLE_HOME)
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get(); click(activity, "settings")
            assertTrue("An unheld Home role must keep setup available even when only our activity is visible",
                activity.window.decorView.findViewWithTag<View>("home-role").isEnabled)
            roles.addHeldRole(android.app.role.RoleManager.ROLE_HOME)
            display(activity, HomeState())
            assertFalse("The selected default launcher action must be disabled", activity.window.decorView.findViewWithTag<View>("home-role").isEnabled)
            roles.removeHeldRole(android.app.role.RoleManager.ROLE_HOME)
            roles.addAvailableRole(android.app.role.RoleManager.ROLE_HOME)
            display(activity, HomeState())
            assertTrue(activity.window.decorView.findViewWithTag<View>("home-role").isEnabled)
        }
    }

    @Test fun returningFromLowerChannelRowsRevealsTheAppsHeading() {
        val rows = (1..8).map { id -> ChannelRow("source.app", id.toLong(), "row$id", "List $id",
            listOf(ChannelItem(id.toLong(), "Item $id", null))) }
        val prefs = LauncherPreferences(lists = rows.map { SavedList(it.key, it.ownerPackage, it.title, true) })
        Robolectric.buildActivity(MainActivity::class.java).create().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = prefs))
            layout(activity)
            val last = activity.window.decorView.findViewWithTag<View>("card:id:8")
            last.requestFocus()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS)
            var parent = last.parent
            while (parent !is android.widget.ScrollView) parent = parent.parent
            assertTrue("Start well below the Apps section", (parent as android.widget.ScrollView).scrollY > 0)
            repeat(8) {
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
                layout(activity)
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
            }
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            val heading = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Apps" }
            val visible = android.graphics.Rect()
            assertTrue("Returning to apps must also reveal their heading; scrollY=${parent.scrollY}, headingTop=${heading.top}", heading.getGlobalVisibleRect(visible))
            assertTrue("The Apps heading must be fully visible below the clock", visible.height() >= heading.height)
        }
    }

    @Test fun favouriteEditorUsesTheSameCompactPanelStyleAsHome() {
        val source = TvApp(ComponentName("source.app", "source.app.Main"), "Player", null,
            android.graphics.drawable.ColorDrawable(android.graphics.Color.RED))
        val available = TvApp(ComponentName("other.app", "other.app.Main"), "Other", null)
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(apps = listOf(source, available), preferences = LauncherPreferences(favouriteApps = listOf(source.key))))
            layout(activity)
            val home = activity.window.decorView.findViewWithTag<ContentCard>("card:${source.key}")
            val homeWidth = home.layoutParams.width
            val homeArtworkHeight = (home.art.parent as View).layoutParams.height
            val homeMargins = home.layoutParams as ViewGroup.MarginLayoutParams
            click(activity, "settings"); click(activity, "settings-apps"); layout(activity)
            for (key in listOf(source.key, available.key)) {
                val editor = activity.window.decorView.findViewWithTag<ContentCard>("card:$key")
                val margins = editor.layoutParams as ViewGroup.MarginLayoutParams
                assertEquals("Both favourite and available panels must match Home", homeWidth, editor.layoutParams.width)
                assertEquals(homeArtworkHeight, (editor.art.parent as View).layoutParams.height)
                assertEquals(homeMargins.leftMargin, margins.leftMargin)
                assertEquals(homeMargins.rightMargin, margins.rightMargin)
            }
        }
    }

    @Test fun scrolledRowsAreClippedBelowTheHomeHeader() {
        val rows = (1..8).map { id -> ChannelRow("source.app", id.toLong(), "row$id", "List $id",
            listOf(ChannelItem(id.toLong(), "Item $id", null))) }
        val prefs = LauncherPreferences(lists = rows.map { SavedList(it.key, it.ownerPackage, it.title, true) })
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = prefs))
            layout(activity)
            val root = activity.window.decorView
            val header = root.findViewWithTag<View>("settings").parent as View
            val card = root.findViewWithTag<View>("card:id:1")
            var parent = card.parent
            while (parent !is android.widget.ScrollView) parent = parent.parent
            val scroll = parent as android.widget.ScrollView
            val cardPosition = IntArray(2); card.getLocationOnScreen(cardPosition)
            val viewport = IntArray(2); scroll.getLocationOnScreen(viewport)
            scroll.scrollTo(0, cardPosition[1] - viewport[1] + card.height / 2)
            val visible = android.graphics.Rect(); val headerBounds = android.graphics.Rect()
            assertTrue(header.getGlobalVisibleRect(headerBounds))
            assertTrue("Part of the row should still be visible", card.getGlobalVisibleRect(visible))
            assertTrue("Scrolled content must not overlap the fixed Home header: row=$visible header=$headerBounds",
                visible.top >= headerBounds.bottom)
        }
    }

    @Test fun selectedListsFromOlderVersionsAppearWithoutASeparateVisibilityToggle() {
        val row = ChannelRow("source.app", 1, "one", "List", listOf(ChannelItem(11, "Item", null)))
        val preferences = LauncherPreferences(lists = listOf(SavedList(row.key, row.ownerPackage, row.title, false)))
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(channelState = ChannelLoadState.Ready(listOf(row)), lastRows = listOf(row), preferences = preferences))
            layout(activity)
            assertNotNull("A selected nonempty list must appear without a hidden state", activity.window.decorView.findViewWithTag<View>("card:id:11"))
        }
    }
    @Test fun listNamesIncludeApplicationAndSelectedListsHaveOnlyReorderAndRemove() {
        val source = TvApp(ComponentName("source.app", "source.app.Main"), "Player", null)
        val row = ChannelRow("source.app", 1, "one", "Favourite", listOf(ChannelItem(11, "Item", null)))
        val preferences = LauncherPreferences(lists = selectList(emptyList(), row))
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(apps = listOf(source), channelState = ChannelLoadState.Ready(listOf(row)), lastRows = listOf(row), preferences = preferences))
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Player: Favourite" })
            click(activity, "settings"); click(activity, "settings-lists")
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Player: Favourite" })
            assertNull(activity.window.decorView.findViewWithTag<View>("list:${row.key}:show"))
            val up = activity.window.decorView.findViewWithTag<View>("list:${row.key}:up")
            val down = activity.window.decorView.findViewWithTag<View>("list:${row.key}:down")
            assertEquals("Move up", up.contentDescription)
            assertEquals("Move down", down.contentDescription)
            assertFalse(up.isEnabled); assertFalse(down.isEnabled)
            assertFalse(up is TextView && up.text.isNotEmpty())
            assertNotNull(activity.window.decorView.findViewWithTag<View>("list:${row.key}:forget"))
        }
    }

    @Test fun channelAccessStatusDisablesGrantedActionAndExplainsBothStates() {
        Shadows.shadowOf(app).denyPermissions(READ_TV_LISTINGS)
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get(); click(activity, "settings")
            assertNull(activity.window.decorView.findViewWithTag<View>("app-permissions"))
            assertTrue(activity.window.decorView.findViewWithTag<View>("permission").isEnabled)
            val initialStatus = activity.window.decorView.findViewWithTag<TextView>("permission-status")
            assertNotNull("Channel access must explain its status", initialStatus)
            val explanation = initialStatus.text.toString()
            assertTrue(explanation.isNotBlank())
            Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
            display(activity, HomeState())
            assertFalse(activity.window.decorView.findViewWithTag<View>("permission").isEnabled)
            assertNotEquals(explanation, activity.window.decorView.findViewWithTag<TextView>("permission-status").text.toString())
        }
    }

    @Test fun homeSelectionDisablesActionWhenResolvedAsDefault() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        fun resolvedHome(owner: String) = android.content.pm.ResolveInfo().apply {
            isDefault = true
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = owner; name = "$owner.Home" }
        }
        val packageManager = Shadows.shadowOf(app.packageManager)
        // The manifest alone makes this launcher the sole Home candidate in Robolectric.
        // Supply a different current default before testing the transition to Clear View Launcher.
        packageManager.setResolveInfosForIntent(intent, listOf(resolvedHome("stock.home")))
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get(); click(activity, "settings")
            assertTrue(activity.window.decorView.findViewWithTag<View>("home-role").isEnabled)
            val initialStatus = activity.window.decorView.findViewWithTag<TextView>("home-role-status")
            assertNotNull("Home selection must explain its status", initialStatus)
            val before = initialStatus.text.toString()
            assertTrue(before.isNotBlank())
            packageManager.setResolveInfosForIntent(intent, listOf(resolvedHome("dev.personal.tvlauncher")))
            display(activity, HomeState())
            assertFalse("The selected default launcher action must be disabled", activity.window.decorView.findViewWithTag<View>("home-role").isEnabled)
            assertNotEquals(before, activity.window.decorView.findViewWithTag<TextView>("home-role-status").text.toString())
        }
    }
    private val app get() = ApplicationProvider.getApplicationContext<LauncherApplication>()
    @Before fun clearPreferences() = runBlocking {
        val old = app.preferences.data.first()
        app.preferences.setShowApps(true)
        app.preferences.setAppsLayout(AppsLayout.ROW)
        app.preferences.setBackground(BackgroundTheme.MIDNIGHT)
        old.favouriteApps.forEach { app.preferences.favourite(it, false) }
        old.lists.forEach { app.preferences.forget(it.key) }
    }

    @Test fun homeHasOnlyLaunchControlsWhenChannelsAreDeniedEmptyOrMissing() {
        val row = ChannelRow("source.app", 1, "one", "Empty source list", emptyList())
        val saved = selectList(emptyList(), row)
        val fixtures = listOf(
            HomeState(channelState = ChannelLoadState.PermissionDenied),
            HomeState(channelState = ChannelLoadState.Ready(emptyList())),
            HomeState(channelState = ChannelLoadState.Ready(listOf(row)), lastRows = listOf(row), preferences = LauncherPreferences(lists = saved)),
            HomeState(channelState = ChannelLoadState.Ready(emptyList()), preferences = LauncherPreferences(lists = saved)),
            HomeState(channelState = ChannelLoadState.Failed(dev.personal.tvlauncher.R.string.error_read_channels)),
        )
        fixtures.forEach { state ->
            Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
                val activity = controller.get(); display(activity, state)
                val all = views(activity.window.decorView)
                assertNull(activity.window.decorView.findViewWithTag<View>("manage"))
                assertNull(activity.window.decorView.findViewWithTag<View>("system-settings"))
                assertTrue(all.filterIsInstance<TextView>().any { it.text.toString() == "Apps" })
                assertFalse(all.filterIsInstance<TextView>().any { it.text.contains("Hold OK") || it.text.contains("Empty source list") })
                listOf("choose", "allow", "permission-settings", "retry").forEach { assertNull(activity.window.decorView.findViewWithTag<View>(it)) }
            }
        }
    }

    @Test fun appAndChannelCardsHaveNoLongPressEditAction() {
        val source = TvApp(ComponentName("source.app", "source.app.Main"), "Source", null)
        val row = ChannelRow("source.app", 1, "one", "List", listOf(ChannelItem(11, "Item", null)))
        val state = HomeState(apps = listOf(source), channelState = ChannelLoadState.Ready(listOf(row)), lastRows = listOf(row),
            preferences = LauncherPreferences(favouriteApps = listOf(source.key), lists = selectList(emptyList(), row)))
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get(); display(activity, state); layout(activity)
            val cards = views(activity.window.decorView).filterIsInstance<ContentCard>()
            assertTrue(cards.isNotEmpty())
            cards.forEach { assertFalse("${it.contentDescription} must not offer long-press editing", it.isLongClickable) }
            activity.window.decorView.findViewWithTag<View>("card:all-apps").performClick(); layout(activity)
            views(activity.window.decorView).filterIsInstance<ContentCard>().forEach { assertFalse(it.isLongClickable) }
        }
    }

    @Test fun deviceSettingsIsFirstInSettingsAndUsesStandardDeviceSettingsIntent() {
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get()
            assertNull("Device settings must not be on Home", activity.window.decorView.findViewWithTag<View>("system-settings"))
            click(activity, "settings")
            val button = activity.window.decorView.findViewWithTag<View>("system-settings")
            assertNotNull("Device settings must be available in Settings", button)
            val access = activity.window.decorView.findViewWithTag<View>("permission")
            val device = access.parent as ViewGroup
            assertSame("Device settings and Channel access belong in the same section", device, button.parent)
            assertTrue("Device settings must precede Channel access", device.indexOfChild(button) < device.indexOfChild(access))
            button.performClick()
            assertEquals(Settings.ACTION_SETTINGS, Shadows.shadowOf(activity).nextStartedActivity.action)
        }
    }

    @Test fun favouritesCanBeAddedMovedAndRemovedOnlyThroughSettings() {
        val apps = listOf("one", "two").map { TvApp(ComponentName("source.$it", "source.$it.Main"), it, null) }
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(apps = apps))
            click(activity, "settings"); click(activity, "settings-apps")
            layout(activity)
            appAction(activity, apps[0].key, "Add to favourites"); await { saved().favouriteApps == listOf(apps[0].key) }
            display(activity, HomeState(apps = apps, preferences = saved()))
            layout(activity)
            appAction(activity, apps[1].key, "Add to favourites"); await { saved().favouriteApps.size == 2 }
            display(activity, HomeState(apps = apps, preferences = saved()))
            layout(activity)
            appAction(activity, apps[1].key, "Move left"); await { saved().favouriteApps == listOf(apps[1].key, apps[0].key) }
            display(activity, HomeState(apps = apps, preferences = saved()))
            layout(activity)
            appAction(activity, apps[0].key, "Remove"); await { saved().favouriteApps == listOf(apps[1].key) }
        }
    }

    @Test fun removedFavouriteRemainsRemovableInSettings() {
        val key = "missing.app/missing.app.Main"
        runBlocking { app.preferences.favourite(key, true) }
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get(); display(activity, HomeState(preferences = saved()))
            click(activity, "settings"); click(activity, "settings-apps")
            layout(activity)
            appAction(activity, key, "Remove"); await { saved().favouriteApps.isEmpty() }
        }
    }

    @Test fun availableListsAreGroupedByOwnerAndAddPreservesSelectedOrder() {
        val rows = listOf(
            ChannelRow("a.app", 1, "a1", "A first", emptyList()),
            ChannelRow("b.app", 2, "b1", "B first", emptyList()),
            ChannelRow("a.app", 3, "a2", "A second", emptyList()),
        )
        Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows))
            click(activity, "settings"); click(activity, "settings-lists")
            val a = activity.window.decorView.findViewWithTag<ViewGroup>("available:a.app")
            val b = activity.window.decorView.findViewWithTag<ViewGroup>("available:b.app")
            assertNotNull("Available lists must have application groups", a); assertNotNull(b)
            assertNotNull(a.findViewWithTag<View>("add:${rows[0].key}")); assertNotNull(a.findViewWithTag<View>("add:${rows[2].key}"))
            assertNull(a.findViewWithTag<View>("add:${rows[1].key}")); assertNotNull(b.findViewWithTag<View>("add:${rows[1].key}"))
            click(activity, "add:${rows[1].key}"); await { saved().lists.size == 1 }
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = saved()))
            click(activity, "add:${rows[0].key}"); await { saved().lists.size == 2 }
            assertEquals(listOf(rows[1].key, rows[0].key), saved().lists.map { it.key })
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = saved()))
            click(activity, "list:${rows[0].key}:up"); await { saved().lists.first().key == rows[0].key }
            display(activity, HomeState(channelState = ChannelLoadState.Ready(rows), lastRows = rows, preferences = saved()))
            click(activity, "list:${rows[0].key}:forget"); await { saved().lists.map { it.key } == listOf(rows[1].key) }
        }
    }

    @Test @Config(qualifiers = "uk-rUA-w960dp-h540dp-land")
    fun ukrainianLocaleTranslatesHomeSettingsAndUnavailableAppDialog() {
        val key = "missing.app/missing.app.Main"
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            display(activity, HomeState(preferences = LauncherPreferences(favouriteApps = listOf(key))))
            layout(activity)
            assertEquals("Налаштування", activity.window.decorView.findViewWithTag<View>("settings").contentDescription)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Застосунки" })
            click(activity, "card:$key")
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.toString().startsWith("Цей застосунок недоступний"))
            dialog.dismiss()
            click(activity, "settings")
            assertEquals("Мова", (activity.window.decorView.findViewWithTag<View>("language") as TextView).text.toString())
            click(activity, "settings-lists")
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Списки каналів" })
        }
    }

    @Test fun languagePickerSavesNativePerAppChoiceAndCanReturnToSystemLanguage() {
        val manager = app.getSystemService(android.app.LocaleManager::class.java)
        try {
            Robolectric.buildActivity(MainActivity::class.java).create().use { controller ->
                val activity = controller.get(); click(activity, "settings")
                val language = activity.window.decorView.findViewWithTag<View>("language")
                assertNotNull("Settings must offer language selection", language)
                language.performClick()
                var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
                assertEquals(3, dialog.listView.count)
                dialog.listView.performItemClick(null, 2, 2)
                assertEquals("uk", manager.applicationLocales.toLanguageTags())
                language.performClick()
                dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
                dialog.listView.performItemClick(null, 0, 0)
                assertTrue(manager.applicationLocales.isEmpty)
            }
        } finally { manager.applicationLocales = android.os.LocaleList.getEmptyLocaleList() }
    }

    private fun saved() = runBlocking { app.preferences.data.first() }
    private fun appAction(activity: MainActivity, key: String, action: String) {
        val tile = activity.window.decorView.findViewWithTag<View>("card:$key")
        assertTrue("Favourites must use image tiles", tile is ContentCard)
        tile.requestFocus(); tile.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("OK must open explicit actions", dialog)
        val options = dialog.listView
        val index = (0 until options.count).firstOrNull { options.adapter.getItem(it).toString() == action } ?: -1
        assertTrue("Missing app action: $action", index >= 0)
        options.performItemClick(null, index, index.toLong())
    }
    private fun click(activity: MainActivity, key: String) {
        val view = activity.window.decorView.findViewWithTag<View>(key)
        assertNotNull("Missing action $key", view); assertTrue(view.performClick())
    }
    // Exercise each rendering state without starting provider observation; writes still use the real DataStore.
    private fun display(activity: MainActivity, state: HomeState) {
        MainActivity::class.java.getDeclaredField("state").apply { isAccessible = true }.set(activity, state)
        MainActivity::class.java.getDeclaredMethod("render", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(activity, false)
    }
    private fun layout(activity: MainActivity) {
        activity.window.decorView.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        activity.window.decorView.layout(0, 0, 960, 540)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < until) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10)
        }
        assertTrue("Setting was not persisted", condition())
    }
}
