package com.theveloper.pixelplay.data.network.coverartarchive

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for the Cover Art Archive API.
 * Fallback source for album cover art when fanart.tv has no results.
 * Free, no API key required.
 *
 * Base URL: https://coverartarchive.org/
 */
interface CoverArtArchiveApiService {

    /**
     * Get cover art images for a MusicBrainz release.
     * @param mbid MusicBrainz release ID
     * @return Response containing list of cover images
     */
    @GET("release/{mbid}")
    suspend fun getCovers(
        @Path("mbid") mbid: String
    ): CAAResponse
}

data class CAAResponse(
    val images: List<CAAImage>
)

data class CAAImage(
    val image: String,
    val front: Boolean,
    val approved: Boolean,
    val thumbnails: CAAThumbnails
)

data class CAAThumbnails(
    @SerializedName("1200") val large: String?,
    val small: String?
)
