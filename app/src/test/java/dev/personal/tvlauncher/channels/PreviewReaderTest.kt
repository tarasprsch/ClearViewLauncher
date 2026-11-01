package dev.personal.tvlauncher.channels

import android.database.MatrixCursor
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewReaderTest {
    @Test fun readsAllProvidersWithoutSqlSelectionAndPreservesMedia() {
        val queries = mutableListOf<Uri>()
        val reader = PreviewReader { uri ->
            queries += uri
            if (uri.lastPathSegment == "channel") channels() else programmes()
        }
        val state = reader.read(true) as ChannelLoadState.Ready
        assertEquals(2, queries.size)
        assertEquals(listOf("a.app", "b.app"), state.rows.map { it.ownerPackage })
        assertEquals("content://art/poster", state.rows.first().items.first().posterUri)
        assertEquals("content://art/logo", state.rows.first().items.first().logoUri)
        assertEquals("content://art/thumb", state.rows.first().items.first().thumbnailUri)
        assertEquals(6, state.rows.first().items.first().thumbnailAspectRatio)
        assertEquals("b.app://open/2", state.rows[1].items.single().intentUri)
        assertEquals("content://art/only-thumb", state.rows[1].items.single().artwork()?.uri)
        assertEquals(2, state.skippedRecords)
    }

    @Test fun deniedPermissionNeverQueriesAndIsNotAnEmptySnapshot() {
        val reader = PreviewReader { error("Must not query when denied") }
        assertEquals(ChannelLoadState.PermissionDenied, reader.read(false))
    }

    @Test fun revokedPermissionAndNullCursorAreFailuresNotEmptySnapshots() {
        assertEquals(ChannelLoadState.PermissionDenied, PreviewReader { throw SecurityException() }.read(true))
        assertTrue(PreviewReader { null }.read(true) is ChannelLoadState.Failed)
    }

    @Test fun legitimateEmptyProviderIsAuthoritative() {
        val result = PreviewReader { MatrixCursor(arrayOf("_id")) }.read(true)
        assertTrue(result is ChannelLoadState.Ready)
        assertTrue((result as ChannelLoadState.Ready).rows.isEmpty())
    }

    @Test fun requiredColumnsMissingFromNonemptyCursorIsReadFailure() {
        val result = PreviewReader { MatrixCursor(arrayOf("_id")).apply { addRow(arrayOf(1)) } }.read(true)
        assertTrue(result is ChannelLoadState.Failed)
    }

    private fun channels() = MatrixCursor(arrayOf("_id", "package_name", "type", "display_name", "internal_provider_id")).apply {
        addRow(arrayOf(1, "a.app", "TYPE_PREVIEW", "News", "opaque-news"))
        addRow(arrayOf(2, "b.app", "TYPE_PREVIEW", "Videos", null))
        addRow(arrayOf(3, "c.app", "TYPE_OTHER", "EPG", null))
        addRow(arrayOf(4, "", "TYPE_PREVIEW", "Malformed", null))
    }

    private fun programmes() = MatrixCursor(arrayOf("_id", "channel_id", "package_name", "title", "intent_uri", "weight", "poster_art_uri", "logo_uri", "thumbnail_uri", "poster_thumbnail_aspect_ratio")).apply {
        addRow(arrayOf<Any?>(12, 1, "a.app", "Story", "a.app://open/1", 7, "content://art/poster", "content://art/logo", "content://art/thumb", 6))
        addRow(arrayOf<Any?>(13, 2, "b.app", "Clip", "b.app://open/2", 4, null, null, "content://art/only-thumb", 0))
        // A foreign owner must not inject an item into another provider's list.
        addRow(arrayOf<Any?>(14, 1, "b.app", "Wrong owner", "b.app://open/3", 100, null, null, null, null))
    }
}
