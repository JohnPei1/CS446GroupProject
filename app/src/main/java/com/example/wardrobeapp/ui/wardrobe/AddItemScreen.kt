package com.example.wardrobeapp.ui.wardrobe

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    onExitClick: ()->Unit,
    viewModel: WardrobeViewModel = viewModel(factory = WardrobeViewModel.Factory)
) {
    val formState = remember { ClothingItemFormState() }

    val context = LocalContext.current

    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var refreshKey by remember { mutableStateOf(0) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            refreshKey++
        } else {
            imageUri = null
        }
    }

    var cameraError by remember { mutableStateOf<String?>(null) }

    // The permission-request callback's isGranted can be stale (e.g. right after a fresh
    // install/reinstall on this device, it reported granted when the OS had it revoked) --
    // launching the camera without actually holding the permission throws a SecurityException
    // that would otherwise crash the whole screen instead of just failing this one action.
    fun launchCamera() {
        try {
            val uri = createImageFile(context)
            imageUri = uri
            cameraError = null
            cameraLauncher.launch(uri)
        } catch (e: SecurityException) {
            cameraError = "Camera permission isn't actually granted. Enable it in your phone's " +
                "Settings > Apps > Wardrobe > Permissions, then try again."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            cameraError = "Camera permission is needed to take a photo. Enable it in your " +
                "phone's Settings > Apps > Wardrobe > Permissions."
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
                    Text("Add Item")
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
                        key(refreshKey) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
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
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchCamera()
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
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

            cameraError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ClothingItemFormFields(formState)

            Button(
                onClick = {
                    // Save item to database
                    val item = formState.toClothingItem(
                        id = 0, // Room will auto-generate if we set it to 0 and the entity allows
                        imagePath = imageUri?.toString() ?: ""
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

fun createImageFile(context: Context): Uri {
    val imageDir = File(context.cacheDir, "images")
    if (!imageDir.exists()) {
        imageDir.mkdirs()
    }
    val imageFile = File(
        imageDir,
        "camera_photo_${System.currentTimeMillis()}.jpg"
    )
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}


