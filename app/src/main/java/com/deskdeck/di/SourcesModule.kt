package com.deskdeck.di

import com.deskdeck.media.sources.BriefOpenSource
import com.deskdeck.media.sources.MediaBrowserSource
import com.deskdeck.media.sources.MediaButtonSource
import com.deskdeck.media.sources.PlaybackSource
import com.deskdeck.media.sources.SessionKeySource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SourcesModule {

    /** The resume ladder, tried in this order. Add new [PlaybackSource]s (e.g. Spotify Connect) here. */
    @Provides
    fun providePlaybackSources(
        sessionKey: SessionKeySource,
        mediaButton: MediaButtonSource,
        mediaBrowser: MediaBrowserSource,
        briefOpen: BriefOpenSource,
    ): List<PlaybackSource> = listOf(sessionKey, mediaButton, mediaBrowser, briefOpen)
}
