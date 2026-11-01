package dev.personal.tvlauncher.home

import android.view.View
import androidx.activity.OnBackPressedDispatcherOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @Test fun subpageTitlesShareTheBackButtonRowAndAlignToTheRight() {
        Robolectric.buildActivity(MainActivity::class.java).create().visible().use { controller ->
            val activity = controller.get()
            fun check(titleText: String) {
                val root = activity.window.decorView
                val title = root.findViewWithTag<android.widget.TextView>("page-title")
                assertNotNull("Subpages need an aligned title", title)
                val back = root.findViewWithTag<View>("back")
                assertEquals(back.parent, title.parent)
                assertEquals(titleText, title.text.toString())
                assertEquals(android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL, title.gravity)
            }
            activity.window.decorView.findViewWithTag<View>("settings").performClick(); check("Settings")
            activity.window.decorView.findViewWithTag<View>("settings-apps").performClick(); check("Apps & favourites")
            activity.window.decorView.findViewWithTag<View>("back").performClick(); check("Settings")
            activity.window.decorView.findViewWithTag<View>("settings-lists").performClick(); check("Channel lists")
            activity.window.decorView.findViewWithTag<View>("back").performClick()
            activity.window.decorView.findViewWithTag<View>("back").performClick()
            val root = activity.window.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 960, 540)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val allApps = root.findViewWithTag<View>("card:all-apps")
            assertNotNull(allApps); allApps.performClick(); check("All apps")
        }
    }
    @Test fun settingsUsesAccessibleIconAndOffersNoDiagnosticsOrDescriptiveSubtitle() {
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val settings = activity.window.decorView.findViewWithTag<android.widget.Button>("settings")
            assertEquals("", settings.text.toString())
            assertEquals("Settings", settings.contentDescription)
            settings.performClick()
            assertNull(activity.window.decorView.findViewWithTag<View>("diagnostics"))
            assertNotNull(activity.window.decorView.findViewWithTag<View>("settings-apps"))
            assertFalse(texts(activity.window.decorView).contains("Make this Home screen yours"))
            activity.onBackPressedDispatcher.onBackPressed()
            assertNotNull(activity.window.decorView.findViewWithTag<View>("settings"))
            activity.onBackPressedDispatcher.onBackPressed()
            assertFalse(activity.isFinishing)
        }
    }
    private fun texts(view: View): List<String> = (if (view is android.widget.TextView) listOf(view.text.toString()) else emptyList()) +
        if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) } else emptyList()
}
