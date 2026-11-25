package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AgronomistMessage
import com.example.domain.model.CropDiagnosisRecord
import com.example.ui.components.LiveGeminiBadge
import com.example.ui.components.PlaceholderAiChip
import com.example.ui.components.SeverityBadge
import com.example.ui.components.TissueTelemetryBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisDetailScreen(
    record: CropDiagnosisRecord?,
    messages: List<AgronomistMessage>,
    isAskingAgronomist: Boolean,
    agronomistError: String? = null,
    onBack: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onToggleResolved: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAskQuestion: (CropDiagnosisRecord, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var questionInput by remember { mutableStateOf("") }
    val quickPrompts = remember {
        listOf("How often to spray?", "Safe harvest interval?", "Will it spread nearby?")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.detail_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Leaf Photo + Core Diagnosis Header
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (record.imagePath != null) {
                                    AsyncImage(
                                        model = File(record.imagePath),
                                        contentDescription = record.diseaseName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Eco,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SeverityBadge(
                                        severity = record.severity,
                                        isResolved = record.isResolved
                                    )
                                    Text(
                                        text = "${record.confidencePercent}% confidence",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "${record.cropName} • ${record.pathogenType}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = record.diseaseName,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = record.scientificName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (record.isPlaceholderAi) {
                                    PlaceholderAiChip()
                                } else {
                                    LiveGeminiBadge()
                                }

                                Text(
                                    text = record.summary,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                FilledTonalButton(
                                    onClick = { onToggleResolved(record.isResolved) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("detail_toggle_resolved_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (record.isResolved) "Marked as Recovered (Tap to Reopen)"
                                        else stringResource(R.string.detail_mark_resolved)
                                    )
                                }
                            }
                        }
                    }
                }

                // Real On-Device Leaf Tissue Telemetry
                item {
                    SectionCard(
                        title = stringResource(R.string.detail_tissue_telemetry),
                        icon = Icons.Rounded.Eco
                    ) {
                        TissueTelemetryBar(
                            chlorophyllPercent = record.chlorophyllPercent,
                            chlorosisPercent = record.chlorosisPercent,
                            necrosisPercent = record.necrosisPercent
                        )
                    }
                }

                // Observed Symptoms
                if (record.symptoms.isNotEmpty()) {
                    item {
                        SectionCard(
                            title = "Key Visual Indicators",
                            icon = Icons.Rounded.Healing
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                record.symptoms.forEach { symptom ->
                                    Text(
                                        text = "• $symptom",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Treatment Action Plan (Room-persisted)
                if (record.treatmentSteps.isNotEmpty()) {
                    item {
                        SectionCard(
                            title = stringResource(R.string.detail_treatment_checklist),
                            icon = Icons.Rounded.CheckCircle
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                record.treatmentSteps.forEach { step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onToggleStep(step.index) }
                                            .padding(vertical = 4.dp)
                                            .testTag("treatment_step_${step.index}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = step.completed,
                                            onCheckedChange = { onToggleStep(step.index) }
                                        )
                                        Text(
                                            text = step.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                textDecoration = if (step.completed) TextDecoration.LineThrough else null
                                            ),
                                            color = if (step.completed) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Organic & Cultural Care
                if (record.organicCare.isNotEmpty()) {
                    item {
                        SectionCard(
                            title = stringResource(R.string.detail_organic_care),
                            icon = Icons.Rounded.Spa
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                record.organicCare.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Prevention & Monitoring
                if (record.preventionTips.isNotEmpty()) {
                    item {
                        SectionCard(
                            title = stringResource(R.string.detail_prevention),
                            icon = Icons.Rounded.Shield
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                record.preventionTips.forEach { tip ->
                                    Text(
                                        text = "• $tip",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Ask Field Agronomist (Placeholder AI Interface)
                item {
                    SectionCard(
                        title = stringResource(R.string.detail_ask_agronomist),
                        icon = Icons.Rounded.Healing
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(quickPrompts) { prompt ->
                                    AssistChip(
                                        onClick = { onAskQuestion(record, prompt) },
                                        label = { Text(prompt) },
                                        enabled = !isAskingAgronomist
                                    )
                                }
                            }

                            messages.forEach { msg ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (msg.isUser) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (msg.isUser) "You"
                                            else if (msg.isPlaceholder) "Field Agronomist (Sample)"
                                            else "Field Agronomist • Gemini",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            if (!agronomistError.isNullOrBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = agronomistError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = questionInput,
                                    onValueChange = { questionInput = it },
                                    placeholder = { Text(stringResource(R.string.detail_ask_hint)) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("agronomist_question_input")
                                )
                                Button(
                                    onClick = {
                                        val q = questionInput.trim()
                                        if (q.isNotEmpty()) {
                                            questionInput = ""
                                            onAskQuestion(record, q)
                                        }
                                    },
                                    enabled = !isAskingAgronomist && questionInput.isNotBlank(),
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("agronomist_send_button")
                                ) {
                                    if (isAskingAgronomist) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = stringResource(R.string.detail_send_question)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}
