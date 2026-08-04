package com.example.wardrobeapp.ui.outfit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import com.example.wardrobeapp.ui.wardrobe.CATEGORIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualOutfitScreen(
    onExitClick: () -> Unit,
    viewModel: ManualOutfitViewModel = viewModel(
        factory = ManualOutfitViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showWearOptions by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Leave the screen once the outfit is saved or planned; the result shows up in My Outfits.
    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onExitClick()
    }

    if (showWearOptions) {
        WearOptionsDialog(
            outfitName = uiState.outfitName.trim().ifBlank { "My Outfit" },
            onWearToday = {
                showWearOptions = false
                viewModel.requestSchedule(normalizeToUtcDay(System.currentTimeMillis()))
            },
            onPickDate = {
                showWearOptions = false
                showDatePicker = true
            },
            onDismiss = { showWearOptions = false }
        )
    }
    if (showDatePicker) {
        PlanDatePickerDialog(
            onDismiss = { showDatePicker = false },
            onPick = { picked ->
                showDatePicker = false
                viewModel.requestSchedule(picked)
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
        topBar = {
            TopAppBar(
                title = { Text("Create Outfit") },
                navigationIcon = {
                    IconButton(onClick = onExitClick) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.outfitName,
                onValueChange = viewModel::onNameChanged,
                label = { Text("Outfit Name") },
                placeholder = { Text("e.g. Friday interview") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            uiState.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    uiState.items.isEmpty() -> Text(
                        text = "Your wardrobe is empty! Add some items first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CATEGORIES.forEach { category ->
                            val categoryItems = uiState.items.filter { it.category == category }
                            if (categoryItems.isNotEmpty()) {
                                item(key = category) {
                                    CategorySection(
                                        category = category,
                                        items = categoryItems,
                                        selectedItemIds = uiState.selectedItemIds,
                                        onItemClick = viewModel::toggleItem
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = selectionSummary(uiState.selectedItems),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))
            val hasSelection = uiState.selectedItemIds.isNotEmpty()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = viewModel::clearSelection,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
                OutlinedButton(
                    onClick = { viewModel.save() },
                    enabled = !uiState.isLoading && hasSelection,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showWearOptions = true },
                enabled = !uiState.isLoading && hasSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Event, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Schedule")
            }
        }
    }
}

private fun selectionSummary(items: List<ClothingItem>): String =
    if (items.isEmpty()) "Tap items to build your outfit" else items.joinToString(" · ") { it.name }
@Composable
private fun CategorySection(
    category: String,
    items: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    onItemClick: (ClothingItem) -> Unit
) {
    Column {
        Text(category, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.id }) { item ->
                SelectableItemCard(
                    item = item,
                    selected = item.id in selectedItemIds,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun SelectableItemCard(
    item: ClothingItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = item.imagePath,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery),
                placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}
