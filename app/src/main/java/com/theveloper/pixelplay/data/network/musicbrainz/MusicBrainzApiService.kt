package com.theveloper.pixelplay.data.network.musicbrainz

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the MusicBrainz API.
 * Provides album metadata, track listings, and MBIDs needed for cover art lookups.
 * Free, no API key required. Strict 1 request/second rate limit enforced by
 * [MusicBrainzThrottleInterceptor].
 *
 * Base URL: https://musicbrainz.org/ws/2/
 */
interface MusicBrainzApiService {

    /**
     * Search for a release by album name and artist.
     * @param query Lucene query, e.g. 'release:"Thriller" AND artist:"Michael Jackson"'
     * @param format Response format (json)
     * @param limit Maximum results
     * @param includes Related entities to include in the response
     */
    @GET("release")
    suspend fun searchRelease(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("inc") includes: String = "recordings+artist-credits+labels"
    ): MBReleaseSearchResponse

    /**
     * Get full release details by MusicBrainz release ID.
     * @param mbid MusicBrainz release ID
     * @param format Response format (json)
     * @param includes Related entities to include
     */
    @GET("release/{mbid}")
    suspend fun getRelease(
        @Path("mbid") mbid: String,
        @Query("fmt") format: String = "json",
        @Query("inc") includes: String = "recordings+artist-credits+labels+release-groups"
    ): MBRelease
}

data class MBReleaseSearchResponse(
    val releases: List<MBRelease>
)

data class MBRelease(
    val id: String,
    val title: String,
    val date: String?,
    val country: String?,
    @SerializedName("artist-credit") val artistCredit: List<MBArtistCredit>?,
    val media: List<MBMedium>?,
    @SerializedName("release-group") val releaseGroup: MBReleaseGroup?
)

data class MBArtistCredit(
    val artist: MBArtist,
    val name: String
)

data class MBArtist(
    val id: String,
    val name: String
)

data class MBMedium(
    val tracks: List<MBTrack>?
)

data class MBTrack(
    val number: String,
    val title: String,
    val recording: MBRecording?
)

data class MBRecording(
    val id: String,
    val length: Int?
)

data class MBReleaseGroup(
    val id: String,
    @SerializedName("primary-type") val primaryType: String?
)
