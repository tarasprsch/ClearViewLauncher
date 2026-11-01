package dev.personal.tvlauncher.fixture

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/** Deterministic independent provider, installed only with the test APK. */
class PreviewFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(selection == null && selectionArgs == null && sortOrder == null)
        return if (uri.lastPathSegment == "channel") {
            MatrixCursor(arrayOf("_id", "package_name", "type", "display_name", "internal_provider_id")).apply {
                addRow(arrayOf<Any?>(1, "fixture.source.one", "TYPE_PREVIEW", "Source one list", "opaque-1"))
                addRow(arrayOf<Any?>(2, "fixture.source.two", "TYPE_PREVIEW", "Source two list", "opaque-2"))
            }
        } else {
            MatrixCursor(arrayOf("_id", "channel_id", "package_name", "title", "weight", "intent_uri", "poster_art_uri", "logo_uri")).apply {
                addRow(arrayOf<Any?>(1, 1, "fixture.source.one", "Artwork", 10, "fixture://one/1", "android.resource://android/drawable/ic_media_play", null))
                addRow(arrayOf<Any?>(2, 1, "fixture.source.one", "Logo only", 9, "fixture://one/2", null, "android.resource://android/drawable/ic_media_play"))
                addRow(arrayOf<Any?>(3, 1, "fixture.source.one", "No artwork", 8, null, null, null))
                addRow(arrayOf<Any?>(4, 2, "fixture.source.two", "Inaccessible content", 7, "malformed", "content://missing.authority/image", null))
                addRow(arrayOf<Any?>(5, 2, "fixture.source.two", "Failed remote image", 6, "fixture://two/5", "http://127.0.0.1:1/image", null))
            }
        }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.fixture.preview"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
