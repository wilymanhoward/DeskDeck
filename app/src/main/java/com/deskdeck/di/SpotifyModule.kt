package com.deskdeck.di

import com.deskdeck.data.db.DeskDeckDatabase
import com.deskdeck.data.db.PlaylistDao
import com.deskdeck.data.spotify.SpotifyAccountsApi
import com.deskdeck.data.spotify.SpotifyApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object SpotifyModule {

    private val moshi: Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideSpotifyApi(client: OkHttpClient): SpotifyApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyApi::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAccountsApi(client: OkHttpClient): SpotifyAccountsApi = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyAccountsApi::class.java)

    @Provides
    fun providePlaylistDao(db: DeskDeckDatabase): PlaylistDao = db.playlistDao()
}
