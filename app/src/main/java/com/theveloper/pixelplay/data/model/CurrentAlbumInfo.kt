package com.theveloper.pixelplay.data.model

import android.net.Uri

/**
 * Snapshot of the currently embedded metadata read from the audio files
 * of an album. Displayed immediately in the "ⓘ" bottom sheet alongside
 * the MusicBrainz-fetched data for comparison.
 */
data class CurrentAlbumInfo(
    val albumTitle: String,
    val artist: String,
    val year: String?,
    val trackCount: Int,
    val trackTitles: List<String>,
    val coverArtUri: Uri?
)
