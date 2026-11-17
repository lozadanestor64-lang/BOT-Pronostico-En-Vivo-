package ar.nogoya.pronostico.widget

import ar.nogoya.pronostico.datos.Informe
import ar.nogoya.pronostico.datos.Vigencia
import ar.nogoya.pronostico.datos.vigencia
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Textos del widget. Si el informe está vencido se dice claramente: nunca se muestra como actual. */
data class TextosWidget(
    val emoji: String,
    val temperatura: String,
    val descripcion: String,
    val detalle: String,
    val estado: String,
    val vencido: Boolean,
)

private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun textosWidgetVacios(): TextosWidget = TextosWidget(
    emoji = "🌦️",
    temperatura = "--°",
    descripcion = "Sin datos",
    detalle = "Abre la app para actualizar",
    estado = "",
    vencido = true,
)

fun construirTextosWidget(i: Informe, ahora: ZonedDateTime): TextosWidget {
    val vigente = i.vigencia(ahora) == Vigencia.VIGENTE
    val maxMin = listOfNotNull(i.maxima?.let { "🔺$it°" }, i.minima?.let { "🔻$it°" }).joinToString(" ")
    val linea1 = listOfNotNull(
        i.sensacion?.let { "ST $it°" },
        maxMin.ifEmpty { null },
        i.humedad?.let { "💧$it%" },
    ).joinToString(" · ")
    val linea2 = when {
        i.tormentas != null -> "⛈️ Tormentas: ${i.tormentas}"
        i.sinLluvia -> "☀️ Sin lluvia prevista"
        i.probLluviaMax != null -> "☔ Lluvia máx. ${i.probLluviaMax}%"
        else -> ""
    }
    val estado = if (vigente) {
        "✅ ${i.generado.format(FORMATO_HORA)}" +
            (i.vigenteHasta?.let { " · vigente hasta ${it.format(FORMATO_HORA)}" } ?: "")
    } else {
        "⛔ VENCIDO · abre la app"
    }
    return TextosWidget(
        emoji = i.emojiClima,
        temperatura = "${i.temperatura}°",
        descripcion = i.descripcion,
        detalle = listOf(linea1, linea2).filter { it.isNotEmpty() }.joinToString("\n"),
        estado = estado,
        vencido = !vigente,
    )
}
