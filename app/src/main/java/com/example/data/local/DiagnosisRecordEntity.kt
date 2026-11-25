package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CropDiagnosisRecord
import com.example.domain.model.DiseaseSeverity
import com.example.domain.model.TreatmentStep

@Entity(tableName = "diagnosis_records")
data class DiagnosisRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cropName: String,
    val diseaseName: String,
    val scientificName: String,
    val pathogenType: String,
    val confidencePercent: Int,
    val severityName: String,
    val summary: String,
    val symptomsJoined: String,
    val stepsJoined: String,
    val organicCareJoined: String,
    val preventionJoined: String,
    val chlorophyllPercent: Int,
    val chlorosisPercent: Int,
    val necrosisPercent: Int,
    val imagePath: String?,
    val isResolved: Boolean,
    val isPlaceholderAi: Boolean,
    val createdAtMillis: Long
)

private const val LIST_SEPARATOR = "\n|||\n"
private const val STEP_DONE_PREFIX = "[1] "
private const val STEP_TODO_PREFIX = "[0] "

fun DiagnosisRecordEntity.toDomain(): CropDiagnosisRecord {
    val severity = DiseaseSeverity.entries.firstOrNull { it.name == severityName }
        ?: DiseaseSeverity.MODERATE
    val steps = stepsJoined
        .split(LIST_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapIndexed { idx, raw ->
            when {
                raw.startsWith(STEP_DONE_PREFIX) -> TreatmentStep(idx, raw.removePrefix(STEP_DONE_PREFIX), true)
                raw.startsWith(STEP_TODO_PREFIX) -> TreatmentStep(idx, raw.removePrefix(STEP_TODO_PREFIX), false)
                else -> TreatmentStep(idx, raw, false)
            }
        }
    return CropDiagnosisRecord(
        id = id,
        cropName = cropName,
        diseaseName = diseaseName,
        scientificName = scientificName,
        pathogenType = pathogenType,
        confidencePercent = confidencePercent,
        severity = severity,
        summary = summary,
        symptoms = symptomsJoined.split(LIST_SEPARATOR).filter { it.isNotBlank() },
        treatmentSteps = steps,
        organicCare = organicCareJoined.split(LIST_SEPARATOR).filter { it.isNotBlank() },
        preventionTips = preventionJoined.split(LIST_SEPARATOR).filter { it.isNotBlank() },
        chlorophyllPercent = chlorophyllPercent,
        chlorosisPercent = chlorosisPercent,
        necrosisPercent = necrosisPercent,
        imagePath = imagePath,
        isResolved = isResolved,
        isPlaceholderAi = isPlaceholderAi,
        createdAtMillis = createdAtMillis
    )
}

fun encodeTreatmentSteps(steps: List<TreatmentStep>): String =
    steps.joinToString(LIST_SEPARATOR) { step ->
        val prefix = if (step.completed) STEP_DONE_PREFIX else STEP_TODO_PREFIX
        "$prefix${step.text}"
    }

fun encodeStringList(items: List<String>): String =
    items.joinToString(LIST_SEPARATOR)
