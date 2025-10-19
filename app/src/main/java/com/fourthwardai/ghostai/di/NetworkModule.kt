package com.fourthwardai.ghostai.di

import android.app.Application
import com.fourthwardai.ghostai.BuildConfig
import com.fourthwardai.ghostai.network.ktorHttpClient
import com.fourthwardai.ghostai.service.ElevenLabsService
import com.fourthwardai.ghostai.service.HueLightService
import com.fourthwardai.ghostai.service.OpenAIService
import com.fourthwardai.ghostai.service.TtsPreferenceService
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
        application: Application,
    ): OpenAIService {
        return OpenAIService(
            apiKey = BuildConfig.OPENAI_API_KEY,
            client = client,
            application = application,
        )
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

    @Provides
    @Singleton
    fun provideHueLightService(
        client: HttpClient,
    ): HueLightService = HueLightService(
        client = client,
        bridgeIp = BuildConfig.HUE_BRIDGE_IP,
        username = BuildConfig.HUE_USERNAME,
    )

    @Provides
    @Singleton
    fun provideTtsPreferenceService(application: Application): TtsPreferenceService {
        return TtsPreferenceService(context = application.applicationContext)
    }
}
