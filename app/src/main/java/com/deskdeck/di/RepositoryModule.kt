package com.deskdeck.di

import com.deskdeck.data.lyrics.DefaultLyricsRepository
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.media.MediaRepository
import com.deskdeck.media.MediaSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMediaRepository(impl: MediaSessionRepository): MediaRepository

    @Binds
    abstract fun bindLyricsRepository(impl: DefaultLyricsRepository): LyricsRepository
}
