package ar.nogoya.pronostico.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime

class VigenciaTest {

    private fun a(h: Int, m: Int): ZonedDateTime = ZonedDateTime.of(2026, 11, 7, h, m, 0, 0, ZONA_NOGOYA)

    @Test
    fun vigenteAntesDelLimite() {
        val i = informeDe(ejemplo())
        assertEquals(Vigencia.VIGENTE, i.vigencia(a(15, 10)))
        assertEquals(Vigencia.VIGENTE, i.vigencia(a(18, 4)))
    }

    @Test
    fun vencidoEnElLimiteYDespues() {
        val i = informeDe(ejemplo())
        assertEquals(Vigencia.VENCIDO, i.vigencia(a(18, 5)))
        assertEquals(Vigencia.VENCIDO, i.vigencia(a(23, 0)))
    }

    @Test
    fun vigenciaQueCruzaMedianoche() {
        val texto = ejemplo().replace("vigente hasta 18:05", "vigente hasta 01:30 (08/11)")
        val i = informeDe(texto)
        assertEquals(ZonedDateTime.of(2026, 11, 8, 1, 30, 0, 0, ZONA_NOGOYA), i.vigenteHasta)
        assertEquals(Vigencia.VIGENTE, i.vigencia(a(23, 59)))
    }

    @Test
    fun sinLineaDeVigenciaSeConsideraVencido() {
        val sin = ejemplo().replace(Regex("⏳ vigente hasta \\d{2}:\\d{2}"), "")
        val i = informeDe(sin)
        assertNull(i.vigenteHasta)
        assertEquals(Vigencia.VENCIDO, i.vigencia(a(15, 10)))
    }

    @Test
    fun edadYFormatoDeDuracion() {
        val i = informeDe(ejemplo())
        assertEquals(Duration.ofHours(3), i.edad(a(18, 5)))
        assertEquals("3 h 00 min", formatearDuracion(i.edad(a(18, 5))))
        assertEquals("45 min", formatearDuracion(Duration.ofMinutes(45)))
        assertEquals("0 min", formatearDuracion(Duration.ofMinutes(-10)))
        assertEquals("1 h 05 min", formatearDuracion(Duration.ofMinutes(65)))
    }

    @Test
    fun formatoNumeroUsaComaDecimal() {
        assertEquals("7,0", formatoNumero(7.0, 1))
        assertEquals("2,8", formatoNumero(2.84, 1))
        assertEquals("24", formatoNumero(24.14, 0))
    }
}
