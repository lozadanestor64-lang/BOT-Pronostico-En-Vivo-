package ar.nogoya.pronostico.aviso

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import ar.nogoya.pronostico.BuildConfig
import ar.nogoya.pronostico.datos.AlmacenPrefs
import ar.nogoya.pronostico.datos.Carga
import ar.nogoya.pronostico.datos.FuenteHttp
import ar.nogoya.pronostico.datos.Repositorio
import ar.nogoya.pronostico.datos.ResultadoLatest
import ar.nogoya.pronostico.datos.Vigencia
import ar.nogoya.pronostico.datos.ZONA_NOGOYA
import ar.nogoya.pronostico.datos.vigencia
import ar.nogoya.pronostico.widget.PronosticoWidget
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Revisa latest.txt en segundo plano: refresca el widget y, si están activados, avisa. */
class AvisoWorker(contexto: Context, parametros: WorkerParameters) : Worker(contexto, parametros) {

    override fun doWork(): Result {
        val contexto = applicationContext
        val prefs = Preferencias(contexto)

        val carga = Repositorio(FuenteHttp(BuildConfig.URL_LATEST), AlmacenPrefs(contexto)).cargar()
        PronosticoWidget.refrescarTodos(contexto)
        if (!prefs.avisosActivados) return Result.success()

        // Con aviso = no es una descarga nueva (sin conexión o ilegible): no hay nada que anunciar.
        if (carga !is Carga.Exito || carga.aviso != null) return Result.success()
        val resultado = carga.resultado
        if (resultado !is ResultadoLatest.Pronostico) return Result.success()
        val informe = resultado.informe

        val ahora = ZonedDateTime.now(ZONA_NOGOYA)
        if (informe.vigencia(ahora) != Vigencia.VIGENTE) return Result.success()
        val silenciosa = esHorarioSilencioso(ahora)

        // 1) Pronóstico nuevo
        if (debeAvisar(informe, ahora, prefs.ultimoAvisado)) {
            val publicada = Notificaciones.publicar(contexto, construirAviso(informe), silenciosa)
            if (publicada) prefs.ultimoAvisado = informe.generado.toInstant().toEpochMilli()
        }

        // 2) Alerta de lluvia o tormenta cercana (una sola vez por evento)
        val alerta = construirAlerta(informe)
        if (alerta == null) {
            prefs.ultimaAlerta = ""
        } else if (alerta.clave != prefs.ultimaAlerta) {
            val publicada = Notificaciones.publicar(contexto, alerta.contenido, silenciosa, esAlerta = true)
            if (publicada) prefs.ultimaAlerta = alerta.clave
        }
        return Result.success()
    }
}

object ProgramadorAvisos {
    // Si algún día cambia el intervalo, usar un nombre nuevo (KEEP conserva el trabajo existente).
    private const val NOMBRE = "pronostico-aviso-v1"

    fun programar(contexto: Context) {
        val pedido = PeriodicWorkRequestBuilder<AvisoWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(contexto.applicationContext)
            .enqueueUniquePeriodicWork(NOMBRE, ExistingPeriodicWorkPolicy.KEEP, pedido)
    }

    fun cancelar(contexto: Context) {
        WorkManager.getInstance(contexto.applicationContext).cancelUniqueWork(NOMBRE)
    }
}
