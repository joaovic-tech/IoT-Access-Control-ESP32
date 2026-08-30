package com.example.espcamapp.ui.theme

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
    primary = PurplePrimary,
    onPrimary = OffWhite,
    primaryContainer = PurpleDark,
    onPrimaryContainer = OffWhite,
    background = DarkSlateBg,
    onBackground = OffWhite,
    surface = DarkGrayCard,
    onSurface = OffWhite,
    surfaceVariant = DarkGrayInput,
    onSurfaceVariant = SlateGray,
    outline = SlateGray.copy(alpha = 0.3f),
    error = StatusRedLive,
    onError = OffWhite
)

private val LightColorScheme = lightColorScheme(
    // Roxo vibrante como cor primária
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),     // Roxo bem clarinho para fundos selecionados
    onPrimaryContainer = Color(0xFF3B0764),   // Roxo escuro para texto sobre primaryContainer

    // Fundo geral do app claro
    background = Color(0xFFF8FAFC),           // Branco gelo / Slate 50
    onBackground = DarkSlateBg,               // Texto principal no tom escuro (#0F172A)

    // Superfícies (Cards, Diálogos, Sheets)
    surface = Color.White,                    // Cards brancos
    onSurface = DarkSlateBg,                  // Texto sobre cards escuro
    surfaceVariant = Color(0xFFF1F5F9),       // Fundo de inputs / campos de busca (Slate 100)
    onSurfaceVariant = SlateGray,             // Texto secundário / placeholders

    // Bordas e erros
    outline = Color(0xFFCBD5E1),              // Bordas suaves (Slate 300)
    error = StatusRedLive,
    onError = Color.White
)

@Composable
fun ESPCamAppTheme(
    // O design dos mockups é sempre em tema escuro (fundo Slate #0F172A).
    darkTheme: Boolean = true,
    // Dynamic color desativado: a paleta do app é fixa (ver "paletas de cores.png").
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}