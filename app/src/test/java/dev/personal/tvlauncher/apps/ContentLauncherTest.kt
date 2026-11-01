package dev.personal.tvlauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentLauncherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun missingOrMalformedContentIntentProducesActionableFailure() {
        val launcher = ContentLauncher(context)
        assertTrue(launcher.channel("source.app", null) is LaunchResult.Failed)
        assertTrue(launcher.channel("source.app", "not a link") is LaunchResult.Failed)
        assertTrue(launcher.channel("source.app", "javascript:bad") is LaunchResult.Failed)
    }

    @Test fun removedApplicationDoesNotThrow() {
        assertTrue(ContentLauncher(context).app(ComponentName("missing.app", "missing.app.Main")) is LaunchResult.Failed)
        assertTrue(ContentLauncher(context).source("missing.app") is LaunchResult.Failed)
    }

    @Test fun providerCannotLaunchAComponentOwnedByAnotherApplication() {
        val uri = Intent(Intent.ACTION_VIEW).setComponent(ComponentName("other.app", "other.app.Main")).toUri(Intent.URI_INTENT_SCHEME)
        assertNull(contentIntent("source.app", uri))
    }

    @Test fun providerLaunchKeepsDataAndStripsGrantFlags() {
        val raw = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("source://item/1"))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .toUri(Intent.URI_INTENT_SCHEME)
        val parsed = contentIntent("source.app", raw)!!
        assertEquals("source.app", parsed.`package`)
        assertEquals("source://item/1", parsed.dataString)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, parsed.flags)
    }
}
