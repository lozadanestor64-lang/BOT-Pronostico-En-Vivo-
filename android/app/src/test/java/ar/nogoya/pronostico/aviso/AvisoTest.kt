package ar.nogoya.pronostico.aviso

import ar.nogoya.pronostico.datos.ZONA_NOGOYA
import ar.nogoya.pronostico.datos.ejemplo
import ar.nogoya.pronostico.datos.informeDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class AvisoTest {

    private fun a(h: Int, m: Int): ZonedDateTime = ZonedDateTime.of(2026, 11, 7, h, m, 0, 0, ZONA_NOGOYA)

    @Test
    fun tituloYDetalleSalenDelInforme() {
        val c = construirAviso(informeDe(ejemplo()))
        assertTrue(c.titulo.contains("Nogoyá 30°"))
        assertTrue(c.titulo.contains("Parcialmente nublado"))
        assertTrue(c.detalle.contains("ST 30°"))
        assertTrue(c.detalle.contains("🔺31° 🔻17°"))
        assertTrue(c.detalle.contains("💧48%"))
        assertTrue(c.detalle.contains("17 km/h NE"))
        assertTrue(c.detalle.contains("ráf. 32"))
        assertTrue(c.detalle.contains("☔ 60%"))
        assertTrue(c.detalle.contains("2,8 mm"))
        assertTrue(c.detalle.contains("Chaparrones: 18h–19h (1,5 mm)"))
        assertTrue(c.detalle.contains("Tormentas: 19h–20h"))
        assertTrue(c.detalle.contains("Vigente hasta 18:05"))
    }

    @Test
    fun resumenEsLaPrimeraLinea() {
        val c = construirAviso(informeDe(ejemplo()))
        assertEquals(c.detalle.lines().first(), c.resumen)
    }

    @Test
    fun noApareceNullNiTextosVacios() {
        val c = construirAviso(informeDe(ejemplo()))
        assertFalse(c.titulo.contains("null"))
        assertFalse(c.detalle.contains("null"))
        assertTrue(c.detalle.lines().none { it.isBlank() })
    }

    @Test
    fun sinDatosDeLluviaNoSeInventaNada() {
        val sinLluvia = ejemplo().lines().filterNot {
            it.contains("Prob. máx.") || it.contains("Chaparrones:") || it.contains("Tormentas:")
        }.joinToString("\n")
        val c = construirAviso(informeDe(sinLluvia))
        assertFalse(c.detalle.contains("☔"))
        assertFalse(c.detalle.contains("Tormentas"))
        assertFalse(c.detalle.contains("Chaparrones"))
    }

    @Test
    fun informeSinLluvia() {
        val lineas = ejemplo().lines().toMutableList()
        val inicio = lineas.indexOfFirst { it.contains("LLUVIA") }
        lineas[inicio + 1] = "☀️ Sin lluvia prevista"
        lineas.removeAt(inicio + 2)
        lineas.removeAt(inicio + 2)
        val c = construirAviso(informeDe(lineas.joinToString("\n")))
        assertTrue(c.detalle.contains("Sin lluvia prevista"))
    }

    @Test
    fun avisaSoloSiEsNuevoYVigente() {
        val i = informeDe(ejemplo())
        val generado = i.generado.toInstant().toEpochMilli()
        assertTrue(debeAvisar(i, a(15, 10), 0L))
        assertTrue(debeAvisar(i, a(15, 10), generado - 1))
        assertFalse(debeAvisar(i, a(15, 10), generado))
        assertFalse(debeAvisar(i, a(15, 10), generado + 1000))
    }

    @Test
    fun nuncaAvisaUnInformeVencido() {
        val i = informeDe(ejemplo())
        assertFalse(debeAvisar(i, a(18, 5), 0L))
        assertFalse(debeAvisar(i, a(22, 0), 0L))
    }

    @Test
    fun horarioSilencioso() {
        assertTrue(esHorarioSilencioso(a(23, 0)))
        assertTrue(esHorarioSilencioso(a(0, 0)))
        assertTrue(esHorarioSilencioso(a(3, 30)))
        assertTrue(esHorarioSilencioso(a(5, 59)))
        assertFalse(esHorarioSilencioso(a(6, 0)))
        assertFalse(esHorarioSilencioso(a(12, 0)))
        assertFalse(esHorarioSilencioso(a(22, 59)))
    }
}
