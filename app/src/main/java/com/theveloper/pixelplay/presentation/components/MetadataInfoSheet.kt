package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.model.CurrentAlbumInfo
import com.theveloper.pixelplay.data.repository.MetadataEnrichmentRepository
import com.theveloper.pixelplay.presentation.viewmodel.EnrichmentState

/**
 * Two-column bottom sheet shown when the user taps "ⓘ" on the Album detail screen.
 *
 * Left column: current embedded metadata (always visible, loaded instantly).
 * Right column: MusicBrainz-fetched data (skeleton shimmer while loading,
 * real data when ready). Changed fields are highlighted in the tertiary color.
 *
 * A ⭐ "Apply & Embed" button appears only when fetched data is ready.
 * Tapping it shows a confirmation dialog before permanently writing tags.
 */
@Composable
fun MetadataInfoSheet(
    currentInfo: CurrentAlbumInfo?,
    enrichmentState: EnrichmentState,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {

        // ── Sheet handle + title ─────────────────────────────────────────────
        Text(
            text = "Album Info",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // ── Two-column layout: CURRENT | FETCHED ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // LEFT COLUMN — Current (always shown immediately)
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("Current")
                Spacer(Modifier.height(8.dp))

                if (currentInfo != null) {
                    // Current cover art
                    AsyncImage(
                        model = currentInfo.coverArtUri,
                        contentDescription = "Current cover",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .align(Alignment.CenterHorizontally),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))

                    MetaRow(label = "Title", value = currentInfo.albumTitle)
                    MetaRow(label = "Artist", value = currentInfo.artist)
                    MetaRow(label = "Year", value = currentInfo.year ?: "—")
                    MetaRow(label = "Tracks", value = "${currentInfo.trackCount}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    currentInfo.trackTitles.take(8).forEachIndexed { i, title ->
                        Text(
                            text = "${i + 1}. $title",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (currentInfo.trackTitles.size > 8) {
                        Text(
                            text = "…and ${currentInfo.trackTitles.size - 8} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Vertical divider
            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // RIGHT COLUMN — Fetched (skeleton while loading, real data when ready)
            Column(modifier = Modifier.weight(1f)) {
                SectionHeader("From MusicBrainz")
                Spacer(Modifier.height(8.dp))

                when (enrichmentState) {
                    is EnrichmentState.Loading -> {
                        ShimmerBox(width = 110.dp, height = 110.dp, shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.height(10.dp))
                        repeat(4) {
                            ShimmerBox(width = 140.dp, height = 14.dp)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    is EnrichmentState.Preview -> {
                        val enriched = enrichmentState.enriched

                        // Fetched cover art preview
                        if (enriched.coverArtBytes != null) {
                            AsyncImage(
                                model = enriched.coverArtBytes,
                                contentDescription = "Fetched cover",
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .align(Alignment.CenterHorizontally),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .align(Alignment.CenterHorizontally),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No art found", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        val cur = currentInfo
                        MetaRow(
                            label = "Title",
                            value = enriched.title,
                            highlight = cur != null && enriched.title != cur.albumTitle
                        )
                        MetaRow(
                            label = "Artist",
                            value = enriched.artist,
                            highlight = cur != null && enriched.artist != cur.artist
                        )
                        MetaRow(
                            label = "Year",
                            value = enriched.year ?: "—",
                            highlight = cur != null && enriched.year != cur.year
                        )
                        MetaRow(
                            label = "Tracks",
                            value = "${enriched.tracks.size}",
                            highlight = cur != null && enriched.tracks.size != cur.trackCount
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        enriched.tracks.take(8).forEach { track ->
                            Text(
                                text = "${track.trackNumber}. ${track.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (enriched.tracks.size > 8) {
                            Text(
                                text = "…and ${enriched.tracks.size - 8} more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is EnrichmentState.Error -> {
                        Text(
                            text = "Could not fetch:\n${enrichmentState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    else -> {}
                }
            }
        }

        // ── Star Apply Button — only shown when Preview is ready ─────────────
        AnimatedVisibility(
            visible = enrichmentState is EnrichmentState.Preview,
            enter = fadeIn() + slideInVertically { it / 2 }
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply & Embed Metadata")
                }
            }
        }

        // Applying / Done states
        if (enrichmentState is EnrichmentState.Applying) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Writing to files…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (enrichmentState is EnrichmentState.Done) {
            Spacer(Modifier.height(16.dp))
            Text(
                "✓ Metadata embedded successfully!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // ── Confirmation dialog before applying ───────────────────────────────────
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = { Icon(Icons.Filled.Star, contentDescription = null) },
            title = { Text("Apply Metadata?") },
            text = {
                Text(
                    "This will permanently overwrite the metadata tags in all audio files " +
                    "of this album. This cannot be undone. Proceed?"
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

// ── Small helper composables ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun MetaRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShimmerBox(width: Dp, height: Dp, shape: Shape = RoundedCornerShape(4.dp)) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    )
}
