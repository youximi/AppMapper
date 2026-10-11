package dev.youximi.appmapper

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun AppTheme(
    dynamicColorEnabled: Boolean,
    themeColor: AppThemeColor,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> themeColor.dark
        else -> themeColor.light
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

// Precomputed with Material Color Utilities 0.3.0, Tonal Spot, standard contrast.
// Seeds: blue #1E88E5, cyan #00838F, green #388E3C, orange #EF6C00, pink #D81B60.
internal enum class AppThemeColor(val label: String, val light: ColorScheme, val dark: ColorScheme) {
    Default("默认紫", lightColorScheme(), darkColorScheme()),
    Blue("蓝色",
        lightColorScheme(
            primary = Color(0xFF39608F), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E4FF), onPrimaryContainer = Color(0xFF1D4875),
            inversePrimary = Color(0xFFA3C9FE), secondary = Color(0xFF545F70),
            onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD7E3F8),
            onSecondaryContainer = Color(0xFF3C4858), tertiary = Color(0xFF6C5677),
            onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFF4D9FF),
            onTertiaryContainer = Color(0xFF533F5E), background = Color(0xFFF8F9FF),
            onBackground = Color(0xFF191C20), surface = Color(0xFFF8F9FF),
            onSurface = Color(0xFF191C20), surfaceVariant = Color(0xFFDFE2EB),
            onSurfaceVariant = Color(0xFF43474E), inverseSurface = Color(0xFF2E3035),
            inverseOnSurface = Color(0xFFEFF0F7), outline = Color(0xFF73777F),
            outlineVariant = Color(0xFFC3C6CF), surfaceBright = Color(0xFFF8F9FF),
            surfaceDim = Color(0xFFD8DAE0), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF2F3FA), surfaceContainer = Color(0xFFECEDF4),
            surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE1E2E8),
        ),
        darkColorScheme(
            primary = Color(0xFFA3C9FE), onPrimary = Color(0xFF00315B),
            primaryContainer = Color(0xFF1D4875), onPrimaryContainer = Color(0xFFD3E4FF),
            inversePrimary = Color(0xFF39608F), secondary = Color(0xFFBBC7DB),
            onSecondary = Color(0xFF263141), secondaryContainer = Color(0xFF3C4858),
            onSecondaryContainer = Color(0xFFD7E3F8), tertiary = Color(0xFFD8BDE3),
            onTertiary = Color(0xFF3C2947), tertiaryContainer = Color(0xFF533F5E),
            onTertiaryContainer = Color(0xFFF4D9FF), background = Color(0xFF111418),
            onBackground = Color(0xFFE1E2E8), surface = Color(0xFF111418),
            onSurface = Color(0xFFE1E2E8), surfaceVariant = Color(0xFF43474E),
            onSurfaceVariant = Color(0xFFC3C6CF), inverseSurface = Color(0xFFE1E2E8),
            inverseOnSurface = Color(0xFF2E3035), outline = Color(0xFF8D9199),
            outlineVariant = Color(0xFF43474E), surfaceBright = Color(0xFF37393E),
            surfaceDim = Color(0xFF111418), surfaceContainerLowest = Color(0xFF0C0E13),
            surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
            surfaceContainerHigh = Color(0xFF272A2F), surfaceContainerHighest = Color(0xFF32353A),
        ),
    ),
    Cyan("青色",
        lightColorScheme(
            primary = Color(0xFF006972), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF9DF0FB), onPrimaryContainer = Color(0xFF004F56),
            inversePrimary = Color(0xFF81D3DF), secondary = Color(0xFF4A6366),
            onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFCDE7EB),
            onSecondaryContainer = Color(0xFF324B4E), tertiary = Color(0xFF515E7D),
            onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFD9E2FF),
            onTertiaryContainer = Color(0xFF3A4664), background = Color(0xFFF5FAFB),
            onBackground = Color(0xFF171D1E), surface = Color(0xFFF5FAFB),
            onSurface = Color(0xFF171D1E), surfaceVariant = Color(0xFFDBE4E6),
            onSurfaceVariant = Color(0xFF3F484A), inverseSurface = Color(0xFF2B3132),
            inverseOnSurface = Color(0xFFECF2F3), outline = Color(0xFF6F797A),
            outlineVariant = Color(0xFFBEC8CA), surfaceBright = Color(0xFFF5FAFB),
            surfaceDim = Color(0xFFD5DBDC), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFEFF5F5), surfaceContainer = Color(0xFFE9EFF0),
            surfaceContainerHigh = Color(0xFFE3E9EA), surfaceContainerHighest = Color(0xFFDEE4E4),
        ),
        darkColorScheme(
            primary = Color(0xFF81D3DF), onPrimary = Color(0xFF00363C),
            primaryContainer = Color(0xFF004F56), onPrimaryContainer = Color(0xFF9DF0FB),
            inversePrimary = Color(0xFF006972), secondary = Color(0xFFB1CBCF),
            onSecondary = Color(0xFF1C3437), secondaryContainer = Color(0xFF324B4E),
            onSecondaryContainer = Color(0xFFCDE7EB), tertiary = Color(0xFFB9C6EA),
            onTertiary = Color(0xFF23304D), tertiaryContainer = Color(0xFF3A4664),
            onTertiaryContainer = Color(0xFFD9E2FF), background = Color(0xFF0E1415),
            onBackground = Color(0xFFDEE4E4), surface = Color(0xFF0E1415),
            onSurface = Color(0xFFDEE4E4), surfaceVariant = Color(0xFF3F484A),
            onSurfaceVariant = Color(0xFFBEC8CA), inverseSurface = Color(0xFFDEE4E4),
            inverseOnSurface = Color(0xFF2B3132), outline = Color(0xFF899294),
            outlineVariant = Color(0xFF3F484A), surfaceBright = Color(0xFF343A3B),
            surfaceDim = Color(0xFF0E1415), surfaceContainerLowest = Color(0xFF090F10),
            surfaceContainerLow = Color(0xFF171D1E), surfaceContainer = Color(0xFF1B2122),
            surfaceContainerHigh = Color(0xFF252B2C), surfaceContainerHighest = Color(0xFF303637),
        ),
    ),
    Green("绿色",
        lightColorScheme(
            primary = Color(0xFF3B6939), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFBCF0B4), onPrimaryContainer = Color(0xFF245024),
            inversePrimary = Color(0xFFA1D39A), secondary = Color(0xFF52634F),
            onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFD5E8CE),
            onSecondaryContainer = Color(0xFF3B4B38), tertiary = Color(0xFF38656A),
            onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFBCEBF0),
            onTertiaryContainer = Color(0xFF1F4D52), background = Color(0xFFF7FBF1),
            onBackground = Color(0xFF191D17), surface = Color(0xFFF7FBF1),
            onSurface = Color(0xFF191D17), surfaceVariant = Color(0xFFDEE5D8),
            onSurfaceVariant = Color(0xFF424940), inverseSurface = Color(0xFF2D322C),
            inverseOnSurface = Color(0xFFEFF2E9), outline = Color(0xFF72796F),
            outlineVariant = Color(0xFFC2C9BD), surfaceBright = Color(0xFFF7FBF1),
            surfaceDim = Color(0xFFD8DBD2), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF1F5EB), surfaceContainer = Color(0xFFECEFE6),
            surfaceContainerHigh = Color(0xFFE6E9E0), surfaceContainerHighest = Color(0xFFE0E4DB),
        ),
        darkColorScheme(
            primary = Color(0xFFA1D39A), onPrimary = Color(0xFF0A390F),
            primaryContainer = Color(0xFF245024), onPrimaryContainer = Color(0xFFBCF0B4),
            inversePrimary = Color(0xFF3B6939), secondary = Color(0xFFBACCB3),
            onSecondary = Color(0xFF253423), secondaryContainer = Color(0xFF3B4B38),
            onSecondaryContainer = Color(0xFFD5E8CE), tertiary = Color(0xFFA0CFD4),
            onTertiary = Color(0xFF00363B), tertiaryContainer = Color(0xFF1F4D52),
            onTertiaryContainer = Color(0xFFBCEBF0), background = Color(0xFF10140F),
            onBackground = Color(0xFFE0E4DB), surface = Color(0xFF10140F),
            onSurface = Color(0xFFE0E4DB), surfaceVariant = Color(0xFF424940),
            onSurfaceVariant = Color(0xFFC2C9BD), inverseSurface = Color(0xFFE0E4DB),
            inverseOnSurface = Color(0xFF2D322C), outline = Color(0xFF8C9388),
            outlineVariant = Color(0xFF424940), surfaceBright = Color(0xFF363A34),
            surfaceDim = Color(0xFF10140F), surfaceContainerLowest = Color(0xFF0B0F0A),
            surfaceContainerLow = Color(0xFF191D17), surfaceContainer = Color(0xFF1D211B),
            surfaceContainerHigh = Color(0xFF272B25), surfaceContainerHighest = Color(0xFF323630),
        ),
    ),
    Orange("橙色",
        lightColorScheme(
            primary = Color(0xFF8C4E29), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBCA), onPrimaryContainer = Color(0xFF703714),
            inversePrimary = Color(0xFFFFB68F), secondary = Color(0xFF765848),
            onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFFFDBCA),
            onSecondaryContainer = Color(0xFF5C4132), tertiary = Color(0xFF636032),
            onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFEAE5AB),
            onTertiaryContainer = Color(0xFF4B481D), background = Color(0xFFFFF8F6),
            onBackground = Color(0xFF221A15), surface = Color(0xFFFFF8F6),
            onSurface = Color(0xFF221A15), surfaceVariant = Color(0xFFF4DED4),
            onSurfaceVariant = Color(0xFF52443D), inverseSurface = Color(0xFF382E29),
            inverseOnSurface = Color(0xFFFFEDE6), outline = Color(0xFF85746B),
            outlineVariant = Color(0xFFD7C2B9), surfaceBright = Color(0xFFFFF8F6),
            surfaceDim = Color(0xFFE8D7CF), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFF1EB), surfaceContainer = Color(0xFFFCEAE3),
            surfaceContainerHigh = Color(0xFFF6E5DD), surfaceContainerHighest = Color(0xFFF0DFD8),
        ),
        darkColorScheme(
            primary = Color(0xFFFFB68F), onPrimary = Color(0xFF532201),
            primaryContainer = Color(0xFF703714), onPrimaryContainer = Color(0xFFFFDBCA),
            inversePrimary = Color(0xFF8C4E29), secondary = Color(0xFFE6BEAB),
            onSecondary = Color(0xFF432B1D), secondaryContainer = Color(0xFF5C4132),
            onSecondaryContainer = Color(0xFFFFDBCA), tertiary = Color(0xFFCEC991),
            onTertiary = Color(0xFF343208), tertiaryContainer = Color(0xFF4B481D),
            onTertiaryContainer = Color(0xFFEAE5AB), background = Color(0xFF1A120D),
            onBackground = Color(0xFFF0DFD8), surface = Color(0xFF1A120D),
            onSurface = Color(0xFFF0DFD8), surfaceVariant = Color(0xFF52443D),
            onSurfaceVariant = Color(0xFFD7C2B9), inverseSurface = Color(0xFFF0DFD8),
            inverseOnSurface = Color(0xFF382E29), outline = Color(0xFF9F8D84),
            outlineVariant = Color(0xFF52443D), surfaceBright = Color(0xFF413732),
            surfaceDim = Color(0xFF1A120D), surfaceContainerLowest = Color(0xFF140C09),
            surfaceContainerLow = Color(0xFF221A15), surfaceContainer = Color(0xFF271E19),
            surfaceContainerHigh = Color(0xFF322823), surfaceContainerHighest = Color(0xFF3D332E),
        ),
    ),
    Pink("粉色",
        lightColorScheme(
            primary = Color(0xFF8E4958), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9DE), onPrimaryContainer = Color(0xFF713341),
            inversePrimary = Color(0xFFFFB2BF), secondary = Color(0xFF75565B),
            onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFFFD9DE),
            onSecondaryContainer = Color(0xFF5C3F44), tertiary = Color(0xFF7A5832),
            onTertiary = Color(0xFFFFFFFF), tertiaryContainer = Color(0xFFFFDCBB),
            onTertiaryContainer = Color(0xFF5F401D), background = Color(0xFFFFF8F7),
            onBackground = Color(0xFF22191B), surface = Color(0xFFFFF8F7),
            onSurface = Color(0xFF22191B), surfaceVariant = Color(0xFFF3DDE0),
            onSurfaceVariant = Color(0xFF524345), inverseSurface = Color(0xFF382E2F),
            inverseOnSurface = Color(0xFFFEEDEE), outline = Color(0xFF847375),
            outlineVariant = Color(0xFFD6C2C4), surfaceBright = Color(0xFFFFF8F7),
            surfaceDim = Color(0xFFE7D6D8), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFF0F1), surfaceContainer = Color(0xFFFBEAEB),
            surfaceContainerHigh = Color(0xFFF5E4E6), surfaceContainerHighest = Color(0xFFF0DEE0),
        ),
        darkColorScheme(
            primary = Color(0xFFFFB2BF), onPrimary = Color(0xFF561D2B),
            primaryContainer = Color(0xFF713341), onPrimaryContainer = Color(0xFFFFD9DE),
            inversePrimary = Color(0xFF8E4958), secondary = Color(0xFFE4BDC2),
            onSecondary = Color(0xFF43292E), secondaryContainer = Color(0xFF5C3F44),
            onSecondaryContainer = Color(0xFFFFD9DE), tertiary = Color(0xFFEBBE90),
            onTertiary = Color(0xFF462A08), tertiaryContainer = Color(0xFF5F401D),
            onTertiaryContainer = Color(0xFFFFDCBB), background = Color(0xFF191113),
            onBackground = Color(0xFFF0DEE0), surface = Color(0xFF191113),
            onSurface = Color(0xFFF0DEE0), surfaceVariant = Color(0xFF524345),
            onSurfaceVariant = Color(0xFFD6C2C4), inverseSurface = Color(0xFFF0DEE0),
            inverseOnSurface = Color(0xFF382E2F), outline = Color(0xFF9F8C8E),
            outlineVariant = Color(0xFF524345), surfaceBright = Color(0xFF413738),
            surfaceDim = Color(0xFF191113), surfaceContainerLowest = Color(0xFF140C0D),
            surfaceContainerLow = Color(0xFF22191B), surfaceContainer = Color(0xFF261D1F),
            surfaceContainerHigh = Color(0xFF312829), surfaceContainerHighest = Color(0xFF3C3234),
        ),
    ),
}
