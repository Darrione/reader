package com.reader.app.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reader.app.data.repository.SettingsRepository
import com.reader.app.player.TtsServiceConnector
import com.reader.app.reader.tts.TtsController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

private const val TAG = "ReaderViewModel"

sealed class ReaderUiState {
    data object Loading : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
    data class Ready(
        val publication: Publication,
        val navigatorFactory: EpubNavigatorFactory,
        val initialLocator: Locator?,
    ) : ReaderUiState()
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    private val bookId: Long,
    private val readerRepository: ReaderRepository,
    private val settingsRepository: SettingsRepository,
    ttsServiceConnector: TtsServiceConnector,
) : ViewModel(), EpubNavigatorFragment.Listener {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState

    private var openBook: OpenBook? = null

    var tts: TtsController? = null
        private set

    private val _navigationRequests = Channel<Locator>(Channel.BUFFERED)

    /** Locators requested from the UI (e.g. tapping a table of contents entry). */
    val navigationRequests: Flow<Locator> = _navigationRequests.receiveAsFlow()

    fun goTo(locator: Locator) {
        _navigationRequests.trySend(locator)
    }

    private val _ttsStartRequests = Channel<Unit>(Channel.CONFLATED)

    /**
     * Emitted when the UI wants to start TTS from scratch (not resume a paused session). The
     * fragment hosting the visual navigator is responsible for resolving this to "the first
     * sentence on the currently visible page" since only it knows what's on screen.
     */
    val ttsStartRequests: Flow<Unit> = _ttsStartRequests.receiveAsFlow()

    fun requestTtsStart() {
        _ttsStartRequests.trySend(Unit)
    }

    init {
        viewModelScope.launch {
            val initialSpeed = settingsRepository.ttsSpeed.first()

            readerRepository.open(bookId).fold(
                onSuccess = { book ->
                    openBook = book
                    book.ttsNavigatorFactory?.let { factory ->
                        tts = TtsController(viewModelScope, bookId, factory, ttsServiceConnector, initialSpeed)
                    }
                    _uiState.value = ReaderUiState.Ready(book.publication, book.navigatorFactory, book.initialLocator)
                },
                onFailure = { error ->
                    _uiState.value = ReaderUiState.Error(error.message ?: "Nepodařilo se otevřít knihu.")
                }
            )
        }
    }

    fun saveProgression(locator: Locator) {
        viewModelScope.launch { readerRepository.saveProgression(bookId, locator) }
    }

    fun setTtsSpeed(speed: Float) {
        tts?.setSpeed(speed)
        viewModelScope.launch { settingsRepository.setTtsSpeed(speed) }
    }

    override fun onCleared() {
        tts?.stop()
        openBook?.publication?.close()
    }

    // EpubNavigatorFragment.Listener

    override fun onResourceLoadFailed(url: Url, error: ReadError) {
        Log.w(TAG, "Resource load failed for $url: $error")
    }

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // External links are not handled in this MVP.
    }

    override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean = true

    class Factory(
        private val bookId: Long,
        private val readerRepository: ReaderRepository,
        private val settingsRepository: SettingsRepository,
        private val ttsServiceConnector: TtsServiceConnector,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(bookId, readerRepository, settingsRepository, ttsServiceConnector) as T
    }
}
