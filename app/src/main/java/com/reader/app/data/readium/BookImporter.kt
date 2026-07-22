package com.reader.app.data.readium

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.reader.app.data.db.BookEntity
import com.reader.app.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener

/**
 * Copies a picked EPUB into the app's private storage and registers it in the library.
 *
 * The file is copied (instead of keeping the picker's content:// [Uri]) so the book remains
 * readable even if the original document is moved, renamed or the grant is revoked.
 */
@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    private val bookRepository: BookRepository,
) {

    suspend fun import(sourceUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val bookFile = File(booksDir, "${UUID.randomUUID()}.epub")

        try {
            copyTo(sourceUri, bookFile)

            val url = bookFile.toUrl(isDirectory = false)
            val asset = assetRetriever.retrieve(url)
                .getOrElse { error ->
                    bookFile.delete()
                    return@withContext Result.failure(Exception(error.message))
                }

            val publication = publicationOpener.open(asset, allowUserInteraction = false)
                .getOrElse { error ->
                    bookFile.delete()
                    return@withContext Result.failure(Exception(error.message))
                }

            val bookId = try {
                registerBook(bookFile, publication)
            } finally {
                publication.close()
            }

            Result.success(bookId)
        } catch (e: Exception) {
            bookFile.delete()
            Result.failure(e)
        }
    }

    private suspend fun registerBook(bookFile: File, publication: Publication): Long {
        val title = publication.metadata.title ?: bookFile.nameWithoutExtension
        val author = publication.metadata.authors
            .joinToString(", ") { it.name }
            .ifBlank { null }

        val bookId = bookRepository.insert(
            BookEntity(
                title = title,
                author = author,
                filePath = bookFile.absolutePath,
                addedAt = System.currentTimeMillis()
            )
        )

        val cover: Bitmap? = publication.cover()
        if (cover != null) {
            val coverFile = File(File(context.filesDir, "covers").apply { mkdirs() }, "$bookId.png")
            FileOutputStream(coverFile).use { out ->
                cover.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bookRepository.updateCover(bookId, coverFile.absolutePath)
        }

        return bookId
    }

    private fun copyTo(sourceUri: Uri, destination: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Cannot open $sourceUri")
    }
}
