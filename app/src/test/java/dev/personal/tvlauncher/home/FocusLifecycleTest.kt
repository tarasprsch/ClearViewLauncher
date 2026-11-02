package dev.personal.tvlauncher.home

import android.content.*
import android.content.pm.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.personal.tvlauncher.LauncherApplication
import dev.personal.tvlauncher.channels.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land")
class FocusLifecycleTest {
    @Test fun homeIntentAndContentRefreshNeverTemporarilySelectTheHeader() {
        installApps(1)
        var appCount = 1
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        runBlocking { app.preferences.select(ChannelRow("source.app", 1, "stable", "List", emptyList())) }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            pumpUntil { activity.window.decorView.findViewWithTag<View>("card:id:12") != null }
            pumpFor(200)
            val transitions = mutableListOf<String?>()
            val listener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
                transitions.add(focused?.tag as? String)
            }
            activity.window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
            try {
                for (key in listOf("card:all-apps", "card:id:12")) {
                    for (homePress in listOf(true, false)) {
                        activity.window.decorView.findViewWithTag<View>(key).requestFocus()
                        pumpFor(100)
                        transitions.clear()
                        if (homePress) {
                            installApps(++appCount)
                            controller.pause().newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)).resume()
                        } else {
                            provider.extraLists++
                            app.contentResolver.notifyChange(Uri.parse("content://android.media.tv/channel"), null)
                        }
                        pumpFor(500)
                        assertEquals("Home resets to the first panel; ordinary refreshes retain selection",
                            if (homePress) "card:all-apps" else key, activity.window.decorView.findFocus()?.tag)
                        assertFalse("Header focus flashed while refreshing $key (Home=$homePress): $transitions",
                            transitions.any { it == "settings" || it == "system-settings" })
                    }
                }
            } finally {
                activity.window.decorView.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
            }
        }
    }

    @Test fun publishedAppBannersReachHomeAllAppsAndFavouritesWithIconFallback() {
        val bannerId = 0x7f080abc
        val iconId = 0x7f080abd
        installApps(2) { info ->
            if (info.packageName == "source.app0") info.applicationInfo.banner = bannerId
            else info.applicationInfo.icon = iconId
        }
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        val pm = Shadows.shadowOf(app.packageManager)
        val banner = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
        val icon = android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
        pm.addDrawableResolution("source.app0", bannerId, banner)
        pm.addDrawableResolution("source.app1", iconId, icon)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val resolved = app.packageManager.queryIntentActivities(launch, 0).first { it.activityInfo.packageName == "source.app0" }
        assertSame("The installed fixture must publish its application banner", banner, resolved.activityInfo.loadBanner(app.packageManager))
        runBlocking {
            app.preferences.favourite("source.app0/source.app0.Main", true)
            app.preferences.favourite("source.app1/source.app1.Main", true)
        }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            fun checkCards() {
                pumpUntil { activity.window.decorView.findViewWithTag<View>("card:source.app0/source.app0.Main") != null }
                pumpFor(100)
                val bannerCard = activity.window.decorView.findViewWithTag<ContentCard>("card:source.app0/source.app0.Main")
                val iconCard = activity.window.decorView.findViewWithTag<ContentCard>("card:source.app1/source.app1.Main")
                assertEquals("Published app banners must be used on every app screen", android.graphics.Color.RED,
                    (bannerCard.art.drawable as? android.graphics.drawable.ColorDrawable)?.color)
                assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, bannerCard.art.layoutParams.width)
                assertEquals(android.graphics.Color.BLUE, (iconCard.art.drawable as? android.graphics.drawable.ColorDrawable)?.color)
                assertEquals(app.dp(48), iconCard.art.layoutParams.width)
            }
            checkCards()
            activity.window.decorView.findViewWithTag<View>("card:all-apps").performClick(); checkCards()
            activity.window.decorView.findViewWithTag<View>("back").performClick()
            activity.window.decorView.findViewWithTag<View>("settings").performClick()
            activity.window.decorView.findViewWithTag<View>("settings-apps").performClick(); checkCards()
            assertTrue("Secondary pages keep the Home clock/settings header", (activity.window.decorView.findViewWithTag<View>("settings").parent as View).isShown)
        }
    }

    @Test fun favouriteImageEditorKeepsEditedAppFocusedAfterAddMoveAndRemove() {
        installApps(9)
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        fun favourites() = runBlocking { app.preferences.data.first().favouriteApps }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            activity.window.decorView.findViewWithTag<View>("settings").performClick()
            activity.window.decorView.findViewWithTag<View>("settings-apps").performClick()
            fun edit(index: Int, choice: Int) {
                val key = "card:source.app$index/source.app$index.Main"
                pumpUntil { activity.window.decorView.findViewWithTag<View>(key) != null }
                pumpFor(200)
                activity.window.decorView.findViewWithTag<View>(key).apply { requestFocus(); performClick() }
                val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
                dialog.listView.performItemClick(null, choice, choice.toLong())
            }
            edit(7, 0)
            pumpUntil { favourites().size == 1 }; pumpFor(300)
            assertEquals("card:source.app7/source.app7.Main", activity.window.decorView.findFocus()?.tag)
            edit(1, 0)
            pumpUntil { favourites().size == 2 }; pumpFor(300)
            assertEquals("card:source.app1/source.app1.Main", activity.window.decorView.findFocus()?.tag)
            edit(1, 0)
            pumpUntil { favourites().first() == "source.app1/source.app1.Main" }; pumpFor(300)
            assertEquals("card:source.app1/source.app1.Main", activity.window.decorView.findFocus()?.tag)
            edit(1, 1)
            pumpUntil { favourites().size == 1 }; pumpFor(300)
            val focus = activity.window.decorView.findFocus()
            assertEquals("card:source.app1/source.app1.Main", focus?.tag)
            assertTrue(focus!!.getGlobalVisibleRect(android.graphics.Rect()))
            assertEquals(listOf("source.app7/source.app7.Main"), favourites())
            listOf(
                android.view.KeyEvent.KEYCODE_DPAD_UP to "card:source.app7/source.app7.Main",
                android.view.KeyEvent.KEYCODE_DPAD_UP to "back",
                android.view.KeyEvent.KEYCODE_DPAD_DOWN to "card:source.app7/source.app7.Main",
                android.view.KeyEvent.KEYCODE_DPAD_DOWN to "card:source.app1/source.app1.Main",
                android.view.KeyEvent.KEYCODE_DPAD_DOWN to "card:source.app8/source.app8.Main",
                android.view.KeyEvent.KEYCODE_DPAD_DOWN to "home-apps-layout",
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT to "home-apps-visible",
                android.view.KeyEvent.KEYCODE_DPAD_UP to "card:source.app8/source.app8.Main",
            ).forEach { (key, expected) ->
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
                activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
                pumpFor(100)
                assertEquals("Back and image rows must remain reachable by D-pad", expected, activity.window.decorView.findFocus()?.tag)
            }
        }
    }
    @Test fun addingAListFromFarDownSettingsKeepsTheNewSelectionVisible() {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        provider.extraLists = 32
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            activity.window.decorView.findViewWithTag<View>("settings").performClick()
            activity.window.decorView.findViewWithTag<View>("settings-lists").performClick()
            val key = "10:source.app:internal:extra31"
            pumpUntil { activity.window.decorView.findViewWithTag<View>("add:$key") != null }
            pumpFor(200)
            val add = activity.window.decorView.findViewWithTag<View>("add:$key")
            add.requestFocus(); pumpFor(250)
            var ancestor = add.parent
            while (ancestor != null && ancestor !is android.widget.ScrollView) ancestor = ancestor.parent
            assertTrue((ancestor as android.widget.ScrollView).scrollY > 0)
            add.performClick()
            pumpUntil { activity.window.decorView.findViewWithTag<View>("list:$key:forget") != null }
            pumpFor(300)
            val focus = activity.window.decorView.findFocus()
            assertEquals("list:$key:forget", focus?.tag)
            val bounds = android.graphics.Rect()
            assertTrue("The selected list control must be on screen after adding it", focus!!.getGlobalVisibleRect(bounds))
            assertTrue("The selected control must be fully visible", bounds.height() >= focus.height - 2)
        }
    }
    @Before fun clearSavedSelections() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<LauncherApplication>().preferences
        val saved = preferences.data.first()
        preferences.setShowApps(true)
        preferences.setAppsLayout(dev.personal.tvlauncher.settings.AppsLayout.ROW)
        preferences.setBackground(dev.personal.tvlauncher.settings.BackgroundTheme.MIDNIGHT)
        saved.favouriteApps.forEach { preferences.favourite(it, false) }
        saved.lists.forEach { preferences.forget(it.key) }
    }
    @Test fun focusedFirstContentCardAndItsHeadingStayFullyVisibleAtTvSize() {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        runBlocking { app.preferences.select(ChannelRow("source.app", 1, "stable", "List", emptyList())) }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            pumpUntil { activity.window.decorView.findViewWithTag<View>("card:id:11") != null }
            pumpFor(200)
            val card = activity.window.decorView.findViewWithTag<View>("card:id:11")
            activity.window.decorView.findViewWithTag<View>("card:all-apps").requestFocus()
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            pumpFor(250)
            assertEquals("card:id:11", activity.window.decorView.findFocus()?.tag)
            val bounds = android.graphics.Rect()
            assertTrue(card.getGlobalVisibleRect(bounds))
            assertTrue("The focused artwork card is clipped", bounds.height() >= kotlin.math.round(card.height * card.scaleY).toInt() - 2)
            var ancestor = card.parent
            while (ancestor != null && ancestor !is android.widget.ScrollView) ancestor = ancestor.parent
            val scroll = ancestor as android.widget.ScrollView
            val children = scroll.getChildAt(0) as android.view.ViewGroup
            val heading = (0 until children.childCount).map { children.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().first { it.text.toString().endsWith(": List") }
            val headingBounds = android.graphics.Rect()
            assertTrue("The focused row heading must be on screen", heading.getGlobalVisibleRect(headingBounds))
            assertEquals("Its complete heading must remain visible", heading.height, headingBounds.height())
        }
    }

    @Test fun lastRowDownKeepsCardSelectedAndUpReachesHeader() {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        runBlocking { app.preferences.select(ChannelRow("source.app", 1, "stable", "List", emptyList())) }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            val key = "card:id:11"
            pumpUntil { activity.window.decorView.findViewWithTag<View>(key) != null }
            pumpFor(200)
            activity.window.decorView.findViewWithTag<View>(key).requestFocus()
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            pumpFor(100)
            assertEquals(key, activity.window.decorView.findFocus()?.tag)
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
            pumpFor(100)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_UP))
            assertEquals("date", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun emptyHomeKeepsFocusOnAppsAndChannelSetupIsInSettings() {
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            pumpUntil { activity.window.decorView.findViewWithTag<View>("card:all-apps") != null }
            pumpFor(200)
            assertEquals("An empty Apps row must select All apps by default", "card:all-apps", activity.window.decorView.findFocus()?.tag)
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            activity.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN))
            pumpFor(100)
            assertEquals("card:all-apps", activity.window.decorView.findFocus()?.tag)
            assertNull(activity.window.decorView.findViewWithTag<View>("allow"))
            assertNull(activity.window.decorView.findViewWithTag<View>("choose"))
            activity.window.decorView.findViewWithTag<View>("settings").performClick()
            activity.window.decorView.findViewWithTag<View>("settings-lists").performClick()
            assertNotNull(activity.window.decorView.findViewWithTag<View>("allow"))
        }
    }

    @Test fun savedChannelFocusWaitsForInitialProviderLoadWithAppsHidden() {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        provider.delayMillis = 300
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        val row = ChannelRow("source.app", 1, "stable", "List", emptyList())
        runBlocking { app.preferences.select(row); app.preferences.setShowApps(false) }
        val saved = android.os.Bundle().apply {
            putString("focusRow", row.key); putString("focusItem", "id:12")
            putInt("focusRowIndex", 1); putInt("focusItemIndex", 1)
        }
        Robolectric.buildActivity(MainActivity::class.java).create(saved).start().resume().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            pumpUntil { activity.window.decorView.findViewWithTag<View>("card:id:12") != null }
            pumpFor(200)
            assertEquals("card:id:12", activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun channelFocusSurvivesStoppedStartedWithDelayedProviderRefresh() {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        Shadows.shadowOf(app).grantPermissions(READ_TV_LISTINGS)
        val provider = Robolectric.buildContentProvider(FocusProvider::class.java).create().get()
        ShadowContentResolver.registerProviderInternal("android.media.tv", provider)
        runBlocking { app.preferences.select(ChannelRow("source.app", 1, "stable", "List", emptyList())) }
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            val key = "card:id:12"
            pumpUntil { activity.window.decorView.findViewWithTag<View>(key) != null }
            pumpFor(300)
            val initialCard = activity.window.decorView.findViewWithTag<View>(key)
            assertTrue("Initial card focus: shown=${initialCard.isShown}, touch=${initialCard.isInTouchMode}, size=${initialCard.width}x${initialCard.height}, focusable=${initialCard.isFocusable}", initialCard.requestFocus())
            assertEquals(key, activity.window.decorView.findFocus()?.tag)
            controller.pause().stop()
            pumpFor(100)
            provider.delayMillis = 300
            controller.start().resume().visible().windowFocusChanged(true)
            pumpFor(900)
            assertEquals(key, activity.window.decorView.findFocus()?.tag)
        }
    }

    @Test fun laterAllAppsSelectionSurvivesReturningFromAnotherAppWithoutEditMenu() {
        installApps(9)
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        assertEquals(9, app.packageManager.queryIntentActivities(intent, 0).count { it.activityInfo.packageName.startsWith("source.app") })
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            activity.window.decorView.findViewWithTag<View>("card:all-apps")?.performClick()
                ?: pumpUntil { activity.window.decorView.findViewWithTag<View>("card:all-apps")?.performClick() == true }
            val key = "card:source.app7/source.app7.Main"
            pumpUntil { activity.window.decorView.findViewWithTag<View>(key) != null }
            val selected = activity.window.decorView.findViewWithTag<View>(key)
            selected.requestFocus()
            assertFalse(selected.isLongClickable)
            pumpFor(400)
            assertEquals(key, activity.window.decorView.findFocus()?.tag)
            controller.pause().stop(); pumpFor(100); controller.start().resume().visible().windowFocusChanged(true); pumpFor(500)
            assertEquals(key, activity.window.decorView.findFocus()?.tag)
        }
    }

    private fun installApps(count: Int, decorate: (ActivityInfo) -> Unit = {}) {
        val app = ApplicationProvider.getApplicationContext<LauncherApplication>()
        val packageManager = Shadows.shadowOf(app.packageManager)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        repeat(count) { index ->
            packageManager.addResolveInfoForIntent(intent, ResolveInfo().apply {
                nonLocalizedLabel = "App $index"
                activityInfo = ActivityInfo().apply {
                    packageName = "source.app$index"; name = "source.app$index.Main"; enabled = true; exported = true
                    applicationInfo = ApplicationInfo().apply { packageName = "source.app$index"; enabled = true }
                    decorate(this)
                }
            })
        }
    }

    private fun pumpUntil(predicate: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!predicate() && System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        assertTrue("UI condition did not become true", predicate())
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
    }
    private fun pumpFor(millis: Long) {
        val end = System.nanoTime() + millis * 1_000_000
        while (System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
    }

    class FocusProvider : ContentProvider() {
        @Volatile var delayMillis = 0L
        @Volatile var extraLists = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            Thread.sleep(delayMillis)
            return if (uri.lastPathSegment == "channel") MatrixCursor(arrayOf("_id", "package_name", "type", "display_name", "internal_provider_id")).apply {
                addRow(arrayOf<Any?>(1, "source.app", "TYPE_PREVIEW", "List", "stable"))
                repeat(extraLists) { index -> addRow(arrayOf<Any?>(index + 2, "source.app", "TYPE_PREVIEW", "List $index", "extra$index")) }
            } else MatrixCursor(arrayOf("_id", "channel_id", "package_name", "title")).apply {
                addRow(arrayOf<Any?>(11, 1, "source.app", "First")); addRow(arrayOf<Any?>(12, 1, "source.app", "Second"))
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
