package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.AlbumArtFetchRepository
import com.theveloper.pixelplay.data.repository.EnrichedAlbumData
import com.theveloper.pixelplay.utils.MediaStorePermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlbumEnrichmentHolder"

sealed interface AlbumEnrichmentState {
    data object Idle : AlbumEnrichmentState
    data object LoadingCurrentInfo : AlbumEnrichmentState
    data class CurrentInfoReady(val info: CurrentAlbumInfo) : AlbumEnrichmentState
    data class FetchingFromNetwork(val currentInfo: CurrentAlbumInfo) : AlbumEnrichmentState
    data class Preview(
        val currentInfo: CurrentAlbumInfo,
        val enriched: EnrichedAlbumData
    ) : AlbumEnrichmentState
    data class FetchError(
        val currentInfo: CurrentAlbumInfo,
        val message: String
    ) : AlbumEnrichmentState
    data class Applying(val currentInfo: CurrentAlbumInfo) : AlbumEnrichmentState
    data object Done : AlbumEnrichmentState
    data class ApplyError(val message: String) : AlbumEnrichmentState
}

data class CurrentAlbumInfo(
    val albumTitle: String,
    val artist: String,
    val year: String?,
    val trackCount: Int,
    val trackTitles: List<String>,
    val coverArtUri: Uri?,
    val hasEmbeddedArt: Boolean
)

data class AlbumArtFetchState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

private data class PendingArtEmbed(val songs: List<Song>, val bytes: ByteArray)
private data class PendingEnrichApply(val songs: List<Song>, val enriched: EnrichedAlbumData)

@Singleton
class AlbumMetadataEnrichmentStateHolder @Inject constructor(
    private val albumArtFetchRepository: AlbumArtFetchRepository,
    private val songMetadataEditor: SongMetadataEditor,
    @ApplicationContext private val context: Context
) {
    private val _enrichmentState = MutableStateFlow<AlbumEnrichmentState>(AlbumEnrichmentState.Idle)
    val enrichmentState: StateFlow<AlbumEnrichmentState> = _enrichmentState.asStateFlow()

    private val _albumArtFetchState = MutableStateFlow(AlbumArtFetchState())
    val albumArtFetchState: StateFlow<AlbumArtFetchState> = _albumArtFetchState.asStateFlow()

    private val _writePermissionRequest = MutableSharedFlow<IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val writePermissionRequest: SharedFlow<IntentSender> = _writePermissionRequest.asSharedFlow()

    @Volatile private var pendingEnrichApply: PendingEnrichApply? = null
    @Volatile private var pendingArtEmbed: PendingArtEmbed? = null

    fun reset() { _enrichmentState.value = AlbumEnrichmentState.Idle }
    fun resetArtFetchState() { _albumArtFetchState.value = AlbumArtFetchState() }

    // ── Step 1: Load current info from local db/files immediately ─────────────

    suspend fun loadCurrentInfo(songs: List<Song>, album: Album) = withContext(Dispatchers.IO) {
        _enrichmentState.value = AlbumEnrichmentState.LoadingCurrentInfo

        val sorted = songs.sortedWith(
            compareBy<Song> { it.discNumber ?: 1 }.thenBy { it.trackNumber }
        )

        val hasEmbeddedArt = sorted.firstOrNull()?.path?.let { path ->
            runCatching { AudioMetadataReader.read(File(path))?.artwork != null }.getOrDefault(false)
        } ?: false

        val coverUri = album.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }

        val info = CurrentAlbumInfo(
            albumTitle = album.title,
            artist = album.albumArtist ?: album.artist,
            year = sorted.firstOrNull()?.year?.takeIf { it > 0 }?.toString(),
            trackCount = sorted.size,
            trackTitles = sorted.map { it.title },
            coverArtUri = coverUri,
            hasEmbeddedArt = hasEmbeddedArt
        )
        _enrichmentState.value = AlbumEnrichmentState.CurrentInfoReady(info)
    }

    // ── Step 2: Fetch from MusicBrainz / fanart.tv ────────────────────────────

    suspend fun fetchEnrichedData(songs: List<Song>, album: Album) = withContext(Dispatchers.IO) {
        val currentInfo = (_enrichmentState.value as? AlbumEnrichmentState.CurrentInfoReady)?.info
            ?: return@withContext

        _enrichmentState.value = AlbumEnrichmentState.FetchingFromNetwork(currentInfo)

        val artistName = album.albumArtist ?: album.artist
        val result = albumArtFetchRepository.fetchFullAlbumData(album.title, artistName)

        _enrichmentState.value = if (result != null) {
            AlbumEnrichmentState.Preview(currentInfo, result)
        } else {
            AlbumEnrichmentState.FetchError(
                currentInfo,
                "No results found for \"${album.title}\" by $artistName on MusicBrainz."
            )
        }
    }

    // ── Step 3: Apply ─────────────────────────────────────────────────────────

    suspend fun applyEnrichment(songs: List<Song>) = withContext(Dispatchers.IO) {
        val state = _enrichmentState.value as? AlbumEnrichmentState.Preview ?: return@withContext
        _enrichmentState.value = AlbumEnrichmentState.Applying(state.currentInfo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intentSender = buildWriteRequestForSongs(songs)
            if (intentSender != null) {
                pendingEnrichApply = PendingEnrichApply(songs, state.enriched)
                _writePermissionRequest.emit(intentSender)
                return@withContext
            }
        }
        performApply(songs, state.enriched)
    }

    // ── Album-art-only (Player screen button + art picker) ────────────────────

    suspend fun fetchAndEmbedAlbumArt(songs: List<Song>, albumName: String, artistName: String) =
        withContext(Dispatchers.IO) {
            _albumArtFetchState.value = AlbumArtFetchState(isLoading = true)
            val bytes = albumArtFetchRepository.fetchAlbumArtBytes(albumName, artistName)
            if (bytes == null) {
                _albumArtFetchState.value = AlbumArtFetchState(
                    errorMessage = "No album art found for \"$albumName\"."
                )
                return@withContext
            }
            embedArtBytes(songs, bytes)
        }

    /** Called from the art picker when the user selects a specific image URL. */
    suspend fun embedArtFromUrl(songs: List<Song>, imageUrl: String) = withContext(Dispatchers.IO) {
        _albumArtFetchState.value = AlbumArtFetchState(isLoading = true)
        val bytes = albumArtFetchRepository.downloadBytesPublic(imageUrl)
        if (bytes == null || bytes.isEmpty()) {
            _albumArtFetchState.value = AlbumArtFetchState(errorMessage = "Failed to download image.")
            return@withContext
        }
        embedArtBytes(songs, bytes)
    }

    /** Called from the art picker when the user picks a local image URI. */
    suspend fun embedArtFromLocalUri(songs: List<Song>, uri: Uri) = withContext(Dispatchers.IO) {
        _albumArtFetchState.value = AlbumArtFetchState(isLoading = true)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read local image URI")
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            _albumArtFetchState.value = AlbumArtFetchState(errorMessage = "Failed to read selected image.")
            return@withContext
        }
        embedArtBytes(songs, bytes)
    }

    private suspend fun embedArtBytes(songs: List<Song>, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intentSender = buildWriteRequestForSongs(songs)
                if (intentSender != null) {
                    pendingArtEmbed = PendingArtEmbed(songs, bytes)
                    _writePermissionRequest.emit(intentSender)
                    return@withContext
                }
            }
            performEmbedArtOnly(songs, bytes)
        }

    // ── OS write-permission result ─────────────────────────────────────────────

    suspend fun onWritePermissionGranted(granted: Boolean) = withContext(Dispatchers.IO) {
        pendingArtEmbed?.let { pending ->
            pendingArtEmbed = null
            if (granted) {
                performEmbedArtOnly(pending.songs, pending.bytes)
            } else {
                _albumArtFetchState.value = AlbumArtFetchState(
                    errorMessage = "Write permission denied. Tap 'Allow' in the system dialog to modify audio files."
                )
            }
            return@withContext
        }
        pendingEnrichApply?.let { pending ->
            pendingEnrichApply = null
            if (granted) {
                performApply(pending.songs, pending.enriched)
            } else {
                val ci = (_enrichmentState.value as? AlbumEnrichmentState.Applying)?.currentInfo
                    ?: CurrentAlbumInfo("", "", null, 0, emptyList(), null, false)
                _enrichmentState.value = AlbumEnrichmentState.FetchError(
                    ci,
                    "Write permission denied. Tap 'Allow' in the system dialog to modify audio files."
                )
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun performApply(songs: List<Song>, enriched: EnrichedAlbumData) {
        try {
            val sorted = songs.sortedWith(
                compareBy<Song> { it.discNumber ?: 1 }.thenBy { it.trackNumber }
            )
            var successCount = 0
            sorted.forEachIndexed { index, song ->
                val songId = song.id.toLongOrNull() ?: return@forEachIndexed
                val mbTrack = enriched.tracks.find {
                    it.title.trim().equals(song.title.trim(), ignoreCase = true)
                } ?: enriched.tracks.getOrNull(index)

                val coverUpdate = enriched.coverArtBytes?.let {
                    CoverArtUpdate(bytes = it, mimeType = "image/jpeg")
                }

                val result = songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = mbTrack?.title ?: song.title,
                    newArtist = mbTrack?.artist ?: song.displayArtist,
                    newAlbum = enriched.title,
                    newAlbumArtist = enriched.artist,
                    newComposer = null,
                    newGenre = song.genre ?: "",
                    newLyrics = song.lyrics ?: "",
                    newTrackNumber = mbTrack?.trackNumber ?: song.trackNumber,
                    newDiscNumber = song.discNumber,
                    coverArtUpdate = coverUpdate
                )
                if (result.success) successCount++
            }

            scanFiles(sorted)

            _enrichmentState.value = if (successCount == 0) {
                AlbumEnrichmentState.ApplyError("Failed to write metadata to any files.")
            } else {
                AlbumEnrichmentState.Done
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error applying enrichment")
            _enrichmentState.value = AlbumEnrichmentState.ApplyError(
                "Error writing metadata: ${e.localizedMessage}"
            )
        }
    }

    private suspend fun performEmbedArtOnly(songs: List<Song>, bytes: ByteArray) {
        try {
            var successCount = 0
            songs.forEach { song ->
                val songId = song.id.toLongOrNull() ?: return@forEach
                val existingMeta = runCatching {
                    AudioMetadataReader.read(File(song.path))
                }.getOrNull()

                val result = songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = existingMeta?.title ?: song.title,
                    newArtist = existingMeta?.artist ?: song.displayArtist,
                    newAlbum = existingMeta?.album ?: song.album,
                    newAlbumArtist = existingMeta?.albumArtist ?: song.albumArtist ?: "",
                    newComposer = existingMeta?.composer,
                    newGenre = existingMeta?.genre ?: song.genre ?: "",
                    newLyrics = existingMeta?.lyrics ?: song.lyrics ?: "",
                    newTrackNumber = existingMeta?.trackNumber ?: song.trackNumber,
                    newDiscNumber = existingMeta?.discNumber ?: song.discNumber,
                    coverArtUpdate = CoverArtUpdate(bytes = bytes, mimeType = "image/jpeg")
                )
                if (result.success) successCount++
            }

            scanFiles(songs)

            _albumArtFetchState.value = if (successCount > 0) {
                AlbumArtFetchState(isSuccess = true)
            } else {
                AlbumArtFetchState(
                    errorMessage = "Could not embed art into any files. " +
                        "Some songs may be on read-only storage."
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error embedding art")
            _albumArtFetchState.value = AlbumArtFetchState(
                errorMessage = "Error: ${e.localizedMessage}"
            )
        }
    }

    private fun scanFiles(songs: List<Song>) {
        val paths = songs.mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }
        if (paths.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        }
    }

    /**
     * Builds a write-permission IntentSender for ALL songs in the batch.
     * Uses [MediaStorePermissionHelper.getMediaStoreUri(context, id)] which correctly
     * resolves the volume name for each song — fixing "permission denied" on secondary volumes.
     * Returns null if no permission is needed (pre-Android-11 or all URIs invalid).
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun buildWriteRequestForSongs(songs: List<Song>): IntentSender? {
        val uris = songs.mapNotNull { song ->
            song.id.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
                // Use the context-aware overload that queries the actual volume name
                MediaStorePermissionHelper.getMediaStoreUri(context, id)
            }
        }
        if (uris.isEmpty()) return null
        return MediaStorePermissionHelper.createWriteRequestIntentSender(context, uris)
    }
}
