package com.example.ghostai.di

import com.example.ghostai.BuildConfig
import com.example.ghostai.network.ktorHttpClient
import com.example.ghostai.service.ElevenLabsService
import com.example.ghostai.service.OpenAIService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = ktorHttpClient()

    @Provides
    @Singleton
    fun provideOpenAIService(): OpenAIService {
        return OpenAIService(BuildConfig.OPENAI_API_KEY, ktorHttpClient())
    }

    @Provides
    @Singleton
    fun provideElevenLabsService(): ElevenLabsService {
        return ElevenLabsService(BuildConfig.ELEVEN_LABS_API_KEY, ktorHttpClient())
    }
}
