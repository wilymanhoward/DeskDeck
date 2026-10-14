package com.deskdeck.di

import com.deskdeck.data.brief.OpenMeteoApi
import com.deskdeck.data.brief.OpenMeteoGeocodingApi
import com.deskdeck.data.brief.TwelveDataApi
import com.deskdeck.data.db.BriefDao
import com.deskdeck.data.db.DeskDeckDatabase
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
object BriefModule {

    private val moshi: Moshi = Moshi.Builder().build()

    private inline fun <reified T> api(client: OkHttpClient, baseUrl: String): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(T::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteo(client: OkHttpClient): OpenMeteoApi = api(client, "https://api.open-meteo.com/")

    @Provides
    @Singleton
    fun provideGeocoding(client: OkHttpClient): OpenMeteoGeocodingApi = api(client, "https://geocoding-api.open-meteo.com/")

    @Provides
    @Singleton
    fun provideTwelveData(client: OkHttpClient): TwelveDataApi = api(client, "https://api.twelvedata.com/")

    @Provides
    fun provideBriefDao(db: DeskDeckDatabase): BriefDao = db.briefDao()
}
