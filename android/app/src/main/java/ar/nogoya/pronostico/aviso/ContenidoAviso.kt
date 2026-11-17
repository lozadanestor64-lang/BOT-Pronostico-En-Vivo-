package ar.nogoya.pronostico.aviso

import ar.nogoya.pronostico.datos.Informe
import ar.nogoya.pronostico.datos.Vigencia
import ar.nogoya.pronostico.datos.formatoNumero
import ar.nogoya.pronostico.datos.vigencia
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Texto de la notificación. Todo sale del informe leído de latest.txt; no se inventa nada. */
data class ContenidoAviso(val titulo: String, val resumen: String, val detalle: String)

private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun construirAviso(i: Informe): ContenidoAviso {
    val titulo = "${i.emojiClima} Nogoyá ${i.temperatura}° · ${i.descripcion}"

    val maxMin = listOfNotNull(i.maxima?.let { "🔺$it°" }, i.minima?.let { "🔻$it°" }).joinToString(" ")
    val temperaturas = listOfNotNull(
        i.sensacion?.let { "ST $it°" },
        maxMin.ifEmpty { null },
        i.humedad?.let { "💧$it%" },
    ).joinToString(" · ")

    val viento = listOfNotNull(
        i.vientoKmh?.let { "💨 $it km/h" + (i.vientoDir?.let { d -> " $d" } ?: "") },
        i.rafagasKmh?.let { "🌬️ ráf. $it" },
    ).joinToString(" · ")

    val lluvia = if (i.sinLluvia) {
        "☀️ Sin lluvia prevista"
    } else {
        listOfNotNull(
            i.probLluviaMax?.let { "☔ $it%" },
            i.lluviaMm?.let { "💦 ${formatoNumero(it, 1)} mm" },
        ).joinToString(" · ")
    }

    val lineaVigencia = i.vigenteHasta?.let { "⏳ Vigente hasta ${it.format(FORMATO_HORA)}" } ?: ""

    val lineas = listOfNotNull(
        temperaturas.ifEmpty { null },
        viento.ifEmpty { null },
        lluvia.ifEmpty { null },
        i.chaparrones?.let { "🌦️ Chaparrones: $it" },
        i.tormentas?.let { "⛈️ Tormentas: $it" },
        lineaVigencia.ifEmpty { null },
    )
    return ContenidoAviso(
        titulo = titulo,
        resumen = lineas.firstOrNull() ?: "Toca para ver el pronóstico",
        detalle = lineas.joinToString("\n"),
    )
}

/** Avisa solo si el informe es nuevo (posterior al último avisado) y todavía está vigente. */
fun debeAvisar(informe: Informe, ahora: ZonedDateTime, ultimoAvisadoMillis: Long): Boolean =
    informe.vigencia(ahora) == Vigencia.VIGENTE &&
        informe.generado.toInstant().toEpochMilli() > ultimoAvisadoMillis

/** De 23:00 a 06:00 la notificación llega en silencio. */
fun esHorarioSilencioso(ahora: ZonedDateTime): Boolean = ahora.hour >= 23 || ahora.hour < 6

/** Alerta aparte: lluvia o tormenta que empiezan pronto. [clave] evita repetirla para el mismo evento. */
data class Alerta(val clave: String, val contenido: ContenidoAviso)

private val FORMATO_HH: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")

fun construirAlerta(i: Informe, horizonteHoras: Int = 6, umbralLluvia: Int = 60): Alerta? {
    val proximas = i.horas.take(horizonteHoras)
    val base = i.generado.truncatedTo(ChronoUnit.HOURS)

    fun detalleDe(indice: Int): Pair<String, String> {
        val inicio = base.plusHours((indice + 1).toLong())
        val dia = if (inicio.toLocalDate() == i.generado.toLocalDate()) "" else " (mañana)"
        val hora = "${inicio.format(FORMATO_HH)}h$dia"
        val prob = proximas[indice].probLluvia?.let { "\nProbabilidad de lluvia a esa hora: $it%." } ?: ""
        return Pair("${inicio.toLocalDate()}", hora + prob)
    }

    val tormenta = proximas.indexOfFirst { it.emoji.contains("⛈") }
    if (tormenta >= 0) {
        val (fecha, texto) = detalleDe(tormenta)
        val hora = texto.substringBefore("\n")
        return Alerta(
            clave = "tormenta|$fecha",
            contenido = ContenidoAviso(
                titulo = "⛈️ Tormenta en las próximas horas",
                resumen = "Posible tormenta desde las $hora",
                detalle = "Posible tormenta desde las $hora." + texto.substringAfter(hora),
            ),
        )
    }
    val lluvia = proximas.indexOfFirst { (it.probLluvia ?: 0) >= umbralLluvia }
    if (lluvia >= 0) {
        val (fecha, texto) = detalleDe(lluvia)
        val hora = texto.substringBefore("\n")
        return Alerta(
            clave = "lluvia|$fecha",
            contenido = ContenidoAviso(
                titulo = "☔ Lluvia en las próximas horas",
                resumen = "Lluvia probable desde las $hora",
                detalle = "Lluvia probable desde las $hora." + texto.substringAfter(hora),
            ),
        )
    }
    return null
}
