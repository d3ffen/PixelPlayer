package com.theveloper.pixelplay.data.network.fanart

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class FanartMusicResponse(
    @SerializedName("mbid_id") val mbid: String? = null,
    @SerializedName("artistbackground") val artistBackgrounds: List<FanartImage>? = null,
    @SerializedName("albums") val albums: Map<String, FanartAlbum>? = null
)

data class FanartAlbum(
    @SerializedName("albumcover") val albumCovers: List<FanartImage>? = null,
    @SerializedName("cdart") val cdArts: List<FanartImage>? = null
)

data class FanartImage(
    @SerializedName("id") val id: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("likes") val likes: String = "0",
    @SerializedName("lang") val lang: String = ""
)

/**
 * Fanart.tv API service for high-quality album artwork.
 * Requires a personal API key (free for personal use): https://fanart.tv/get-an-api-key/
 * Add FANART_TV_API_KEY=your_key to local.properties.
 * Base URL: https://webservice.fanart.tv/v3/
 */
interface FanartTvApiService {

    @GET("music/{mbid}")
    suspend fun getMusicArt(
        @Path("mbid") mbid: String,
        @Query("api_key") apiKey: String
    ): FanartMusicResponse
}
