package com.example.wardrobeapp.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun CalendarScreen(onBack: () -> Unit) {
    Column {
        Text(text = "Calendar Screen")
        Button(onClick = onBack) { Text("Back") }
    }
}
