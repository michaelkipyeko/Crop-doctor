package com.example.data.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.LeafTelemetry
import kotlin.math.roundToInt

/**
 * Lightweight on-device pixel analyzer that samples a downscaled grid of the leaf image
 * to quantify healthy green chlorophyll vs. yellowing chlorosis vs. dark necrotic lesions.
 */
object LeafTelemetryAnalyzer {

    fun analyze(bitmap: Bitmap): LeafTelemetry {
        val sampleW = 48
        val sampleH = 48
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        var leafPixels = 0
        var healthyGreen = 0
        var yellowChlorosis = 0
        var brownNecrosis = 0
        val total = sampleW * sampleH
        val hsv = FloatArray(3)

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val pixel = scaled.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]        // 0..360
                val sat = hsv[1]        // 0..1
                val value = hsv[2]      // 0..1

                val isLeafHue = hue in 15f..165f && sat > 0.16f && value > 0.12f
                val isDarkLesion = hue in 8f..45f && sat > 0.22f && value in 0.10f..0.46f

                if (isLeafHue || isDarkLesion) {
                    leafPixels++
                    when {
                        hue in 68f..160f && sat >= 0.22f && value >= 0.20f -> healthyGreen++
                        hue in 38f..68f && sat >= 0.24f && value >= 0.35f -> yellowChlorosis++
                        else -> brownNecrosis++
                    }
                }
            }
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        val coveragePct = ((leafPixels.toFloat() / total) * 100f).roundToInt().coerceIn(0, 100)
        if (leafPixels < 25) {
            return LeafTelemetry(
                chlorophyllPercent = 0,
                chlorosisPercent = 0,
                necrosisPercent = 0,
                leafCoveragePercent = coveragePct,
                tissueSignal = "Center a leaf inside the reticle"
            )
        }

        val greenPct = ((healthyGreen.toFloat() / leafPixels) * 100f).roundToInt().coerceIn(0, 100)
        val yellowPct = ((yellowChlorosis.toFloat() / leafPixels) * 100f).roundToInt().coerceIn(0, 100)
        val necrosisPct = (100 - greenPct - yellowPct).coerceIn(0, 100)

        val signal = when {
            necrosisPct >= 22 -> "Elevated necrotic spotting detected"
            yellowPct >= 22 -> "Chlorotic yellowing / halo pattern"
            greenPct >= 70 -> "Strong chlorophyll uniformity"
            else -> "Mixed foliage contrast — ready to inspect"
        }

        return LeafTelemetry(
            chlorophyllPercent = greenPct,
            chlorosisPercent = yellowPct,
            necrosisPercent = necrosisPct,
            leafCoveragePercent = coveragePct,
            tissueSignal = signal
        )
    }
}
