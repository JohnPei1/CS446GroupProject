package com.example.wardrobeapp.data.local

import android.content.Context
import android.net.Uri

/**
 * Helper class for saving and loading clothing photos on the device.
 */
class LocalImageStore(private val context: Context) {
    suspend fun saveImage(uri: Uri): String {
        // Implementation for saving image to internal storage
        return ""
    }

    fun loadImage(path: String): Uri? {
        // Implementation for loading image from internal storage
        return null
    }
}
