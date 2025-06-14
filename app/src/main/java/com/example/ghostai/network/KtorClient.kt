package com.example.ghostai.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import timber.log.Timber

fun ktorHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }

        install(Logging) {
            logger = KtorLogger()
            level = LogLevel.BODY
        }

        // Optional default headers
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
}

class KtorLogger() : Logger {
    override fun log(message: String) {
        Timber.d(message)
    }
}

