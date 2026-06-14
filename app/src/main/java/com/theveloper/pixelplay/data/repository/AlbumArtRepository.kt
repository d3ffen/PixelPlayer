package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.network.coverartarchive.CoverArtArchiveApiService
import com.theveloper.pixelplay.data.network.fanart.FanartTvApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that encapsulates the two-step "resolve MBID → fetch art" logic.
 *
 * Tries fanart.tv (community-curated high-quality art) first,
 * then falls back to CoverArtArchive.org (official MusicBrainz partner).
 */
@Singleton
class AlbumArtRepository @Inject constructor(
    private val fanartTvApi: FanartTvApiService,
    private val musicBrainzApi: MusicBrainzApiService,
    private val coverArtArchiveApi: CoverArtArchiveApiService,
    private val okHttpClient: OkHttpClient
) {

    /**
     * Main entry point.
     * Returns album art as raw bytes, or null if nothing was found.
     * Tries fanart.tv first, then CoverArtArchive.
     */
    suspend fun fetchAlbumArtBytes(albumName: String, artistName: String): ByteArray? {
        val mbid = resolveMbid(albumName, artistName) ?: return null
        return fetchFromFanartTv(mbid)
            ?: fetchFromCoverArtArchive(mbid)
    }

    private suspend fun resolveMbid(album: String, artist: String): String? {
        return try {
            val query = "release:\"$album\" AND artist:\"$artist\""
            val results = musicBrainzApi.searchRelease(query)
            results.releases.firstOrNull()?.id
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve MBID for '$album' by '$artist'")
            null
        }
    }

    private suspend fun fetchFromFanartTv(mbid: String): ByteArray? {
        return try {
            val apiKey = BuildConfig.FANART_TV_API_KEY
            val response = fanartTvApi.getAlbumArt(mbid, apiKey)
            val covers = response.albums?.get(mbid)?.albumCovers ?: return null
            val bestUrl = covers.maxByOrNull { it.likes.toIntOrNull() ?: 0 }?.url ?: return null
            downloadBytes(bestUrl)
        } catch (e: Exception) {
            Timber.w(e, "fanart.tv lookup failed for MBID $mbid")
            null
        }
    }

    private suspend fun fetchFromCoverArtArchive(mbid: String): ByteArray? {
        return try {
            val response = coverArtArchiveApi.getCovers(mbid)
            val frontCover = response.images
                .filter { it.front && it.approved }
                .firstOrNull() ?: response.images.firstOrNull() ?: return null
            val url = frontCover.thumbnails.large ?: frontCover.image
            downloadBytes(url)
        } catch (e: Exception) {
            Timber.w(e, "CoverArtArchive lookup failed for MBID $mbid")
            null
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to download image from $url")
            null
        }
    }
}
