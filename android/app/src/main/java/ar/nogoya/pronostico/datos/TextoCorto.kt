package ar.nogoya.pronostico.datos

import java.time.format.DateTimeFormatter

/** Margen prudente bajo el límite del Estado de texto de WhatsApp (se cree de unos 700 caracteres). */
const val LIMITE_TEXTO_CORTO = 650

private val FORMATO_ENCABEZADO: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val FORMATO_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun diaCorto(dia: String?): String = when {
    dia == null -> ""
    dia.startsWith("Hoy") -> "Hoy"
    dia.startsWith("Mañana") -> "Mañana"
    else -> dia.substringBefore(" ")
}

private fun celda(h: HoraPrevista): String {
    val temperatura = h.temperatura?.let { "$it°" } ?: "s/d"
    val prob = h.probLluvia?.let { "$it%" } ?: "–"
    return "${h.hora.toString().padStart(2, '0')}h ${h.emoji}$temperatura $prob"
}

/** Una muestra cada [paso] horas, agrupada por día en filas de hasta 3. */
private fun bloqueHoras(horas: List<HoraPrevista>, paso: Int): List<String> {
    val muestra = horas.filterIndexed { indice, _ -> indice % paso == 0 }
    val lineas = mutableListOf<String>()
    var fila = mutableListOf<String>()
    var diaActual: String? = null

    fun cerrarFila() {
        if (fila.isNotEmpty()) {
            lineas.add(fila.joinToString(" · "))
            fila = mutableListOf()
        }
    }

    for (h in muestra) {
        val dia = diaCorto(h.dia)
        if (dia != diaActual) {
            cerrarFila()
            if (dia.isNotEmpty()) lineas.add("*$dia*")
            diaActual = dia
        }
        fila.add(celda(h))
        if (fila.size == 3) cerrarFila()
    }
    cerrarFila()
    return lineas
}

private fun armar(i: Informe, paso: Int, conChaparrones: Boolean): String {
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
    val pie = listOfNotNull(
        i.vigenteHasta?.let { "⏳ Vigente hasta ${it.format(FORMATO_HORA)}" },
        "🌐 Open-Meteo.com",
    ).joinToString(" · ")

    val lineas = mutableListOf<String>()
    lineas.add("🌦️ *NOGOYÁ · ${i.generado.format(FORMATO_ENCABEZADO)}*")
    lineas.add("${i.emojiClima} *${i.temperatura}°* ${i.descripcion}")
    listOf(temperaturas, viento, lluvia).forEach { if (it.isNotEmpty()) lineas.add(it) }
    if (conChaparrones) i.chaparrones?.let { lineas.add("🌦️ Chaparrones: $it") }
    i.tormentas?.let { lineas.add("⛈️ Tormentas: $it") }
    val horas = bloqueHoras(i.horas, paso)
    if (horas.isNotEmpty()) {
        lineas.add("━━━━━━━━")
        lineas.addAll(horas)
        lineas.add("━━━━━━━━")
    }
    lineas.add(pie)
    return lineas.joinToString("\n")
}

/**
 * Versión compacta para pegar en un Estado de texto. Si se pasa del límite, va quitando detalle
 * (chaparrones y, después, horas intermedias) hasta que entre. Todo sale del informe real.
 */
fun construirTextoCorto(i: Informe, limite: Int = LIMITE_TEXTO_CORTO): String {
    var ultimo = ""
    for ((paso, conChaparrones) in listOf(3 to true, 3 to false, 6 to false, 8 to false)) {
        ultimo = armar(i, paso, conChaparrones)
        if (ultimo.length <= limite) return ultimo
    }
    return ultimo
}
