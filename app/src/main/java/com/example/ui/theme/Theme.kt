package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CropDoctorDarkColorScheme = darkColorScheme(
    primary = MintPrimaryDark,
    onPrimary = OnMintPrimaryDark,
    primaryContainer = CanopyContainerDark,
    onPrimaryContainer = OnCanopyContainerDark,
    secondary = SageSecondaryDark,
    onSecondary = OnSageSecondaryDark,
    secondaryContainer = SoftSageContainerDark,
    onSecondaryContainer = OnSoftSageContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = OnAmberTertiaryDark,
    tertiaryContainer = WarmAmberContainerDark,
    onTertiaryContainer = OnWarmAmberContainerDark,
    background = DeepMossBackground,
    onBackground = OffWhiteOnDark,
    surface = ElevatedPineSurface,
    onSurface = OffWhiteOnDark,
    surfaceVariant = PineSurfaceVariant,
    onSurfaceVariant = MutedSageOnDark,
    outline = OutlineSubtleDark,
    error = CriticalRust,
    onError = OnForestPrimary,
    errorContainer = CriticalContainer
)

private val CropDoctorLightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = OnForestPrimary,
    primaryContainer = CanopyContainer,
    onPrimaryContainer = OnCanopyContainer,
    secondary = SageSecondary,
    onSecondary = OnSageSecondary,
    secondaryContainer = SoftSageContainer,
    onSecondaryContainer = OnSoftSageContainer,
    tertiary = TerracottaTertiary,
    onTertiary = OnTerracottaTertiary,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OnWarmAmberContainer,
    background = ParchmentBackground,
    onBackground = DeepLoamOnBackground,
    surface = CardSurface,
    onSurface = DeepLoamOnBackground,
    surfaceVariant = SurfaceVariantCream,
    onSurfaceVariant = OnSurfaceMuted,
    outline = OutlineSubtle,
    error = CriticalRust,
    onError = OnForestPrimary,
    errorContainer = CriticalContainer
)

val CropDoctorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CropDoctorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CropDoctorDarkColorScheme else CropDoctorLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CropDoctorTypography,
        shapes = CropDoctorShapes,
        content = content
    )
}
