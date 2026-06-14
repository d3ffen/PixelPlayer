package com.theveloper.pixelplay.data.network.fanart

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the fanart.tv API.
 * Provides high-quality album cover art and artist images.
 * Requires an API key from https://fanart.tv/get-an-api-key/.
 *
 * Base URL: https://webservice.fanart.tv/v3/
 */
interface FanartTvApiService {

    /**
     * Fetch album artwork for a MusicBrainz release MBID.
     * @param mbid MusicBrainz release ID
     * @param apiKey fanart.tv API key
     * @return Response containing album cover images sorted by community likes
     */
    @GET("music/{mbid}")
    suspend fun getAlbumArt(
        @Path("mbid") mbid: String,
        @Query("api_key") apiKey: String
    ): FanartAlbumResponse
}

data class FanartAlbumResponse(
    @SerializedName("albums") val albums: Map<String, FanartAlbum>?
)

data class FanartAlbum(
    @SerializedName("albumcover") val albumCovers: List<FanartImage>?
)

data class FanartImage(
    val url: String,
    val likes: String
)
