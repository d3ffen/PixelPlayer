@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.repository.EnrichedAlbumData
import com.theveloper.pixelplay.presentation.viewmodel.AlbumEnrichmentState
import com.theveloper.pixelplay.presentation.viewmodel.CurrentAlbumInfo
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Bottom sheet showing:
 * 1. Current embedded metadata (left column) — shown instantly from local data
 * 2. Fetched MusicBrainz metadata (right column) — shimmer while loading, then real data
 * 3. ⭐ Star "Apply & Embed" button that slides in once the preview is ready
 */
@Composable
fun AlbumMetadataInfoSheet(
    enrichmentState: AlbumEnrichmentState,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Album Info",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // ── Two-column layout ─────────────────────────────────────────────
            when (enrichmentState) {
                is AlbumEnrichmentState.LoadingCurrentInfo -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                is AlbumEnrichmentState.CurrentInfoReady,
                is AlbumEnrichmentState.FetchingFromNetwork,
                is AlbumEnrichmentState.Preview,
                is AlbumEnrichmentState.FetchError -> {
                    val currentInfo: CurrentAlbumInfo = when (enrichmentState) {
                        is AlbumEnrichmentState.CurrentInfoReady -> enrichmentState.info
                        is AlbumEnrichmentState.FetchingFromNetwork -> enrichmentState.currentInfo
                        is AlbumEnrichmentState.Preview -> enrichmentState.currentInfo
                        is AlbumEnrichmentState.FetchError -> enrichmentState.currentInfo
                        else -> return@Column
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // LEFT: current info
                        CurrentInfoColumn(
                            info = currentInfo,
                            modifier = Modifier.weight(1f)
                        )

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // RIGHT: fetched info
                        FetchedInfoColumn(
                            enrichmentState = enrichmentState,
                            currentInfo = currentInfo,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Star apply button (slides in when Preview is ready) ────
                    val isPreview = enrichmentState is AlbumEnrichmentState.Preview
                    val isFetchError = enrichmentState is AlbumEnrichmentState.FetchError

                    AnimatedVisibility(
                        visible = isPreview,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
                    ) {
                        Column {
                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showConfirmDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Apply & Embed Metadata",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isFetchError) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }

                is AlbumEnrichmentState.Applying -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ContainedLoadingIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Writing metadata to files…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is AlbumEnrichmentState.Done -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Metadata embedded successfully!",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    }
                }

                is AlbumEnrichmentState.ApplyError -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            enrichmentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Close")
                        }
                    }
                }

                else -> {}
            }
        }
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(Icons.Rounded.Star, contentDescription = null)
            },
            title = { Text("Apply Metadata?", fontFamily = GoogleSansRounded) },
            text = {
                Text(
                    "This will permanently overwrite the metadata tags in all audio files " +
                    "of this album, including track titles, artist, year, and album art. " +
                    "This cannot be undone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    onApply()
                }) { Text("Apply") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Left column: current embedded info ────────────────────────────────────────

@Composable
private fun CurrentInfoColumn(info: CurrentAlbumInfo, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        SectionLabel("Current")
        Spacer(Modifier.height(10.dp))

        // Cover art
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .align(Alignment.CenterHorizontally)
        ) {
            if (info.coverArtUri != null) {
                AsyncImage(
                    model = info.coverArtUri,
                    contentDescription = "Current cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        MetaField("Title", info.albumTitle)
        MetaField("Artist", info.artist)
        MetaField("Year", info.year ?: "—")
        MetaField("Tracks", info.trackCount.toString())

        if (info.trackTitles.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Track list",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            info.trackTitles.take(8).forEachIndexed { i, title ->
                Text(
                    "${i + 1}. $title",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (info.trackTitles.size > 8) {
                Text(
                    "…and ${info.trackTitles.size - 8} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Right column: fetched info (shimmer → real data) ──────────────────────────

@Composable
private fun FetchedInfoColumn(
    enrichmentState: AlbumEnrichmentState,
    currentInfo: CurrentAlbumInfo,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionLabel("MusicBrainz")
        Spacer(Modifier.height(10.dp))

        AnimatedContent(
            targetState = enrichmentState,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
            label = "fetchedContent"
        ) { state ->
            when (state) {
                is AlbumEnrichmentState.FetchingFromNetwork,
                is AlbumEnrichmentState.CurrentInfoReady -> {
                    FetchingShimmerColumn()
                }

                is AlbumEnrichmentState.Preview -> {
                    FetchedDataColumn(state.enriched, currentInfo)
                }

                is AlbumEnrichmentState.FetchError -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(24.dp))
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun FetchingShimmerColumn() {
    Column {
        // Art placeholder
        ShimmerBox(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(10.dp))
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        repeat(5) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FetchedDataColumn(enriched: EnrichedAlbumData, current: CurrentAlbumInfo) {
    Column {
        // Cover art
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (enriched.coverArtBytes != null)
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(10.dp)
                        )
                    else Modifier
                )
                .align(Alignment.CenterHorizontally)
        ) {
            if (enriched.coverArtBytes != null) {
                AsyncImage(
                    model = enriched.coverArtBytes,
                    contentDescription = "Fetched cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        MetaField(
            label = "Title",
            value = enriched.title,
            highlight = !enriched.title.equals(current.albumTitle, ignoreCase = true)
        )
        MetaField(
            label = "Artist",
            value = enriched.artist,
            highlight = !enriched.artist.equals(current.artist, ignoreCase = true)
        )
        MetaField(
            label = "Year",
            value = enriched.year ?: "—",
            highlight = enriched.year != current.year
        )
        MetaField(
            label = "Tracks",
            value = enriched.tracks.size.toString(),
            highlight = enriched.tracks.size != current.trackCount
        )

        if (enriched.tracks.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Track list",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            enriched.tracks.take(8).forEach { track ->
                Text(
                    "${track.trackNumber}. ${track.title}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (enriched.tracks.size > 8) {
                Text(
                    "…and ${enriched.tracks.size - 8} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun MetaField(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
