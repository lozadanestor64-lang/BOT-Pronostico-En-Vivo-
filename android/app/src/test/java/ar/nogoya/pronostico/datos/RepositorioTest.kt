package ar.nogoya.pronostico.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FuenteFalsa(private val respuesta: () -> String) : FuenteTexto {
    var llamadas = 0
    override fun descargar(): String {
        llamadas++
        return respuesta()
    }
}

private class AlmacenFalso(var guardado: Guardado? = null) : Almacen {
    override fun leer(): Guardado? = guardado
    override fun guardar(guardado: Guardado) {
        this.guardado = guardado
    }
}

class RepositorioTest {

    private fun repo(fuente: FuenteTexto, almacen: Almacen) = Repositorio(fuente, almacen) { AHORA }

    @Test
    fun descargaCorrectaSeGuardaYNoLlevaAviso() {
        val almacen = AlmacenFalso()
        val carga = repo(FuenteFalsa { ejemplo() }, almacen).cargar()
        carga as Carga.Exito
        assertTrue(carga.resultado is ResultadoLatest.Pronostico)
        assertNull(carga.aviso)
        assertEquals(ejemplo(), almacen.guardado!!.texto)
    }

    @Test
    fun sinConexionUsaLoGuardadoConAviso() {
        val almacen = AlmacenFalso(Guardado(ejemplo(), java.time.Instant.now()))
        val carga = repo(FuenteFalsa { throw IOException("sin red") }, almacen).cargar()
        carga as Carga.Exito
        assertTrue(carga.resultado is ResultadoLatest.Pronostico)
        assertNotNull(carga.aviso)
        assertTrue(carga.aviso!!.contains("Sin conexión"))
        assertTrue(carga.aviso!!.contains("guardados"))
    }

    @Test
    fun sinConexionYSinGuardadoEsFallo() {
        val carga = repo(FuenteFalsa { throw IOException("sin red") }, AlmacenFalso()).cargar()
        assertTrue(carga is Carga.Fallo)
        assertTrue((carga as Carga.Fallo).mensaje.contains("sin red"))
    }

    @Test
    fun textoIlegibleUsaGuardadoYNoLoSobrescribe() {
        val original = Guardado(ejemplo(), java.time.Instant.now())
        val almacen = AlmacenFalso(original)
        val carga = repo(FuenteFalsa { "<html>404</html>" }, almacen).cargar()
        carga as Carga.Exito
        assertTrue(carga.aviso!!.contains("no se pudo leer"))
        assertEquals(original, almacen.guardado)
    }

    @Test
    fun textoIlegibleSinGuardadoEsFallo() {
        val almacen = AlmacenFalso()
        val carga = repo(FuenteFalsa { "basura" }, almacen).cargar()
        assertTrue(carga is Carga.Fallo)
        assertNull(almacen.guardado)
    }

    @Test
    fun avisoDelMotorSeMuestraYNuncaSeGuardaNiSeReemplazaPorLoViejo() {
        val aviso = "⚠️ *PRONÓSTICO NO DISPONIBLE*\n❗ Motivo: falla de red\n"
        val almacen = AlmacenFalso(Guardado(ejemplo(), java.time.Instant.now()))
        val antes = almacen.guardado
        val carga = repo(FuenteFalsa { aviso }, almacen).cargar()
        carga as Carga.Exito
        assertTrue(carga.resultado is ResultadoLatest.NoDisponible)
        assertNull(carga.aviso)
        assertEquals(antes, almacen.guardado)
    }
}
