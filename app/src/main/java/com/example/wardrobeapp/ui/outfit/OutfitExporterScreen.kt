package com.example.wardrobeapp.ui.outfit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.wardrobeapp.domain.model.ClothingItem
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import kotlinx.coroutines.launch

@Composable
fun OutfitExporterScreen(
    clothingIds: List<Long>,
    onBack: () -> Unit,
    viewModel: OutfitExporterViewModel
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()

    val coroutineScope =
        rememberCoroutineScope()

    var canvasWidth by remember {
        mutableStateOf(0)
    }

    var canvasHeight by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(clothingIds) {
        viewModel.initializeOutfit(clothingIds)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                )
                .onSizeChanged {
                    canvasWidth = it.width
                    canvasHeight = it.height
                }
        ) {
            uiState.clothing.forEach { clothingItem ->

                AsyncImage(
                    model = clothingItem.item.imagePath,
                    contentDescription = clothingItem.item.name,
                    contentScale = ContentScale.Fit,

                    modifier =
                        Modifier
                            .size(150.dp)
                            .offset {
                                IntOffset(
                                    clothingItem.position.x.roundToInt(),
                                    clothingItem.position.y.roundToInt()
                                )
                            }
                            .pointerInput(Unit) {

                                detectDragGestures {
                                        change,
                                        dragAmount ->
                                    change.consume()

                                    viewModel.moveClothing(
                                        clothingItem.item,
                                        dragAmount
                                    )
                                }

                            }
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            items(uiState.clothing) { item ->
                AsyncImage(
                    model = item.item.imagePath,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .size(90.dp)
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)

        ) {
            Button(
                modifier =
                    Modifier.weight(1f),
                enabled = !uiState.isSaving,
                onClick = {

                    coroutineScope.launch {

                        viewModel.startSaving()

                        try {
                            val bitmap =
                                createBitmap(
                                    context,
                                    uiState.clothing,
                                    canvasWidth,
                                    canvasHeight
                                )

                            saveBitmap(
                                context,
                                bitmap
                            )

                            viewModel.saveCompleted()

                        } catch (e: Exception) {

                            viewModel.saveFailed(
                                e.message
                                    ?: "Failed to save outfit"
                            )
                        }
                    }
                }
            ) {
                Text(
                    if (uiState.isSaving) {
                        "Saving..."
                    } else {
                        "Save"
                    }
                )
            }

            Button(
                modifier =
                    Modifier.weight(1f),
                enabled = !uiState.isSharing,
                onClick = {

                    coroutineScope.launch {

                        viewModel.startSharing()

                        try {
                            val bitmap =
                                createBitmap(
                                    context,
                                    uiState.clothing,
                                    canvasWidth,
                                    canvasHeight
                                )

                            shareBitmap(
                                context,
                                bitmap
                            )

                            viewModel.sharingCompleted()

                        } catch (e: Exception) {

                            viewModel.sharingFailed(
                                e.message
                                    ?: "Failed to share outfit"
                            )
                        }
                    }
                }
            ) {
                Text(
                    if (uiState.isSharing) {
                        "Sharing..."
                    } else {
                        "Share"
                    }
                )
            }
        }
    }

}

suspend fun createBitmap(
    context: Context,
    clothing: List<DraggableClothing>,
    canvasWidth: Int,
    canvasHeight: Int
): Bitmap {

    val bitmap =
        Bitmap.createBitmap(
            1080,
            1080,
            Bitmap.Config.ARGB_8888
        )

    val canvas =
        Canvas(bitmap)

    canvas.drawColor(
        Color.WHITE
    )

    if (canvasWidth <= 0 || canvasHeight <= 0) {
        return bitmap
    }

    val scaleX =
        1080f / canvasWidth

    val scaleY =
        1080f / canvasHeight

    val density =
        context.resources.displayMetrics.density

    val imageSizePx =
        150f * density

    clothing.forEach { clothingItem ->

        val request =
            ImageRequest.Builder(context)
                .data(clothingItem.item.imagePath)
                .allowHardware(false)
                .build()

        val result =
            context.imageLoader.execute(request)

        val drawable =
            result.drawable

        if (drawable != null) {

            val sourceBitmap =
                drawable.toBitmap()

            val scale =
                minOf(
                    imageSizePx / sourceBitmap.width,
                    imageSizePx / sourceBitmap.height
                )

            val scaledWidth =
                (sourceBitmap.width * scale)
                    .roundToInt()

            val scaledHeight =
                (sourceBitmap.height * scale)
                    .roundToInt()

            val scaledBitmap =
                sourceBitmap.scale(scaledWidth, scaledHeight)

            val x =
                clothingItem.position.x * scaleX

            val y =
                clothingItem.position.y * scaleY

            val exportScale =
                minOf(
                    scaleX,
                    scaleY
                )

            val finalWidth =
                (scaledBitmap.width * exportScale)
                    .roundToInt()

            val finalHeight =
                (scaledBitmap.height * exportScale)
                    .roundToInt()

            val finalBitmap =
                scaledBitmap.scale(finalWidth, finalHeight)

            canvas.drawBitmap(
                finalBitmap,
                x,
                y,
                null
            )

            if (finalBitmap != scaledBitmap) {
                finalBitmap.recycle()
            }

            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle()
            }

            sourceBitmap.recycle()
        }
    }

    return bitmap

}

fun saveBitmap(
    context: Context,
    bitmap: Bitmap
) {
    val file =
        File(
            context.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES
            ),
            "outfit.png"
        )

    FileOutputStream(file).use {
        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            it
        )
    }

    bitmap.recycle()

}

fun shareBitmap(
    context: Context,
    bitmap: Bitmap
) {
    val file =
        File(
            context.cacheDir,
            "shared_outfit_${System.currentTimeMillis()}.png"
        )

    file.outputStream().use {
        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            it
        )
    }

    bitmap.recycle()

    val uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

    val shareIntent =
        Intent(
            Intent.ACTION_SEND
        ).apply {
            type = "image/png"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    context.startActivity(
        Intent.createChooser(
            shareIntent,
            "Share outfit"
        )
    )

}
