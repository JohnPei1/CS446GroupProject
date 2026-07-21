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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Checkroom
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
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.TagOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun OutfitGeneratorScreen(
    date: Long? = null,
    viewModel: OutfitViewModel = viewModel(
        factory = OutfitViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var weatherAware by remember { mutableStateOf(true) }
    var selectedOccasion by remember { mutableStateOf<String?>(null) }
    var userPrompt by remember { mutableStateOf("") }
    var optionsExpanded by remember { mutableStateOf(true) }

    fun generateAndCollapse() {
        optionsExpanded = false
        viewModel.generate(weatherAware, selectedOccasion, userPrompt)
    }

    LaunchedEffect(date) {
        if (date != null) {
            viewModel.loadPlannedOutfit(date)
        } else {
            viewModel.loadPlannedOutfit(System.currentTimeMillis())
        }
    }

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
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            TextButton(onClick = { optionsExpanded = !optionsExpanded }) {
                Text(if (optionsExpanded) "Hide options" else "Options")
                Icon(
                    if (optionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        if (optionsExpanded) {
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Weather-aware", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Add warmer clothing when it's cold",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = weatherAware, onCheckedChange = { weatherAware = it })
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Occasion", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = userPrompt,
                onValueChange = { userPrompt = it },
                label = { Text("Anything specific? (optional)") },
                placeholder = { Text("e.g. red and black, going swimming, job interview") },
                supportingText = { Text("Guides AI suggestions; also nudges color/keyword matching without AI.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text(
                text = optionsSummary(weatherAware, selectedOccasion, userPrompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                uiState.generatedOutfit == null -> EmptyOutfitMessage()
                else -> OutfitResult(outfit = uiState.generatedOutfit!!)
            } }

        Spacer(Modifier.height(12.dp))
        if (uiState.generatedOutfit == null) {
            Button(
                onClick = { generateAndCollapse() },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Outfit")}
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
                    Text("Try Again")}

                Button(
                    onClick = { viewModel.save() },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.markWorn() },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Checkroom, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Mark as Worn")
            }
        } } }

private fun optionsSummary(weatherAware: Boolean, occasion: String?, userPrompt: String): String {
    val parts = buildList {
        if (weatherAware) add("Weather-aware")
        occasion?.let { add(it) }
        if (userPrompt.isNotBlank()) add("\"$userPrompt\"")
    }
    return if (parts.isEmpty()) "No extra preferences set" else parts.joinToString(" · ")
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

@Composable
private fun OutfitResult(outfit: Outfit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = outfit.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        outfit.note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        if (outfit.items.isEmpty()) {
            Text(
                "There aren't enough items in your wardrobe yet! Add some clothing first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(outfit.items, key = { it.id }) { item ->
                    OutfitItemRow(item)
                } } } } }

@Composable
private fun OutfitItemRow(item: ClothingItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth() ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
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
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    item.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                ) } } } }

private fun formatDateLong(date: Long): String {
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(date))
}
