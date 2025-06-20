package com.example.ghostai.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class VoicesResponse(
    val voices: List<Voice>,
)

@Serializable
data class Voice(
    val voice_id: String,
    val name: String,
    val category: String,
    val labels: Map<String, String>? = null,
    val samples: JsonElement? = null,
)
