package dev.personal.tvlauncher.channels

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

class ChannelRepository(private val context: Context) {
    private val refreshes = MutableStateFlow(0L)
    private val reader = PreviewReader { uri -> context.contentResolver.query(uri, null, null, null, null) }

    fun refresh() { refreshes.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observeRows(): Flow<ChannelLoadState> {
        val changes = callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { trySend(Unit) }
            }
            var registered = false
            try {
                context.contentResolver.registerContentObserver(android.net.Uri.parse("content://android.media.tv"), true, observer)
                registered = true
            } catch (_: SecurityException) { /* Read below reports denied access. */ }
            awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
        }
        return merge(refreshes.map { Unit }, changes)
            .debounce(180)
            .map { reader.read(context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED) }
            .onStart { emit(ChannelLoadState.Loading) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }
}
