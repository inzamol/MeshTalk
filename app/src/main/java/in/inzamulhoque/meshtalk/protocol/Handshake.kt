package `in`.inzamulhoque.meshtalk.protocol

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Handshake(
    @Json(name = "p") val peerId: String,
    @Json(name = "e") val encryptionKey: String,
    @Json(name = "n") val displayName: String,
    @Json(name = "i") val inventory: List<String>,
    @Json(name = "b") val bio: String? = null,
    @Json(name = "a") val avatarBase64: String? = null
)
