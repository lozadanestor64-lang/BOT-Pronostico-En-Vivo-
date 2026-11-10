package ar.nogoya.pronostico.datos

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Origen del texto de latest.txt. Falla con IOException. */
interface FuenteTexto {
    @Throws(IOException::class)
    fun descargar(): String
}

class FuenteHttp(
    private val url: String,
    private val reloj: () -> Long = { System.currentTimeMillis() },
) : FuenteTexto {

    override fun descargar(): String {
        // El parámetro t evita copias en caché de GitHub (cambia cada minuto).
        val conexion = URL("${url}?t=${reloj() / 60_000}").openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 10_000
            conexion.readTimeout = 15_000
            conexion.requestMethod = "GET"
            conexion.setRequestProperty("Cache-Control", "no-cache")
            conexion.setRequestProperty("User-Agent", "PronosticoNogoya-Android/1.0")

            val codigo = conexion.responseCode
            if (codigo == 404) {
                throw IOException(
                    "no se encontró latest.txt (HTTP 404): el repositorio debe ser público " +
                        "y el workflow del pronóstico debe haberse ejecutado al menos una vez"
                )
            }
            if (codigo != 200) throw IOException("el servidor respondió HTTP $codigo")

            val salida = ByteArrayOutputStream()
            conexion.inputStream.use { entrada ->
                val buffer = ByteArray(4096)
                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos < 0) break
                    salida.write(buffer, 0, leidos)
                    if (salida.size() > LIMITE_BYTES) throw IOException("el archivo es demasiado grande")
                }
            }
            return String(salida.toByteArray(), Charsets.UTF_8)
        } finally {
            conexion.disconnect()
        }
    }

    private companion object {
        const val LIMITE_BYTES = 64 * 1024
    }
}

data class Guardado(val texto: String, val descargadoEn: Instant)

/** Copia local de la última descarga válida (solo pronósticos, nunca avisos). */
interface Almacen {
    fun leer(): Guardado?
    fun guardar(guardado: Guardado)
}

class AlmacenPrefs(contexto: Context) : Almacen {
    private val prefs = contexto.applicationContext
        .getSharedPreferences("cache_pronostico", Context.MODE_PRIVATE)

    override fun leer(): Guardado? {
        val texto = prefs.getString(CLAVE_TEXTO, null) ?: return null
        val instante = prefs.getLong(CLAVE_INSTANTE, 0L)
        if (texto.isBlank() || instante <= 0L) return null
        return Guardado(texto, Instant.ofEpochMilli(instante))
    }

    override fun guardar(guardado: Guardado) {
        prefs.edit()
            .putString(CLAVE_TEXTO, guardado.texto)
            .putLong(CLAVE_INSTANTE, guardado.descargadoEn.toEpochMilli())
            .apply()
    }

    private companion object {
        const val CLAVE_TEXTO = "texto"
        const val CLAVE_INSTANTE = "descargado_en"
    }
}
