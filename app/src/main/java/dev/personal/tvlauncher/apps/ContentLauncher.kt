package dev.personal.tvlauncher.apps

import dev.personal.tvlauncher.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException

sealed interface LaunchResult {
    data object Opened : LaunchResult
    data class Failed(val message: Int) : LaunchResult
}

fun contentIntent(owner: String, uri: String?): Intent? {
    if (owner.isBlank() || uri.isNullOrBlank()) return null
    return try {
        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        if (intent.component?.packageName?.let { it != owner } == true) return null
        if (intent.`package`?.let { it != owner } == true) return null
        if (intent.selector != null) return null
        val scheme = intent.data?.scheme
        if (scheme == null && intent.component == null) return null
        if (scheme?.lowercase() in setOf("javascript", "file", "data")) return null
        intent.setPackage(owner).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } catch (_: Exception) { null }
}

class ContentLauncher(private val context: Context) {
    fun app(component: ComponentName): LaunchResult = start(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    fun source(owner: String): LaunchResult {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(owner)
            ?: return LaunchResult.Failed(R.string.error_source_unavailable)
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun channel(owner: String, uri: String?): LaunchResult {
        val intent = contentIntent(owner, uri)
            ?: return LaunchResult.Failed(R.string.error_no_content_link)
        return start(intent)
    }
    fun canOpen(owner: String, uri: String?): Boolean = contentIntent(owner, uri)?.let {
        context.packageManager.resolveActivity(it, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) != null
    } ?: false

    private fun start(intent: Intent): LaunchResult = try {
        if (context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY) == null) {
            LaunchResult.Failed(R.string.error_no_activity)
        } else {
            context.startActivity(intent)
            LaunchResult.Opened
        }
    } catch (_: ActivityNotFoundException) {
        LaunchResult.Failed(R.string.error_activity_unavailable)
    } catch (_: SecurityException) {
        LaunchResult.Failed(R.string.error_source_denied)
    } catch (_: IllegalArgumentException) {
        LaunchResult.Failed(R.string.error_invalid_link)
    }
}
