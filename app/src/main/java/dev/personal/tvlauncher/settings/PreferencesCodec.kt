package dev.personal.tvlauncher.settings

import org.json.JSONArray
import org.json.JSONObject

fun encodePreferences(value: LauncherPreferences): String = JSONObject().apply {
    put("version", 1)
    put("showApps", value.showApps)
    put("appsLayout", value.appsLayout.name)
    put("background", value.background.name)
    put("apps", JSONArray(value.favouriteApps))
    put("lists", JSONArray().apply {
        value.lists.forEach { row -> put(JSONObject().apply {
            put("key", row.key); put("owner", row.ownerPackage); put("title", row.title); put("visible", row.visible)
        }) }
    })
}.toString()

fun decodePreferences(value: String?): LauncherPreferences {
    if (value == null) return LauncherPreferences()
    val root = JSONObject(value)
    require(root.getInt("version") == 1) { "Unsupported preference version" }
    val apps = root.getJSONArray("apps")
    val lists = root.getJSONArray("lists")
    return LauncherPreferences(
        List(apps.length()) { apps.getString(it) }.distinct(),
        List(lists.length()) { index ->
            val row = lists.getJSONObject(index)
            SavedList(row.getString("key"), row.getString("owner"), row.getString("title"), row.getBoolean("visible"))
        }.distinctBy { it.key },
        showApps = root.optBoolean("showApps", true),
        appsLayout = AppsLayout.entries.firstOrNull { it.name == root.optString("appsLayout") } ?: AppsLayout.ROW,
        background = BackgroundTheme.entries.firstOrNull { it.name == root.optString("background") } ?: BackgroundTheme.MIDNIGHT,
    )
}
