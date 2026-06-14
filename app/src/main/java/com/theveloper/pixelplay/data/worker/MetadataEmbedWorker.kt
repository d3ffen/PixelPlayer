package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.repository.AlbumArtRepository
import com.theveloper.pixelplay.data.repository.MetadataEnrichmentRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.TagLibEmbedHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * WorkManager Worker that downloads album art and/or MusicBrainz metadata,
 * then permanently embeds it into the audio files of an album using TagLib.
 *
 * Supports two modes:
 * - **Cover-only** (from Player screen "Fetch Album Art"): downloads and embeds
 *   album art into every song file of the album.
 * - **Full enrichment** (from Album screen "ⓘ"): downloads full album + track
 *   metadata, embeds both tags and cover art (if missing).
 *
 * After writing, triggers a MediaStore rescan so PixelPlayer's library
 * picks up the changes without a manual refresh.
 */
@HiltWorker
class MetadataEmbedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val albumArtRepository: AlbumArtRepository,
    private val metadataEnrichmentRepository: MetadataEnrichmentRepository,
    private val musicRepository: MusicRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val albumId = inputData.getLong(KEY_ALBUM_ID, -1L)
        val albumName = inputData.getString(KEY_ALBUM_NAME) ?: return failure("Missing album name")
        val artistName = inputData.getString(KEY_ARTIST_NAME) ?: return failure("Missing artist name")
        val embedCoverOnly = inputData.getBoolean(KEY_COVER_ONLY, false)

        val songs = musicRepository.getSongsForAlbum(albumId).first()

        if (embedCoverOnly) {
            return embedCoverArt(albumName, artistName, songs)
        } else {
            return embedFullEnrichment(albumName, artistName, songs)
        }
    }

    private suspend fun embedCoverArt(
        albumName: String,
        artistName: String,
        songs: List<com.theveloper.pixelplay.data.model.Song>
    ): Result {
        val artBytes = albumArtRepository.fetchAlbumArtBytes(albumName, artistName)
            ?: return failure("No album art found for '$albumName' by '$artistName'")

        var successCount = 0
        songs.forEach { song ->
            val path = resolveFilePath(song)
            if (path != null && TagLibEmbedHelper.embedAlbumArt(applicationContext, path, artBytes)) {
                successCount++
                TagLibEmbedHelper.rescanFile(applicationContext, path)
            }
        }

        Timber.d("Cover art embedded in $successCount/${songs.size} files")
        return if (successCount > 0) Result.success() else failure("Failed to embed cover art in any file")
    }

    private suspend fun embedFullEnrichment(
        albumName: String,
        artistName: String,
        songs: List<com.theveloper.pixelplay.data.model.Song>
    ): Result {
        val enriched = metadataEnrichmentRepository.enrichAlbum(albumName, artistName)
            ?: return failure("No MusicBrainz data found for '$albumName' by '$artistName'")

        var coverSuccess = 0
        var metadataSuccess = 0

        // Embed cover art into files that don't already have it
        enriched.coverArtBytes?.let { artBytes ->
            songs.forEach { song ->
                val path = resolveFilePath(song)
                if (path != null && !TagLibEmbedHelper.hasEmbeddedArt(path)) {
                    if (TagLibEmbedHelper.embedAlbumArt(applicationContext, path, artBytes)) {
                        coverSuccess++
                    }
                }
            }
        }

        // Match songs to MusicBrainz tracks and embed metadata
        songs.forEach { song ->
            val path = resolveFilePath(song) ?: return@forEach
            val mbTrack = enriched.tracks.find {
                it.title.equals(song.title, ignoreCase = true)
            } ?: enriched.tracks.getOrNull(song.trackNumber - 1)

            if (TagLibEmbedHelper.embedTrackMetadata(
                    filePath = path,
                    title = mbTrack?.title ?: song.title,
                    artist = mbTrack?.artist ?: song.artist,
                    albumArtist = enriched.artist,
                    album = enriched.title,
                    year = enriched.year,
                    trackNumber = mbTrack?.trackNumber ?: song.trackNumber
                )
            ) {
                metadataSuccess++
            }

            // Rescan after each file so changes are picked up
            TagLibEmbedHelper.rescanFile(applicationContext, path)
        }

        Timber.d("Enrichment complete: cover=$coverSuccess/${songs.size}, metadata=$metadataSuccess/${songs.size}")
        return Result.success()
    }

    /**
     * Resolves the absolute file path from a Song model.
     * Prefers the direct [Song.path] field, falls back to parsing the content URI.
     */
    private fun resolveFilePath(song: com.theveloper.pixelplay.data.model.Song): String? {
        if (song.path.isNotBlank() && java.io.File(song.path).exists()) {
            return song.path
        }
        // Fallback: try to resolve from content URI via MediaStore
        return try {
            val uri = android.net.Uri.parse(song.contentUriString)
            if (uri.scheme == "file") {
                uri.path
            } else if (uri.scheme == "content") {
                applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                        if (dataIndex >= 0) cursor.getString(dataIndex) else null
                    } else null
                }
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve file path for song ${song.id}")
            null
        }
    }

    private fun failure(message: String): Result {
        Timber.w("MetadataEmbedWorker failed: $message")
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    companion object {
        const val KEY_ALBUM_ID = "album_id"
        const val KEY_ALBUM_NAME = "album_name"
        const val KEY_ARTIST_NAME = "artist_name"
        const val KEY_COVER_ONLY = "cover_only"
        const val KEY_ERROR = "error"
    }
}
