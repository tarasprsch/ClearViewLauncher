package dev.personal.tvlauncher.home

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w960dp-h540dp-land")
class SystemDateTimeTest {
    private lateinit var previousZone: TimeZone
    private lateinit var previousSystemConfiguration: Configuration

    @Before fun prepare() {
        previousZone = TimeZone.getDefault()
        previousSystemConfiguration = Configuration(Resources.getSystem().configuration)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restore() {
        TimeZone.setDefault(previousZone)
        Resources.getSystem().updateConfiguration(previousSystemConfiguration, null)
    }

    @Test @Config(qualifiers = "uk-w960dp-h540dp-land") fun ukrainianInterfaceKeepsSystemUsDateTimeWithoutTranslatingIt() {
        systemLocale(Locale.US)
        activity("uk") { activity ->
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, null)
            activity.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
            idle()
            assertEquals("uk", activity.resources.configuration.locales[0].language)
            assertHeader(activity, "h:mm a", "MMMM d, yyyy")
        }
    }

    @Test fun englishInterfaceKeepsSystemBritishDateAndDefault24HourClock() {
        systemLocale(Locale.UK)
        activity("en-US") { activity ->
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, null)
            activity.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
            idle()
            assertHeader(activity, "HH:mm", "d MMMM yyyy")
        }
    }

    @Test @Config(qualifiers = "uk-w960dp-h540dp-land") fun systemHourPreferenceChangesRefreshTheVisibleClock() {
        systemLocale(Locale.US)
        activity("uk") { activity ->
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, "24")
            idle()
            assertHeader(activity, "HH:mm", "MMMM d, yyyy")
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, "12")
            idle()
            assertHeader(activity, "h:mm a", "MMMM d, yyyy")
        }
    }

    @Test fun systemLocaleChangesRefreshDateTimeWithoutChangingInterfaceLanguage() {
        systemLocale(Locale.US)
        activity("en-US") { activity ->
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, null)
            systemLocale(Locale.UK)
            activity.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
            idle()
            assertEquals("en", activity.resources.configuration.locales[0].language)
            assertHeader(activity, "HH:mm", "d MMMM yyyy")
        }
    }

    @Test fun englishInterfaceUsesSystemUkrainianLongDate() {
        systemLocale(Locale.forLanguageTag("uk-UA"))
        activity("en-US") { activity ->
            Settings.System.putString(activity.contentResolver, Settings.System.TIME_12_24, null)
            activity.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
            idle()
            assertHeader(activity, "HH:mm", "d MMMM yyyy '\u0440'.", Locale.forLanguageTag("uk-UA"))
        }
    }

    private fun activity(language: String, block: (MainActivity) -> Unit) {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup().visible()
            assertEquals(language.substringBefore('-'), controller.get().resources.configuration.locales[0].language)
            block(controller.get())
        }
    }

    private fun systemLocale(locale: Locale) {
        val resources = Resources.getSystem()
        resources.updateConfiguration(Configuration(resources.configuration).apply { setLocale(locale) }, null)
    }

    private fun assertHeader(activity: MainActivity, timePattern: String, datePattern: String, locale: Locale = Locale.US) {
        val header = activity.window.decorView.findViewWithTag<View>("settings").parent as ViewGroup
        val clockBlock = header.getChildAt(0) as ViewGroup
        fun text(index: Int) = (clockBlock.getChildAt(index) as TextView).text.toString()
            .replace('\u202f', ' ').replace('\u00a0', ' ')
        // Literal regional patterns are independent of the Android formatter-selection code.
        val now = Date()
        assertEquals("Clock must use the system format and language",
            SimpleDateFormat(timePattern, locale).format(now), text(0))
        assertEquals("Date must use the system format and language",
            SimpleDateFormat(datePattern, locale).format(now), text(1))
    }

    private fun idle() { Shadows.shadowOf(Looper.getMainLooper()).idle() }
}
