package com.deskdeck.di

import com.deskdeck.data.fake.FakeLyricsRepository
import com.deskdeck.data.fake.FakeMediaRepository
import com.deskdeck.data.lyrics.LyricsRepository
import com.deskdeck.media.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMediaRepository(impl: FakeMediaRepository): MediaRepository

    @Binds
    abstract fun bindLyricsRepository(impl: FakeLyricsRepository): LyricsRepository
}
