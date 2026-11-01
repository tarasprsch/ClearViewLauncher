package dev.personal.tvlauncher

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.personal.tvlauncher.apps.AppsRepository
import dev.personal.tvlauncher.apps.ContentLauncher
import dev.personal.tvlauncher.channels.ChannelRepository
import dev.personal.tvlauncher.settings.PreferencesRepository

class LauncherApplication : Application(), ImageLoaderFactory {
    val apps by lazy { AppsRepository(this) }
    val channels by lazy { ChannelRepository(this) }
    val preferences by lazy { PreferencesRepository(this) }
    val launcher by lazy { ContentLauncher(this) }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(24 * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("artwork")).maxSizeBytes(48L * 1024 * 1024).build() }
        .crossfade(false)
        .build()
}
