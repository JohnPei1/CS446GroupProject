package com.example.wardrobeapp.ui.outfit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import com.example.wardrobeapp.domain.model.Outfit
import java.io.File
import java.io.FileOutputStream

/**
 * Builds a shareable PNG of an outfit -- item photos in a simple grid under the outfit name.
 * This is the "Exporting outfit" feature originally planned for the team project (see meeting
 * notes) -- scoped down to a straightforward composed image rather than the background-removal
 * step that was researched but never actually implemented by anyone.
 *
 * Writes into the same app-private cache "images" directory already declared in file_paths.xml
 * for camera capture, and reuses the same FileProvider authority -- no new manifest entries
 * needed.
 */
object OutfitImageExporter {

    suspend fun export(context: Context, outfit: Outfit): Uri? {
        val bitmap = buildBitmap(context, outfit) ?: return null
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "outfit_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private suspend fun buildBitmap(context: Context, outfit: Outfit): Bitmap? {
        if (outfit.items.isEmpty()) return null

        val width = 1080
        val headerHeight = 170
        val footerHeight = 90
        val columns = 2
        val cellSize = width / columns
        val rows = (outfit.items.size + columns - 1) / columns
        val height = headerHeight + rows * cellSize + footerHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 56f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(outfit.name, width / 2f, 105f, titlePaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val placeholderPaint = Paint().apply { color = Color.rgb(230, 230, 230) }
        val padding = 16

        outfit.items.forEachIndexed { index, item ->
            val col = index % columns
            val row = index / columns
            val left = col * cellSize
            val top = headerHeight + row * cellSize

            val dest = RectF(
                (left + padding).toFloat(),
                (top + padding).toFloat(),
                (left + cellSize - padding).toFloat(),
                (top + cellSize - padding - 44).toFloat()
            )
            val itemBitmap = loadBitmap(context, item.imagePath)
            if (itemBitmap != null) {
                canvas.drawBitmapCropped(itemBitmap, dest)
            } else {
                canvas.drawRoundRect(dest, 12f, 12f, placeholderPaint)
            }
            canvas.drawText(item.category, left + cellSize / 2f, (top + cellSize - 12).toFloat(), labelPaint)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Made with Wardrobe", width / 2f, (height - 34).toFloat(), footerPaint)

        return bitmap
    }

    private suspend fun loadBitmap(context: Context, path: String): Bitmap? {
        if (path.isBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context).data(path).allowHardware(false).build()
            val drawable = context.imageLoader.execute(request).drawable ?: return null
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)
            bmp
        }.getOrNull()
    }

    /** Center-crop draw, matching Coil's ContentScale.Crop used everywhere else item photos show. */
    private fun Canvas.drawBitmapCropped(bitmap: Bitmap, dest: RectF) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destRatio = dest.width() / dest.height()
        val src = if (srcRatio > destRatio) {
            val newWidth = (bitmap.height * destRatio).toInt().coerceAtLeast(1)
            val xOffset = (bitmap.width - newWidth) / 2
            Rect(xOffset, 0, xOffset + newWidth, bitmap.height)
        } else {
            val newHeight = (bitmap.width / destRatio).toInt().coerceAtLeast(1)
            val yOffset = (bitmap.height - newHeight) / 2
            Rect(0, yOffset, bitmap.width, yOffset + newHeight)
        }
        drawBitmap(bitmap, src, dest, null)
    }
}
