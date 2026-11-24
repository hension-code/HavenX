package sh.haven.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = HavenGreen,
    onPrimary = Color(0xFF08210B),
    primaryContainer = HavenPrimaryContainerDark,
    onPrimaryContainer = HavenOnPrimaryContainerDark,
    secondary = HavenDarkGreen,
    secondaryContainer = HavenSecondaryContainerDark,
    onSecondaryContainer = HavenOnSecondaryContainerDark,
    background = HavenBackground,
    surface = HavenSurface,
    surfaceVariant = HavenSurfaceVariant,
    onSurfaceVariant = Color(0xFFA8B3C7),
    outlineVariant = HavenOutlineVariant,
    error = HavenError,
)

private val LightColorScheme = lightColorScheme(
    primary = HavenDarkGreen,
    primaryContainer = HavenPrimaryContainerLight,
    onPrimaryContainer = HavenOnPrimaryContainerLight,
    secondary = HavenGreen,
    background = HavenLightBackground,
    surface = HavenLightSurface,
    surfaceVariant = HavenLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4E5969),
    outlineVariant = HavenLightOutlineVariant,
)

@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HavenTypography,
        content = content,
    )
}
