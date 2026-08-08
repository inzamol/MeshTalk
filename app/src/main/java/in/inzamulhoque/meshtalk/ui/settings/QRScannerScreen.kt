package `in`.inzamulhoque.meshtalk.ui.settings

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@ExperimentalGetImage
@Composable
fun QRScannerScreen(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val image = InputImage.fromFilePath(context, it)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            barcodes[0].rawValue?.let { result ->
                                onResult(result)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("QRScanner", "Error processing gallery image", e)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(surfaceProvider)
                            }
                            
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            val scanner = BarcodeScanning.getClient()
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    if (barcodes.isNotEmpty()) {
                                                        barcodes[0].rawValue?.let { 
                                                            Log.d("QRScanner", "QR Detected: $it")
                                                            onResult(it)
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Center Frame
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .padding(2.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 4.dp.toPx()
                        val cornerSize = 40.dp.toPx()
                        val color = Color.White
                        
                        // Top Left
                        drawLine(color, Offset(0f, 0f), Offset(cornerSize, 0f), strokeWidth)
                        drawLine(color, Offset(0f, 0f), Offset(0f, cornerSize), strokeWidth)
                        
                        // Top Right
                        drawLine(color, Offset(size.width, 0f), Offset(size.width - cornerSize, 0f), strokeWidth)
                        drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerSize), strokeWidth)
                        
                        // Bottom Left
                        drawLine(color, Offset(0f, size.height), Offset(cornerSize, size.height), strokeWidth)
                        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - cornerSize), strokeWidth)
                        
                        // Bottom Right
                        drawLine(color, Offset(size.width, size.height), Offset(size.width - cornerSize, size.height), strokeWidth)
                        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - cornerSize), strokeWidth)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "Scan a peer's identity QR code",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small).padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Bottom Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LargeScannerButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        cameraControl?.enableTorch(isFlashOn)
                    },
                    icon = if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    label = "Flash"
                )

                LargeScannerButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "Gallery"
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@Composable
fun LargeScannerButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
