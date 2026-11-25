package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.domain.model.CropDiagnosisRecord
import com.example.domain.model.CropDiagnosisResult
import com.example.domain.model.DiseaseSeverity
import com.example.domain.model.LeafTelemetry
import com.example.domain.model.LiveTriagePulse
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CropAiRepository"

interface CropDiagnosisAiRepository {
    suspend fun analyzeCropImage(
        bitmap: Bitmap,
        cropHint: String?,
        telemetry: LeafTelemetry
    ): CropDiagnosisResult

    suspend fun analyzeLiveFramePulse(
        bitmap: Bitmap,
        telemetry: LeafTelemetry,
        cropHint: String?
    ): LiveTriagePulse

    fun askAgronomistStream(
        diagnosis: CropDiagnosisRecord,
        question: String
    ): Flow<String>
}

/**
 * Live multimodal Gemini implementation backed by Firebase AI Logic (`Firebase.ai`).
 */
class FirebaseAiCropDiagnosisRepository : CropDiagnosisAiRepository {

    private val diagnosisSchema = Schema.obj(
        properties = mapOf(
            "cropName" to Schema.string(description = "Identified crop or plant species"),
            "diseaseName" to Schema.string(description = "Detected disease, pest damage, or Healthy Foliage"),
            "scientificName" to Schema.string(description = "Scientific pathogen name or 'No active pathogen detected'"),
            "pathogenType" to Schema.string(description = "e.g., Fungal Pathogen, Bacterial Pathogen, Viral, Nutrient Stress, or Healthy Tissue"),
            "confidencePercent" to Schema.integer(description = "Confidence score from 0 to 100"),
            "severity" to Schema.enumeration(
                values = listOf("HEALTHY", "MODERATE", "CRITICAL"),
                description = "Overall triage severity"
            ),
            "summary" to Schema.string(description = "Concise 2-sentence visual diagnosis summary"),
            "symptoms" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 concise visual symptoms on the foliage"
            ),
            "immediateSteps" to Schema.array(
                items = Schema.string(),
                description = "3 to 4 prioritized immediate treatment actions"
            ),
            "organicCare" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 organic or cultural care recommendations"
            ),
            "preventionTips" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 long-term prevention tips"
            )
        )
    )

    private val livePulseSchema = Schema.obj(
        properties = mapOf(
            "statusLabel" to Schema.string(description = "Short 1-2 word status like 'Live Triage'"),
            "suspectedCondition" to Schema.string(description = "Short crop + condition headline under 42 chars"),
            "severity" to Schema.enumeration(
                values = listOf("HEALTHY", "MODERATE", "CRITICAL"),
                description = "Live severity classification"
            ),
            "confidencePercent" to Schema.integer(description = "Confidence percentage 0 to 100"),
            "quickTip" to Schema.string(description = "One-line immediate field tip under 55 chars")
        )
    )

    private val diagnosisConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = diagnosisSchema
        }
    }

    private val diagnosisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = diagnosisConfig
        )
    }

    private val fastDiagnosisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = diagnosisConfig
        )
    }

    private val livePulseModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = livePulseSchema
            }
        )
    }

    private val agronomistModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    override suspend fun analyzeCropImage(
        bitmap: Bitmap,
        cropHint: String?,
        telemetry: LeafTelemetry
    ): CropDiagnosisResult {
        val scaled = withContext(Dispatchers.Default) {
            scaleBitmapForInference(bitmap, maxSide = 512)
        }
        val hintText = cropHint?.takeIf { it.isNotBlank() && it != "Auto-Detect" }
            ?: "Not specified (auto-detect from leaf)"

        val prompt = """
            You are an expert plant pathologist and field agronomist.
            Analyze the attached crop leaf image.
            Crop focus hint: $hintText.
            Leaf color telemetry: Chlorophyll ${telemetry.chlorophyllPercent}%, Chlorosis ${telemetry.chlorosisPercent}%, Necrotic lesion ${telemetry.necrosisPercent}%.
            Identify the crop, diagnose any visible disease/pest/deficiency (or confirm healthy),
            and provide concise, field-ready treatment steps, organic care, and prevention tips.
            Return ONLY JSON matching the schema.
        """.trimIndent()

        val input = content {
            image(scaled)
            text(prompt)
        }

        try {
            val jsonText = try {
                streamJsonResponse(diagnosisModel, input, timeoutMs = 45_000L)
            } catch (timeout: TimeoutCancellationException) {
                Log.w(TAG, "Primary gemini-3.8-flash diagnosis timed out; retrying with gemini-3.5-flash-lite", timeout)
                streamJsonResponse(fastDiagnosisModel, input, timeoutMs = 30_000L)
            }

            return withContext(Dispatchers.Default) {
                parseDiagnosisJson(jsonText, hintText, telemetry)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Crop diagnosis timed out", e)
            throw IllegalStateException("Diagnosis timed out. Please check your internet connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI diagnosis failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw IllegalStateException(e.localizedMessage ?: "Diagnosis failed (${e.javaClass.simpleName}).", e)
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    private suspend fun streamJsonResponse(
        model: com.google.firebase.ai.GenerativeModel,
        input: com.google.firebase.ai.type.Content,
        timeoutMs: Long
    ): String {
        val buffer = StringBuilder()
        withTimeout(timeoutMs) {
            model.generateContentStream(input).collect { chunk ->
                chunk.text?.let { buffer.append(it) }
            }
        }
        return buffer.toString()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty diagnostic response.")
    }

    private fun parseDiagnosisJson(
        jsonText: String,
        hintText: String,
        telemetry: LeafTelemetry
    ): CropDiagnosisResult {
        val json = JSONObject(jsonText)
        val severityStr = json.optString("severity", "MODERATE").uppercase()
        val severity = DiseaseSeverity.entries.firstOrNull { it.name == severityStr }
            ?: DiseaseSeverity.MODERATE

        return CropDiagnosisResult(
            cropName = json.optString("cropName", hintText),
            diseaseName = json.optString("diseaseName", "Unclassified Foliar Condition"),
            scientificName = json.optString("scientificName", "Unknown"),
            pathogenType = json.optString("pathogenType", "Visual Assessment"),
            confidencePercent = json.optInt("confidencePercent", 80).coerceIn(0, 100),
            severity = severity,
            summary = json.optString("summary", ""),
            symptoms = json.optJSONArray("symptoms").toStringList(),
            immediateSteps = json.optJSONArray("immediateSteps").toStringList(),
            organicCare = json.optJSONArray("organicCare").toStringList(),
            preventionTips = json.optJSONArray("preventionTips").toStringList(),
            telemetry = telemetry,
            isPlaceholderAi = false
        )
    }

    override suspend fun analyzeLiveFramePulse(
        bitmap: Bitmap,
        telemetry: LeafTelemetry,
        cropHint: String?
    ): LiveTriagePulse {
        val scaled = withContext(Dispatchers.Default) {
            scaleBitmapForInference(bitmap, maxSide = 384)
        }
        val hintText = cropHint?.takeIf { it.isNotBlank() && it != "Auto-Detect" } ?: "Auto-Detect"

        val prompt = """
            Rapid real-time crop leaf triage on this camera frame.
            Crop hint: $hintText.
            Telemetry: Green ${telemetry.chlorophyllPercent}%, Yellow ${telemetry.chlorosisPercent}%, Lesion ${telemetry.necrosisPercent}%.
            Return concise live triage JSON matching the schema.
        """.trimIndent()

        try {
            val jsonText = streamJsonResponse(
                livePulseModel,
                content {
                    image(scaled)
                    text(prompt)
                },
                timeoutMs = 25_000L
            )

            val json = JSONObject(jsonText)
            val severityStr = json.optString("severity", "MODERATE").uppercase()
            val severity = DiseaseSeverity.entries.firstOrNull { it.name == severityStr }
                ?: DiseaseSeverity.MODERATE

            return LiveTriagePulse(
                statusLabel = json.optString("statusLabel", "Live AI"),
                suspectedCondition = json.optString("suspectedCondition", "Foliage detected"),
                severity = severity,
                confidencePercent = json.optInt("confidencePercent", 75).coerceIn(0, 100),
                quickTip = json.optString("quickTip", "Tap capture for full treatment plan"),
                isPlaceholderAi = false
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Live frame triage timed out", e)
            throw IllegalStateException("Live scan timed out.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Live frame triage failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
    }

    override fun askAgronomistStream(
        diagnosis: CropDiagnosisRecord,
        question: String
    ): Flow<String> = flow {
        val prompt = """
            You are a practical, trustworthy field agronomist advising a grower.
            Context from their recent crop scan:
            - Crop: ${diagnosis.cropName}
            - Diagnosis: ${diagnosis.diseaseName} (${diagnosis.scientificName})
            - Pathogen Type: ${diagnosis.pathogenType}
            - Severity: ${diagnosis.severity.label} (${diagnosis.confidencePercent}% confidence)
            - Summary: ${diagnosis.summary}

            Grower's question: "$question"

            Provide a clear, actionable, concise answer (3 to 5 sentences max). Include safety or harvest-interval notes if relevant to sprays.
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                agronomistModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulated.append(chunkText)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Agronomist assistant returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Agronomist streaming timed out", e)
            throw IllegalStateException("Response timed out after 60 seconds. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agronomist streaming failed: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    private fun scaleBitmapForInference(source: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxSide) return source
        val scale = maxSide.toFloat() / largest.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { idx ->
            optString(idx).takeIf { it.isNotBlank() }
        }
    }
}
