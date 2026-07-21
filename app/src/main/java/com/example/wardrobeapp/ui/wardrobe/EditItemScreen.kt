package com.example.wardrobeapp.ui.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    itemId: Long,
    viewModel: WardrobeViewModel,
    onExitClick: ()->Unit,
) {
    val item by viewModel.getItem(itemId).collectAsState(initial = null)

    val formState = remember { ClothingItemFormState() }

    val context = LocalContext.current

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    // Initialize state when item is loaded
    LaunchedEffect(item) {
        item?.let {
            formState.loadFrom(it)
            if (it.imagePath.isNotEmpty()) {
                imageUri = Uri.parse(it.imagePath)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (!success) {
            imageUri = null
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit Item")
                },
                navigationIcon = {
                    IconButton(onClick = onExitClick){Icon(imageVector = Icons.Default.Close, contentDescription = null) }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {

                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No Image Selected")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = { //take photo
                        val uri = createImageFile(context)
                        imageUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Take Photo")
                }

                Button(
                    onClick = {
                        // Launch gallery
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Gallery")
                }
            }

            ClothingItemFormFields(formState)

            Button(
                onClick = {
                    // Save item to database, carrying forward system-managed wear-tracking fields
                    val item = formState.toClothingItem(
                        id = itemId,
                        imagePath = imageUri?.toString() ?: "",
                        timesWorn = item?.timesWorn ?: 0,
                        lastWornDate = item?.lastWornDate,
                        dateAdded = item?.dateAdded ?: System.currentTimeMillis()
                    )
                    viewModel.addItem(item)
                    onExitClick()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Item")
            }
        }
    }
}

