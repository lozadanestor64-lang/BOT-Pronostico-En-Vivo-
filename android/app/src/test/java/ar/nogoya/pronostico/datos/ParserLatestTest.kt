package ar.nogoya.pronostico.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** Referencia: el mismo texto que genera el motor Python en tests/fixtures/latest_ejemplo.txt. */
internal fun ejemplo(): String =
    ParserLatestTest::class.java.getResourceAsStream("/ejemplo_latest.txt")!!
        .bufferedReader(Charsets.UTF_8).readText()

internal val AHORA: ZonedDateTime = ZonedDateTime.of(2026, 11, 7, 15, 10, 0, 0, ZONA_NOGOYA)

internal fun informeDe(texto: String, ahora: ZonedDateTime = AHORA): Informe {
    val resultado = ParserLatest.parsear(texto, ahora)
    assertTrue("Se esperaba Pronostico pero fue $resultado", resultado is ResultadoLatest.Pronostico)
    return (resultado as ResultadoLatest.Pronostico).informe
}

class ParserLatestTest {

    @Test
    fun leeTodosLosValoresActuales() {
        val i = informeDe(ejemplo())
        assertEquals(30, i.temperatura)
        assertEquals(30, i.sensacion)
        assertEquals(31, i.maxima)
        assertEquals(17, i.minima)
        assertEquals(48, i.humedad)
        assertEquals(16, i.puntoRocio)
        assertEquals(1012, i.presion)
        assertEquals(7.0, i.uv!!, 0.001)
        assertEquals("Alto", i.uvNivel)
        assertEquals(24.0, i.visibilidadKm!!, 0.001)
        assertEquals(17, i.vientoKmh)
        assertEquals("NE", i.vientoDir)
        assertEquals(32, i.rafagasKmh)
        assertEquals("Parcialmente nublado", i.descripcion)
    }

    @Test
    fun leeLluviaChaparronesYTormentas() {
        val i = informeDe(ejemplo())
        assertEquals(60, i.probLluviaMax)
        assertEquals(2.8, i.lluviaMm!!, 0.001)
        assertEquals("18h–19h (1,5 mm)", i.chaparrones)
        assertEquals("19h–20h", i.tormentas)
        assertEquals(false, i.sinLluvia)
    }

    @Test
    fun leeSolYFechas() {
        val i = informeDe(ejemplo())
        assertEquals("05:52", i.amanecer)
        assertEquals("19:34", i.atardecer)
        assertEquals("15:00", i.datosHora)
        assertEquals(ZonedDateTime.of(2026, 11, 7, 15, 5, 0, 0, ZONA_NOGOYA), i.generado)
        assertEquals(ZonedDateTime.of(2026, 11, 7, 18, 5, 0, 0, ZONA_NOGOYA), i.vigenteHasta)
    }

    @Test
    fun leeLas24Horas() {
        val horas = informeDe(ejemplo()).horas
        assertEquals(24, horas.size)
        assertEquals(16, horas.first().hora)
        assertEquals(15, horas.last().hora)
        assertEquals(30, horas[0].temperatura)
        assertEquals(10, horas[0].probLluvia)
        assertEquals(18, horas[2].hora)
        assertEquals(28, horas[2].temperatura)
        assertEquals(60, horas[2].probLluvia)
        assertTrue(horas[2].emoji.startsWith("🌦"))
    }

    @Test
    fun conservaElTextoOriginalParaCopiar() {
        val i = informeDe(ejemplo())
        assertEquals(ejemplo().trim(), i.textoOriginal)
    }

    @Test
    fun aceptaFinesDeLineaWindows() {
        val i = informeDe(ejemplo().replace("\n", "\r\n"))
        assertEquals(30, i.temperatura)
        assertEquals(24, i.horas.size)
    }

    @Test
    fun temperaturaNegativa() {
        val i = informeDe(ejemplo().replace("*30°*", "*-3°*").replace("ST 30°", "ST -6°"))
        assertEquals(-3, i.temperatura)
        assertEquals(-6, i.sensacion)
    }

    @Test
    fun datoFaltanteQuedaEnNullSinRomperElResto() {
        val sinSol = ejemplo().lines().filterNot { it.contains("🌅") }.joinToString("\n")
        val i = informeDe(sinSol)
        assertNull(i.amanecer)
        assertNull(i.atardecer)
        assertEquals(30, i.temperatura)

        val sinRocio = ejemplo().replace(Regex(" · \\S+ Rocío 16°"), "")
        assertNull(informeDe(sinRocio).puntoRocio)
    }

    @Test
    fun horaSinTemperaturaOSinProbabilidad() {
        val texto = ejemplo().replace(Regex("(16h \\S+?)30° 10%")) { m -> m.groupValues[1] + "s/d –" }
        val h = informeDe(texto).horas.first()
        assertNull(h.temperatura)
        assertNull(h.probLluvia)
        assertEquals(24, informeDe(texto).horas.size)
    }

    @Test
    fun sinLluvia() {
        val lineas = ejemplo().lines().toMutableList()
        val inicio = lineas.indexOfFirst { it.contains("LLUVIA") }
        lineas[inicio + 1] = "☀️ Sin lluvia prevista"
        lineas.removeAt(inicio + 2)
        lineas.removeAt(inicio + 2)
        val i = informeDe(lineas.joinToString("\n"))
        assertTrue(i.sinLluvia)
        assertNull(i.probLluviaMax)
        assertNull(i.tormentas)
    }

    @Test
    fun avisoDeErrorEsNoDisponible() {
        val aviso = "⚠️ *PRONÓSTICO NO DISPONIBLE*\n🚫 *NO PUBLICAR*: no hay datos.\n" +
            "🕒 Intento: Sáb 07/11 18:00 hs\n❗ Motivo: sin conexión\n"
        val r = ParserLatest.parsear(aviso, AHORA)
        assertTrue(r is ResultadoLatest.NoDisponible)
        assertEquals("sin conexión", (r as ResultadoLatest.NoDisponible).motivo)
    }

    @Test
    fun placeholderInicialEsNoDisponible() {
        val r = ParserLatest.parsear("⏳ *Aún no se generó ningún pronóstico.*\n🚫 No publicar este texto.", AHORA)
        assertTrue(r is ResultadoLatest.NoDisponible)
    }

    @Test
    fun textoVacioOBasuraEsIlegible() {
        assertTrue(ParserLatest.parsear("", AHORA) is ResultadoLatest.Ilegible)
        assertTrue(ParserLatest.parsear("   \n  ", AHORA) is ResultadoLatest.Ilegible)
        assertTrue(ParserLatest.parsear("<html>404: Not Found</html>", AHORA) is ResultadoLatest.Ilegible)
    }

    @Test
    fun sinTemperaturaEsIlegible() {
        val r = ParserLatest.parsear(ejemplo().replace("*30°*", "*s/d*"), AHORA)
        assertTrue(r is ResultadoLatest.Ilegible)
    }

    @Test
    fun anioSeInfiereYRetrocedeEnFinDeAnio() {
        val texto = ejemplo().replace("07/11", "31/12").replace("15:05 hs", "23:05 hs")
        val i = informeDe(texto, ZonedDateTime.of(2027, 1, 1, 0, 30, 0, 0, ZONA_NOGOYA))
        assertEquals(2026, i.generado.year)
        assertNotNull(i.generado)
    }

    @Test
    fun fechaInvalidaEsIlegible() {
        val r = ParserLatest.parsear(ejemplo().replace("07/11", "31/02"), AHORA)
        assertTrue(r is ResultadoLatest.Ilegible)
    }
}
