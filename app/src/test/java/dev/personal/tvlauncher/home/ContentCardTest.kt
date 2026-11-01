package dev.personal.tvlauncher.home

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import dev.personal.tvlauncher.channels.ChannelItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.Shadows
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentCardTest {
    @Test fun twoLineChannelLabelsReserveTheirCompleteFontBoundsIncludingDescenders() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        for (scale in listOf(1f, 1.4f)) {
            val config = android.content.res.Configuration(base.resources.configuration).apply { fontScale = scale }
            val context = base.createConfigurationContext(config)
            val card = ContentCard(context)
            card.bind("gyp\n\u0443\u0440\u0444", null, null, false, 16f / 9f)
            val width = card.layoutParams.width; val height = card.layoutParams.height
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            card.layout(0, 0, width, height)
            val text = card.title
            assertEquals(2, text.lineCount)
            val inkBottom = text.layout.getLineBaseline(1) + text.paint.fontMetricsInt.bottom
            val availableHeight = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
            assertTrue("Font scale $scale: descenders need $inkBottom px but label allows $availableHeight px", inkBottom <= availableHeight)
        }
    }

    @Test fun focusBorderStaysAboveOpaqueArtworkAndClearsWhenFocusMoves() {
        Robolectric.buildActivity(android.app.Activity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            val row = android.widget.LinearLayout(activity)
            val other = android.widget.Button(activity).apply { text = "Other" }
            activity.setContentView(row)
            for (presentation in CardPresentation.entries) {
                val card = ContentCard(activity)
                card.bind("Opaque artwork", null, android.graphics.drawable.ColorDrawable(android.graphics.Color.RED), false, 16f / 9f, presentation)
                row.removeAllViews(); row.addView(card); row.addView(other)
                card.requestFocus()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
                val overlay = (card.art.parent as android.widget.FrameLayout).foreground
                assertNotNull("Every selected tile needs a border drawn above its image", overlay)
                assertEquals(255, overlay.alpha)
                other.requestFocus()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
                assertEquals("An unselected tile must not keep its focus border", 0, overlay.alpha)
            }
        }
    }

    @Test @Config(qualifiers = "w960dp-h540dp-land")
    fun focusedHomePanelOverlapsBothNeighboursWithoutMovingThem() {
        Robolectric.buildActivity(android.app.Activity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            val row = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(24, 24, 24, 24); clipChildren = false; clipToPadding = false
            }
            val cards = (1..3).map { id -> ContentCard(activity).apply {
                bind("App $id", null, android.graphics.drawable.ColorDrawable(android.graphics.Color.RED), false, 16f / 9f, CardPresentation.COMPACT_APP)
                row.addView(this)
            } }
            activity.setContentView(row)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            val positions = cards.map { it.left }
            cards[1].requestFocus()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            val bounds = cards.map { android.graphics.Rect().also { rect ->
                assertTrue((it.art.parent as View).getGlobalVisibleRect(rect))
            } }
            assertTrue("Focused artwork should overlap its left neighbour", bounds[1].left < bounds[0].right)
            assertTrue("Focused artwork should overlap its right neighbour", bounds[1].right > bounds[2].left)
            assertEquals("Focus growth must not reflow the row", positions, cards.map { it.left })
            assertTrue(cards[1].translationZ > cards[0].translationZ)
        }
    }

    @Test fun appBannersFillTheirRectangleAndRebindingAnIconKeepsItUncropped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val card = ContentCard(context)
        val banner = android.graphics.drawable.ColorDrawable(android.graphics.Color.RED)
        card.bind("Banner app", null, banner, false, 16f / 9f, CardPresentation.APP)
        assertSame(banner, card.art.drawable)
        assertEquals("Banners fill the app artwork area", android.view.ViewGroup.LayoutParams.MATCH_PARENT, card.art.layoutParams.width)
        assertEquals(ImageView.ScaleType.CENTER_CROP, card.art.scaleType)
        card.bind("Icon app", null, context.getDrawable(android.R.drawable.ic_media_play), true, 1f, CardPresentation.APP)
        assertEquals(context.dp(48), card.art.layoutParams.width)
        assertEquals(context.dp(48), card.art.layoutParams.height)
        assertEquals(ImageView.ScaleType.FIT_CENTER, card.art.scaleType)
    }

    @Test fun onlyTheFocusedTileShowsItsTitleAndGrowsWithoutMovingItsNeighbours() {
        Robolectric.buildActivity(android.app.Activity::class.java).setup().visible().windowFocusChanged(true).use { controller ->
            val activity = controller.get()
            val row = android.widget.LinearLayout(activity)
            val first = ContentCard(activity); val second = ContentCard(activity)
            first.bind("First app", null, null, true, 1f, CardPresentation.APP)
            second.bind("Second app", null, null, true, 1f, CardPresentation.APP)
            row.addView(first); row.addView(second); activity.setContentView(row)
            first.requestFocus()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            assertEquals(View.VISIBLE, first.title.visibility)
            assertEquals(View.INVISIBLE, second.title.visibility)
            assertTrue(first.scaleX > second.scaleX)
            val width = second.layoutParams.width
            second.requestFocus()
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(250, TimeUnit.MILLISECONDS)
            assertEquals(View.INVISIBLE, first.title.visibility)
            assertEquals(View.VISIBLE, second.title.visibility)
            assertEquals(width, second.layoutParams.width)
            assertEquals("First app", first.contentDescription)
        }
    }

    @Test fun compactChannelArtworkRemainsLargerThanAppArtworkAndRebindingKeepsFocusTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ContentCard(context); val channel = ContentCard(context)
        app.bind("App", null, null, true, 1f, CardPresentation.APP)
        channel.bind("Channel", null, null, true, 1f)
        assertTrue(channel.art.layoutParams.height > app.art.layoutParams.height)
        assertTrue("Channel artwork should be smaller than the old 128dp cards", channel.art.layoutParams.height <= context.dp(108))
        assertEquals(View.INVISIBLE, channel.title.visibility)
        channel.recycle()
        assertEquals(View.INVISIBLE, channel.title.visibility)
    }
    @Test fun appShortcutsAreShorterThanArtworkCardsAndNeverCropIcons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = ContentCard(context)
        val content = ContentCard(context)
        app.bind("Player", null, null, true, 16f / 9f, CardPresentation.APP)
        content.bind("A programme", null, null, false, 16f / 9f)
        assertTrue(app.layoutParams.height < content.layoutParams.height)
        assertTrue(app.art.layoutParams.height < content.art.layoutParams.height)
        assertEquals(ImageView.ScaleType.FIT_CENTER, app.art.scaleType)
    }

    @Test fun rebindingAnAppAsContentRestoresMultilineTitleAndMediaGeometry() {
        val card = ContentCard(ApplicationProvider.getApplicationContext<Context>())
        card.bind("App", null, null, false, 1f, CardPresentation.APP)
        assertEquals(1, card.title.maxLines)
        val appHeight = card.layoutParams.height
        card.bind("A long provider title", null, null, false, 16f / 9f)
        assertEquals(2, card.title.maxLines)
        assertTrue(card.layoutParams.height > appHeight)
    }

    @Test fun recyclingClearsFocusTransformsBeforeAnotherItemIsBound() {
        val card = ContentCard(ApplicationProvider.getApplicationContext<Context>())
        card.bind("One", null, null, false, 1f)
        card.scaleX = 1.05f; card.scaleY = 1.05f; card.translationZ = 8f
        card.recycle()
        assertEquals(1f, card.scaleX, 0f)
        assertEquals(1f, card.scaleY, 0f)
        assertEquals(0f, card.translationZ, 0f)
    }

    @Test fun missingArtKeepsAccessibleFocusableTitle() {
        val card = ContentCard(ApplicationProvider.getApplicationContext<Context>())
        card.bind("A title", null, null, false, 16f / 9f)
        assertEquals("A title", card.title.text.toString())
        assertEquals(View.INVISIBLE, card.title.visibility)
        assertTrue(card.isFocusable)
        assertEquals("A title", card.contentDescription)
    }

    @Test fun recycledCardsClearPreviousImageAndTitleAndLogoUsesFit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val card = ContentCard(context)
        card.bind("Logo", null, context.getDrawable(android.R.drawable.ic_media_play), true, 1f)
        assertEquals(ImageView.ScaleType.FIT_CENTER, card.art.scaleType)
        card.recycle()
        assertNull(card.art.drawable)
        card.bind("No artwork", null, null, false, 16f / 9f)
        assertNull(card.art.drawable)
        assertEquals("No artwork", card.title.text.toString())
    }
}
