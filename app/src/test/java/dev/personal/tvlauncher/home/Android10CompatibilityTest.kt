package dev.personal.tvlauncher.home

import android.view.View
import android.os.Looper
import org.robolectric.Shadows
import dev.personal.tvlauncher.LauncherApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-rUS-w960dp-h540dp-land")
class Android10CompatibilityTest {
    @Test fun launcherStartsAndLanguageChoiceSurvivesRecreation() {
        val app = RuntimeEnvironment.getApplication() as LauncherApplication
        try {
            Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                assertNotNull(activity.window.decorView.findViewWithTag<View>("launcher-root"))
                assertTrue(activity.window.decorView.findViewWithTag<View>("settings").performClick())
                assertTrue(activity.window.decorView.findViewWithTag<View>("language").performClick())
                ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 2, 2)
                Shadows.shadowOf(Looper.getMainLooper()).idle()
            }
            Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
                assertEquals("uk", controller.get().resources.configuration.locales[0].language)
            }
        } finally {
            app.getSharedPreferences("language", 0).edit().clear().commit()
        }
    }
}
