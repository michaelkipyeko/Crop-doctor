package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.vision.LeafTelemetryAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CropDoctorTelemetryTest {

    @Test
    fun `healthy green bitmap produces high chlorophyll ratio`() {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(42, 138, 62))
        }
        val telemetry = LeafTelemetryAnalyzer.analyze(bitmap)
        assertTrue(telemetry.chlorophyllPercent >= 80)
        assertTrue(telemetry.leafCoveragePercent >= 80)
    }

    @Test
    fun `yellow chlorotic bitmap detects elevated chlorosis`() {
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(215, 195, 50))
        }
        val telemetry = LeafTelemetryAnalyzer.analyze(bitmap)
        assertTrue(telemetry.chlorosisPercent >= 50)
    }
}
