package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DiseaseSeverity
import com.example.ui.theme.CriticalContainer
import com.example.ui.theme.CriticalRust
import com.example.ui.theme.HealthyContainer
import com.example.ui.theme.HealthyGreen
import com.example.ui.theme.ModerateAmber
import com.example.ui.theme.ModerateContainer

@Composable
fun LiveGeminiBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = stringResource(R.string.ai_live_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun PlaceholderAiChip(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = stringResource(R.string.ai_placeholder_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun SeverityBadge(
    severity: DiseaseSeverity,
    isResolved: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon, text) = when {
        isResolved -> Quad(
            HealthyContainer,
            HealthyGreen,
            Icons.Rounded.CheckCircle,
            "Recovered"
        )
        severity == DiseaseSeverity.HEALTHY -> Quad(
            HealthyContainer,
            HealthyGreen,
            Icons.Rounded.CheckCircle,
            severity.label
        )
        severity == DiseaseSeverity.MODERATE -> Quad(
            ModerateContainer,
            ModerateAmber,
            Icons.Rounded.WarningAmber,
            severity.label
        )
        else -> Quad(
            CriticalContainer,
            CriticalRust,
            Icons.Rounded.ErrorOutline,
            severity.label
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
fun TissueTelemetryBar(
    chlorophyllPercent: Int,
    chlorosisPercent: Int,
    necrosisPercent: Int,
    modifier: Modifier = Modifier
) {
    val total = (chlorophyllPercent + chlorosisPercent + necrosisPercent).coerceAtLeast(1)
    val greenWeight = (chlorophyllPercent.toFloat() / total).coerceAtLeast(0.02f)
    val yellowWeight = (chlorosisPercent.toFloat() / total).coerceAtLeast(0.02f)
    val rustWeight = (necrosisPercent.toFloat() / total).coerceAtLeast(0.02f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .weight(greenWeight)
                    .fillMaxSize()
                    .background(HealthyGreen)
            )
            Box(
                modifier = Modifier
                    .weight(yellowWeight)
                    .fillMaxSize()
                    .background(ModerateAmber)
            )
            Box(
                modifier = Modifier
                    .weight(rustWeight)
                    .fillMaxSize()
                    .background(CriticalRust)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TelemetryLegendDot(color = HealthyGreen, label = "Healthy", value = "$chlorophyllPercent%")
            TelemetryLegendDot(color = ModerateAmber, label = "Chlorosis", value = "$chlorosisPercent%")
            TelemetryLegendDot(color = CriticalRust, label = "Lesion", value = "$necrosisPercent%")
        }
    }
}

@Composable
private fun TelemetryLegendDot(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun BotanicalReticleOverlay(
    isScanningActive: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "scan_line")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_progress"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val boxSize = minOf(w, h) * 0.72f
        val left = (w - boxSize) / 2f
        val top = (h - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerLen = boxSize * 0.16f
        val strokePx = 4.dp.toPx()

        val bracketColor = accentColor.copy(alpha = 0.92f)

        // Top-left bracket
        drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLen), strokePx, StrokeCap.Round)
        // Top-right bracket
        drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right, top), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLen), strokePx, StrokeCap.Round)
        // Bottom-left bracket
        drawLine(bracketColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokePx, StrokeCap.Round)
        // Bottom-right bracket
        drawLine(bracketColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, bottom - cornerLen), Offset(right, bottom), strokePx, StrokeCap.Round)

        if (isScanningActive) {
            val scanY = top + (boxSize * progress)
            drawLine(
                color = accentColor.copy(alpha = 0.55f),
                start = Offset(left + 12.dp.toPx(), scanY),
                end = Offset(right - 12.dp.toPx(), scanY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
