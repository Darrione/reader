package com.reader.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/**
 * The Readium objects are stateless and expensive to recreate, so they are shared app-wide.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = DefaultHttpClient()

    @Provides
    @Singleton
    fun provideAssetRetriever(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
    ): AssetRetriever = AssetRetriever(context.contentResolver, httpClient)

    @Provides
    @Singleton
    fun providePublicationOpener(
        @ApplicationContext context: Context,
        httpClient: HttpClient,
        assetRetriever: AssetRetriever,
    ): PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null
        )
    )
}
