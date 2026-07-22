package com.reader.app.player

import android.app.Application
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.readium.navigator.media.common.Media3Adapter
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Binds/starts [TtsPlaybackService] on demand and exposes its current playback session.
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class TtsServiceConnector @Inject constructor(
    private val application: Application,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()

    private var binder: TtsPlaybackService.Binder? = null
    private var bindingJob: Job? = null

    private val sessionMutable = MutableStateFlow<TtsPlaybackService.Session?>(null)
    val session: StateFlow<TtsPlaybackService.Session?> = sessionMutable.asStateFlow()

    suspend fun <N> openSession(
        bookId: Long,
        navigator: N,
    ) where N : AnyMediaNavigator, N : Media3Adapter {
        mutex.withLock {
            TtsPlaybackService.start(application)
            val boundBinder = try {
                TtsPlaybackService.bind(application)
            } catch (e: Exception) {
                TtsPlaybackService.stop(application)
                throw e
            }
            binder = boundBinder
            bindingJob = boundBinder.session
                .onEach { sessionMutable.value = it }
                .launchIn(coroutineScope)
            boundBinder.openSession(navigator, bookId)
        }
    }

    fun closeSession() {
        coroutineScope.launch {
            bindingJob?.cancelAndJoin()
            binder?.closeSession()
            binder?.stop()
            sessionMutable.value = null
            binder = null
        }
    }
}
