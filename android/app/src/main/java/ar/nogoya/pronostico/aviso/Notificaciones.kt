package ar.nogoya.pronostico.aviso

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.nogoya.pronostico.MainActivity
import ar.nogoya.pronostico.R

object Notificaciones {
    private const val CANAL_AVISO = "pronostico_nuevo"
    private const val CANAL_ALERTA = "alerta_lluvia"
    private const val ID_AVISO = 1001
    private const val ID_ALERTA = 1002

    fun tienePermiso(contexto: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Devuelve true si la notificación se publicó. Una nueva reemplaza a la anterior del mismo tipo.
     * Las alertas usan un canal propio, de mayor importancia, para poder silenciarlas por separado.
     */
    @SuppressLint("MissingPermission")
    fun publicar(
        contexto: Context,
        contenido: ContenidoAviso,
        silenciosa: Boolean,
        esAlerta: Boolean = false,
    ): Boolean {
        if (!tienePermiso(contexto)) return false
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled()) return false

        val canal = if (esAlerta) CANAL_ALERTA else CANAL_AVISO
        gestor.createNotificationChannel(
            if (esAlerta) {
                NotificationChannelCompat.Builder(canal, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Alertas de lluvia y tormenta")
                    .setDescription("Aviso cuando la lluvia o una tormenta empiezan pronto")
                    .build()
            } else {
                NotificationChannelCompat.Builder(canal, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Pronóstico nuevo")
                    .setDescription("Aviso cada vez que hay un pronóstico nuevo de Nogoyá")
                    .build()
            }
        )

        val abrirApp = PendingIntent.getActivity(
            contexto,
            if (esAlerta) 1 else 0,
            Intent(contexto, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificacion = NotificationCompat.Builder(contexto, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(contenido.titulo)
            .setContentText(contenido.resumen)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contenido.detalle))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .setSilent(silenciosa)
            .setPriority(if (esAlerta) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        gestor.notify(if (esAlerta) ID_ALERTA else ID_AVISO, notificacion)
        return true
    }
}
