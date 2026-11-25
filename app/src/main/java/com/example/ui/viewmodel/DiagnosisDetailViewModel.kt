package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.CropDiagnosisAiRepository
import com.example.data.local.CropHistoryRepository
import com.example.domain.model.AgronomistMessage
import com.example.domain.model.CropDiagnosisRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "DiagnosisDetailVM"

class DiagnosisDetailViewModel(
    private val recordId: Long,
    private val aiRepository: CropDiagnosisAiRepository,
    private val historyRepository: CropHistoryRepository
) : ViewModel() {

    val record: StateFlow<CropDiagnosisRecord?> = historyRepository.observeRecord(recordId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    private val _messages = MutableStateFlow<List<AgronomistMessage>>(emptyList())
    val messages: StateFlow<List<AgronomistMessage>> = _messages.asStateFlow()

    private val _isAsking = MutableStateFlow(false)
    val isAsking: StateFlow<Boolean> = _isAsking.asStateFlow()

    private val _agronomistError = MutableStateFlow<String?>(null)
    val agronomistError: StateFlow<String?> = _agronomistError.asStateFlow()

    fun toggleStep(stepIndex: Int) {
        viewModelScope.launch {
            historyRepository.toggleTreatmentStep(recordId, stepIndex)
        }
    }

    fun toggleResolved(currentResolved: Boolean) {
        viewModelScope.launch {
            historyRepository.setResolved(recordId, !currentResolved)
        }
    }

    fun deleteRecord(onDeleted: () -> Unit) {
        viewModelScope.launch {
            historyRepository.deleteRecord(recordId)
            onDeleted()
        }
    }

    fun clearAgronomistError() {
        _agronomistError.value = null
    }

    fun askAgronomist(currentRecord: CropDiagnosisRecord, question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _isAsking.value) return

        val userMsg = AgronomistMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = trimmed,
            isPlaceholder = false
        )
        val replyId = UUID.randomUUID().toString()
        _messages.value = _messages.value + userMsg
        _agronomistError.value = null
        _isAsking.value = true

        viewModelScope.launch {
            try {
                var insertedReply = false
                aiRepository.askAgronomistStream(currentRecord, trimmed).collect { partial ->
                    val botMsg = AgronomistMessage(
                        id = replyId,
                        isUser = false,
                        text = partial,
                        isPlaceholder = false
                    )
                    if (!insertedReply) {
                        _messages.value = _messages.value + botMsg
                        insertedReply = true
                    } else {
                        _messages.value = _messages.value.map {
                            if (it.id == replyId) botMsg else it
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Agronomist streaming failed", e)
                _agronomistError.value = e.localizedMessage ?: "Failed to get agronomist response."
            } finally {
                _isAsking.value = false
            }
        }
    }

    class Factory(
        private val recordId: Long,
        private val aiRepository: CropDiagnosisAiRepository,
        private val historyRepository: CropHistoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiagnosisDetailViewModel(recordId, aiRepository, historyRepository) as T
        }
    }
}
