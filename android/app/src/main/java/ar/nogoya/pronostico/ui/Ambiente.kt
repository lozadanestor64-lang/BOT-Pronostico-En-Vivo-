package ar.nogoya.pronostico.ui

/** Ambiente visual según el emoji del clima actual (sin dependencias de Compose, es testeable). */
enum class Ambiente { DIA, NOCHE, NUBLADO, LLUVIA, TORMENTA, NIEBLA, DESCONOCIDO }

fun ambienteDe(emoji: String): Ambiente = when {
    emoji.contains("⛈") -> Ambiente.TORMENTA
    emoji.contains("🌧") || emoji.contains("🌦") || emoji.contains("🌨") -> Ambiente.LLUVIA
    emoji.contains("🌙") -> Ambiente.NOCHE
    emoji.contains("🌫") -> Ambiente.NIEBLA
    emoji.contains("☁") -> Ambiente.NUBLADO
    emoji.contains("☀") || emoji.contains("⛅") || emoji.contains("🌤") -> Ambiente.DIA
    else -> Ambiente.DESCONOCIDO
}

/** "Hoy · Lun 09/11" -> "Hoy"; "Mañana · Mar 10/11" -> "Mañana"; "Mié 11/11" -> "Mié". */
fun etiquetaCorta(dia: String?): String = when {
    dia == null -> ""
    dia.startsWith("Hoy") -> "Hoy"
    dia.startsWith("Mañana") -> "Mañana"
    else -> dia.substringBefore(" ")
}

fun emojiUv(nivel: String?): String = when (nivel) {
    "Bajo" -> "🟢"
    "Moderado" -> "🟡"
    "Alto" -> "🟠"
    "Muy alto" -> "🔴"
    "Extremo" -> "🟣"
    else -> "🔆"
}
