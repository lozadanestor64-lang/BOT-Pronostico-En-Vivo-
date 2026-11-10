package ar.nogoya.pronostico.datos

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

val ZONA_NOGOYA: ZoneId = ZoneId.of("America/Argentina/Cordoba")

/** Una hora del pronóstico de las próximas 24 h. Un dato faltante es null, nunca cero. */
data class HoraPrevista(
    val hora: Int,
    val emoji: String,
    val temperatura: Int?,
    val probLluvia: Int?,
)

/** Informe leído de latest.txt. Todo viene del archivo; la app no calcula meteorología. */
data class Informe(
    val textoOriginal: String,
    val generado: ZonedDateTime,
    val datosHora: String?,
    val vigenteHasta: ZonedDateTime?,
    val emojiClima: String,
    val descripcion: String,
    val temperatura: Int,
    val sensacion: Int?,
    val maxima: Int?,
    val minima: Int?,
    val humedad: Int?,
    val puntoRocio: Int?,
    val presion: Int?,
    val uv: Double?,
    val uvNivel: String?,
    val visibilidadKm: Double?,
    val vientoKmh: Int?,
    val vientoDir: String?,
    val rafagasKmh: Int?,
    val probLluviaMax: Int?,
    val lluviaMm: Double?,
    val sinLluvia: Boolean,
    val chaparrones: String?,
    val tormentas: String?,
    val amanecer: String?,
    val atardecer: String?,
    val horas: List<HoraPrevista>,
)

enum class Vigencia { VIGENTE, VENCIDO }

/** Sin "vigente hasta" confiable se considera VENCIDO: nunca se asume vigente. */
fun Informe.vigencia(ahora: ZonedDateTime): Vigencia {
    val limite = vigenteHasta ?: return Vigencia.VENCIDO
    return if (ahora.isBefore(limite)) Vigencia.VIGENTE else Vigencia.VENCIDO
}

fun Informe.edad(ahora: ZonedDateTime): Duration = Duration.between(generado, ahora)

fun formatearDuracion(duracion: Duration): String {
    val minutos = maxOf(duracion.toMinutes(), 0L)
    val horas = minutos / 60
    val resto = minutos % 60
    return if (horas > 0) "$horas h ${resto.toString().padStart(2, '0')} min" else "$resto min"
}

fun formatoNumero(valor: Double, decimales: Int): String =
    String.format(Locale.US, "%.${decimales}f", valor).replace('.', ',')

sealed interface ResultadoLatest {
    data class Pronostico(val informe: Informe) : ResultadoLatest
    data class NoDisponible(val motivo: String?, val texto: String) : ResultadoLatest
    data class Ilegible(val razon: String) : ResultadoLatest
}
