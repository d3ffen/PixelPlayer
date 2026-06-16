@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.viewmodel.AlbumArtPickerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ArtCandidate
import com.theveloper.pixelplay.presentation.viewmodel.ArtPickerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

/**
 * Full-screen bottom sheet for selecting album art.
 * Shows a searchable grid of candidates from fanart.tv, CoverArtArchive, and Deezer,
 * plus a "Choose from gallery" option for local images — matching PixelPlayer's M3 Expressive style.
 */
@Composable
fun AlbumArtPickerSheet(
    songs: List<Song>,
    albumName: String,
    artistName: String,
    onDismiss: () -> Unit,
    viewModel: AlbumArtPickerViewModel = hiltViewModel()
) {
    val pickerState by viewModel.state.collectAsStateWithLifecycle()
    val embedState by viewModel.enrichmentStateHolder.albumArtFetchState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedCandidate by remember { mutableStateOf<ArtCandidate?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingLocalUri by remember { mutableStateOf<Uri?>(null) }

    // Write-permission launcher (Android 11+)
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            viewModel.enrichmentStateHolder.onWritePermissionGranted(
                result.resultCode == Activity.RESULT_OK
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.enrichmentStateHolder.writePermissionRequest.collect { sender ->
            writePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingLocalUri = uri
            showConfirmDialog = true
        }
    }

    // Auto-start search on first open
    LaunchedEffect(albumName, artistName) {
        if (pickerState is ArtPickerState.Idle) {
            viewModel.searchArt(albumName, artistName)
        }
    }

    // Auto-dismiss on success after short delay
    LaunchedEffect(embedState) {
        if (embedState.isSuccess) {
            kotlinx.coroutines.delay(1200)
            viewModel.reset()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ImageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Choose Album Art",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // "Choose from gallery" button
                FilledIconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = "Choose from gallery",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Refresh/retry search
                FilledIconButton(
                    onClick = { viewModel.searchArt(albumName, artistName) },
                    enabled = pickerState !is ArtPickerState.Searching,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Content ─────────────────────────────────────────────────────
            AnimatedContent(
                targetState = when {
                    embedState.isLoading || pickerState is ArtPickerState.Applying -> "applying"
                    embedState.isSuccess -> "success"
                    embedState.errorMessage != null -> "embed_error"
                    else -> pickerState::class.simpleName ?: "idle"
                },
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "pickerContent",
                modifier = Modifier.fillMaxSize()
            ) { contentKey ->
                when (contentKey) {
                    "Searching" -> SearchingContent()
                    "Results" -> {
                        val candidates = (pickerState as? ArtPickerState.Results)?.candidates
                            ?: emptyList()
                        ResultsGrid(
                            candidates = candidates,
                            onSelect = { candidate ->
                                selectedCandidate = candidate
                                showConfirmDialog = true
                            }
                        )
                    }
                    "NoResults" -> {
                        val msg = (pickerState as? ArtPickerState.NoResults)?.message ?: ""
                        NoResultsContent(
                            message = msg,
                            onPickFromGallery = { galleryLauncher.launch("image/*") }
                        )
                    }
                    "applying" -> ApplyingContent()
                    "success" -> SuccessContent()
                    "embed_error" -> {
                        ErrorContent(
                            message = embedState.errorMessage ?: "Unknown error",
                            onRetry = { viewModel.searchArt(albumName, artistName) },
                            onPickFromGallery = { galleryLauncher.launch("image/*") }
                        )
                    }
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    // ── Confirm URL selection dialog ─────────────────────────────────────────
    if (showConfirmDialog) {
        val candidate = selectedCandidate
        val localUri = pendingLocalUri
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                selectedCandidate = null
                pendingLocalUri = null
            },
            icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
            title = {
                Text("Set as Album Art?", fontFamily = GoogleSansRounded)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        candidate != null -> {
                            AsyncImage(
                                model = candidate.url,
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Source: ${candidate.source}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        localUri != null -> {
                            AsyncImage(
                                model = localUri,
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "From your gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will permanently embed this image into all ${songs.size} " +
                        "audio file(s) in this album.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    when {
                        candidate != null -> viewModel.pickFromUrl(songs, candidate.url)
                        localUri != null -> viewModel.pickFromLocalUri(songs, localUri)
                    }
                    selectedCandidate = null
                    pendingLocalUri = null
                }) { Text("Set Art") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showConfirmDialog = false
                    selectedCandidate = null
                    pendingLocalUri = null
                }) { Text("Cancel") }
            }
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun ResultsGrid(
    candidates: List<ArtCandidate>,
    onSelect: (ArtCandidate) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(candidates, key = { it.url }) { candidate ->
            ArtCandidateCard(candidate = candidate, onClick = { onSelect(candidate) })
        }
    }
}

@Composable
private fun ArtCandidateCard(candidate: ArtCandidate, onClick: () -> Unit) {
    var loaded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        // Shimmer while loading
        if (!loaded) {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        }

        AsyncImage(
            model = candidate.url,
            contentDescription = "Album art from ${candidate.source}",
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize()
        )

        // Source badge (bottom-start)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = candidate.source,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = GoogleSansRounded,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchingContent() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
private fun NoResultsContent(message: String, onPickFromGallery: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ImageNotSupported,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFromGallery) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose from Gallery", fontFamily = GoogleSansRounded)
        }
    }
}

@Composable
private fun ApplyingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ContainedLoadingIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            "Embedding album art…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuccessContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Album art updated!",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPickFromGallery) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }
            Button(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}
