package ar.nogoya.pronostico.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbienteTest {

    @Test
    fun ambienteSegunEmoji() {
        assertEquals(Ambiente.TORMENTA, ambienteDe("⛈️"))
        assertEquals(Ambiente.LLUVIA, ambienteDe("🌧️"))
        assertEquals(Ambiente.LLUVIA, ambienteDe("🌦️"))
        assertEquals(Ambiente.NOCHE, ambienteDe("🌙"))
        assertEquals(Ambiente.NIEBLA, ambienteDe("🌫️"))
        assertEquals(Ambiente.NUBLADO, ambienteDe("☁️"))
        assertEquals(Ambiente.DIA, ambienteDe("☀️"))
        assertEquals(Ambiente.DIA, ambienteDe("⛅"))
        assertEquals(Ambiente.DESCONOCIDO, ambienteDe("❔"))
    }

    @Test
    fun etiquetaCortaDelDia() {
        assertEquals("Hoy", etiquetaCorta("Hoy · Lun 09/11"))
        assertEquals("Mañana", etiquetaCorta("Mañana · Mar 10/11"))
        assertEquals("Mié", etiquetaCorta("Mié 11/11"))
        assertEquals("", etiquetaCorta(null))
    }

    @Test
    fun emojiDelNivelUv() {
        assertEquals("🟢", emojiUv("Bajo"))
        assertEquals("🔴", emojiUv("Muy alto"))
        assertEquals("🟣", emojiUv("Extremo"))
        assertEquals("🔆", emojiUv(null))
        assertEquals("🔆", emojiUv("otro"))
    }
}
