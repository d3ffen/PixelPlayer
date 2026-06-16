package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.coverartarchive.CoverArtArchiveApiService
import com.theveloper.pixelplay.data.network.fanart.FanartTvApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import com.theveloper.pixelplay.data.repository.AlbumArtFetchRepository
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** A single candidate image shown in the picker grid. */
data class ArtCandidate(
    val url: String,
    val source: String,        // "Fanart.tv" | "Cover Art Archive" | "Last.fm" | "Deezer"
    val mbid: String? = null
)

sealed interface ArtPickerState {
    data object Idle : ArtPickerState
    data object Searching : ArtPickerState
    data class Results(val candidates: List<ArtCandidate>) : ArtPickerState
    data class NoResults(val message: String) : ArtPickerState
    data object Applying : ArtPickerState
    data object Done : ArtPickerState
    data class Error(val message: String) : ArtPickerState
}

@HiltViewModel
class AlbumArtPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicBrainzApi: MusicBrainzApiService,
    private val fanartTvApi: FanartTvApiService,
    private val coverArtArchiveApi: CoverArtArchiveApiService,
    private val albumArtFetchRepository: AlbumArtFetchRepository,
    private val okHttpClient: okhttp3.OkHttpClient,
    val enrichmentStateHolder: AlbumMetadataEnrichmentStateHolder
) : ViewModel() {

    private val _state = MutableStateFlow<ArtPickerState>(ArtPickerState.Idle)
    val state: StateFlow<ArtPickerState> = _state.asStateFlow()

    /** Songs to embed into when the user picks an image. Set by the caller. */
    var targetSongs: List<Song> = emptyList()

    fun searchArt(albumName: String, artistName: String) {
        viewModelScope.launch {
            _state.value = ArtPickerState.Searching
            val candidates = mutableListOf<ArtCandidate>()

            // 1. Resolve MBID
            val mbid = withContext(Dispatchers.IO) {
                runCatching {
                    val q = "release:\"${albumName.replace("\"","'")}\" AND artist:\"${artistName.replace("\"","'")}\""
                    musicBrainzApi.searchRelease(q).releases.firstOrNull()?.id
                }.getOrNull()
            }

            if (mbid != null) {
                // 2. Fanart.tv — multiple resolution options
                withContext(Dispatchers.IO) {
                    val key = BuildConfig.FANART_TV_API_KEY
                    if (key.isNotBlank() && key != "\"\"") {
                        runCatching {
                            val resp = fanartTvApi.getMusicArt(mbid, key)
                            resp.albums?.get(mbid)?.albumCovers
                                ?.filter { it.url.isNotBlank() }
                                ?.sortedByDescending { it.likes.toIntOrNull() ?: 0 }
                                ?.take(6)
                                ?.forEach { img ->
                                    candidates.add(ArtCandidate(img.url, "Fanart.tv", mbid))
                                }
                        }
                    }
                }

                delay(300) // rate-limit between calls

                // 3. Cover Art Archive — all approved front covers
                withContext(Dispatchers.IO) {
                    runCatching {
                        val resp = coverArtArchiveApi.getCovers(mbid)
                        resp.images
                            .filter { it.approved }
                            .sortedByDescending { it.front }
                            .take(8)
                            .forEach { img ->
                                val url = img.thumbnails.large ?: img.thumbnails.medium ?: img.image
                                if (url.isNotBlank()) {
                                    candidates.add(ArtCandidate(url, "Cover Art Archive", mbid))
                                }
                            }
                    }
                }
            }

            // 4. Deezer (no MBID needed — search by album + artist)
            withContext(Dispatchers.IO) {
                runCatching {
                    // Deezer album search: /search/album?q=<artist> <album>
                    val query = "$artistName $albumName"
                    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                    val url = "https://api.deezer.com/search/album?q=$encoded&limit=5"
                    val request = okhttp3.Request.Builder().url(url).build()
                    okHttpClient.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: return@use
                            val json = org.json.JSONObject(body)
                            val data = json.optJSONArray("data") ?: return@use
                            for (i in 0 until minOf(data.length(), 4)) {
                                val album = data.getJSONObject(i)
                                val coverXl = album.optString("cover_xl").takeIf { it.isNotBlank() }
                                val coverBig = album.optString("cover_big").takeIf { it.isNotBlank() }
                                (coverXl ?: coverBig)?.let { imgUrl ->
                                    candidates.add(ArtCandidate(imgUrl, "Deezer"))
                                }
                            }
                        }
                    }
                }
            }

            _state.value = if (candidates.isEmpty()) {
                ArtPickerState.NoResults(
                    "No artwork found for \"$albumName\" by $artistName.\n" +
                    "Try using a local image instead."
                )
            } else {
                ArtPickerState.Results(candidates.distinctBy { it.url })
            }
        }
    }

    fun pickFromUrl(songs: List<Song>, url: String) {
        viewModelScope.launch {
            _state.value = ArtPickerState.Applying
            enrichmentStateHolder.embedArtFromUrl(songs, url)
            // Result is observed via enrichmentStateHolder.albumArtFetchState
        }
    }

    fun pickFromLocalUri(songs: List<Song>, uri: Uri) {
        viewModelScope.launch {
            _state.value = ArtPickerState.Applying
            enrichmentStateHolder.embedArtFromLocalUri(songs, uri)
        }
    }

    fun reset() {
        _state.value = ArtPickerState.Idle
        enrichmentStateHolder.resetArtFetchState()
    }
}
