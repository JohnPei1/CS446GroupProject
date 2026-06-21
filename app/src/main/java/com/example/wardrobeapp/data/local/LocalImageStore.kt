package com.example.wardrobeapp.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Helper class for saving and loading clothing photos on the device.
 */
class LocalImageStore(private val context: Context) {
    suspend fun saveImage(uri: Uri): String {
        // Implementation for saving image to internal storage
        // Save image as absolutePath/image_time
        val filename = "image_${System.currentTimeMillis()}";
        val destinationFile = File(context.filesDir, filename);
        return "${context.filesDir.absolutePath}/$filename";
    }

    fun loadImage(path: String): Bitmap {
        // Implementation for loading image from internal storage
        val imgFile= File(path);
        var imgBitmap: Bitmap? = null;
        if (imgFile.exists()){
            imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
        }
        else{
            throw error("Invalid Path");
        }
        return imgBitmap;
    }
}
