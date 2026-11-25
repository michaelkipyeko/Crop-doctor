package com.example.domain.model

import androidx.annotation.DrawableRes

enum class DiseaseSeverity(val label: String) {
    HEALTHY("Healthy"),
    MODERATE("Moderate Risk"),
    CRITICAL("Critical Action")
}

data class LeafTelemetry(
    val chlorophyllPercent: Int,
    val chlorosisPercent: Int,
    val necrosisPercent: Int,
    val leafCoveragePercent: Int,
    val tissueSignal: String
)

data class LiveTriagePulse(
    val statusLabel: String,
    val suspectedCondition: String,
    val severity: DiseaseSeverity,
    val confidencePercent: Int,
    val quickTip: String,
    val isPlaceholderAi: Boolean = true
)

data class TreatmentStep(
    val index: Int,
    val text: String,
    val completed: Boolean = false
)

data class CropDiagnosisResult(
    val cropName: String,
    val diseaseName: String,
    val scientificName: String,
    val pathogenType: String,
    val confidencePercent: Int,
    val severity: DiseaseSeverity,
    val summary: String,
    val symptoms: List<String>,
    val immediateSteps: List<String>,
    val organicCare: List<String>,
    val preventionTips: List<String>,
    val telemetry: LeafTelemetry,
    val isPlaceholderAi: Boolean = true
)

data class CropDiagnosisRecord(
    val id: Long,
    val cropName: String,
    val diseaseName: String,
    val scientificName: String,
    val pathogenType: String,
    val confidencePercent: Int,
    val severity: DiseaseSeverity,
    val summary: String,
    val symptoms: List<String>,
    val treatmentSteps: List<TreatmentStep>,
    val organicCare: List<String>,
    val preventionTips: List<String>,
    val chlorophyllPercent: Int,
    val chlorosisPercent: Int,
    val necrosisPercent: Int,
    val imagePath: String?,
    val isResolved: Boolean,
    val isPlaceholderAi: Boolean,
    val createdAtMillis: Long
)

data class SampleSpecimen(
    val id: String,
    val title: String,
    val cropType: String,
    val visualHint: String,
    @param:DrawableRes val drawableRes: Int
)

data class DiseaseGuideItem(
    val id: String,
    val title: String,
    val pathogenCategory: String,
    val severity: DiseaseSeverity,
    val keySigns: String,
    val rapidAction: String
)

data class AgronomistMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isPlaceholder: Boolean = false
)
