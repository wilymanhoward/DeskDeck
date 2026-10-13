package com.deskdeck.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.deskdeck.BuildConfig
import com.deskdeck.data.db.DeskDeckDatabase
import com.deskdeck.data.db.LyricsDao
import com.deskdeck.data.lyrics.LyricsProvider
import com.deskdeck.data.lyrics.lrclib.LrclibApi
import com.deskdeck.data.lyrics.lrclib.LrclibProvider
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // LRCLIB asks clients to identify themselves.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "DeskDeck/${BuildConfig.VERSION_NAME} (Android desk music dashboard)")
                    .build(),
            )
        }
        .build()

    @Provides
    @Singleton
    fun provideLrclibApi(client: OkHttpClient): LrclibApi = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(LrclibApi::class.java)

    /** Providers are tried in order; add new [LyricsProvider]s here. */
    @Provides
    fun provideLyricsProviders(lrclib: LrclibProvider): List<LyricsProvider> = listOf(lrclib)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeskDeckDatabase =
        Room.databaseBuilder(context, DeskDeckDatabase::class.java, "deskdeck.db").build()

    @Provides
    fun provideLyricsDao(db: DeskDeckDatabase): LyricsDao = db.lyricsDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
