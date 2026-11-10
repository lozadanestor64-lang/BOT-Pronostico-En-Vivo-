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
    fun emojiDelNivelUv() {
        assertEquals("🟢", emojiUv("Bajo"))
        assertEquals("🔴", emojiUv("Muy alto"))
        assertEquals("🟣", emojiUv("Extremo"))
        assertEquals("🔆", emojiUv(null))
        assertEquals("🔆", emojiUv("otro"))
    }
}
