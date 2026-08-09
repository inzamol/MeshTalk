package `in`.inzamulhoque.meshtalk.util.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GithubRelease(
    @Json(name = "tag_name") val tagName: String,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "body") val body: String,
    @Json(name = "assets") val assets: List<GithubAsset>
)

@JsonClass(generateAdapter = true)
data class GithubAsset(
    @Json(name = "name") val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    @Json(name = "size") val size: Long
)
