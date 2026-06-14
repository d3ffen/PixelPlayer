package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.CurrentAlbumInfo
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MetadataEnrichmentRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.worker.MetadataEmbedWorker
import com.theveloper.pixelplay.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class EnrichmentState {
    data object Idle : EnrichmentState()
    data object Loading : EnrichmentState()
    data class Preview(val enriched: MetadataEnrichmentRepository.EnrichedAlbum) : EnrichmentState()
    data object Applying : EnrichmentState()
    data object Done : EnrichmentState()
    data class Error(val message: String) : EnrichmentState()
}

sealed class WriteRequestSideEffect {
    data object None : WriteRequestSideEffect()
    data class RequestWriteAccess(val intentSender: IntentSender) : WriteRequestSideEffect()
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val metadataEnrichmentRepository: MetadataEnrichmentRepository,
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    // ── Metadata Enrichment State ────────────────────────────────────────────
    private val _enrichmentState = MutableStateFlow<EnrichmentState>(EnrichmentState.Idle)
    val enrichmentState: StateFlow<EnrichmentState> = _enrichmentState.asStateFlow()

    private val _currentAlbumInfo = MutableStateFlow<CurrentAlbumInfo?>(null)
    val currentAlbumInfo: StateFlow<CurrentAlbumInfo?> = _currentAlbumInfo.asStateFlow()

    private val _writeRequestEffect = MutableStateFlow<WriteRequestSideEffect>(WriteRequestSideEffect.None)
    val writeRequestEffect: StateFlow<WriteRequestSideEffect> = _writeRequestEffect.asStateFlow()

    private var pendingAlbumId: Long = -1L

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            if (albumId != null) {
                pendingAlbumId = albumId
                loadAlbumData(albumId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.album_detail_invalid_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_detail_id_not_found), isLoading = false) }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)

                combine(albumDetailsFlow, albumSongsFlow) { album, songs ->
                    if (album != null) {
                        AlbumDetailUiState(
                            album = album,
                            songs = songs.sortedWith(
                                compareBy<Song> { it.discNumber ?: 1 }
                                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                    .thenBy { it.title.lowercase() }
                            ),
                            isLoading = false
                        )
                    } else {
                        AlbumDetailUiState(
                            error = context.getString(R.string.album_detail_not_found),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AlbumDetailUiState(
                                error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    // ── Metadata Enrichment ──────────────────────────────────────────────────

    /**
     * Reads the current embedded metadata from the album's songs synchronously
     * (from Room/MediaStore, no network). This populates the "Current" column
     * in the ⓘ bottom sheet instantly.
     */
    fun loadCurrentMetadata(album: Album) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsForAlbum(album.id).first()
                _currentAlbumInfo.value = CurrentAlbumInfo(
                    albumTitle = album.title,
                    artist = album.albumArtist ?: album.artist,
                    year = songs.firstOrNull()?.year?.takeIf { it > 0 }?.toString(),
                    trackCount = songs.size,
                    trackTitles = songs.sortedBy { it.trackNumber }.map { it.title },
                    coverArtUri = album.albumArtUriString?.let { android.net.Uri.parse(it) }
                )
            } catch (e: Exception) {
                // Non-fatal: "Current" column will show what we have from the Album model
                _currentAlbumInfo.value = CurrentAlbumInfo(
                    albumTitle = album.title,
                    artist = album.albumArtist ?: album.artist,
                    year = null,
                    trackCount = album.songCount,
                    trackTitles = emptyList(),
                    coverArtUri = album.albumArtUriString?.let { android.net.Uri.parse(it) }
                )
            }
        }
    }

    /**
     * Fetches enriched metadata from MusicBrainz in the background.
     * When complete, the enrichmentState moves to [EnrichmentState.Preview],
     * which shows the fetched data alongside the current data in the ⓘ sheet.
     */
    fun fetchMetadataPreview(album: Album) {
        _enrichmentState.value = EnrichmentState.Loading
        viewModelScope.launch {
            val result = metadataEnrichmentRepository.enrichAlbum(
                albumName = album.title,
                artistName = album.albumArtist ?: album.artist
            )
            _enrichmentState.value = if (result != null)
                EnrichmentState.Preview(result)
            else
                EnrichmentState.Error("No metadata found for this album on MusicBrainz")
        }
    }

    /**
     * Requests write access via MediaStore.createWriteRequest on API 30+,
     * then enqueues the [MetadataEmbedWorker] to apply metadata.
     *
     * On API ≤ 29, directly enqueues the worker (no scoped storage dialog needed).
     */
    fun requestWriteAccessThenApply(album: Album) {
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val songs = _uiState.value.songs
                val uris = songs.mapNotNull { song ->
                    if (song.mediaStoreId > 0) {
                        ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaStoreId
                        )
                    } else null
                }
                if (uris.isNotEmpty()) {
                    val pi = MediaStore.createWriteRequest(context.contentResolver, uris)
                    _writeRequestEffect.value = WriteRequestSideEffect.RequestWriteAccess(pi.intentSender)
                    return@launch
                }
            }
            // API ≤ 29 or no URIs to request: apply directly
            applyEnrichment(album)
        }
    }

    /**
     * Enqueues the [MetadataEmbedWorker] to permanently write metadata
     * and cover art into the album's audio files.
     */
    fun applyEnrichment(album: Album) {
        _enrichmentState.value = EnrichmentState.Applying
        val workRequest = OneTimeWorkRequestBuilder<MetadataEmbedWorker>()
            .setInputData(workDataOf(
                MetadataEmbedWorker.KEY_ALBUM_ID to album.id,
                MetadataEmbedWorker.KEY_ALBUM_NAME to album.title,
                MetadataEmbedWorker.KEY_ARTIST_NAME to (album.albumArtist ?: album.artist),
                MetadataEmbedWorker.KEY_COVER_ONLY to false
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                _enrichmentState.value = if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED)
                    EnrichmentState.Done
                else
                    EnrichmentState.Error("Failed to apply metadata")
            }
        }

        workManager.enqueue(workRequest)
    }

    fun resetEnrichmentState() {
        _enrichmentState.value = EnrichmentState.Idle
        _currentAlbumInfo.value = null
        _writeRequestEffect.value = WriteRequestSideEffect.None
    }

    fun clearWriteRequestEffect() {
        _writeRequestEffect.value = WriteRequestSideEffect.None
    }
}
