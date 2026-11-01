package dev.personal.tvlauncher.apps

import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

data class TvApp(val component: ComponentName, val title: String, val icon: Drawable?, val banner: Drawable? = null) {
    val key: String get() = component.flattenToString()
}

class AppsRepository(private val context: Context) {
    private val refreshes = MutableStateFlow(0L)
    fun refresh() { refreshes.update { it + 1 } }

    fun observeApps(): Flow<List<TvApp>> {
        val packages = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            awaitClose { context.unregisterReceiver(receiver) }
        }
        return merge(refreshes.map { Unit }, packages).conflate().map {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(0),
            ).mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                if (activity.packageName == context.packageName || !activity.enabled || !activity.exported) return@mapNotNull null
                TvApp(
                    ComponentName(activity.packageName, activity.name),
                    resolved.loadLabel(context.packageManager).toString(),
                    try { resolved.loadIcon(context.packageManager) } catch (_: Exception) { null },
                    try { activity.loadBanner(context.packageManager) } catch (_: Exception) { null },
                )
            }.distinctBy { it.key }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }.flowOn(Dispatchers.IO)
    }
}
