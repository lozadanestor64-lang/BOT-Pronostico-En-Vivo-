package ar.nogoya.pronostico.datos

import java.time.DateTimeException
import java.time.ZonedDateTime

/**
 * Lee el texto que genera el motor Python (formato WhatsApp).
 * Contrato: tests/fixtures/latest_ejemplo.txt del motor == src/test/resources/ejemplo_latest.txt.
 * Cada dato se busca por separado: si falta uno, queda null y el resto se muestra igual.
 */
object ParserLatest {

    private val RE_SEPARADOR = Regex("(?m)^━+[ \\t]*$")
    private val RE_ENCABEZADO = Regex("(\\d{2})/(\\d{2})\\s*·\\s*\\S+\\s*(\\d{2}):(\\d{2})\\s*hs")
    private val RE_AHORA = Regex("(?m)^(\\S+)\\s+\\*AHORA:\\s*(.+?)\\*\\s*$")
    private val RE_TEMP = Regex("\\*(-?\\d+)°\\*")
    private val RE_SENSACION = Regex("ST\\s+(-?\\d+)°")
    private val RE_MAX = Regex("🔺\\s*(-?\\d+)°")
    private val RE_MIN = Regex("🔻\\s*(-?\\d+)°")
    private val RE_HUMEDAD = Regex("💧\\s*(\\d+)%")
    private val RE_ROCIO = Regex("Rocío\\s+(-?\\d+)°")
    private val RE_PRESION = Regex("(\\d+)\\s*hPa")
    private val RE_UV = Regex("UV\\s+(\\d+(?:,\\d+)?)\\s*\\(([^)]+)\\)")
    private val RE_VISIBILIDAD = Regex("(\\d+(?:,\\d+)?)\\s*km(?![/\\w])")
    private val RE_VIENTO = Regex("(\\d+)\\s*km/h(?:\\s+([A-ZÑ]{1,3}))?")
    private val RE_RAFAGAS = Regex("ráf\\.\\s*(\\d+)")
    private val RE_PROB = Regex("Prob\\. máx\\.\\s*(\\d+)%")
    private val RE_LLUVIA_MM = Regex("💦\\s*(\\d+(?:,\\d+)?)\\s*mm")
    private val RE_CHAPARRONES = Regex("Chaparrones:\\s*(.+)")
    private val RE_TORMENTAS = Regex("Tormentas:\\s*(.+)")
    private val RE_AMANECER = Regex("🌅\\s*(\\d{2}:\\d{2})")
    private val RE_ATARDECER = Regex("🌇\\s*(\\d{2}:\\d{2})")
    private val RE_HORA = Regex("(\\d{2})h\\s+(\\S+?)(-?\\d+°|s/d)\\s+(\\d+%|–)")
    private val RE_DATOS = Regex("Datos\\s+(\\d{2}:\\d{2})")
    private val RE_VIGENTE = Regex("vigente hasta\\s+(\\d{2}):(\\d{2})(?:\\s*\\((\\d{2})/(\\d{2})\\))?")
    private val RE_MOTIVO = Regex("Motivo:\\s*(.+)")

    private fun Regex.entero(texto: String): Int? =
        find(texto)?.groupValues?.get(1)?.toIntOrNull()

    private fun Regex.decimal(texto: String): Double? =
        find(texto)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    private fun Regex.cadena(texto: String): String? =
        find(texto)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    fun parsear(textoCrudo: String, ahora: ZonedDateTime): ResultadoLatest {
        val texto = textoCrudo.replace("\r\n", "\n").trim()
        if (texto.isEmpty()) return ResultadoLatest.Ilegible("el archivo está vacío")

        if (texto.contains("PRONÓSTICO NO DISPONIBLE") || texto.contains("Aún no se generó")) {
            return ResultadoLatest.NoDisponible(RE_MOTIVO.cadena(texto), texto)
        }

        val bloques = texto.split(RE_SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() }
        val bloqueAhora = bloques.firstOrNull { it.contains("AHORA:") }
            ?: return ResultadoLatest.Ilegible("no se encontró la sección AHORA")

        val encabezado = RE_ENCABEZADO.find(texto)
            ?: return ResultadoLatest.Ilegible("no se encontró la fecha y hora del informe")
        val generado = construirFecha(
            ahora,
            encabezado.groupValues[1].toInt(),
            encabezado.groupValues[2].toInt(),
            encabezado.groupValues[3].toInt(),
            encabezado.groupValues[4].toInt(),
        ) ?: return ResultadoLatest.Ilegible("fecha inválida en el informe")

        val temperatura = RE_TEMP.entero(bloqueAhora)
            ?: return ResultadoLatest.Ilegible("no se encontró la temperatura actual")
        val clima = RE_AHORA.find(bloqueAhora)

        val bloqueLluvia = bloques.firstOrNull { it.contains("LLUVIA") } ?: ""
        val bloqueSol = bloques.firstOrNull { it.contains("🌅") || it.contains("🌇") } ?: ""
        val bloqueHoras = bloques.firstOrNull { it.contains("PRÓXIMAS 24 H") } ?: ""

        val viento = RE_VIENTO.find(bloqueAhora)
        val uv = RE_UV.find(bloqueAhora)

        val horas = RE_HORA.findAll(bloqueHoras).map { m ->
            HoraPrevista(
                hora = m.groupValues[1].toInt(),
                emoji = m.groupValues[2],
                temperatura = m.groupValues[3].removeSuffix("°").toIntOrNull(),
                probLluvia = m.groupValues[4].removeSuffix("%").toIntOrNull(),
            )
        }.toList()

        return ResultadoLatest.Pronostico(
            Informe(
                textoOriginal = texto,
                generado = generado,
                datosHora = RE_DATOS.cadena(texto),
                vigenteHasta = calcularVigencia(texto, generado),
                emojiClima = clima?.groupValues?.get(1) ?: "❔",
                descripcion = clima?.groupValues?.get(2)?.trim() ?: "Sin descripción",
                temperatura = temperatura,
                sensacion = RE_SENSACION.entero(bloqueAhora),
                maxima = RE_MAX.entero(bloqueAhora),
                minima = RE_MIN.entero(bloqueAhora),
                humedad = RE_HUMEDAD.entero(bloqueAhora),
                puntoRocio = RE_ROCIO.entero(bloqueAhora),
                presion = RE_PRESION.entero(bloqueAhora),
                uv = uv?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull(),
                uvNivel = uv?.groupValues?.get(2)?.trim(),
                visibilidadKm = RE_VISIBILIDAD.decimal(bloqueAhora),
                vientoKmh = viento?.groupValues?.get(1)?.toIntOrNull(),
                vientoDir = viento?.groupValues?.get(2)?.ifEmpty { null },
                rafagasKmh = RE_RAFAGAS.entero(bloqueAhora),
                probLluviaMax = RE_PROB.entero(bloqueLluvia),
                lluviaMm = RE_LLUVIA_MM.decimal(bloqueLluvia),
                sinLluvia = bloqueLluvia.contains("Sin lluvia prevista"),
                chaparrones = RE_CHAPARRONES.cadena(bloqueLluvia),
                tormentas = RE_TORMENTAS.cadena(bloqueLluvia),
                amanecer = RE_AMANECER.cadena(bloqueSol),
                atardecer = RE_ATARDECER.cadena(bloqueSol),
                horas = horas,
            )
        )
    }

    /** El texto no trae año: se toma el actual, retrocediendo uno si cae más de un día en el futuro. */
    private fun construirFecha(
        ahora: ZonedDateTime, dia: Int, mes: Int, hora: Int, minuto: Int,
    ): ZonedDateTime? = try {
        val referencia = ahora.withZoneSameInstant(ZONA_NOGOYA)
        var fecha = ZonedDateTime.of(referencia.year, mes, dia, hora, minuto, 0, 0, ZONA_NOGOYA)
        if (fecha.isAfter(referencia.plusDays(1))) fecha = fecha.minusYears(1)
        fecha
    } catch (e: DateTimeException) {
        null
    }

    private fun calcularVigencia(texto: String, generado: ZonedDateTime): ZonedDateTime? {
        val m = RE_VIGENTE.find(texto) ?: return null
        val hora = m.groupValues[1].toInt()
        val minuto = m.groupValues[2].toInt()
        val dia = m.groupValues[3]
        val mes = m.groupValues[4]
        return try {
            if (dia.isNotEmpty()) {
                var limite = ZonedDateTime.of(
                    generado.year, mes.toInt(), dia.toInt(), hora, minuto, 0, 0, ZONA_NOGOYA,
                )
                if (limite.isBefore(generado)) limite = limite.plusYears(1)
                limite
            } else {
                val limite = generado.withHour(hora).withMinute(minuto).withSecond(0).withNano(0)
                if (limite.isBefore(generado)) null else limite
            }
        } catch (e: DateTimeException) {
            null
        }
    }
}
