package com.example.wardrobeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wardrobeapp.ui.components.ErrorState
import com.example.wardrobeapp.ui.components.LoadingIndicator
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorState(
                message = uiState.error ?: "Something went wrong",
                onRetry = viewModel::clearError
            )
            else -> SettingsContent(
                uiState = uiState,
                onDarkModeToggled = viewModel::onDarkModeToggled,
                onUnitSystemSelected = viewModel::onUnitSystemSelected,
                onLocationChanged = viewModel::onLocationChanged,
                onAiEnabledToggled = viewModel::onAiEnabledToggled,
                onDeleteAiModel = viewModel::deleteAiModel
            )
        }
    }
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onDarkModeToggled: (Boolean) -> Unit,
    onUnitSystemSelected: (UnitSystem) -> Unit,
    onLocationChanged: (String) -> Unit,
    onAiEnabledToggled: (Boolean) -> Unit,
    onDeleteAiModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)

        // Dark mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Dark mode")
            Switch(
                checked = uiState.isDarkMode,
                onCheckedChange = onDarkModeToggled
            )
        }

        Divider()

        // Units
        Column {
            Text(text = "Units", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnitSystem.values().forEach { unit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        RadioButton(
                            selected = uiState.unitSystem == unit,
                            onClick = { onUnitSystemSelected(unit) }
                        )
                        Text(text = if (unit == UnitSystem.METRIC) "Metric (°C)" else "Imperial (°F)")
                    }
                }
            }
        }

        Divider()

        // Location
        Column {
            Text(text = "Location", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            var localLocation by remember { mutableStateOf(uiState.location) }

            OutlinedTextField(
                value = localLocation,
                onValueChange = { localLocation = it }, // Only update local state
                label = { Text("City or area") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onLocationChanged(localLocation) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onLocationChanged(localLocation)
                    }
                )
            )
        }

        Divider()

        // AI outfit suggestions (on-device only, opt-in)
        Column {
            Text(text = "AI Outfit Suggestions (Beta)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runs entirely on your device. Turning this on downloads a free, open-source " +
                    "AI model once (~270MB); your wardrobe data never leaves your phone.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Enable AI suggestions")
                Switch(
                    checked = uiState.isAiEnabled,
                    enabled = uiState.aiDownloadProgress == null,
                    onCheckedChange = onAiEnabledToggled
                )
            }

            uiState.aiDownloadProgress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Downloading AI model… ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            uiState.aiError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.isAiModelAvailable && uiState.aiDownloadProgress == null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDeleteAiModel) {
                    Text("Remove downloaded model")
                }
            }
        }
    }
}