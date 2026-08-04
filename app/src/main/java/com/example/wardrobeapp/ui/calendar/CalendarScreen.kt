package com.example.wardrobeapp.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.WeatherInfo
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import com.example.wardrobeapp.ui.outfit.OutfitItemRow
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToPlanner: (Long) -> Unit,
    onSelectOutfit: (Long) -> Unit,
    viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = uiState.selectedDate
    )

    // Sync date picker state with UI state
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let {
            viewModel.onDateSelected(it)
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Compact header, matching the Outfit Generator and Settings screens
            item {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Weather Forecast Section
            item {
                ForecastSection(uiState.weatherForecast)
            }

            // Calendar Section
            item {
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    showModeToggle = false,
                    title = null,
                    headline = null
                )
            }

            // Selected Date Details
            item {
                SelectedDateDetails(
                    date = uiState.selectedDate,
                    outfit = uiState.scheduledOutfits[uiState.selectedDate],
                    weather = uiState.weatherForecast[uiState.selectedDate],
                    onGenerateOutfit = { onNavigateToPlanner(uiState.selectedDate) },
                    onSelectOutfit = { onSelectOutfit(uiState.selectedDate) }
                )
            }
        }
    }
}

@Composable
fun ForecastSection(forecast: Map<Long, WeatherInfo>) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text("7-Day Forecast", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sortedDates = forecast.keys.sorted()
            items(sortedDates) { date ->
                val weather = forecast[date]!!
                Card {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(formatDateShort(date), style = MaterialTheme.typography.labelSmall)
                        Text("${weather.temperature}°C", style = MaterialTheme.typography.bodyMedium)
                        Text(weather.condition, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedDateDetails(
    date: Long,
    outfit: Outfit?,
    weather: WeatherInfo?,
    onGenerateOutfit: () -> Unit,
    onSelectOutfit: () -> Unit
) {
    // date is always an already-resolved day-key by the time it reaches here (from
    // CalendarViewModel.selectedDate) -- floor it, don't re-run local-time-zone interpretation.
    val isPlannable = floorToUtcMidnight(date) >= normalizeToUtcDay(System.currentTimeMillis())

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Details for ${formatDateLong(date)}",
            style = MaterialTheme.typography.titleLarge
        )

        weather?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Weather: ${it.temperature}°C, ${it.condition}")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (outfit != null) {
            Text(
                text = outfit.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            outfit.items.forEach { item ->
                OutfitItemRow(item = item)
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (isPlannable) {
                // Re-planning is always allowed; the flows confirm before replacing this outfit.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onGenerateOutfit) { Text("Generate new") }
                    TextButton(onClick = onSelectOutfit) { Text("Choose saved") }
                }
            }
        } else {
            Text("No outfit planned for this day.")
            Spacer(modifier = Modifier.height(8.dp))
            if (isPlannable) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onGenerateOutfit) {
                        Text("Generate Outfit")
                    }
                    OutlinedButton(onClick = onSelectOutfit) {
                        Text("Choose Saved")
                    }
                }
            }
        }
    }
}

fun formatDateShort(date: Long): String {
    val sdf = SimpleDateFormat("EEE d", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(date))
}

fun formatDateLong(date: Long): String {
    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(date))
}

