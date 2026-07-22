package com.reader.app.reader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.reader.app.data.repository.SettingsRepository
import com.reader.app.player.TtsServiceConnector
import com.reader.app.ui.theme.ReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReaderActivity : FragmentActivity() {

    @Inject lateinit var readerRepository: ReaderRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var ttsServiceConnector: TtsServiceConnector

    private val bookId: Long by lazy { intent.getLongExtra(EXTRA_BOOK_ID, -1L) }

    private val viewModel: ReaderViewModel by viewModels {
        ReaderViewModel.Factory(bookId, readerRepository, settingsRepository, ttsServiceConnector)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReaderTheme {
                ReaderScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
    }
}
