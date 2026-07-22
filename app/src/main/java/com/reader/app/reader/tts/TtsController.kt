package com.reader.app.reader.tts

import android.content.Context
import com.reader.app.data.repository.DEFAULT_TTS_LANGUAGE
import com.reader.app.data.repository.MAX_TTS_SPEED
import com.reader.app.data.repository.MIN_TTS_SPEED
import com.reader.app.player.TtsServiceConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Language
import org.readium.r2.shared.util.getOrElse

/**
 * Reads a publication aloud sentence by sentence, using the Readium TTS navigator.
 *
 * This is a plain component owned by [com.reader.app.reader.ReaderViewModel], not an Android
 * ViewModel of its own: it shares the reader's [viewModelScope] and lifecycle.
 */
@OptIn(ExperimentalReadiumApi::class)
class TtsController(
    private val viewModelScope: CoroutineScope,
    private val bookId: Long,
    private val ttsNavigatorFactory: AndroidTtsNavigatorFactory,
    private val serviceConnector: TtsServiceConnector,
    initialSpeed: Float,
) : TtsNavigator.Listener {

    sealed class Event {
        data class Error(val message: String) : Event()
        data class MissingVoiceData(val language: Language) : Event()
    }

    private val navigatorNow: AndroidTtsNavigator?
        get() = serviceConnector.session.value?.navigator as? AndroidTtsNavigator

    private var launchJob: Job? = null

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private val _speed = MutableStateFlow(initialSpeed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED))
    val speed: StateFlow<Float> = _speed

    val isActive: StateFlow<Boolean> = serviceConnector.session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isPlaying: StateFlow<Boolean> = serviceConnector.session
        .flatMapLatest { session ->
            session?.navigator?.playback?.map { it.playWhenReady } ?: flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Locator of the sentence currently being spoken, used to highlight and scroll to it. */
    val highlightLocator: StateFlow<Locator?> = serviceConnector.session
        .flatMapLatest { session ->
            (session?.navigator as? AndroidTtsNavigator)?.location?.map { it.utteranceLocator }
                ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        serviceConnector.session
            .flatMapLatest { it?.navigator?.playback ?: flowOf(null) }
            .onEach { playback ->
                when (val state = playback?.state as? TtsNavigator.State) {
                    null, TtsNavigator.State.Ready -> Unit
                    is TtsNavigator.State.Ended -> stop()
                    is TtsNavigator.State.Failure -> onPlaybackError(state.error)
                }
            }
            .launchIn(viewModelScope)
    }

    fun start(initialLocator: Locator?) {
        if (launchJob != null) return
        launchJob = viewModelScope.launch { openSession(initialLocator) }
    }

    private suspend fun openSession(initialLocator: Locator?) {
        val navigator = ttsNavigatorFactory.createNavigator(
            listener = this,
            initialLocator = initialLocator,
            initialPreferences = currentPreferences()
        ).getOrElse {
            _events.trySend(Event.Error(it.toString()))
            launchJob = null
            return
        }

        try {
            serviceConnector.openSession(bookId, navigator)
        } catch (e: Exception) {
            navigator.close()
            _events.trySend(Event.Error(e.message ?: "Nepodařilo se spustit přehrávání."))
            launchJob = null
            return
        }

        navigator.play()
    }

    fun stop() {
        launchJob = null
        serviceConnector.closeSession()
    }

    fun play() {
        navigatorNow?.play()
    }

    fun pause() {
        navigatorNow?.pause()
    }

    fun previousSentence() {
        navigatorNow?.skipToPreviousUtterance()
    }

    fun nextSentence() {
        navigatorNow?.skipToNextUtterance()
    }

    fun setSpeed(newSpeed: Float) {
        _speed.value = newSpeed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
        navigatorNow?.submitPreferences(currentPreferences())
    }

    fun requestInstallVoice(context: Context, language: Language) {
        AndroidTtsEngine.requestInstallVoice(context)
    }

    private fun currentPreferences() = AndroidTtsPreferences(
        language = Language(DEFAULT_TTS_LANGUAGE),
        speed = _speed.value.toDouble()
    )

    override fun onStopRequested() {
        stop()
    }

    private fun onPlaybackError(error: TtsNavigator.Error) {
        val event = when (error) {
            is TtsNavigator.Error.ContentError ->
                Event.Error(error.toString())
            is TtsNavigator.Error.EngineError<*> -> {
                when (val engineError = error.cause as? AndroidTtsEngine.Error) {
                    is AndroidTtsEngine.Error.LanguageMissingData ->
                        Event.MissingVoiceData(engineError.language)
                    else ->
                        Event.Error(engineError?.toString() ?: error.toString())
                }
            }
        }
        viewModelScope.launch { _events.send(event) }
    }
}
