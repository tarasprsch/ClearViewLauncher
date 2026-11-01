package dev.personal.tvlauncher.home

data class FocusRow(val key: String, val items: List<String>)
data class FocusAnchor(val rowKey: String, val itemKey: String, val rowIndex: Int, val itemIndex: Int)
fun restoreFocus(previous: FocusAnchor?, rows: List<FocusRow>): FocusAnchor? {
    val nonempty = rows.indices.filter { rows[it].items.isNotEmpty() }
    if (nonempty.isEmpty()) return null
    val rowIndex = rows.indexOfFirst { it.key == previous?.rowKey && it.items.isNotEmpty() }
        .takeIf { it >= 0 } ?: nonempty.minBy { kotlin.math.abs(it - (previous?.rowIndex ?: 0)) }
    val row = rows[rowIndex]
    val itemIndex = row.items.indexOf(previous?.itemKey).takeIf { it >= 0 }
        ?: (previous?.itemIndex ?: 0).coerceIn(row.items.indices)
    return FocusAnchor(row.key, row.items[itemIndex], rowIndex, itemIndex)
}
