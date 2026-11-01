package dev.personal.tvlauncher.settings

import dev.personal.tvlauncher.channels.ChannelRow

data class SavedList(val key: String, val ownerPackage: String, val title: String, val visible: Boolean = true)
data class ManagedList(val saved: SavedList, val row: ChannelRow?)
data class LauncherPreferences(val favouriteApps: List<String> = emptyList(), val lists: List<SavedList> = emptyList())

fun selectList(saved: List<SavedList>, row: ChannelRow): List<SavedList> =
    if (saved.any { it.key == row.key }) setVisibility(saved, row.key, true)
    else saved + SavedList(row.key, row.ownerPackage, row.title)

fun moveList(saved: List<SavedList>, key: String, delta: Int): List<SavedList> {
    val index = saved.indexOfFirst { it.key == key }
    val target = index + delta
    if (index < 0 || target !in saved.indices || delta !in listOf(-1, 1)) return saved
    return saved.toMutableList().apply { add(target, removeAt(index)) }
}

fun setVisibility(saved: List<SavedList>, key: String, visible: Boolean): List<SavedList> =
    saved.map { if (it.key == key) it.copy(visible = visible) else it }

fun reconcileLists(saved: List<SavedList>, rows: List<ChannelRow>): List<ManagedList> {
    val available = rows.associateBy { it.key }
    return saved.map { entry ->
        val row = available[entry.key]
        ManagedList(if (row != null) entry.copy(title = row.title) else entry, row)
    }
}
