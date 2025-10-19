package com.fourthwardai.ghostai.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

class HueLightService(
    private val client: HttpClient,
    bridgeIp: String = "",
    username: String? = null,
) {
    /** These can be set after construction (e.g., from DataStore) */
    @Volatile var bridgeIp: String = bridgeIp

    @Volatile var username: String? = username

    /** Press the bridge’s link button, then call this. Logs raw response so you can grab in Logcat. */
    suspend fun createUser(devicetype: String = "ghost_ai#android_app"): String {
        check(bridgeIp.isNotBlank()) { "bridgeIp is blank. Set HueLightService.bridgeIp first." }
        val url = "http://$bridgeIp/api"

        val raw: List<CreateUserResponseItem> = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(CreateUserRequest(devicetype))
        }.body()

        // Pretty log so you can copy the username from Logcat
        val pretty = Json { prettyPrint = true }.encodeToString(
            ListSerializer(CreateUserResponseItem.serializer()),
            raw,
        )
        Timber.i("Hue createUser raw response:\n$pretty")

        val first = raw.firstOrNull() ?: error("Empty response from Hue /api")
        first.error?.let { err ->
            error("Hue error ${err.type} @ ${err.address ?: "-"}: ${err.description}")
        }
        val user = first.success?.username ?: error("Hue response had no success.username")
        username = user
        return user
    }

    suspend fun getLights(): Map<String, HueLightInfo> {
        val url = requireUserUrl("/lights")
        return client.get(url).body()
    }

    suspend fun setLightState(
        lightId: String,
        on: Boolean? = null,
        bri: Int? = null,
        hue: Int? = null,
        sat: Int? = null,
        transitionTimeDeci: Int? = null,
    ) {
        val url = requireUserUrl("/lights/$lightId/state")
        val cmd = HueStateCommand(
            on = on,
            bri = bri?.coerceIn(1, 254),
            hue = hue?.coerceIn(0, 65535),
            sat = sat?.coerceIn(0, 254),
            transitionTime = transitionTimeDeci?.coerceAtLeast(0),
        )
        val resp: List<StateResponseItem> = client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(cmd)
        }.body()
        Timber.d("Hue setLightState($lightId) → ${resp.safeSummary()}")
    }

    suspend fun flash(lightId: String, repeating: Boolean = false) {
        val url = requireUserUrl("/lights/$lightId/state")
        val resp: List<StateResponseItem> = client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("alert" to if (repeating) "lselect" else "select"))
        }.body()
        Timber.d("Hue flash($lightId, repeating=$repeating) → ${resp.safeSummary()}")
    }

    suspend fun colorLoop(lightId: String, stop: Boolean = false) {
        val url = requireUserUrl("/lights/$lightId/state")
        val resp: List<StateResponseItem> = client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("effect" to if (stop) "none" else "colorloop"))
        }.body()
        Timber.d("Hue colorLoop($lightId, stop=$stop) → ${resp.safeSummary()}")
    }

    private fun requireUserUrl(path: String): String {
        check(bridgeIp.isNotBlank()) { "bridgeIp is blank. Set HueLightService.bridgeIp first." }
        val u = username ?: error("Hue username not set. Call createUser() or set HueLightService.username.")
        return "http://$bridgeIp/api/$u$path"
    }
}

// ----- models -----
@Serializable private data class CreateUserRequest(val devicetype: String)

@Serializable private data class CreateUserResponseItem(
    val success: SuccessBlock? = null,
    val error: HueError? = null,
)

@Serializable private data class SuccessBlock(val username: String? = null)

@Serializable private data class HueError(val type: Int, val address: String? = null, val description: String)

@Serializable data class HueLightInfo(
    val state: HueState? = null,
    val name: String? = null,
    val type: String? = null,
    val modelid: String? = null,
    val swversion: String? = null,
)

@Serializable data class HueState(
    val on: Boolean? = null,
    val bri: Int? = null,
    val hue: Int? = null,
    val sat: Int? = null,
    val effect: String? = null,
    @SerialName("colormode") val colorMode: String? = null,
    val reachable: Boolean? = null,
)

@Serializable private data class StateResponseItem(
    val success: Map<String, String>? = null,
    val error: HueError? = null,
)

@Serializable
data class HueStateCommand(
    val on: Boolean? = null,
    // 1..254
    val bri: Int? = null,
    // 0..65535
    val hue: Int? = null,
    // 0..254
    val sat: Int? = null,
    // deciseconds
    val transitionTime: Int? = null,
    // "select" | "lselect" | "none"
    val alert: String? = null,
    // "colorloop" | "none"
    val effect: String? = null,
)
private fun List<StateResponseItem>.safeSummary(): String =
    joinToString("; ") { it.error?.let { e -> "error ${e.type}: ${e.description}" } ?: ("ok " + (it.success?.keys?.joinToString(",") ?: "")) }
