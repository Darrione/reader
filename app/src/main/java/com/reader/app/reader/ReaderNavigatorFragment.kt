package com.reader.app.reader

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.reader.app.R
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

private const val NAVIGATOR_TAG = "epub_navigator"
private const val TTS_DECORATION_GROUP = "tts-highlight"

/**
 * Hosts the Readium [EpubNavigatorFragment] as a child fragment, following the pattern used by
 * the Readium Test App (a fragment factory has to be installed on the child [androidx.fragment.app.FragmentManager]
 * before the navigator fragment is created). It is embedded into the Compose reader screen via
 * `androidx.fragment.compose.AndroidFragment`.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderNavigatorFragment : Fragment() {

    private val viewModel: ReaderViewModel by activityViewModels()

    lateinit var navigator: EpubNavigatorFragment
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val state = viewModel.uiState.value as? ReaderUiState.Ready
        if (state != null) {
            childFragmentManager.fragmentFactory = state.navigatorFactory.createFragmentFactory(
                initialLocator = state.initialLocator,
                // Dark background / light text for the book content itself, independent of the
                // app chrome theme - easier on the eyes and battery for reading.
                initialPreferences = EpubPreferences(theme = Theme.DARK),
                listener = viewModel,
                configuration = EpubNavigatorFragment.Configuration()
            )
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val root = FrameLayout(requireContext()).apply { id = R.id.epub_navigator_container }

        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.epub_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment

        return root
    }

    @OptIn(FlowPreview::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    navigator.currentLocator
                        .sample(3_000)
                        .collect { locator -> viewModel.saveProgression(locator) }
                }
                launch {
                    viewModel.tts?.highlightLocator?.filterNotNull()?.collect { locator ->
                        navigator.go(locator, animated = false)
                        (navigator as? DecorableNavigator)?.applyDecorations(
                            listOf(
                                Decoration(
                                    id = "current-sentence",
                                    locator = locator,
                                    style = Decoration.Style.Highlight(tint = Color.YELLOW)
                                )
                            ),
                            TTS_DECORATION_GROUP
                        )
                    }
                }
                launch {
                    viewModel.navigationRequests.collect { locator ->
                        navigator.go(locator, animated = true)
                    }
                }
                launch {
                    viewModel.ttsStartRequests.collect {
                        val startLocator = (navigator as? VisualNavigator)?.firstVisibleElementLocator()
                        viewModel.tts?.start(startLocator)
                    }
                }
            }
        }
    }
}
