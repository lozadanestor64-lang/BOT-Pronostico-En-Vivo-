package ar.nogoya.pronostico.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ar.nogoya.pronostico.MainActivity
import ar.nogoya.pronostico.R
import ar.nogoya.pronostico.aviso.Preferencias
import ar.nogoya.pronostico.aviso.ProgramadorAvisos
import ar.nogoya.pronostico.datos.AlmacenPrefs
import ar.nogoya.pronostico.datos.ParserLatest
import ar.nogoya.pronostico.datos.ResultadoLatest
import ar.nogoya.pronostico.datos.ZONA_NOGOYA
import java.time.ZonedDateTime

/** Widget de pantalla de inicio. Dibuja con la última copia guardada; la actualiza el worker. */
class PronosticoWidget : AppWidgetProvider() {

    override fun onUpdate(contexto: Context, gestor: AppWidgetManager, ids: IntArray) {
        for (id in ids) dibujar(contexto, gestor, id)
    }

    // El primer widget agregado programa la revisión periódica en segundo plano.
    override fun onEnabled(contexto: Context) {
        ProgramadorAvisos.programar(contexto)
    }

    override fun onDisabled(contexto: Context) {
        if (!Preferencias(contexto).avisosActivados) ProgramadorAvisos.cancelar(contexto)
    }

    companion object {
        private fun ids(contexto: Context, gestor: AppWidgetManager): IntArray =
            gestor.getAppWidgetIds(ComponentName(contexto, PronosticoWidget::class.java))

        fun hayWidgets(contexto: Context): Boolean =
            ids(contexto, AppWidgetManager.getInstance(contexto)).isNotEmpty()

        fun refrescarTodos(contexto: Context) {
            val gestor = AppWidgetManager.getInstance(contexto)
            for (id in ids(contexto, gestor)) dibujar(contexto, gestor, id)
        }

        private fun dibujar(contexto: Context, gestor: AppWidgetManager, id: Int) {
            val ahora = ZonedDateTime.now(ZONA_NOGOYA)
            val guardado = AlmacenPrefs(contexto).leer()
            val resultado = guardado?.let { ParserLatest.parsear(it.texto, ahora) }
            val textos = if (resultado is ResultadoLatest.Pronostico) {
                construirTextosWidget(resultado.informe, ahora)
            } else {
                textosWidgetVacios()
            }

            val vistas = RemoteViews(contexto.packageName, R.layout.widget_pronostico)
            vistas.setTextViewText(R.id.w_emoji, textos.emoji)
            vistas.setTextViewText(R.id.w_temp, textos.temperatura)
            vistas.setTextViewText(R.id.w_desc, textos.descripcion)
            vistas.setTextViewText(R.id.w_detalle, textos.detalle)
            vistas.setTextViewText(R.id.w_estado, textos.estado)
            val colorEstado = if (textos.vencido) 0xFFFF6B6B.toInt() else 0xFF7BE0A8.toInt()
            vistas.setTextColor(R.id.w_estado, colorEstado)

            val abrirApp = PendingIntent.getActivity(
                contexto,
                2,
                Intent(contexto, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            vistas.setOnClickPendingIntent(R.id.w_raiz, abrirApp)
            gestor.updateAppWidget(id, vistas)
        }
    }
}
