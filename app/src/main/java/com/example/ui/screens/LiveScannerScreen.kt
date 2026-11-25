package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.model.DiseaseSeverity
import com.example.domain.model.SampleSpecimen
import com.example.ui.components.BotanicalReticleOverlay
import com.example.ui.components.LiveGeminiBadge
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.CriticalRust
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.ModerateAmber
import com.example.ui.viewmodel.LivePulseUiState
import com.example.ui.viewmodel.ScannerUiState
import java.util.concurrent.Executors

private const val TAG = "LiveScannerScreen"

@Composable
fun LiveScannerScreen(
    scannerUiState: ScannerUiState,
    livePulseState: LivePulseUiState,
    cropHints: List<String>,
    selectedCropHint: String,
    livePulseEnabled: Boolean,
    sampleSpecimens: List<SampleSpecimen>,
    onSelectCropHint: (String) -> Unit,
    onToggleLivePulse: () -> Unit,
    onFrameBitmap: (Bitmap) -> Unit,
    onCaptureBitmap: (Bitmap) -> Unit,
    onPickPhoto: () -> Unit,
    onInspectSpecimen: (SampleSpecimen) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    var cameraUnavailableMessage by remember { mutableStateOf<String?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    val pulse = livePulseState.pulse
    val telemetry = livePulseState.telemetry
    val reticleColor = when (pulse?.severity) {
        DiseaseSeverity.CRITICAL -> CriticalRust
        DiseaseSeverity.MODERATE -> ModerateAmber
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission && cameraUnavailableMessage == null) {
            val lifecycleOwner = LocalLifecycleOwner.current
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }

            DisposableEffect(lifecycleOwner) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val listener = Runnable {
                    try {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
                                    try {
                                        val bmp = proxy.toBitmap()
                                        onFrameBitmap(bmp)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Frame conversion skipped", e)
                                    } finally {
                                        proxy.close()
                                    }
                                }
                            }

                        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        }

                        provider.unbindAll()
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            capture,
                            analysis
                        )
                        boundCamera = camera
                        imageCapture = capture
                    } catch (e: Exception) {
                        Log.e(TAG, "CameraX binding failed", e)
                        cameraUnavailableMessage = "No active camera sensor detected on this device."
                    }
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))
                onDispose {
                    runCatching { cameraProviderFuture.get().unbindAll() }
                }
            }

            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            BotanicalReticleOverlay(
                isScanningActive = livePulseEnabled,
                accentColor = reticleColor,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission Denied or No Camera Hardware Fallback
            CameraFallbackView(
                permissionMissing = !hasCameraPermission,
                hardwareMessage = cameraUnavailableMessage,
                sampleSpecimens = sampleSpecimens,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onPickPhoto = onPickPhoto,
                onInspectSpecimen = onInspectSpecimen
            )
        }

        // Top HUD: Crop Hints + Live Pulse Pill
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onToggleLivePulse)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("toggle_live_pulse_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = if (livePulseEnabled) HealthyGreen else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(
                                if (livePulseEnabled) R.string.scanner_live_pulse_on
                                else R.string.scanner_live_pulse_off
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }

                LiveGeminiBadge()
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cropHints) { hint ->
                    val selected = hint == selectedCropHint
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCropHint(hint) },
                        label = {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.56f),
                            labelColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color.White.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("crop_hint_${hint.lowercase()}")
                    )
                }
            }

            if (hasCameraPermission && cameraUnavailableMessage == null && livePulseEnabled && (pulse != null || livePulseState.errorMessage != null)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(
                        1.dp,
                        if (livePulseState.errorMessage != null) CriticalRust.copy(alpha = 0.7f)
                        else reticleColor.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (livePulseState.errorMessage != null) {
                            Text(
                                text = "Live AI Error: ${livePulseState.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CriticalRust
                            )
                        } else if (pulse != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = reticleColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = pulse.suspectedCondition,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (pulse.confidencePercent > 0) {
                                    SeverityBadge(severity = pulse.severity)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = telemetry?.tissueSignal ?: pulse.quickTip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.weight(1f)
                                )
                                if (telemetry != null && telemetry.leafCoveragePercent >= 12) {
                                    Text(
                                        text = "G:${telemetry.chlorophyllPercent}% Y:${telemetry.chlorosisPercent}% L:${telemetry.necrosisPercent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Camera Controls Dock
        if (hasCameraPermission && cameraUnavailableMessage == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPickPhoto,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_gallery_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.cd_open_gallery)
                        )
                    }

                    // Tactile Shutter Capture Button
                    Surface(
                        onClick = {
                            val capture = imageCapture ?: return@Surface
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        try {
                                            val raw = image.toBitmap()
                                            val rotated = rotateBitmapIfNeeded(raw, image.imageInfo.rotationDegrees)
                                            onCaptureBitmap(rotated)
                                        } finally {
                                            image.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e(TAG, "Capture failed", exception)
                                    }
                                }
                            )
                        },
                        enabled = scannerUiState !is ScannerUiState.Analyzing,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(3.dp, Color.White),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("scanner_capture_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = stringResource(R.string.cd_capture_leaf),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    val hasFlash = boundCamera?.cameraInfo?.hasFlashUnit() == true
                    IconButton(
                        onClick = {
                            if (hasFlash) {
                                val next = !torchEnabled
                                torchEnabled = next
                                boundCamera?.cameraControl?.enableTorch(next)
                            }
                        },
                        enabled = hasFlash,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_flash_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.06f),
                            disabledContentColor = Color.White.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = stringResource(R.string.cd_toggle_flash)
                        )
                    }
                }
            }
        }

        if (scannerUiState is ScannerUiState.Analyzing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Card(shape = MaterialTheme.shapes.large) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.scanner_analyzing),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        if (scannerUiState is ScannerUiState.Error) {
            AlertDialogError(
                message = scannerUiState.message,
                onDismiss = onDismissError
            )
        }
    }
}

@Composable
private fun CameraFallbackView(
    permissionMissing: Boolean,
    hardwareMessage: String?,
    sampleSpecimens: List<SampleSpecimen>,
    onRequestPermission: () -> Unit,
    onPickPhoto: () -> Unit,
    onInspectSpecimen: (SampleSpecimen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (permissionMissing) stringResource(R.string.scanner_permission_title)
            else "Camera Preview Unavailable",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hardwareMessage ?: stringResource(R.string.scanner_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (permissionMissing) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("grant_camera_permission_button")
            ) {
                Text(stringResource(R.string.scanner_grant_permission))
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = onPickPhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("fallback_pick_photo_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_pick_photo))
        }

        if (sampleSpecimens.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Or diagnose a sample specimen:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            sampleSpecimens.take(2).forEach { specimen ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onInspectSpecimen(specimen) }
                        .testTag("fallback_specimen_${specimen.id}"),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(specimen.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                specimen.visualHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertDialogError(
    message: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Issue") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
