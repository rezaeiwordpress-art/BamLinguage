package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.core.common.ThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = LapisPrimaryDark,
    onPrimary = LapisOnPrimaryDark,
    primaryContainer = LapisPrimaryContainerDark,
    onPrimaryContainer = LapisOnPrimaryContainerDark,
    secondary = SaffronSecondaryDark,
    onSecondary = SaffronOnSecondaryDark,
    secondaryContainer = SaffronSecondaryContainerDark,
    onSecondaryContainer = SaffronOnSecondaryContainerDark,
    tertiary = TurquoiseTertiaryDark,
    onTertiary = TurquoiseOnTertiaryDark,
    tertiaryContainer = TurquoiseTertiaryContainerDark,
    onTertiaryContainer = TurquoiseOnTertiaryContainerDark,
    background = NightBackgroundDark,
    onBackground = NightOnBackgroundDark,
    surface = NightBackgroundDark,
    onSurface = NightOnBackgroundDark,
    surfaceVariant = NightSurfaceDark,
    onSurfaceVariant = NightOnBackgroundDark.copy(alpha = 0.78f),
    surfaceContainerLow = NightSurfaceLowDark,
    surfaceContainer = NightSurfaceDark,
    surfaceContainerHigh = NightSurfaceHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LapisPrimaryLight,
    onPrimary = LapisOnPrimaryLight,
    primaryContainer = LapisPrimaryContainerLight,
    onPrimaryContainer = LapisOnPrimaryContainerLight,
    secondary = SaffronSecondaryLight,
    onSecondary = SaffronOnSecondaryLight,
    secondaryContainer = SaffronSecondaryContainerLight,
    onSecondaryContainer = SaffronOnSecondaryContainerLight,
    tertiary = TurquoiseTertiaryLight,
    onTertiary = TurquoiseOnTertiaryLight,
    tertiaryContainer = TurquoiseTertiaryContainerLight,
    onTertiaryContainer = TurquoiseOnTertiaryContainerLight,
    background = ParchmentBackgroundLight,
    onBackground = ParchmentOnBackgroundLight,
    surface = ParchmentBackgroundLight,
    onSurface = ParchmentOnBackgroundLight,
    surfaceVariant = ParchmentSurfaceLight,
    onSurfaceVariant = ParchmentOnBackgroundLight.copy(alpha = 0.75f),
    surfaceContainerLow = ParchmentSurfaceLowLight,
    surfaceContainer = ParchmentSurfaceLight,
    surfaceContainerHigh = ParchmentSurfaceHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
  )

val BamLinguageShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun BamLinguageTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = BamLinguageTypography,
    shapes = BamLinguageShapes,
    content = content,
  )
}
