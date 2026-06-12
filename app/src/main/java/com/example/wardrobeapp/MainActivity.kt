package com.example.wardrobeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.wardrobeapp.theme.WardrobeAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WardrobeAppTheme {
                WardrobeApp()
            }
        }
    }
}

@Composable
fun WardrobeApp() {
    Text(text = "Welcome to Wardrobe App!")
}
