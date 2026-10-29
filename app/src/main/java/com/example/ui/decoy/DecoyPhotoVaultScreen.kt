package com.example.ui.decoy

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.example.ui.theme.WaLightTealPrimary
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class VaultPhoto(
    val file: File,
    val dateAdded: Long = file.lastModified()
)

/**
 * Decoy Mode Private Photo Vault
 * 
 * Opened exclusively when entering the decoy code (e.g. 0000=) in the Calculator.
 * Provides a real, secure offline photo vault where users can import, save, view,
 * and delete private photos away from their main gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyPhotoVaultScreen(
    onLockToCalculator: () -> Unit
) {
    val context = LocalContext.current
    val vaultDir = remember {
        File(context.filesDir, "decoy_vault_photos").apply { if (!exists()) mkdirs() }
    }

    var photos by remember {
        mutableStateOf(
            vaultDir.listFiles { file -> file.isFile && (file.extension == "jpg" || file.extension == "png") }
                ?.map { VaultPhoto(it) }
                ?.sortedByDescending { it.dateAdded }
                ?: emptyList()
        )
    }

    var viewingPhoto by remember { mutableStateOf<VaultPhoto?>(null) }

    // Android Zero-Permission Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val newFile = File(vaultDir, "VAULT_$timeStamp.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Refresh list
                photos = vaultDir.listFiles { file -> file.isFile && (file.extension == "jpg" || file.extension == "png") }
                    ?.map { VaultPhoto(it) }
                    ?.sortedByDescending { it.dateAdded }
                    ?: emptyList()

                Toast.makeText(context, "Photo safely saved to Decoy Vault", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        onLockToCalculator()
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = WaLightTealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Private Photo Vault",
                                color = Color(0xFF1E293B),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${photos.size} Private Encrypted Photos",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    // Quick Lock Button to return to Calculator
                    IconButton(
                        onClick = onLockToCalculator,
                        modifier = Modifier.testTag("decoy_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = Color(0xFF64748B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = WaLightTealPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_vault_photo_fab")
            ) {
                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Add Private Photo")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (photos.isEmpty()) {
                // Empty Vault Placeholder
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = WaLightTealPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = WaLightTealPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Decoy Photo Vault is Empty",
                        color = Color(0xFF1E293B),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Save personal private photos here away from your device gallery. Tap '+' to import photos.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WaLightTealPrimary)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Private Photo", color = Color.White)
                    }
                }
            } else {
                // Photo Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(photos, key = { it.file.absolutePath }) { photo ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { viewingPhoto = photo }
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(model = photo.file),
                                contentDescription = "Private Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // Full Screen Photo Viewer Dialog
    if (viewingPhoto != null) {
        val photo = viewingPhoto!!
        Dialog(onDismissRequest = { viewingPhoto = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Private View",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewingPhoto = null }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Image(
                        painter = rememberAsyncImagePainter(model = photo.file),
                        contentDescription = "Full Private Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    photo.file.delete()
                                    photos = photos.filter { it.file.absolutePath != photo.file.absolutePath }
                                    viewingPhoto = null
                                    Toast.makeText(context, "Photo removed from Vault", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF5252))
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Photo")
                        }
                    }
                }
            }
        }
    }
}
