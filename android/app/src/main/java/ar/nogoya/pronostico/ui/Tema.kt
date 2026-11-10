package ar.nogoya.pronostico.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Esquema = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF05213F),
    secondaryContainer = Color(0xFF24406A),
    onSecondaryContainer = Color.White,
    background = Color(0xFF0A1020),
    onBackground = Color.White,
    surface = Color(0xFF12203D),
    onSurface = Color.White,
)

@Composable
fun PronosticoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, content = content)
}

fun degradado(ambiente: Ambiente): List<Color> = when (ambiente) {
    Ambiente.DIA -> listOf(Color(0xFF2A8CFF), Color(0xFF1456B8))
    Ambiente.NOCHE -> listOf(Color(0xFF26346B), Color(0xFF0E1530))
    Ambiente.NUBLADO -> listOf(Color(0xFF5F7A99), Color(0xFF34455C))
    Ambiente.LLUVIA -> listOf(Color(0xFF3F6FA8), Color(0xFF1D3557))
    Ambiente.TORMENTA -> listOf(Color(0xFF5B3FA0), Color(0xFF1E1A4A))
    Ambiente.NIEBLA -> listOf(Color(0xFF7C8B99), Color(0xFF475563))
    Ambiente.DESCONOCIDO -> listOf(Color(0xFF3A4A66), Color(0xFF1B2638))
}
