package com.example.ghostai.di

import android.app.Application
import com.example.ghostai.BuildConfig
import com.example.ghostai.network.ktorHttpClient
import com.example.ghostai.service.ElevenLabsService
import com.example.ghostai.service.OpenAIService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = ktorHttpClient()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun provideOpenAIService(
        client: HttpClient,
    ): OpenAIService {
        return OpenAIService(BuildConfig.OPENAI_API_KEY, client)
    }

    @Provides
    @Singleton
    fun provideElevenLabsService(
        application: Application,
        client: HttpClient,
        okHttpClient: OkHttpClient,
    ): ElevenLabsService {
        return ElevenLabsService(
            apiKey = BuildConfig.ELEVEN_LABS_API_KEY,
            client = client,
            okHttpClient = okHttpClient,
            application = application,
        )
    }
}
