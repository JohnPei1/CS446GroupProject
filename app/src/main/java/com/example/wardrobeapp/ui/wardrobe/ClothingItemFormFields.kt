package com.example.wardrobeapp.ui.wardrobe

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.TagOptions
import com.example.wardrobeapp.domain.model.WarmthLevels

/** Mutable form state for the fields shared by AddItemScreen and EditItemScreen. */
class ClothingItemFormState {
    var name by mutableStateOf("")
    var category by mutableStateOf(CATEGORIES.first())
    var brand by mutableStateOf("")
    var color by mutableStateOf("")
    var description by mutableStateOf("")
    var tags by mutableStateOf<Set<String>>(emptySet())
    var season by mutableStateOf(TagOptions.SEASONS.first())
    var colorFamily by mutableStateOf(TagOptions.COLOR_FAMILIES.first())
    var warmthLevel by mutableStateOf(3)
    var isWaterResistant by mutableStateOf(false)
    var isFavorite by mutableStateOf(false)

    fun loadFrom(item: ClothingItem) {
        name = item.name
        category = item.category
        brand = item.brand
        color = item.color
        description = item.description
        tags = item.tags.toSet()
        season = item.season
        colorFamily = item.colorFamily
        warmthLevel = item.warmthLevel
        isWaterResistant = item.isWaterResistant
        isFavorite = item.isFavorite
    }

    /** Builds the item to persist. [timesWorn]/[lastWornDate]/[dateAdded] must be carried
     * forward from the loaded item when editing -- there is no UI for these system-managed fields. */
    fun toClothingItem(
        id: Long,
        imagePath: String,
        timesWorn: Int = 0,
        lastWornDate: Long? = null,
        dateAdded: Long = System.currentTimeMillis()
    ) = ClothingItem(
        id = id,
        name = name,
        category = category,
        imagePath = imagePath,
        brand = brand,
        color = color,
        description = description,
        tags = tags.toList(),
        season = season,
        colorFamily = colorFamily,
        warmthLevel = warmthLevel,
        isWaterResistant = isWaterResistant,
        isFavorite = isFavorite,
        timesWorn = timesWorn,
        lastWornDate = lastWornDate,
        dateAdded = dateAdded
    )
}

val CATEGORIES = listOf("Tops", "Bottoms", "Footwear", "Outerwear", "Accessories")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClothingItemFormFields(state: ClothingItemFormState) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var seasonExpanded by remember { mutableStateOf(false) }
    var colorFamilyExpanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = state.name,
        onValueChange = { state.name = it },
        label = { Text("Item Name") },
        modifier = Modifier.fillMaxWidth()
    )

    ExposedDropdownMenuBox(
        expanded = categoryExpanded,
        onExpandedChange = { categoryExpanded = !categoryExpanded }
    ) {
        OutlinedTextField(
            value = state.category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
            CATEGORIES.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { state.category = option; categoryExpanded = false })
            }
        }
    }

    OutlinedTextField(
        value = state.brand,
        onValueChange = { state.brand = it },
        label = { Text("Brand") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = state.color,
        onValueChange = { state.color = it },
        label = { Text("Color") },
        modifier = Modifier.fillMaxWidth()
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            ExposedDropdownMenuBox(
                expanded = colorFamilyExpanded,
                onExpandedChange = { colorFamilyExpanded = !colorFamilyExpanded }
            ) {
                OutlinedTextField(
                    value = state.colorFamily,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Color Family") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(colorFamilyExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = colorFamilyExpanded, onDismissRequest = { colorFamilyExpanded = false }) {
                    TagOptions.COLOR_FAMILIES.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { state.colorFamily = option; colorFamilyExpanded = false })
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            ExposedDropdownMenuBox(
                expanded = seasonExpanded,
                onExpandedChange = { seasonExpanded = !seasonExpanded }
            ) {
                OutlinedTextField(
                    value = state.season,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Season") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(seasonExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = seasonExpanded, onDismissRequest = { seasonExpanded = false }) {
                    TagOptions.SEASONS.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { state.season = option; seasonExpanded = false })
                    }
                }
            }
        }
    }

    Column {
        Text("Tags", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TagOptions.OCCASIONS.forEach { tag ->
                FilterChip(
                    selected = tag in state.tags,
                    onClick = { state.tags = if (tag in state.tags) state.tags - tag else state.tags + tag },
                    label = { Text(tag) }
                )
            }
        }
    }

    Column {
        Text("Warmth Level (1 = light, 5 = heavy)", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { level ->
                FilterChip(
                    selected = state.warmthLevel == level,
                    onClick = { state.warmthLevel = level },
                    label = { Text(level.toString()) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Best for ${WarmthLevels.temperatureRangeLabel(state.warmthLevel)} — the outfit " +
                "generator matches this to the forecast",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Water resistant")
        Switch(checked = state.isWaterResistant, onCheckedChange = { state.isWaterResistant = it })
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Favorite")
        }
        Switch(checked = state.isFavorite, onCheckedChange = { state.isFavorite = it })
    }

    OutlinedTextField(
        value = state.description,
        onValueChange = { state.description = it },
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3
    )
}
