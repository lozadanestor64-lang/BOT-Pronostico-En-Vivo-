package ar.nogoya.pronostico.datos

import java.io.IOException
import java.time.Instant
import java.time.ZonedDateTime

sealed interface Carga {
    /** [aviso] se muestra cuando el resultado NO es la descarga nueva (p. ej. sin conexión). */
    data class Exito(val resultado: ResultadoLatest, val aviso: String? = null) : Carga
    data class Fallo(val mensaje: String) : Carga
}

class Repositorio(
    private val fuente: FuenteTexto,
    private val almacen: Almacen,
    private val reloj: () -> ZonedDateTime = { ZonedDateTime.now(ZONA_NOGOYA) },
) {

    /**
     * Descarga latest.txt. Solo los pronósticos válidos se guardan. Si la descarga o la lectura
     * fallan, se recurre a la última copia guardada, siempre acompañada de un aviso; la vigencia
     * real del informe la decide la pantalla.
     */
    fun cargar(): Carga {
        val motivo = try {
            val texto = fuente.descargar()
            when (val resultado = ParserLatest.parsear(texto, reloj())) {
                is ResultadoLatest.Pronostico -> {
                    almacen.guardar(Guardado(texto, Instant.now()))
                    return Carga.Exito(resultado)
                }
                is ResultadoLatest.NoDisponible -> return Carga.Exito(resultado)
                is ResultadoLatest.Ilegible ->
                    "El informe descargado no se pudo leer (${resultado.razon})."
            }
        } catch (e: IOException) {
            "Sin conexión con GitHub (${e.message ?: "error de red"})."
        }
        return desdeGuardado(motivo)
    }

    private fun desdeGuardado(motivo: String): Carga {
        val guardado = almacen.leer() ?: return Carga.Fallo(motivo)
        return when (val resultado = ParserLatest.parsear(guardado.texto, reloj())) {
            is ResultadoLatest.Pronostico ->
                Carga.Exito(resultado, aviso = "$motivo Se muestran los últimos datos guardados.")
            else -> Carga.Fallo(motivo)
        }
    }
}
