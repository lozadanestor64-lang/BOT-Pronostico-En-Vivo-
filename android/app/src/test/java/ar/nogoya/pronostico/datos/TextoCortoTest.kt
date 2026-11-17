package ar.nogoya.pronostico.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoCortoTest {

    private val informe = informeDe(ejemplo())

    @Test
    fun entraEnElLimiteDelEstado() {
        val texto = construirTextoCorto(informe)
        assertTrue("largo ${texto.length}", texto.length <= LIMITE_TEXTO_CORTO)
    }

    @Test
    fun contieneLoEsencial() {
        val texto = construirTextoCorto(informe)
        for (esperado in listOf(
            "*NOGOYÁ · 07/11 15:05*", "*30°*", "Parcialmente nublado", "ST 30°", "🔺31° 🔻17°",
            "💧48%", "17 km/h NE", "ráf. 32", "☔ 60%", "2,8 mm", "Tormentas: hoy 19h–20h",
            "*Hoy*", "*Mañana*", "Vigente hasta 18:05", "Open-Meteo.com",
        )) {
            assertTrue("falta: $esperado", texto.contains(esperado))
        }
    }

    @Test
    fun noTieneNullNiLineasVacias() {
        val texto = construirTextoCorto(informe)
        assertFalse(texto.contains("null"))
        assertTrue(texto.lines().none { it.isBlank() })
    }

    @Test
    fun cadaLineaEsCorta() {
        assertTrue(construirTextoCorto(informe).lines().all { it.length <= 60 })
    }

    @Test
    fun muestraUnaHoraCadaTres() {
        val texto = construirTextoCorto(informe)
        for (hora in listOf("16h", "19h", "22h", "01h", "04h", "07h", "10h", "13h")) {
            assertTrue("falta $hora", texto.contains("$hora "))
        }
        assertFalse(texto.contains("17h "))
    }

    @Test
    fun siNoEntraQuitaDetalleSinInventarNada() {
        val completo = construirTextoCorto(informe)
        val recortado = construirTextoCorto(informe, limite = 300)
        assertTrue(recortado.length <= 300)
        assertTrue(recortado.length < completo.length)
        assertFalse(recortado.contains("Chaparrones"))
        assertTrue(recortado.contains("Tormentas")) // lo importante se conserva
    }

    @Test
    fun conLimiteImposibleDevuelveLoMasCortoSinFallar() {
        val texto = construirTextoCorto(informe, limite = 10)
        assertTrue(texto.isNotEmpty())
        assertEquals(texto, construirTextoCorto(informe, limite = 10))
    }

    @Test
    fun datosFaltantesSeOmiten() {
        val sinViento = informe.copy(vientoKmh = null, vientoDir = null, rafagasKmh = null, humedad = null)
        val texto = construirTextoCorto(sinViento)
        assertFalse(texto.contains("km/h"))
        assertFalse(texto.contains("💧48%"))
        assertFalse(texto.contains("null"))
    }
}
