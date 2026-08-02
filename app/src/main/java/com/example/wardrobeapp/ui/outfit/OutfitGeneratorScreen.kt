package com.example.wardrobeapp.ui.outfit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.TagOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The AI/smart outfit generator. Generates an outfit from the wardrobe, then offers
 * Save / Generate Another / Schedule (wear now or pick a date). When opened from the calendar
 * with a [date], generation uses that day's forecast and scheduling targets that day directly.
 */
@Composable
fun OutfitGeneratorScreen(
    date: Long? = null,
    onNavigateToManual: () -> Unit = {},
    onNavigateToAddItem: () -> Unit = {},
    viewModel: OutfitViewModel = viewModel(
        factory = OutfitViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var weatherAware by remember { mutableStateOf(true) }
    var selectedOccasion by remember { mutableStateOf<String?>(null) }
    var userPrompt by remember { mutableStateOf("") }
    // Collapsed by default: the outfit is the point of this screen, and the expanded options
    // otherwise fill the whole screen on first launch. A summary line shows what's set.
    var optionsExpanded by remember { mutableStateOf(false) }
    var showWearOptions by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun generateAndCollapse() {
        optionsExpanded = false
        viewModel.generate(weatherAware, selectedOccasion, userPrompt, date)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    if (showWearOptions) {
        WearOptionsDialog(
            outfitName = uiState.generatedOutfit?.name ?: "Outfit",
            onWearToday = {
                showWearOptions = false
                viewModel.requestSchedule(System.currentTimeMillis())
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (date != null) "Outfit for ${formatDateLong(date)}" else "Outfit Generator",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { optionsExpanded = !optionsExpanded }) {
                    Text(if (optionsExpanded) "Hide options" else "Options")
                    Icon(
                        if (optionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            // One scrollable list for options + result, so nothing is ever pushed off-screen
            // and unreachable regardless of screen size or how much is expanded.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (optionsExpanded) {
                    item {
                        WeatherAwareCard(
                            weatherAware = weatherAware,
                            onToggle = { weatherAware = it },
                            uiState = uiState
                        )
                    }
                    item {
                        Column {
                            Text("Occasion", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedOccasion == null,
                                    onClick = { selectedOccasion = null },
                                    label = { Text("Any") }
                                )
                                TagOptions.OCCASIONS.forEach { occasion ->
                                    FilterChip(
                                        selected = selectedOccasion == occasion,
                                        onClick = { selectedOccasion = occasion },
                                        label = { Text(occasion) }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = { userPrompt = it },
                            label = { Text("Anything specific? (optional)") },
                            placeholder = { Text("e.g. red and black, going swimming, job interview") },
                            supportingText = { Text("Guides AI suggestions; also nudges color/keyword matching without AI.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 1
                        )
                    }
                } else {
                    item {
                        Text(
                            text = optionsSummary(weatherAware, selectedOccasion, userPrompt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when {
                    uiState.isLoading -> item {
                        CenteredMessage { CircularProgressIndicator() }
                    }
                    uiState.error != null -> item {
                        CenteredMessage {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    uiState.wardrobeGap != null -> item {
                        CenteredMessage {
                            WardrobeGapMessage(
                                gap = uiState.wardrobeGap!!,
                                onAddItems = onNavigateToAddItem
                            )
                        }
                    }
                    uiState.generatedOutfit == null -> item {
                        CenteredMessage { EmptyOutfitMessage() }
                    }
                    else -> outfitResultItems(uiState.generatedOutfit!!)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (uiState.generatedOutfit == null) {
                Button(
                    onClick = { generateAndCollapse() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Outfit")
                }
                TextButton(
                    onClick = onNavigateToManual,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Or build one yourself, piece by piece")
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { generateAndCollapse() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Another")
                    }

                    OutlinedButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isLoading && !uiState.saved,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (uiState.saved) Icons.Default.Check else Icons.Default.BookmarkAdd,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.saved) "Saved" else "Save")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (date != null) viewModel.requestSchedule(date) else showWearOptions = true
                    },
                    enabled = !uiState.isLoading && uiState.scheduledFor == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (uiState.scheduledFor != null) Icons.Default.Check else Icons.Default.Event,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            uiState.scheduledFor != null -> "Planned for ${planDateLabel(uiState.scheduledFor!!)}"
                            date != null -> "Plan for ${planDateLabel(date)}"
                            else -> "Schedule"
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun WeatherAwareCard(
    weatherAware: Boolean,
    onToggle: (Boolean) -> Unit,
    uiState: OutfitUiState
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weather-aware", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "When on: adds a layer if it's cold, and scores every pick by " +
                            "how well its warmth fits the forecast for the day being planned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = weatherAware, onCheckedChange = onToggle)
            }
            if (weatherAware) {
                Spacer(Modifier.height(4.dp))
                val weatherText = uiState.lastWeatherUsed
                    ?.let { "Last generation used ${it.temperature.toInt()}°C, ${it.condition}" }
                    ?: "Weather for the planned day is fetched when you generate"
                Text(
                    weatherText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (uiState.weatherUnavailable) {
                    Text(
                        "Weather wasn't available last time (offline, or the date is past the " +
                            "7-day forecast) — the outfit was generated without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun optionsSummary(weatherAware: Boolean, occasion: String?, userPrompt: String): String {
    val parts = buildList {
        if (weatherAware) add("Weather-aware")
        occasion?.let { add(it) }
        if (userPrompt.isNotBlank()) add("\"$userPrompt\"")
    }
    return if (parts.isEmpty()) "No extra preferences set" else parts.joinToString(" · ")
}

/** Centers short states (loading/error/empty) in the visible list area instead of at the top. */
@Composable
private fun LazyItemScope.CenteredMessage(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillParentMaxHeight(0.6f),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Honest refusal state: the wardrobe can't satisfy the request (no swimwear for Swim, nothing
 * warm enough for the forecast). Points the user at adding items instead of showing an
 * unsuitable outfit.
 */
@Composable
private fun WardrobeGapMessage(gap: String, onAddItems: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Icon(
            Icons.Default.Checkroom,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your wardrobe needs a few additions",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = gap,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAddItems) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Items")
        }
    }
}

@Composable
private fun EmptyOutfitMessage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(8.dp))
        Text("No outfit yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap Generate to create an outfit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun LazyListScope.outfitResultItems(outfit: Outfit) {
    item {
        Column {
            Text(
                text = outfit.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (outfit.isAiGenerated) "AI suggestion" else "Smart pick",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = outfit.note ?: "No explanation available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (outfit.items.isEmpty()) {
        item {
            Text(
                "There aren't enough items in your wardrobe yet! Add some clothing first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(outfit.items, key = { it.id }) { item ->
            OutfitItemRow(item)
        }
    }
}

private fun formatDateLong(date: Long): String {
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(date))
}
