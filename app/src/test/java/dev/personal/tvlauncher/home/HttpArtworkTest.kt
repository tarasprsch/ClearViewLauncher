package dev.personal.tvlauncher.home

import android.content.Context
import android.content.pm.ApplicationInfo
import android.app.Activity
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HttpArtworkTest {
    @Test fun recycledCardDoesNotReceiveLateHttpImageAndFailuresKeepTitle() {
        MockWebServer().use { server ->
            val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)).setBodyDelay(300, TimeUnit.MILLISECONDS))
            server.enqueue(MockResponse().setResponseCode(404))
            server.start()
            Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
                val card = ContentCard(controller.get())
                controller.get().setContentView(card)
                card.bind("Slow image", server.url("/slow.png").toString(), null, false, 1f)
                card.layoutParams = android.widget.FrameLayout.LayoutParams(200, 200)
                pumpUntil { server.requestCount >= 1 }
                card.recycle()
                card.bind("Replacement title", null, null, false, 1f)
                card.layoutParams = android.widget.FrameLayout.LayoutParams(200, 200)
                pumpFor(600)
                assertNull("A recycled card must not receive the old image", card.art.drawable)
                assertEquals("Replacement title", card.title.text.toString())
                card.bind("Remote failure", server.url("/missing.png").toString(), null, false, 1f)
                card.layoutParams = android.widget.FrameLayout.LayoutParams(200, 200)
                pumpUntil { server.requestCount >= 2 }
                pumpFor(300)
                assertNull(card.art.drawable)
                assertEquals("Remote failure", card.title.text.toString())
                card.bind("Inaccessible content", "content://missing.authority/image", null, false, 1f)
                card.layoutParams = android.widget.FrameLayout.LayoutParams(200, 200)
                pumpFor(200)
                assertNull(card.art.drawable)
                assertEquals("Inaccessible content", card.title.text.toString())
            }
        }
    }

    @Test fun suppliedHttpArtworkIsAllowedAndLoadsWithCoil() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("Published HTTP artwork needs explicit Android cleartext opt-in",
            context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
        MockWebServer().use { server ->
            val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
            server.start()
            val loader = ImageLoader.Builder(context).build()
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val loading = scope.async { loader.execute(ImageRequest.Builder(context).data(server.url("/art.png")).size(100, 100).build()) }
                pumpUntil { loading.isCompleted }
                val result = runBlocking { loading.await() }
                assertTrue("Coil must decode the supplied HTTP image: $result", result is SuccessResult)
            } finally { scope.cancel(); loader.shutdown() }
        }
    }
    private fun pumpUntil(predicate: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000L
        while (!predicate() && System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        assertTrue("Image request did not start", predicate())
    }
    private fun pumpFor(millis: Long) {
        val end = System.nanoTime() + millis * 1_000_000
        while (System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS); Thread.sleep(10) }
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
    }
}
