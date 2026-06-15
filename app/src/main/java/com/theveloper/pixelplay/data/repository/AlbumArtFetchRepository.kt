package com.theveloper.pixelplay.data.repository

import android.util.Log
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.network.coverartarchive.CoverArtArchiveApiService
import com.theveloper.pixelplay.data.network.fanart.FanartTvApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MBRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlbumArtFetchRepo"

data class EnrichedAlbumData(
    val mbid: String,
    val title: String,
    val artist: String,
    val year: String?,
    val tracks: List<EnrichedTrackData>,
    val coverArtBytes: ByteArray?
)

data class EnrichedTrackData(
    val trackNumber: Int,
    val title: String,
    val artist: String,
    val mbid: String?
)

@Singleton
class AlbumArtFetchRepository @Inject constructor(
    private val fanartTvApiService: FanartTvApiService,
    private val musicBrainzApiService: MusicBrainzApiService,
    private val coverArtArchiveApiService: CoverArtArchiveApiService,
    private val okHttpClient: OkHttpClient
) {

    /** Fetches only album art bytes. Used by the "Fetch Album Art" player button. */
    suspend fun fetchAlbumArtBytes(albumName: String, artistName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val mbid = resolveMbid(albumName, artistName) ?: return@withContext null
                fetchCoverArt(mbid, albumName, artistName)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error fetching album art for $albumName / $artistName")
                null
            }
        }

    /** Fetches full album metadata + art. Used by the "ⓘ" album info flow. */
    suspend fun fetchFullAlbumData(albumName: String, artistName: String): EnrichedAlbumData? =
        withContext(Dispatchers.IO) {
            try {
                val query = buildMbQuery(albumName, artistName)
                val searchResult = musicBrainzApiService.searchRelease(query)
                val release = searchResult.releases.firstOrNull() ?: return@withContext null

                // Second MB call — rate limit: wait 1.1 s between calls
                delay(1100)
                val fullRelease = try {
                    musicBrainzApiService.getRelease(release.id)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to fetch full release, using search result")
                    release
                }

                val resolvedArtist = fullRelease.artistCredit
                    .joinToString(", ") { it.name }
                    .ifBlank { artistName }

                val tracks = fullRelease.media.flatMapIndexed { _, medium ->
                    medium.tracks.map { track ->
                        EnrichedTrackData(
                            trackNumber = track.position.takeIf { it > 0 }
                                ?: track.number.toIntOrNull() ?: 0,
                            title = track.title,
                            artist = resolvedArtist,
                            mbid = track.recording?.id
                        )
                    }
                }

                val coverArt = fetchCoverArt(fullRelease.id, albumName, artistName)

                EnrichedAlbumData(
                    mbid = fullRelease.id,
                    title = fullRelease.title,
                    artist = resolvedArtist,
                    year = fullRelease.date?.take(4),
                    tracks = tracks,
                    coverArtBytes = coverArt
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error fetching full album data for $albumName / $artistName")
                null
            }
        }

    private suspend fun resolveMbid(albumName: String, artistName: String): String? {
        return try {
            val query = buildMbQuery(albumName, artistName)
            musicBrainzApiService.searchRelease(query).releases.firstOrNull()?.id
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to resolve MBID")
            null
        }
    }

    private suspend fun fetchCoverArt(mbid: String, albumName: String, artistName: String): ByteArray? {
        // 1. Try fanart.tv (highest quality)
        val fanartKey = BuildConfig.FANART_TV_API_KEY
        if (fanartKey.isNotBlank() && fanartKey != "\"\"") {
            try {
                val response = fanartTvApiService.getMusicArt(mbid, fanartKey)
                val covers = response.albums?.get(mbid)?.albumCovers
                val bestUrl = covers
                    ?.filter { it.url.isNotBlank() }
                    ?.maxByOrNull { it.likes.toIntOrNull() ?: 0 }
                    ?.url
                if (bestUrl != null) {
                    val bytes = downloadBytes(bestUrl)
                    if (bytes != null) {
                        Log.d(TAG, "Got cover from fanart.tv for $albumName")
                        return bytes
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "fanart.tv lookup failed, trying CoverArtArchive")
            }
        }

        // 2. Fallback: CoverArtArchive via MusicBrainz
        return try {
            val response = coverArtArchiveApiService.getCovers(mbid)
            val frontCover = response.images
                .filter { it.front && it.approved }
                .firstOrNull()
                ?: response.images.firstOrNull()
            val url = frontCover?.thumbnails?.large
                ?: frontCover?.thumbnails?.medium
                ?: frontCover?.image
            if (url != null) {
                val bytes = downloadBytes(url)
                if (bytes != null) Log.d(TAG, "Got cover from CoverArtArchive for $albumName")
                bytes
            } else null
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "CoverArtArchive also failed for $albumName")
            null
        }
    }

    private fun downloadBytes(url: String): ByteArray? = try {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Failed to download image from $url")
        null
    }

    private fun buildMbQuery(album: String, artist: String): String {
        val safeAlbum = album.replace("\"", "\\\"")
        val safeArtist = artist.replace("\"", "\\\"")
        return "release:\"$safeAlbum\" AND artist:\"$safeArtist\""
    }
}
