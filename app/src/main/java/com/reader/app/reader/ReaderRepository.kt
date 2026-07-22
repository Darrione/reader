package com.reader.app.reader

import android.app.Application
import com.reader.app.data.repository.BookRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener

/** A publication opened for reading, along with everything needed to render and read it aloud. */
@OptIn(ExperimentalReadiumApi::class)
data class OpenBook(
    val bookId: Long,
    val publication: Publication,
    val navigatorFactory: EpubNavigatorFactory,
    val ttsNavigatorFactory: AndroidTtsNavigatorFactory?,
    val initialLocator: Locator?,
)

/**
 * Opens a book from the library for reading. Unlike the Readium Test App, this app only ever
 * has one book open for reading at a time (the current [com.reader.app.reader.ReaderActivity]),
 * so no cross-activity publication cache is needed here.
 */
@Singleton
class ReaderRepository @Inject constructor(
    private val application: Application,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    private val bookRepository: BookRepository,
) {

    suspend fun open(bookId: Long): Result<OpenBook> {
        val book = bookRepository.getById(bookId)
            ?: return Result.failure(Exception("Book $bookId not found in the library."))

        val url = File(book.filePath).toUrl(isDirectory = false)

        val asset = assetRetriever.retrieve(url)
            .getOrElse { return Result.failure(Exception(it.message)) }

        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse { return Result.failure(Exception(it.message)) }

        val initialLocator = book.progressionJson?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }

        val navigatorFactory = EpubNavigatorFactory(publication)
        val ttsNavigatorFactory = AndroidTtsNavigatorFactory(application, publication)

        return Result.success(
            OpenBook(bookId, publication, navigatorFactory, ttsNavigatorFactory, initialLocator)
        )
    }

    suspend fun saveProgression(bookId: Long, locator: Locator) {
        bookRepository.saveProgression(bookId, locator.toJSON().toString())
    }
}
