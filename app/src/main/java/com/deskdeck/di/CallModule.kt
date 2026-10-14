package com.deskdeck.di

import com.deskdeck.media.call.CallSource
import com.deskdeck.media.call.WhatsAppCallSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CallModule {

    /** Apps whose calls DeskDeck follows. Add Telegram, Google Meet, … here as new [CallSource]s. */
    @Provides
    fun provideCallSources(whatsApp: WhatsAppCallSource): List<CallSource> = listOf(whatsApp)
}
