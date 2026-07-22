package com.reader.app.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import com.reader.app.R
import com.reader.app.data.repository.MAX_TTS_SPEED
import com.reader.app.data.repository.MIN_TTS_SPEED
import com.reader.app.reader.tts.TtsController
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var tocOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val tts = (uiState as? ReaderUiState.Ready)?.let { viewModel.tts }

    LaunchedEffect(tts) {
        tts?.events?.collect { event ->
            when (event) {
                is TtsController.Event.Error ->
                    snackbarHostState.showSnackbar(event.message)
                is TtsController.Event.MissingVoiceData -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.reader_voice_missing_title),
                        actionLabel = context.getString(R.string.reader_voice_missing_action)
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        tts.requestInstallVoice(context, event.language)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text((uiState as? ReaderUiState.Ready)?.publication?.metadata?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (uiState is ReaderUiState.Ready) {
                        IconButton(onClick = { tocOpen = true }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.reader_toc))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (tts != null) {
                TtsControlBar(
                    tts = tts,
                    onPlay = {
                        if (!tts.isActive.value) {
                            // No paused session to resume: start fresh from the first sentence
                            // visible on screen right now (resolved by the fragment, which is
                            // the only one that knows what's currently on screen).
                            viewModel.requestTtsStart()
                        } else {
                            tts.play()
                        }
                    },
                    onSpeedChange = { viewModel.setTtsSpeed(it) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val state = uiState
            when (state) {
                is ReaderUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ReaderUiState.Error ->
                    Text(
                        text = stringResource(R.string.reader_open_error),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                is ReaderUiState.Ready ->
                    AndroidFragment<ReaderNavigatorFragment>(modifier = Modifier.fillMaxSize())
            }

            if (tocOpen && state is ReaderUiState.Ready) {
                ModalBottomSheet(onDismissRequest = { tocOpen = false }) {
                    TableOfContents(
                        publication = state.publication,
                        onSelect = { link ->
                            state.publication.locatorFromLink(link)?.let { viewModel.goTo(it) }
                            tocOpen = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun TableOfContents(
    publication: Publication,
    onSelect: (Link) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(publication.tableOfContents) { link ->
            ListItem(
                headlineContent = { Text(link.title ?: link.href.toString()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(link) }
            )
        }
    }
}

@Composable
private fun TtsControlBar(
    tts: TtsController,
    onPlay: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val isPlaying by tts.isPlaying.collectAsState()
    val speed by tts.speed.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { tts.previousSentence() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.reader_previous_sentence))
            }
            FilledIconButton(onClick = {
                if (isPlaying) tts.pause() else onPlay()
            }) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.reader_pause else R.string.reader_play)
                )
            }
            IconButton(onClick = { tts.nextSentence() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.reader_next_sentence))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%.1fx".format(speed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = MIN_TTS_SPEED..MAX_TTS_SPEED,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
