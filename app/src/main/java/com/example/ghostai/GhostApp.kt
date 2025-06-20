package com.example.ghostai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class GhostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // if (com.example.ghostai.BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
        // }
    }
}
