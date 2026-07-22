package com.reader.app.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.app.data.db.BookEntity
import com.reader.app.data.readium.BookImporter
import com.reader.app.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookImporter: BookImporter,
) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = bookRepository.books()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors: Flow<String> = _errors.receiveAsFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            bookImporter.import(uri)
                .onFailure { error -> _errors.trySend(error.message ?: "Import se nezdařil.") }
            _importing.value = false
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.delete(book)
            File(book.filePath).delete()
            book.coverPath?.let { File(it).delete() }
        }
    }
}
