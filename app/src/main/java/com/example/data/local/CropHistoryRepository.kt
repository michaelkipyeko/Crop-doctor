package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import com.example.domain.model.CropDiagnosisRecord
import com.example.domain.model.CropDiagnosisResult
import com.example.domain.model.TreatmentStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CropHistoryRepository(
    private val dao: DiagnosisDao,
    private val appContext: Context
) {
    val allRecords: Flow<List<CropDiagnosisRecord>> = dao.observeAll()
        .map { list -> list.map { it.toDomain() } }

    fun observeRecord(id: Long): Flow<CropDiagnosisRecord?> = dao.observeById(id)
        .map { it?.toDomain() }

    suspend fun saveDiagnosis(result: CropDiagnosisResult, bitmap: Bitmap): Long {
        val imagePath = saveBitmapLocally(bitmap)
        val steps = result.immediateSteps.mapIndexed { index, stepText ->
            TreatmentStep(index = index, text = stepText, completed = false)
        }
        val entity = DiagnosisRecordEntity(
            cropName = result.cropName,
            diseaseName = result.diseaseName,
            scientificName = result.scientificName,
            pathogenType = result.pathogenType,
            confidencePercent = result.confidencePercent,
            severityName = result.severity.name,
            summary = result.summary,
            symptomsJoined = encodeStringList(result.symptoms),
            stepsJoined = encodeTreatmentSteps(steps),
            organicCareJoined = encodeStringList(result.organicCare),
            preventionJoined = encodeStringList(result.preventionTips),
            chlorophyllPercent = result.telemetry.chlorophyllPercent,
            chlorosisPercent = result.telemetry.chlorosisPercent,
            necrosisPercent = result.telemetry.necrosisPercent,
            imagePath = imagePath,
            isResolved = false,
            isPlaceholderAi = result.isPlaceholderAi,
            createdAtMillis = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    suspend fun toggleTreatmentStep(recordId: Long, stepIndex: Int) {
        val existing = dao.getById(recordId) ?: return
        val domain = existing.toDomain()
        val updated = domain.treatmentSteps.map { step ->
            if (step.index == stepIndex) step.copy(completed = !step.completed) else step
        }
        dao.updateSteps(recordId, encodeTreatmentSteps(updated))
    }

    suspend fun setResolved(recordId: Long, resolved: Boolean) {
        dao.updateResolved(recordId, resolved)
    }

    suspend fun deleteRecord(recordId: Long) {
        val existing = dao.getById(recordId)
        if (existing?.imagePath != null) {
            withContext(Dispatchers.IO) {
                runCatching { File(existing.imagePath).delete() }
            }
        }
        dao.deleteById(recordId)
    }

    private suspend fun saveBitmapLocally(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.filesDir, "scans").apply { mkdirs() }
            val file = File(dir, "leaf_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            file.absolutePath
        }.getOrNull()
    }
}
