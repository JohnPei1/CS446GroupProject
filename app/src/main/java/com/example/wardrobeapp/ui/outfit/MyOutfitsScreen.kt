package com.example.wardrobeapp.ui.outfit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Home tab: shows today's planned outfit and the saved outfit library. Tapping a saved outfit
 * offers "wear today" / "wear later"; deleting removes it (and any day plans that used it).
 */
@Composable
fun MyOutfitsScreen(
    onNavigateToGenerator: () -> Unit,
    onCreateOutfit: () -> Unit,
    viewModel: MyOutfitsViewModel = viewModel(
        factory = MyOutfitsViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var wearTarget by remember { mutableStateOf<Outfit?>(null) }
    var datePickerTarget by remember { mutableStateOf<Outfit?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    wearTarget?.let { outfit ->
        WearOptionsDialog(
            outfitName = outfit.name,
            onWearToday = {
                wearTarget = null
                viewModel.requestWear(outfit, normalizeToUtcDay(System.currentTimeMillis()))
            },
            onPickDate = {
                wearTarget = null
                datePickerTarget = outfit
            },
            onDismiss = { wearTarget = null }
        )
    }
    datePickerTarget?.let { outfit ->
        PlanDatePickerDialog(
            onDismiss = { datePickerTarget = null },
            onPick = { picked ->
                datePickerTarget = null
                viewModel.requestWear(outfit, picked)
            }
        )
    }
    uiState.pendingSchedule?.let { pending ->
        ReplacePlanDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingSchedule,
            onDismiss = viewModel::dismissPendingSchedule
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateOutfit,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Outfit") }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "My Outfits",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    TodaySection(
                        todayOutfit = uiState.todayOutfit,
                        onGenerate = onNavigateToGenerator,
                        onRemovePlan = viewModel::removeTodayPlan
                    )
                }

                item {
                    Text(
                        text = "Saved Outfits",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.savedOutfits.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Icon(
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No saved outfits yet", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Save a generated outfit, or build one with New Outfit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.savedOutfits, key = { it.id }) { outfit ->
                        SavedOutfitCard(
                            outfit = outfit,
                            onClick = { wearTarget = outfit },
                            onDelete = { viewModel.delete(outfit) }
                        )
                    }
                    item {
                        Text(
                            text = "Tap an outfit to plan it for today or a future day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySection(
    todayOutfit: Outfit?,
    onGenerate: () -> Unit,
    onRemovePlan: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Today · ${formatTodayDate()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (todayOutfit != null) {
                    IconButton(onClick = onRemovePlan) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove today's plan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (todayOutfit != null) {
                Text(
                    text = todayOutfit.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                OutfitThumbnails(todayOutfit)
                if (todayOutfit.items.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = todayOutfit.items.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "Nothing planned for today",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Generate a fresh outfit, or tap a saved one below to wear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGenerate) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Outfit")
                }
            }
        }
    }
}

/** Card for a saved outfit; tapping it starts the wear/schedule flow. */
@Composable
fun SavedOutfitCard(
    outfit: Outfit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = outfit.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (outfit.items.isEmpty()) {
                            "Items no longer in your wardrobe"
                        } else {
                            outfit.items.joinToString(", ") { it.name }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete outfit",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            if (outfit.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutfitThumbnails(outfit)
            }
        }
    }
}

@Composable
private fun OutfitThumbnails(outfit: Outfit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(outfit.items, key = { it.id }) { item ->
            AsyncImage(
                model = item.imagePath,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery),
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

private fun formatTodayDate(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(normalizeToUtcDay(System.currentTimeMillis())))
}
