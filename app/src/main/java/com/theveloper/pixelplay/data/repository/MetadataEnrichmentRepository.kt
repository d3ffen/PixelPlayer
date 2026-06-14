package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that handles the "ⓘ" metadata enrichment flow.
 *
 * Fetches full album + track data from MusicBrainz and returns structured
 * results ready to be embedded into audio files by [MetadataEmbedWorker].
 */
@Singleton
class MetadataEnrichmentRepository @Inject constructor(
    private val musicBrainzApi: MusicBrainzApiService,
    private val albumArtRepository: AlbumArtRepository
) {

    data class EnrichedAlbum(
        val mbid: String,
        val title: String,
        val artist: String,
        val year: String?,
        val tracks: List<EnrichedTrack>,
        val coverArtBytes: ByteArray?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EnrichedAlbum
            return mbid == other.mbid &&
                title == other.title &&
                artist == other.artist &&
                year == other.year &&
                tracks == other.tracks &&
                coverArtBytes.contentEquals(other.coverArtBytes)
        }

        override fun hashCode(): Int {
            var result = mbid.hashCode()
            result = 31 * result + title.hashCode()
            result = 31 * result + artist.hashCode()
            result = 31 * result + (year?.hashCode() ?: 0)
            result = 31 * result + tracks.hashCode()
            result = 31 * result + (coverArtBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    data class EnrichedTrack(
        val trackNumber: Int,
        val title: String,
        val artist: String,
        val mbid: String?
    )

    /**
     * Fetch full album metadata from MusicBrainz, including per-track data
     * and cover art bytes.
     *
     * @param albumName Album title to search for
     * @param artistName Album artist name
     * @return Enriched album data, or null if the album couldn't be found
     */
    suspend fun enrichAlbum(albumName: String, artistName: String): EnrichedAlbum? {
        return try {
            val query = "release:\"$albumName\" AND artist:\"$artistName\""
            val searchResult = musicBrainzApi.searchRelease(query)
            val release = searchResult.releases.firstOrNull() ?: return null
            val full = musicBrainzApi.getRelease(release.id)

            val tracks = full.media?.flatMap { medium ->
                medium.tracks?.mapIndexed { _, track ->
                    EnrichedTrack(
                        trackNumber = track.number.toIntOrNull() ?: 0,
                        title = track.title,
                        artist = full.artistCredit?.firstOrNull()?.name ?: artistName,
                        mbid = track.recording?.id
                    )
                } ?: emptyList()
            } ?: emptyList()

            val coverArt = albumArtRepository.fetchAlbumArtBytes(albumName, artistName)

            EnrichedAlbum(
                mbid = full.id,
                title = full.title,
                artist = full.artistCredit?.joinToString(", ") { it.name } ?: artistName,
                year = full.date?.take(4),
                tracks = tracks,
                coverArtBytes = coverArt
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to enrich album '$albumName' by '$artistName'")
            null
        }
    }
}
