package com.example.wardrobeapp.ui.outfit

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectOutfitUiState(
    val outfits: List<Outfit> = emptyList(),
    val isLoading: Boolean = true,
    /** Set when scheduling needs the user to confirm replacing an existing day plan. */
    val pendingSchedule: PendingSchedule? = null,
    /** True once the outfit is planned -- the screen then closes. */
    val isDone: Boolean = false
)

class SelectOutfitViewModel(
    private val outfitRepository: OutfitRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val date: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(SelectOutfitUiState())
    val uiState: StateFlow<SelectOutfitUiState> = _uiState.asStateFlow()

    private var pendingOutfit: Outfit? = null

    init {
        viewModelScope.launch {
            outfitRepository.getAllOutfits().collect { outfits ->
                _uiState.update { it.copy(outfits = outfits, isLoading = false) }
            }
        }
    }

    fun select(outfit: Outfit) {
        viewModelScope.launch {
            // date (this class's constructor param) is already a resolved day-key, from the
            // calendar -- floor it, don't re-run local-time-zone interpretation.
            val day = floorToUtcMidnight(date)
            val existing = runCatching { outfitRepository.getScheduledOutfit(day) }.getOrNull()
            if (existing != null && existing.id != outfit.id) {
                pendingOutfit = outfit
                _uiState.update { it.copy(pendingSchedule = PendingSchedule(day, existing.name)) }
            } else {
                performSchedule(outfit, day)
            }
        }
    }

    fun confirmPendingSchedule() {
        val pending = _uiState.value.pendingSchedule ?: return
        val outfit = pendingOutfit ?: return
        pendingOutfit = null
        _uiState.update { it.copy(pendingSchedule = null) }
        viewModelScope.launch { performSchedule(outfit, pending.date) }
    }

    fun dismissPendingSchedule() {
        pendingOutfit = null
        _uiState.update { it.copy(pendingSchedule = null) }
    }

    private suspend fun performSchedule(outfit: Outfit, day: Long) {
        runCatching {
            outfitRepository.scheduleOutfit(outfit.id, day)
            // Planning for today counts as wearing it (feeds anti-repetition scoring).
            if (day == normalizeToUtcDay(System.currentTimeMillis())) {
                wardrobeRepository.markItemsWorn(outfit.items.map { it.id })
            }
        }
        _uiState.update { it.copy(isDone = true) }
    }

    companion object {
        fun provideFactory(context: Context, date: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return SelectOutfitViewModel(
                        outfitRepository = container.outfitRepository,
                        wardrobeRepository = container.wardrobeRepository,
                        date = date
                    ) as T
                }
            }
    }
}

/**
 * Opened from the calendar: pick a saved outfit to plan for [date]. Tapping an outfit plans it
 * (confirming first if the day already has one) and returns to the calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectOutfitScreen(
    date: Long,
    onBack: () -> Unit,
    viewModel: SelectOutfitViewModel = viewModel(
        factory = SelectOutfitViewModel.provideFactory(LocalContext.current, date)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onBack()
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
                title = { Text("Wear on ${planDateLabel(date)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            uiState.outfits.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Bookmarks,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No saved outfits yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Save a generated outfit or create one in My Outfits first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Tap an outfit to plan it for this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.outfits, key = { it.id }) { outfit ->
                    // No delete here -- deletion lives in My Outfits; this screen stays focused.
                    SavedOutfitCard(
                        outfit = outfit,
                        onClick = { viewModel.select(outfit) }
                    )
                }
            }
        }
    }
}
