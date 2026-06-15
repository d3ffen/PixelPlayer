package com.theveloper.pixelplay.data.network.coverartarchive

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

data class CAAResponse(
    @SerializedName("images") val images: List<CAAImage> = emptyList(),
    @SerializedName("release") val release: String = ""
)

data class CAAImage(
    @SerializedName("image") val image: String = "",
    @SerializedName("front") val front: Boolean = false,
    @SerializedName("back") val back: Boolean = false,
    @SerializedName("approved") val approved: Boolean = false,
    @SerializedName("thumbnails") val thumbnails: CAAThumbnails = CAAThumbnails()
)

data class CAAThumbnails(
    @SerializedName("1200") val large: String? = null,
    @SerializedName("500") val medium: String? = null,
    @SerializedName("250") val small: String? = null
)

/**
 * Cover Art Archive API — free, no key required.
 * Base URL: https://coverartarchive.org/
 */
interface CoverArtArchiveApiService {

    @GET("release/{mbid}")
    suspend fun getCovers(
        @Path("mbid") mbid: String
    ): CAAResponse
}
