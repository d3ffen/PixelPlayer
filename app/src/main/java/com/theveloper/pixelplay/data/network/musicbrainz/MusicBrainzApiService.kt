package com.theveloper.pixelplay.data.network.musicbrainz

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MusicBrainz REST API service.
 * Rate limit: 1 request/second. A throttling interceptor is added at the OkHttpClient level.
 * User-Agent is required — set globally in the shared OkHttpClient (already present in AppModule).
 * Base URL: https://musicbrainz.org/ws/2/
 */
interface MusicBrainzApiService {

    @GET("release")
    suspend fun searchRelease(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Query("inc") includes: String = "recordings+artist-credits+release-groups"
    ): MBReleaseSearchResponse

    @GET("release/{mbid}")
    suspend fun getRelease(
        @Path("mbid") mbid: String,
        @Query("fmt") format: String = "json",
        @Query("inc") includes: String = "recordings+artist-credits+release-groups+labels"
    ): MBRelease
}
