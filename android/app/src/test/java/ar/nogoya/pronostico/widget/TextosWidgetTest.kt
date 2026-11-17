package ar.nogoya.pronostico.widget

import ar.nogoya.pronostico.datos.ZONA_NOGOYA
import ar.nogoya.pronostico.datos.ejemplo
import ar.nogoya.pronostico.datos.informeDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class TextosWidgetTest {

    private fun a(h: Int, m: Int): ZonedDateTime = ZonedDateTime.of(2026, 11, 7, h, m, 0, 0, ZONA_NOGOYA)
    private val informe = informeDe(ejemplo())

    @Test
    fun informeVigente() {
        val t = construirTextosWidget(informe, a(15, 10))
        assertEquals("30°", t.temperatura)
        assertEquals("Parcialmente nublado", t.descripcion)
        assertTrue(t.detalle.contains("ST 30°"))
        assertTrue(t.detalle.contains("Tormentas: hoy 19h–20h"))
        assertTrue(t.estado.startsWith("✅"))
        assertTrue(t.estado.contains("vigente hasta 18:05"))
        assertFalse(t.vencido)
    }

    @Test
    fun informeVencidoNuncaSeMuestraComoActual() {
        val t = construirTextosWidget(informe, a(19, 0))
        assertTrue(t.vencido)
        assertEquals("⛔ VENCIDO · abre la app", t.estado)
        assertFalse(t.estado.contains("✅"))
    }

    @Test
    fun sinTormentasMuestraLaProbabilidadDeLluvia() {
        val t = construirTextosWidget(informe.copy(tormentas = null), a(15, 10))
        assertTrue(t.detalle.contains("☔ Lluvia máx. 60%"))
    }

    @Test
    fun sinLluvia() {
        val t = construirTextosWidget(
            informe.copy(tormentas = null, sinLluvia = true, probLluviaMax = null), a(15, 10),
        )
        assertTrue(t.detalle.contains("Sin lluvia prevista"))
    }

    @Test
    fun widgetSinDatos() {
        val t = textosWidgetVacios()
        assertEquals("--°", t.temperatura)
        assertTrue(t.vencido)
        assertTrue(t.detalle.contains("Abre la app"))
    }

    @Test
    fun noApareceNull() {
        val t = construirTextosWidget(informe.copy(sensacion = null, humedad = null, maxima = null, minima = null), a(15, 10))
        assertFalse(t.detalle.contains("null"))
        assertFalse(t.estado.contains("null"))
    }
}
