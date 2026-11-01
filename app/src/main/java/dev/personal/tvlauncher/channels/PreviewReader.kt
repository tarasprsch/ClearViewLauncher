package dev.personal.tvlauncher.channels

import dev.personal.tvlauncher.R
import android.database.Cursor
import android.net.Uri

class PreviewReader(private val query: (Uri) -> Cursor?) {
    fun read(hasPermission: Boolean): ChannelLoadState {
        if (!hasPermission) return ChannelLoadState.PermissionDenied
        return try {
            var skipped = 0
            val rows = mutableListOf<ChannelRow>()
            query(CHANNELS)?.use { cursor ->
                while (cursor.moveToNext()) {
                    requireColumns(cursor, "_id", "package_name", "type", "display_name")
                    if (cursor.string("type") != "TYPE_PREVIEW") continue
                    val id = cursor.long("_id")
                    val owner = cursor.string("package_name")
                    if (id == null || owner.isNullOrBlank()) { skipped++; continue }
                    rows += ChannelRow(
                        owner, id, cursor.string("internal_provider_id"),
                        cursor.string("display_name")?.takeIf { it.isNotBlank() } ?: "",
                        emptyList(), "content://android.media.tv/channel/$id/logo",
                    )
                }
            } ?: return ChannelLoadState.Failed(R.string.error_channel_data)
            val owners = rows.associate { it.providerId to it.ownerPackage }
            val items = mutableMapOf<Long, MutableList<ChannelItem>>()
            query(PROGRAMMES)?.use { cursor ->
                while (cursor.moveToNext()) {
                    requireColumns(cursor, "_id", "channel_id", "package_name", "title")
                    val rowId = cursor.long("channel_id")
                    val id = cursor.long("_id")
                    if (rowId == null || id == null) { skipped++; continue }
                    val owner = owners[rowId] ?: continue
                    if (cursor.string("package_name") != owner) { skipped++; continue }
                    items.getOrPut(rowId) { mutableListOf() } += ChannelItem(
                        providerId = id,
                        title = cursor.string("title")?.takeIf { it.isNotBlank() } ?: "",
                        intentUri = cursor.string("intent_uri"),
                        weight = cursor.long("weight")?.toInt(),
                        internalId = cursor.string("internal_provider_id"),
                        posterUri = cursor.string("poster_art_uri"),
                        thumbnailUri = cursor.string("thumbnail_uri"),
                        logoUri = cursor.string("logo_uri"),
                        posterAspectRatio = cursor.long("poster_art_aspect_ratio")?.toInt(),
                        thumbnailAspectRatio = cursor.long("poster_thumbnail_aspect_ratio")?.toInt(),
                    )
                }
            } ?: return ChannelLoadState.Failed(R.string.error_programme_data)
            ChannelLoadState.Ready(rows.distinctBy { it.key }.map { row ->
                row.copy(items = orderedItems(items[row.providerId].orEmpty()))
            }, skipped)
        } catch (_: SecurityException) {
            ChannelLoadState.PermissionDenied
        } catch (exception: Exception) {
            ChannelLoadState.Failed(R.string.error_read_channels)
        }
    }

    private fun requireColumns(cursor: Cursor, vararg names: String) {
        require(names.all { cursor.getColumnIndex(it) >= 0 }) { "Missing provider columns" }
    }

    private fun Cursor.string(name: String): String? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.long(name: String): Long? = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it)?.toLongOrNull() }

    companion object {
        val CHANNELS: Uri = Uri.parse("content://android.media.tv/channel")
        val PROGRAMMES: Uri = Uri.parse("content://android.media.tv/preview_program")
    }
}
