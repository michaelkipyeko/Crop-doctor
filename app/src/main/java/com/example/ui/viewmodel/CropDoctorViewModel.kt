package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.ai.CropDiagnosisAiRepository
import com.example.data.local.CropHistoryRepository
import com.example.data.vision.LeafTelemetryAnalyzer
import com.example.domain.model.CropDiagnosisRecord
import com.example.domain.model.DiseaseGuideItem
import com.example.domain.model.DiseaseSeverity
import com.example.domain.model.LeafTelemetry
import com.example.domain.model.LiveTriagePulse
import com.example.domain.model.SampleSpecimen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CropDoctorVM"

sealed interface ScannerUiState {
    data object Idle : ScannerUiState
    data object Analyzing : ScannerUiState
    data class Error(val message: String) : ScannerUiState
}

data class LivePulseUiState(
    val telemetry: LeafTelemetry? = null,
    val pulse: LiveTriagePulse? = null,
    val isAiEvaluating: Boolean = false,
    val errorMessage: String? = null
)

class CropDoctorViewModel(
    private val aiRepository: CropDiagnosisAiRepository,
    private val historyRepository: CropHistoryRepository
) : ViewModel() {

    val historyRecords: StateFlow<List<CropDiagnosisRecord>> = historyRepository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val _scannerUiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val scannerUiState: StateFlow<ScannerUiState> = _scannerUiState.asStateFlow()

    private val _livePulseState = MutableStateFlow(LivePulseUiState())
    val livePulseState: StateFlow<LivePulseUiState> = _livePulseState.asStateFlow()

    private val _selectedCropHint = MutableStateFlow("Auto-Detect")
    val selectedCropHint: StateFlow<String> = _selectedCropHint.asStateFlow()

    private val _livePulseEnabled = MutableStateFlow(true)
    val livePulseEnabled: StateFlow<Boolean> = _livePulseEnabled.asStateFlow()

    private val isAnalyzingFrame = AtomicBoolean(false)
    private var livePulseJob: Job? = null
    private var lastTelemetryMs = 0L
    private var lastLiveAiCallMs = 0L

    val cropHints: List<String> = listOf(
        "Auto-Detect", "Tomato", "Maize", "Potato", "Pepper", "Wheat"
    )

    val sampleSpecimens: List<SampleSpecimen> = listOf(
        SampleSpecimen(
            id = "tomato_blight",
            title = "Tomato Early Blight",
            cropType = "Tomato",
            visualHint = "Concentric brown spots",
            drawableRes = R.drawable.img_sample_tomato_blight
        ),
        SampleSpecimen(
            id = "maize_rust",
            title = "Maize Common Rust",
            cropType = "Maize",
            visualHint = "Powdery rust pustules",
            drawableRes = R.drawable.img_sample_maize_rust
        ),
        SampleSpecimen(
            id = "healthy_leaf",
            title = "Healthy Pepper Leaf",
            cropType = "Pepper",
            visualHint = "Uniform chlorophyll",
            drawableRes = R.drawable.img_sample_healthy_leaf
        )
    )

    val diseaseGuides: List<DiseaseGuideItem> = listOf(
        DiseaseGuideItem(
            id = "early_blight",
            title = "Early Blight (Alternaria)",
            pathogenCategory = "Fungal • Tomato & Potato",
            severity = DiseaseSeverity.CRITICAL,
            keySigns = "Bullseye brown rings on older lower leaves with yellow halos.",
            rapidAction = "Prune lower leaves, water soil base only, apply copper or bio-fungicide."
        ),
        DiseaseGuideItem(
            id = "common_rust",
            title = "Common Rust (Puccinia)",
            pathogenCategory = "Fungal • Maize & Cereals",
            severity = DiseaseSeverity.MODERATE,
            keySigns = "Cinnamon-brown powdery pustules along upper and lower leaf blades.",
            rapidAction = "Scout neighboring rows; improve airflow; treat early if canopy >15% affected."
        ),
        DiseaseGuideItem(
            id = "bacterial_spot",
            title = "Bacterial Leaf Spot",
            pathogenCategory = "Bacterial • Pepper & Tomato",
            severity = DiseaseSeverity.MODERATE,
            keySigns = "Water-soaked dark specks that turn papery and tear in wind.",
            rapidAction = "Avoid handling wet foliage; apply copper bactericide and sanitize tools."
        )
    )

    fun selectCropHint(hint: String) {
        _selectedCropHint.value = hint
    }

    fun toggleLivePulse() {
        _livePulseEnabled.value = !_livePulseEnabled.value
    }

    fun clearScannerError() {
        _scannerUiState.value = ScannerUiState.Idle
    }

    fun onLiveCameraFrame(bitmap: Bitmap) {
        if (!_livePulseEnabled.value || _scannerUiState.value is ScannerUiState.Analyzing) {
            bitmap.recycle()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTelemetryMs < 900L || !isAnalyzingFrame.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }
        lastTelemetryMs = now

        livePulseJob = viewModelScope.launch {
            try {
                val telemetry = withContext(Dispatchers.Default) {
                    LeafTelemetryAnalyzer.analyze(bitmap)
                }

                if (telemetry.leafCoveragePercent < 15) {
                    _livePulseState.value = LivePulseUiState(
                        telemetry = telemetry,
                        pulse = LiveTriagePulse(
                            statusLabel = "Searching",
                            suspectedCondition = "Center a leaf inside the reticle",
                            severity = DiseaseSeverity.HEALTHY,
                            confidencePercent = 0,
                            quickTip = "Hold steady 15–25 cm from leaf surface",
                            isPlaceholderAi = false
                        ),
                        isAiEvaluating = false,
                        errorMessage = null
                    )
                    return@launch
                }

                val currentMs = System.currentTimeMillis()
                if (currentMs - lastLiveAiCallMs >= 5_000L) {
                    lastLiveAiCallMs = currentMs
                    _livePulseState.value = _livePulseState.value.copy(
                        telemetry = telemetry,
                        isAiEvaluating = true,
                        errorMessage = null
                    )
                    val pulse = aiRepository.analyzeLiveFramePulse(
                        bitmap = bitmap,
                        telemetry = telemetry,
                        cropHint = _selectedCropHint.value
                    )
                    _livePulseState.value = LivePulseUiState(
                        telemetry = telemetry,
                        pulse = pulse,
                        isAiEvaluating = false,
                        errorMessage = null
                    )
                } else {
                    _livePulseState.value = _livePulseState.value.copy(
                        telemetry = telemetry
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live frame Gemini triage failed", e)
                _livePulseState.value = _livePulseState.value.copy(
                    isAiEvaluating = false,
                    errorMessage = e.localizedMessage ?: "Live Gemini triage failed."
                )
            } finally {
                bitmap.recycle()
                isAnalyzingFrame.set(false)
            }
        }
    }

    fun diagnoseCapturedBitmap(bitmap: Bitmap, onSaved: (Long) -> Unit) {
        if (_scannerUiState.value is ScannerUiState.Analyzing) return
        livePulseJob?.cancel()
        viewModelScope.launch {
            _scannerUiState.value = ScannerUiState.Analyzing
            try {
                val telemetry = withContext(Dispatchers.Default) {
                    LeafTelemetryAnalyzer.analyze(bitmap)
                }
                val result = aiRepository.analyzeCropImage(
                    bitmap = bitmap,
                    cropHint = _selectedCropHint.value,
                    telemetry = telemetry
                )
                val savedId = historyRepository.saveDiagnosis(result, bitmap)
                _scannerUiState.value = ScannerUiState.Idle
                onSaved(savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Captured crop diagnosis failed", e)
                _scannerUiState.value = ScannerUiState.Error(
                    e.localizedMessage ?: "Could not analyze leaf image. Please try again."
                )
            }
        }
    }

    fun diagnoseSampleSpecimen(
        context: Context,
        specimen: SampleSpecimen,
        onSaved: (Long) -> Unit
    ) {
        if (_scannerUiState.value is ScannerUiState.Analyzing) return
        livePulseJob?.cancel()
        viewModelScope.launch {
            _scannerUiState.value = ScannerUiState.Analyzing
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val opts = BitmapFactory.Options().apply { inScaled = false }
                    BitmapFactory.decodeResource(context.resources, specimen.drawableRes, opts)
                } ?: error("Unable to load sample specimen image.")
                val telemetry = withContext(Dispatchers.Default) {
                    LeafTelemetryAnalyzer.analyze(bitmap)
                }
                val result = aiRepository.analyzeCropImage(
                    bitmap = bitmap,
                    cropHint = specimen.cropType,
                    telemetry = telemetry
                )
                val savedId = historyRepository.saveDiagnosis(result, bitmap)
                _scannerUiState.value = ScannerUiState.Idle
                onSaved(savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sample specimen Gemini diagnosis failed", e)
                _scannerUiState.value = ScannerUiState.Error(
                    e.localizedMessage ?: "Failed to analyze sample specimen."
                )
            }
        }
    }

    fun diagnosePickedUri(
        context: Context,
        uri: Uri,
        onSaved: (Long) -> Unit
    ) {
        if (_scannerUiState.value is ScannerUiState.Analyzing) return
        livePulseJob?.cancel()
        viewModelScope.launch {
            _scannerUiState.value = ScannerUiState.Analyzing
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeScaledBitmap(context, uri, maxDimension = 1024)
                }
                val telemetry = withContext(Dispatchers.Default) {
                    LeafTelemetryAnalyzer.analyze(bitmap)
                }
                val result = aiRepository.analyzeCropImage(
                    bitmap = bitmap,
                    cropHint = _selectedCropHint.value,
                    telemetry = telemetry
                )
                val savedId = historyRepository.saveDiagnosis(result, bitmap)
                _scannerUiState.value = ScannerUiState.Idle
                onSaved(savedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Picked photo Gemini diagnosis failed", e)
                _scannerUiState.value = ScannerUiState.Error(
                    e.localizedMessage ?: "Could not analyze the selected photo."
                )
            }
        }
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val maxSide = maxOf(w, h)
                if (maxSide > maxDimension) {
                    val scale = maxDimension.toFloat() / maxSide
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val maxSide = maxOf(raw.width, raw.height)
        return if (maxSide > maxDimension) {
            val scale = maxDimension.toFloat() / maxSide
            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
        } else {
            raw
        }
    }

    class Factory(
        private val aiRepository: CropDiagnosisAiRepository,
        private val historyRepository: CropHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CropDoctorViewModel(aiRepository, historyRepository) as T
        }
    }
}
