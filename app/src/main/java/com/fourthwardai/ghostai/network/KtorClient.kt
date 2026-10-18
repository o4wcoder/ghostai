package com.fourthwardai.ghostai.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Mark a request with this attribute to keep it (and its response) out of the HTTP body log, e.g. image uploads. */
val SkipHttpLogging = AttributeKey<Unit>("SkipHttpLogging")

fun ktorHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }

        install(Logging) {
            logger = KtorLogger()
            level = LogLevel.BODY
            filter { request -> !request.attributes.contains(SkipHttpLogging) }
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
