package dev.personal.tvlauncher.channels

data class ChannelItem(
    val providerId: Long,
    val title: String,
    val intentUri: String?,
    val weight: Int? = null,
    val internalId: String? = null,
    val posterUri: String? = null,
    val thumbnailUri: String? = null,
    val logoUri: String? = null,
    val posterAspectRatio: Int? = null,
    val thumbnailAspectRatio: Int? = null,
) {
    val key: String get() = internalId?.takeIf { it.isNotBlank() }?.let { "internal:$it" } ?: "id:$providerId"
    fun artwork(): Artwork? {
        if (readableArtwork(posterUri)) return Artwork(posterUri!!, false, posterAspectRatio(posterAspectRatio))
        if (readableArtwork(thumbnailUri)) return Artwork(thumbnailUri!!, false, posterAspectRatio(thumbnailAspectRatio))
        if (readableArtwork(logoUri)) return Artwork(logoUri!!, true, 16f / 9f)
        return null
    }
}

data class Artwork(val uri: String, val isLogo: Boolean, val aspectRatio: Float)

data class ChannelRow(
    val ownerPackage: String,
    val providerId: Long,
    val internalId: String?,
    val title: String,
    val items: List<ChannelItem>,
    val logoUri: String? = null,
) {
    val key: String get() = listIdentity(ownerPackage, internalId, providerId)
}

sealed interface ChannelLoadState {
    data object Loading : ChannelLoadState
    data class Ready(val rows: List<ChannelRow>, val skippedRecords: Int = 0) : ChannelLoadState
    data object PermissionDenied : ChannelLoadState
    data class Failed(val message: Int) : ChannelLoadState
}

fun listIdentity(owner: String, internalId: String?, providerId: Long): String {
    val identity = internalId?.takeIf { it.isNotBlank() }?.let { "internal:$it" } ?: "id:$providerId"
    return "${owner.length}:$owner:$identity"
}

fun orderedItems(items: List<ChannelItem>): List<ChannelItem> =
    items.sortedWith(compareByDescending<ChannelItem> { it.weight ?: 0 }.thenBy { it.providerId })

fun posterAspectRatio(value: Int?): Float = when (value) {
    0 -> 16f / 9f
    1 -> 3f / 2f
    2 -> 4f / 3f
    3 -> 1f
    4 -> 2f / 3f
    5 -> 1f / 1.441f
    6 -> 3f / 4f
    else -> 16f / 9f
}

private fun readableArtwork(uri: String?): Boolean =
    uri?.substringBefore(':')?.lowercase() in setOf("content", "android.resource", "http", "https")
